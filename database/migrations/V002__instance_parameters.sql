-- PREPARADA, NO EJECUTADA. Solo modelo; los adaptadores actuales NO usan estas tablas todavia.
-- Ejecutar una unica vez con cuenta administrativa; detenerse ante cualquier error, sin --force.
-- DDL tiene commits implicitos. No ejecutar desde JPA, Maven ni el arranque.
-- MySQL 8.0.16+ / MariaDB con CHECK efectivos; verificar en versiones reales antes de produccion.
-- DATETIME(6) en UTC; semillas con UTC_TIMESTAMP(6). JPA no genera esquemas.
-- Las reglas URI completas, espacios Unicode y autenticidad AES-GCM requieren Java.
-- CHECK valida la envoltura cifrada, NO autentica ni descifra; no se implementa criptografia aqui.
-- La inmutabilidad tras el alta se protege en JPA. SQL administrativo puede eludirla:
-- no ejecutar UPDATE sobre identidades/catalogo mediante procedimientos genericos.
-- modified_by es una referencia opaca al actor (usuario/operador tecnico), no una FK entre aplicaciones.
-- No hay secretos, claves maestras, identidades de ejemplo, cambios de usuarios MySQL ni asignaciones de roles.

USE llavor_academia;

CREATE TABLE academy_instance (
    id BIGINT NOT NULL,
    instance_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    academy_code VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    portal_instance_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_academy_instance_uuid (instance_id),
    CONSTRAINT ck_academy_instance_singleton CHECK (id=1),
    CONSTRAINT ck_academy_instance_code CHECK (CHAR_LENGTH(academy_code) BETWEEN 1 AND 80 AND academy_code NOT REGEXP '[^A-Z0-9_]'),
    CONSTRAINT ck_academy_instance_id CHECK (CHAR_LENGTH(instance_id)=36 AND instance_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' AND instance_id NOT REGEXP '[^0-9a-f-]' AND instance_id<>'00000000-0000-0000-0000-000000000000'),
    CONSTRAINT ck_academy_portal_instance_id CHECK (CHAR_LENGTH(portal_instance_id)=36 AND portal_instance_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' AND portal_instance_id NOT REGEXP '[^0-9a-f-]' AND portal_instance_id<>'00000000-0000-0000-0000-000000000000')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- academy_instance queda VACIA: inicializacion explicita posterior con UUID reales ya existentes.

CREATE TABLE academy_parameters (
    id BIGINT NOT NULL AUTO_INCREMENT,
    param_key VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(190) NOT NULL,
    description VARCHAR(500) NULL,
    param_group VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    value_type VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    param_value VARCHAR(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    editable TINYINT(1) NOT NULL,
    critical TINYINT(1) NOT NULL,
    is_sensitive TINYINT(1) NOT NULL,
    visible TINYINT(1) NOT NULL,
    sort_order INT NOT NULL DEFAULT 100,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    modified_by VARCHAR(190) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_academy_local_parameters_key (param_key),
    KEY ix_academy_local_parameters_catalogue (param_group, active, sort_order),
    CONSTRAINT ck_ap_key CHECK (CHAR_LENGTH(param_key) BETWEEN 1 AND 120 AND param_key REGEXP '^[a-z][A-Za-z0-9_.-]*$' AND param_key NOT REGEXP '[^A-Za-z0-9_.-]'),
    CONSTRAINT ck_ap_name CHECK (CHAR_LENGTH(TRIM(name))>0 AND name NOT REGEXP '^[[:space:]]*$' AND LOCATE(CHAR(0),name)=0),
    CONSTRAINT ck_ap_description CHECK (description IS NULL OR (CHAR_LENGTH(TRIM(description))>0 AND LOCATE(CHAR(0),description)=0)),
    CONSTRAINT ck_ap_group CHECK (HEX(param_group) IN (HEX('GENERAL'), HEX('PROVISIONING'), HEX('COMMUNICATION'), HEX('SECURITY'), HEX('IDENTITY'))),
    CONSTRAINT ck_ap_type CHECK (HEX(value_type) IN (HEX('TEXT'), HEX('BOOLEAN'), HEX('INTEGER'), HEX('DECIMAL'), HEX('URL'), HEX('EMAIL'), HEX('DATE'), HEX('DATETIME'), HEX('UUID'), HEX('ENCRYPTED_SECRET'), HEX('SHA256_VERIFIER'))),
    CONSTRAINT ck_ap_flags CHECK (active IN (0,1) AND editable IN (0,1) AND critical IN (0,1) AND is_sensitive IN (0,1) AND visible IN (0,1)),
    CONSTRAINT ck_ap_metadata CHECK (sort_order>=0 AND version>=0 AND (modified_by IS NULL OR (CHAR_LENGTH(TRIM(modified_by))>0 AND modified_by NOT REGEXP '^[[:space:]]*$' AND LOCATE(CHAR(0),modified_by)=0))),
    CONSTRAINT ck_ap_policy CHECK (
        is_sensitive = (value_type IN ('ENCRYPTED_SECRET','SHA256_VERIFIER'))
        AND (critical=0 OR editable=0) AND (is_sensitive=0 OR (critical=1 AND editable=0))
        AND (param_group NOT IN ('IDENTITY','SECURITY') OR critical=1)
        AND ((param_key NOT LIKE 'llavor.portal.provisioning.%' AND param_key NOT LIKE 'llavor.academia.%' AND param_key<>'llavor.portal.expected-instance-id') OR critical=1)
    ),
    CONSTRAINT ck_ap_catalogue CHECK (
        (param_key NOT LIKE 'llavor.portal.provisioning.%' AND param_key NOT LIKE 'llavor.academia.%') OR
        ((param_key NOT LIKE '%.credential' OR value_type='ENCRYPTED_SECRET')
        AND (param_key NOT LIKE '%.verifier' OR value_type='SHA256_VERIFIER')
        AND ((param_key NOT LIKE '%.enabled' AND param_key NOT LIKE '%.allow-loopback-http') OR value_type='BOOLEAN')
        AND (param_key NOT LIKE '%.allowed-base-url' OR value_type='URL')
        AND ((param_key NOT LIKE '%-timeout-seconds' AND param_key NOT LIKE '%.lease-seconds' AND param_key NOT LIKE '%.batch-size' AND param_key NOT LIKE '%.max-attempts') OR value_type='INTEGER'))
    ),
    CONSTRAINT ck_ap_scope CHECK (param_key NOT IN ('llavor.portal.expected-instance-id','llavor.academia.instance-id','llavor.academia.code','llavor.academia.portal-instance-id') AND value_type<>'ENCRYPTED_SECRET' AND param_key NOT LIKE 'llavor.portal.%' AND value_type<>'UUID' AND param_group<>'IDENTITY'),
    CONSTRAINT ck_ap_value CHECK (param_value IS NULL OR (LOCATE(CHAR(0),param_value)=0 AND (CASE value_type
            WHEN 'TEXT' THEN 1
            WHEN 'BOOLEAN' THEN HEX(param_value) IN (HEX('true'),HEX('false'))
            WHEN 'INTEGER' THEN CASE WHEN param_value REGEXP '^(0|-?[1-9][0-9]{0,18})$' AND param_value NOT REGEXP '[^0-9-]'
                THEN CAST(param_value AS DECIMAL(20,0)) BETWEEN -9223372036854775808 AND 9223372036854775807 ELSE 0 END
            WHEN 'DECIMAL' THEN param_value REGEXP '^-?(0|[1-9][0-9]{0,14})([.][0-9]{0,3}[1-9])?$'
                AND param_value NOT REGEXP '[^0-9.-]' AND HEX(param_value)<>HEX('-0')
            WHEN 'DATE' THEN CASE WHEN CHAR_LENGTH(param_value)=10 AND param_value REGEXP '^[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}$'
                THEN CAST(SUBSTRING(param_value,6,2) AS UNSIGNED) BETWEEN 1 AND 12 AND CAST(SUBSTRING(param_value,9,2) AS UNSIGNED) BETWEEN 1 AND
                CASE CAST(SUBSTRING(param_value,6,2) AS UNSIGNED) WHEN 2 THEN CASE WHEN MOD(CAST(SUBSTRING(param_value,1,4) AS UNSIGNED),400)=0 OR (MOD(CAST(SUBSTRING(param_value,1,4) AS UNSIGNED),4)=0 AND MOD(CAST(SUBSTRING(param_value,1,4) AS UNSIGNED),100)<>0) THEN 29 ELSE 28 END
                    WHEN 4 THEN 30 WHEN 6 THEN 30 WHEN 9 THEN 30 WHEN 11 THEN 30 ELSE 31 END ELSE 0 END
            WHEN 'DATETIME' THEN CASE WHEN param_value REGEXP '^[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}([.][0-9]{1,6})?Z$'
                AND param_value NOT REGEXP '[^0-9TZ:.-]'
                THEN CAST(SUBSTRING(param_value,6,2) AS UNSIGNED) BETWEEN 1 AND 12 AND CAST(SUBSTRING(param_value,9,2) AS UNSIGNED) BETWEEN 1 AND
                CASE CAST(SUBSTRING(param_value,6,2) AS UNSIGNED) WHEN 2 THEN CASE WHEN MOD(CAST(SUBSTRING(param_value,1,4) AS UNSIGNED),400)=0 OR (MOD(CAST(SUBSTRING(param_value,1,4) AS UNSIGNED),4)=0 AND MOD(CAST(SUBSTRING(param_value,1,4) AS UNSIGNED),100)<>0) THEN 29 ELSE 28 END
                    WHEN 4 THEN 30 WHEN 6 THEN 30 WHEN 9 THEN 30 WHEN 11 THEN 30 ELSE 31 END AND CAST(SUBSTRING(param_value,12,2) AS UNSIGNED)<=23
                    AND CAST(SUBSTRING(param_value,15,2) AS UNSIGNED)<=59 AND CAST(SUBSTRING(param_value,18,2) AS UNSIGNED)<=59 ELSE 0 END
            WHEN 'UUID' THEN CHAR_LENGTH(param_value)=36
                AND param_value REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                AND param_value NOT REGEXP '[^0-9a-f-]' AND param_value<>'00000000-0000-0000-0000-000000000000'
            WHEN 'SHA256_VERIFIER' THEN CHAR_LENGTH(param_value)=64 AND param_value NOT REGEXP '[^0-9a-f]'
            WHEN 'ENCRYPTED_SECRET' THEN param_value REGEXP '^v1[.][A-Za-z0-9_-]{1,32}[.][A-Za-z0-9_-]{16}[.][A-Za-z0-9_-]{23,2731}$'
                AND param_value NOT REGEXP '[^A-Za-z0-9_.-]'
                AND MOD(CHAR_LENGTH(SUBSTRING_INDEX(param_value,'.',-1)),4)<>1
                AND (MOD(CHAR_LENGTH(SUBSTRING_INDEX(param_value,'.',-1)),4)<>2 OR RIGHT(param_value,1) REGEXP '^[AQgw]$')
                AND (MOD(CHAR_LENGTH(SUBSTRING_INDEX(param_value,'.',-1)),4)<>3 OR RIGHT(param_value,1) REGEXP '^[AEIMQUYcgkosw048]$')
            WHEN 'EMAIL' THEN CHAR_LENGTH(param_value)<=254 AND param_value NOT REGEXP '[^!-~]'
                AND param_value REGEXP '^[A-Za-z0-9.!#$%&''*+/=?^_`{|}~-]+@[A-Za-z0-9-]+([.][A-Za-z0-9-]+)+$'
            WHEN 'URL' THEN CHAR_LENGTH(param_value)<=500 AND param_value NOT REGEXP '[^!-~]'
                AND param_value REGEXP '^https?://[^/?#@[:space:]]+(/[^?#[:space:]]*)?$' AND RIGHT(param_value,1)<>'/'
                AND (HEX(LEFT(param_value,7))=HEX('http://') OR HEX(LEFT(param_value,8))=HEX('https://'))
                AND HEX(SUBSTRING_INDEX(SUBSTRING(param_value,LOCATE('://',param_value)+3),'/',1))
                    =HEX(LOWER(SUBSTRING_INDEX(SUBSTRING(param_value,LOCATE('://',param_value)+3),'/',1)))
            ELSE 0 END)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO academy_parameters (param_key,name,param_group,value_type,param_value,active,editable,critical,is_sensitive,visible,sort_order,created_at,updated_at) VALUES
    ('llavor.academia.provisioning.enabled', 'Habilitar receptor', 'PROVISIONING', 'BOOLEAN', 'false', 1, 0, 1, 0, 1, 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('llavor.academia.provisioning.allow-loopback-http', 'Permitir HTTP local', 'PROVISIONING', 'BOOLEAN', 'false', 1, 0, 1, 0, 1, 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
