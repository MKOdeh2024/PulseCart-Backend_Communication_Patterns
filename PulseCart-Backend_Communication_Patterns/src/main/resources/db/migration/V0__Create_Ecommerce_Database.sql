DO $$ 
BEGIN
   IF NOT EXISTS (
      SELECT 
      FROM   pg_catalog.pg_database 
      WHERE  datname = 'ecommerce') THEN
      
      CREATE DATABASE ecommerce;
   END IF;
END
$$;
