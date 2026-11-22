-- Llavor Academia V001. PREPARADA, NO EJECUTADA.
-- DESTINO EXCLUSIVO: llavor_academia. Primera migracion funcional.
-- Ejecutar una sola vez con cuenta administrativa. No usar --force.
-- DDL con commits implicitos: detenerse ante errores y revisar estado parcial.
-- MySQL 8.0.45 / compatibilidad prevista MariaDB con CHECK efectivos; validar motor real.
-- No se crean aulas ni recibos de ejemplo. No hay acceso ni FK a otra base de datos.
-- Fechas DATETIME(6) en UTC, generadas por Java. Generacion JPA desactivada.
-- La FK de contexto adicional impide enlazar un recibo de exito a otra identidad.

USE llavor_academia;

CREATE TABLE classrooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    portal_instance_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    portal_classroom_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    technical_academy_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(190) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    prepared_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_local_classroom_identity (portal_instance_id, portal_classroom_id),
    UNIQUE KEY uk_local_classroom_code (portal_instance_id, code),
    UNIQUE KEY uk_local_classroom_context (id, portal_instance_id, portal_classroom_id, technical_academy_id),
    KEY ix_local_classroom_academy (technical_academy_id, id),
    CONSTRAINT ck_local_classroom_portal CHECK (CHAR_LENGTH(portal_instance_id) = 36 AND portal_instance_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' AND HEX(portal_instance_id) = HEX(LOWER(portal_instance_id)) AND portal_instance_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT ck_local_classroom_uuid CHECK (CHAR_LENGTH(portal_classroom_id) = 36 AND portal_classroom_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' AND HEX(portal_classroom_id) = HEX(LOWER(portal_classroom_id)) AND portal_classroom_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT ck_local_classroom_academy CHECK (CHAR_LENGTH(technical_academy_id) = 36 AND technical_academy_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' AND HEX(technical_academy_id) = HEX(LOWER(technical_academy_id)) AND technical_academy_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT ck_local_classroom_code CHECK (
        CHAR_LENGTH(code) BETWEEN 1 AND 80 AND code NOT REGEXP '[^A-Z0-9_]'
        AND HEX(code) = HEX(UPPER(TRIM(code)))),
    CONSTRAINT ck_local_classroom_name CHECK (
        CHAR_LENGTH(TRIM(name)) > 0 AND name NOT REGEXP '^[[:space:]]*$'),
    CONSTRAINT ck_local_classroom_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE classroom_provisioning_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    portal_instance_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    portal_classroom_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    technical_academy_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    local_classroom_id BIGINT NULL,
    request_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    error_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL,
    completed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_receipt_operation (portal_instance_id, operation_id),
    KEY ix_receipt_classroom_identity (portal_instance_id, portal_classroom_id),
    KEY ix_receipt_local_context (local_classroom_id, portal_instance_id, portal_classroom_id, technical_academy_id),
    CONSTRAINT fk_receipt_classroom FOREIGN KEY (local_classroom_id) REFERENCES classrooms (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_receipt_classroom_context FOREIGN KEY (local_classroom_id, portal_instance_id, portal_classroom_id, technical_academy_id)
        REFERENCES classrooms (id, portal_instance_id, portal_classroom_id, technical_academy_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_receipt_portal CHECK (CHAR_LENGTH(portal_instance_id) = 36 AND portal_instance_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' AND HEX(portal_instance_id) = HEX(LOWER(portal_instance_id)) AND portal_instance_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT ck_receipt_operation CHECK (CHAR_LENGTH(operation_id) = 36 AND operation_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' AND HEX(operation_id) = HEX(LOWER(operation_id)) AND operation_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT ck_receipt_classroom CHECK (CHAR_LENGTH(portal_classroom_id) = 36 AND portal_classroom_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' AND HEX(portal_classroom_id) = HEX(LOWER(portal_classroom_id)) AND portal_classroom_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT ck_receipt_academy CHECK (CHAR_LENGTH(technical_academy_id) = 36 AND technical_academy_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' AND HEX(technical_academy_id) = HEX(LOWER(technical_academy_id)) AND technical_academy_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT ck_receipt_result CHECK (HEX(result) IN (HEX('READY'), HEX('FAILED'))),
    CONSTRAINT ck_receipt_error CHECK (error_code IS NULL OR (HEX(error_code) IN (HEX('INVALID_REQUEST'), HEX('UNSUPPORTED_CONTRACT_VERSION'), HEX('UNAUTHORIZED'), HEX('FORBIDDEN'), HEX('ACADEMY_IDENTITY_MISMATCH'), HEX('PORTAL_IDENTITY_MISMATCH'), HEX('OPERATION_CONTENT_CONFLICT'), HEX('CLASSROOM_IDENTITY_CONFLICT'), HEX('ACADEMY_INACTIVE'), HEX('INVALID_CONFIGURATION'), HEX('TIMEOUT'), HEX('REMOTE_UNAVAILABLE'), HEX('RATE_LIMITED'), HEX('PREPARATION_REJECTED')))),
    CONSTRAINT ck_receipt_hash CHECK (
        CHAR_LENGTH(request_hash) = 64 AND request_hash NOT REGEXP '[^0-9a-f]' AND HEX(request_hash) = HEX(LOWER(request_hash))),
    CONSTRAINT ck_receipt_local_id CHECK (local_classroom_id IS NULL OR local_classroom_id > 0),
    CONSTRAINT ck_receipt_result_context CHECK (
        (result = 'READY' AND local_classroom_id IS NOT NULL AND error_code IS NULL)
        OR (result = 'FAILED' AND local_classroom_id IS NULL AND error_code IS NOT NULL AND error_code = 'PREPARATION_REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
