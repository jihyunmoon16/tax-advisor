#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

usage() {
  cat <<'EOF'
Usage:
  ./deploy.sh

Optional environment variables:
  GCP_PROJECT_ID             Default: black-works-488802-r1
  GCP_REGION                 Default: us-central1
  CLOUD_RUN_SERVICE          Default: tax-advisor
  ARTIFACT_REPOSITORY        Default: tax-advisor
  GEMINI_SECRET_NAME         Default: gemini-api-key
  CLOUD_RUN_SERVICE_ACCOUNT  Default: <PROJECT_NUMBER>-compute@developer.gserviceaccount.com
  IMAGE_TAG                  Default: <timestamp>-amd64

Required in .env:
  GEMINI_API_KEY
  GEMINI_MODEL               (optional, default: gemini-3-flash-preview)
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ -x /opt/homebrew/share/google-cloud-sdk/bin/gcloud ]]; then
  export PATH="/opt/homebrew/share/google-cloud-sdk/bin:$PATH"
fi

for cmd in gcloud docker; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[ERROR] '$cmd' command not found."
    exit 1
  fi
done

if [[ ! -f ".env" ]]; then
  echo "[ERROR] .env file not found. Create it from .env.example first."
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

if [[ -z "${GEMINI_API_KEY:-}" ]]; then
  echo "[ERROR] GEMINI_API_KEY is empty in .env"
  exit 1
fi

PROJECT_ID="${GCP_PROJECT_ID:-black-works-488802-r1}"
REGION="${GCP_REGION:-us-central1}"
SERVICE_NAME="${CLOUD_RUN_SERVICE:-tax-advisor}"
REPOSITORY="${ARTIFACT_REPOSITORY:-tax-advisor}"
SECRET_NAME="${GEMINI_SECRET_NAME:-gemini-api-key}"
MODEL_NAME="${GEMINI_MODEL:-gemini-3-flash-preview}"
IMAGE_TAG="${IMAGE_TAG:-$(date +%Y%m%d-%H%M%S)-amd64}"

ACTIVE_ACCOUNT="$(gcloud auth list --filter=status:ACTIVE --format='value(account)' | head -n1)"
if [[ -z "$ACTIVE_ACCOUNT" ]]; then
  echo "[ERROR] No active gcloud account. Run: gcloud auth login"
  exit 1
fi

echo "[INFO] Active gcloud account: $ACTIVE_ACCOUNT"
echo "[INFO] Project: $PROJECT_ID"
echo "[INFO] Region: $REGION"

gcloud config set project "$PROJECT_ID" >/dev/null
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com >/dev/null

if ! gcloud artifacts repositories describe "$REPOSITORY" --location="$REGION" >/dev/null 2>&1; then
  echo "[INFO] Creating Artifact Registry repository: $REPOSITORY"
  gcloud artifacts repositories create "$REPOSITORY" \
    --repository-format=docker \
    --location="$REGION" \
    --description="Tax advisor images" >/dev/null
fi

if ! gcloud secrets describe "$SECRET_NAME" >/dev/null 2>&1; then
  echo "[INFO] Creating Secret Manager secret: $SECRET_NAME"
  gcloud secrets create "$SECRET_NAME" --replication-policy=automatic >/dev/null
fi

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
DEFAULT_RUNTIME_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
RUNTIME_SA="${CLOUD_RUN_SERVICE_ACCOUNT:-$DEFAULT_RUNTIME_SA}"
IMAGE_URI="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPOSITORY}/${SERVICE_NAME}:${IMAGE_TAG}"

echo "[INFO] Runtime service account: $RUNTIME_SA"
echo "[INFO] Image: $IMAGE_URI"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${RUNTIME_SA}" \
  --role="roles/secretmanager.secretAccessor" \
  --quiet >/dev/null

echo "[INFO] Building jar"
./gradlew bootJar -q

echo "[INFO] Configuring Docker auth for Artifact Registry"
gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet >/dev/null

echo "[INFO] Building and pushing linux/amd64 image"
DOCKER_BUILDKIT=1 docker buildx build \
  --platform linux/amd64 \
  -t "$IMAGE_URI" \
  --push .

echo "[INFO] Uploading Gemini API key to Secret Manager (new version)"
printf "%s" "$GEMINI_API_KEY" | gcloud secrets versions add "$SECRET_NAME" --data-file=- >/dev/null

echo "[INFO] Deploying Cloud Run service"
gcloud run deploy "$SERVICE_NAME" \
  --image "$IMAGE_URI" \
  --region "$REGION" \
  --allow-unauthenticated \
  --service-account "$RUNTIME_SA" \
  --set-secrets "GEMINI_API_KEY=${SECRET_NAME}:latest" \
  --set-env-vars "GEMINI_MODEL=${MODEL_NAME}" \
  --quiet >/dev/null

SERVICE_URL="$(gcloud run services describe "$SERVICE_NAME" --region "$REGION" --format='value(status.url)')"
echo
echo "[DONE] Deployment complete"
echo "SERVICE_URL=$SERVICE_URL"
echo "Tools API:  $SERVICE_URL/api/tools"
echo "Advice API: $SERVICE_URL/api/advice"
