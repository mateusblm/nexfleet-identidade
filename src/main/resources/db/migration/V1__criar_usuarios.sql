-- Começo limpo no Azure SQL: o schema identidade é criado pelo bootstrap do administrador e o
-- login do serviço só tem CONTROL nele (spring.flyway.create-schemas=false).
EXEC sys.sp_addextendedproperty
    @name = N'MS_Description', @value = N'NexFleet: dados exclusivos do servico identidade',
    @level0type = N'SCHEMA', @level0name = N'identidade';

-- O e-mail é gravado normalizado (minúsculas, sem espaços nas pontas). A comparação da CHECK usa
-- colação binária, porque a colação padrão ignora maiúsculas e a igualdade sempre passaria; o
-- DATALENGTH pega espaços à direita, que o "=" do SQL Server ignora. Com tudo minúsculo, o índice
-- único já é indiferente à caixa.
-- O hash é BCrypt ($2a/$2b/$2y, custo de 2 dígitos, 53 caracteres do alfabeto ./A-Za-z0-9): o
-- mesmo que a regex do PostgreSQL exigia, escrito com LIKE sob colação binária.
CREATE TABLE identidade.usuario (
    id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_usuario PRIMARY KEY,
    email NVARCHAR(254) COLLATE Latin1_General_100_CI_AS NOT NULL
        CONSTRAINT ux_usuario_email UNIQUE,
    senha_hash VARCHAR(60) COLLATE Latin1_General_100_BIN2 NOT NULL,
    ativo BIT NOT NULL CONSTRAINT df_usuario_ativo DEFAULT 1,
    perfil VARCHAR(20) COLLATE Latin1_General_100_BIN2 NOT NULL
        CONSTRAINT df_usuario_perfil DEFAULT 'GESTOR_FROTA',
    CONSTRAINT email_normalizado CHECK (
        email COLLATE Latin1_General_100_BIN2 = LOWER(TRIM(email))
        AND DATALENGTH(email) = DATALENGTH(LOWER(TRIM(email)))),
    CONSTRAINT senha_bcrypt CHECK (
        DATALENGTH(senha_hash) = 60
        AND senha_hash LIKE '$2[aby]$[0-9][0-9]$%'
        AND SUBSTRING(senha_hash, 8, 53) NOT LIKE '%[^./A-Za-z0-9]%'),
    CONSTRAINT usuario_perfil_valido CHECK (perfil IN ('ADMINISTRADOR', 'GESTOR_FROTA'))
);

EXEC sys.sp_addextendedproperty
    @name = N'MS_Description', @value = N'Contas de acesso ao NexFleet',
    @level0type = N'SCHEMA', @level0name = N'identidade',
    @level1type = N'TABLE', @level1name = N'usuario';
