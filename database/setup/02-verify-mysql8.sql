-- Llavor Academy: verificaciones de solo lectura, como administrador.
-- Se puede ejecutar antes del aprovisionamiento solo el bloque PREVIO.
-- El bloque POSTERIOR requiere que ya existan la base y la cuenta.

-- PREVIO: confirmar servidor local MySQL 8.0.16+, sin roles obligatorios.
SELECT VERSION() AS version_servidor,
       @@hostname AS servidor,
       @@port AS puerto,
       @@GLOBAL.partial_revokes AS partial_revokes,
       @@GLOBAL.mandatory_roles AS roles_obligatorios;
-- Si hay roles obligatorios con privilegios, detenerse y revisar su alcance.
-- El script NO altera roles ni configuracion global.

-- ANTES: ambas consultas deben devolver cero filas.
-- DESPUES: una base y una unica cuenta, con Host = localhost.
SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = 'llavor_academia';

SELECT User, Host
FROM mysql.user
WHERE User = 'llavor_academia_user'
ORDER BY Host;

-- POSTERIOR: ejecutar solo despues del aprovisionamiento.
SHOW CREATE DATABASE `llavor_academia`;
SHOW GRANTS FOR 'llavor_academia_user'@'localhost';

-- Resultado esperado de SHOW GRANTS:
-- USAGE ON *.*: ausencia de privilegios globales (no concede acceso global).
-- SELECT, INSERT, UPDATE, DELETE exclusivamente sobre la base propia.
-- Sin WITH GRANT OPTION, roles ni otros permisos.
-- El "_" puede aparecer escapado como \_ dependiendo de partial_revokes.