-- Contas existentes não ganham privilégios administrativos automaticamente.
ALTER TABLE identidade.usuario
    ADD COLUMN perfil VARCHAR(20) NOT NULL DEFAULT 'GESTOR_FROTA',
    ADD CONSTRAINT usuario_perfil_valido CHECK (perfil IN ('ADMINISTRADOR', 'GESTOR_FROTA'));
