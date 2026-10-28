\getenv app_password BOOKLANE_DB_PASSWORD

SELECT 'CREATE ROLE booklane_booking LOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'booklane_booking')\gexec

ALTER ROLE booklane_booking PASSWORD :'app_password';
ALTER ROLE booklane_booking IN DATABASE booklane SET search_path = booking, public;
GRANT CONNECT ON DATABASE booklane TO booklane_booking;
CREATE SCHEMA IF NOT EXISTS booking AUTHORIZATION booklane_booking;
CREATE EXTENSION IF NOT EXISTS btree_gist;
