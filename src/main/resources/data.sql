INSERT INTO realized_gain (user_id, stock_name, gain_amount, realized_date) VALUES
('me', 'US_INDEX_ETF', 2500000, DATE '2026-01-10'),
('me', 'SAMSUNG_ELEC', 3200000, DATE '2026-02-10'),
('me', 'MACQUARIE_INFRA', 900000, DATE '2026-02-20');

INSERT INTO portfolio (user_id, market, stock_name, average_price, current_price, quantity) VALUES
('me', 'US', 'NVDA', 500000, 700000, 20),
('me', 'US', 'TSLA', 300000, 200000, 20),
('me', 'KR', 'SAMSUNG_ELEC', 65000, 92000, 2000),
('me', 'KR', 'MACQUARIE_INFRA', 12000, 12500, 3000);
