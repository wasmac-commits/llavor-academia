-- Llavor Academy: aprovisionamiento local, MySQL 8.0.16 o posterior.
-- PLANTILLA: sustituir el marcador SOLO en una copia privada fuera de Git.
-- Ejecutar como administrador en modo batch, SIN --force (ver README.txt).
-- Una sola ejecucion. Sin IF NOT EXISTS: si existe la BD o la cuenta,
-- detenerse e inspeccionar; no continuar con GRANT tras un error.
-- No crea tablas, no modifica cuentas existentes y no configura JDBC.

CREATE DATABASE `llavor_academia`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER 'llavor_academia_user'@'localhost'
    IDENTIFIED BY '__REEMPLAZAR_PASSWORD_LLAVOR_ACADEMIA__';

-- Permisos de aplicacion: solo lectura/escritura de datos, sin DDL.
-- Escapado exacto del "_" para evitar permisos sobre otras bases.
-- Con partial_revokes=ON el identificador se trata literalmente.
-- CHAR(92) evita depender del modo NO_BACKSLASH_ESCAPES.
-- No cambia variables globales ni concede privilegios globales.
SET @llavor_scope = IF(
    @@GLOBAL.partial_revokes = 1,
    '`llavor_academia`',
    CONCAT('`llavor', CHAR(92), '_academia`')
);
SET @llavor_grant_sql = CONCAT(
    'GRANT SELECT, INSERT, UPDATE, DELETE ON ',
    @llavor_scope,
    '.* TO ''llavor_academia_user''@''localhost'''
);
PREPARE llavor_grant FROM @llavor_grant_sql;
EXECUTE llavor_grant;
DEALLOCATE PREPARE llavor_grant;
SET @llavor_scope = NULL;
SET @llavor_grant_sql = NULL;

-- Comprobacion administrativa: sin WITH GRANT OPTION, sin roles
-- ni permisos efectivos fuera de la base indicada.
SHOW GRANTS FOR 'llavor_academia_user'@'localhost';
SHOW CREATE DATABASE `llavor_academia`;