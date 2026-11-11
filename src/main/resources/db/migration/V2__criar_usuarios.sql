CREATE TABLE identidade.usuario (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    senha_hash VARCHAR(60) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT email_normalizado CHECK (email = lower(trim(email))),
    CONSTRAINT senha_bcrypt CHECK (senha_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$')
);
