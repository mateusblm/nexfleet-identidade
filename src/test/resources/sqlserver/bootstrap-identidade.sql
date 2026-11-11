-- Reproduz o bootstrap do administrador: o schema existe antes do Flyway (create-schemas=false).
IF SCHEMA_ID('identidade') IS NULL EXEC('CREATE SCHEMA identidade');
