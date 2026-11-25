-- PREPARADA, NO EJECUTADA. Destino exclusivo llavor_academia, despues de V003.
-- Respaldo y comprobacion de aplicacion parcial obligatorios. DDL implica commits.
-- Sin semillas ni credenciales. DATETIME(6) UTC. MySQL 8.0.16+.
USE llavor_academia;
CREATE TABLE sso_credentials (
    id BIGINT NOT NULL,
    academy_instance_id BIGINT NOT NULL,
    academy_instance_uuid VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    portal_instance_uuid VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    academy_code VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    credential_version INT NOT NULL,
    master_key_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    encrypted_secret VARCHAR(103) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    credential_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DISABLED',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sso_credential_identity_key (academy_instance_uuid, key_id),
    UNIQUE KEY uk_sso_credential_operation (operation_id),
    KEY ix_sso_credential_instance (academy_instance_id, academy_instance_uuid, portal_instance_uuid, academy_code),
    CONSTRAINT fk_sso_credential_instance FOREIGN KEY
        (academy_instance_id,academy_instance_uuid,portal_instance_uuid,academy_code)
        REFERENCES academy_instance (id,instance_id,portal_instance_id,academy_code),
    CONSTRAINT ck_sso_credential_singleton CHECK (id=1 AND academy_instance_id=1),
    CONSTRAINT ck_sso_credential_key CHECK (key_id='S01' AND master_key_id='M01' AND credential_version=1),
    CONSTRAINT ck_sso_credential_state CHECK (credential_state IN ('DISABLED','ENABLED')),
    CONSTRAINT ck_sso_credential_version CHECK (version>=0),
    CONSTRAINT ck_sso_credential_dates CHECK (updated_at>=created_at),
    CONSTRAINT ck_sso_credential_envelope CHECK
        (REGEXP_LIKE(encrypted_secret,'^v1[.]M01[.][A-Za-z0-9_-]{16}[.][A-Za-z0-9_-]{79}$','c')),
    CONSTRAINT ck_sso_credential_operation CHECK
        (REGEXP_LIKE(operation_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
