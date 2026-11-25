-- PREPARADA, NO EJECUTADA. Etapa 19. Despues de V004 de Academia.
-- No sincroniza, no publica, no instala identidades ni activa trabajadores.
USE llavor_academia;
CREATE TABLE i18n_local_catalog (
    id BIGINT NOT NULL,
    catalog_version BIGINT NOT NULL DEFAULT 0,
    source_portal_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    payload LONGTEXT NULL,
    digest VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    installed_at DATETIME(6) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_i18n_local_state CHECK (id=1 AND row_version>=0 AND (
        (catalog_version=0 AND source_portal_id IS NULL AND payload IS NULL AND digest IS NULL AND installed_at IS NULL)
        OR (catalog_version>0 AND source_portal_id IS NOT NULL AND CHAR_LENGTH(source_portal_id)=36
            AND payload IS NOT NULL AND JSON_VALID(payload) AND digest IS NOT NULL AND CHAR_LENGTH(digest)=64
            AND digest NOT REGEXP '[^0-9a-f]' AND installed_at IS NOT NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO i18n_local_catalog (id) VALUES (1);
