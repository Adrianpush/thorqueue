-- ThorQueue database initialization
-- Creates separate databases for the app and Keycloak

CREATE DATABASE keycloak;

GRANT ALL PRIVILEGES ON DATABASE keycloak TO pmadmin;
GRANT ALL PRIVILEGES ON DATABASE thorqueue TO pmadmin;
