-- PREPARADA, NO EJECUTADA. Modelo administrativo de credenciales; no prepara credenciales.
-- Ejecutar una sola vez, tras respaldo y revision de aplicacion parcial, sin --force.
-- DDL implica commits. MySQL 8.0.16+; comprobar MariaDB de destino con CHECK efectivos.
-- DATETIME(6) representa UTC. No semillas, permisos, secretos ni cambios de habilitacion.
-- SQL protege valores y coherencia de cada fila; las transiciones entre versiones de una fila,
-- autorizacion y bloqueos operativos pertenecen al dominio y a los futuros servicios.
-- Destino exclusivo: llavor_academia, despues de V002.
-- El indice adicional de academy_instance no modifica sus registros ni sus identidades.
-- La FK compuesta impide recibos con UUID/codigo/Portal diferentes de la identidad local.
-- Cada revision conserva su recibo. El futuro instalador bloqueara academy_instance
-- para rechazar revisiones anteriores y contradicciones entre recibos de una operacion.
USE llavor_academia;

ALTER TABLE academy_instance
    ADD UNIQUE KEY uk_academy_instance_receipt_identity (id, instance_id, portal_instance_id, academy_code);

CREATE TABLE credential_installation_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    package_revision INT NOT NULL,
    academy_instance_id BIGINT NOT NULL,
    portal_instance_uuid VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    academy_instance_uuid VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    academy_code VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    package_sha256 VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    configuration_fingerprint VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    installation_result VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'INSTALLED',
    installed_at DATETIME(6) NOT NULL,
    verified_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_credential_receipt_revision (operation_id, package_revision),
    KEY ix_credential_receipt_identity (academy_instance_id, academy_instance_uuid, portal_instance_uuid, academy_code),
    KEY ix_credential_receipt_key (academy_instance_id, key_id, verified_at),
    CONSTRAINT fk_credential_receipt_instance FOREIGN KEY (academy_instance_id) REFERENCES academy_instance (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_credential_receipt_identity FOREIGN KEY (academy_instance_id, academy_instance_uuid, portal_instance_uuid, academy_code) REFERENCES academy_instance (id, instance_id, portal_instance_id, academy_code) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_ci_operation CHECK (CHAR_LENGTH(operation_id)=36 AND operation_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' AND HEX(operation_id)=HEX(LOWER(operation_id)) AND operation_id<>'00000000-0000-0000-0000-000000000000'),
    CONSTRAINT ck_ci_revision CHECK (package_revision>0),
    CONSTRAINT ck_ci_instance CHECK (academy_instance_id=1),
    CONSTRAINT ck_ci_portal_uuid CHECK (CHAR_LENGTH(portal_instance_uuid)=36 AND portal_instance_uuid REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' AND HEX(portal_instance_uuid)=HEX(LOWER(portal_instance_uuid)) AND portal_instance_uuid<>'00000000-0000-0000-0000-000000000000'),
    CONSTRAINT ck_ci_academy_uuid CHECK (CHAR_LENGTH(academy_instance_uuid)=36 AND academy_instance_uuid REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' AND HEX(academy_instance_uuid)=HEX(LOWER(academy_instance_uuid)) AND academy_instance_uuid<>'00000000-0000-0000-0000-000000000000'),
    CONSTRAINT ck_ci_code CHECK (CHAR_LENGTH(academy_code) BETWEEN 1 AND 80 AND academy_code NOT REGEXP '[^A-Z0-9_]' AND HEX(academy_code)=HEX(UPPER(academy_code))),
    CONSTRAINT ck_ci_key CHECK (CHAR_LENGTH(key_id) BETWEEN 1 AND 32 AND key_id NOT REGEXP '[^A-Za-z0-9_-]'),
    CONSTRAINT ck_ci_hash CHECK (CHAR_LENGTH(package_sha256)=64 AND package_sha256 NOT REGEXP '[^0-9a-f]' AND HEX(package_sha256)=HEX(LOWER(package_sha256))),
    CONSTRAINT ck_ci_fingerprint CHECK (CHAR_LENGTH(configuration_fingerprint)=64 AND configuration_fingerprint NOT REGEXP '[^0-9a-f]' AND HEX(configuration_fingerprint)=HEX(LOWER(configuration_fingerprint))),
    CONSTRAINT ck_ci_result CHECK (HEX(installation_result) IN (HEX('INSTALLED'), HEX('ALREADY_INSTALLED'))),
    CONSTRAINT ck_ci_dates CHECK (verified_at>=installed_at),
    CONSTRAINT ck_ci_version CHECK (version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
