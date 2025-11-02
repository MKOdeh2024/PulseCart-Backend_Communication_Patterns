-- Create database only if it does not exist
CREATE ROLE PulseCart LOGIN PASSWORD 'root';
CREATE SCHEMA IF NOT EXISTS ecommerce;
ALTER SCHEMA ecommerce OWNER TO PulseCart;
