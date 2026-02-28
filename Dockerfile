FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/*.jar app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
