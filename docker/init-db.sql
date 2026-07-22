-- This script runs automatically when PostgreSQL container starts for the first time.
-- It creates the separate schemas for each service.

-- Create schemas (one per service)
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS merchant;
CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS settlement;

-- Grant permissions
GRANT ALL PRIVILEGES ON SCHEMA identity TO payflow;
GRANT ALL PRIVILEGES ON SCHEMA merchant TO payflow;
GRANT ALL PRIVILEGES ON SCHEMA payment TO payflow;
GRANT ALL PRIVILEGES ON SCHEMA settlement TO payflow;
