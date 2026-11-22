LLAVOR ACADEMY - APROVISIONAMIENTO LOCAL DE LLAVOR_ACADEMIA

Archivos
  01-provision-mysql8.sql : crea la base y la cuenta y concede permisos.
  02-verify-mysql8.sql    : consultas administrativas de solo lectura.

Destino
  MySQL local 8.0.16 o posterior.
  Base: llavor_academia
  Cuenta: 'llavor_academia_user'@'localhost'
  Codificacion: utf8mb4; cotejamiento: utf8mb4_unicode_ci.
  El cotejamiento tambien existe en MariaDB. Estos scripts se preparan para
  MySQL 8; no ejecutar directamente en produccion MariaDB sin adaptarlos
  y revisarlos (partial_revokes es especifico de MySQL).

Permisos previstos
  SELECT, INSERT, UPDATE, DELETE solo sobre llavor_academia.
  No se conceden CREATE, ALTER, DROP, permisos globales, GRANT OPTION ni roles.
  No se permite gestionar usuarios ni acceder a datos de la otra aplicacion.
  Las futuras migraciones de tablas se ejecutaran con una cuenta administrativa.
  MySQL puede mostrar metadatos del sistema accesibles por defecto;
  USAGE ON *.* no constituye un privilegio global efectivo.
  El alcance del GRANT se adapta a partial_revokes para que el guion bajo
  del nombre no actue como comodin. No se modifica esta variable.

Ejecucion posterior (no se ha ejecutado ahora)
  1. Usar una cuenta administrativa de MySQL capaz de crear bases, crear
     usuarios y conceder los cuatro permisos indicados.
     Confirmar host/puerto de desarrollo y ejecutar por separado las consultas
     del bloque PREVIO de 02-verify-mysql8.sql. La base y cualquier cuenta con
     este nombre deben estar ausentes. Revisar que no haya roles obligatorios
     que concedan permisos adicionales. Si algo ya existe, detenerse:
     no borrar, alterar ni reutilizar cuentas automaticamente.

  2. Crear una copia privada de 01-provision-mysql8.sql FUERA del repositorio,
     con acceso restringido. Sustituir:
       __REEMPLAZAR_PASSWORD_LLAVOR_ACADEMIA__
     por una contrasena fuerte, unica y distinta de la otra aplicacion.
     Nunca editar la plantilla versionada con contrasenas reales ni guardar
     estas copias en Git. Si la clave contiene una comilla simple, duplicarla
     dentro del literal SQL. Para evitar ambiguedad por modos SQL, usar una
     clave sin barras inversas. No ejecutar el marcador tal cual.

  3. Ejecutar la copia en modo batch desde SIMBOLO DEL SISTEMA (cmd.exe),
     no desde PowerShell: el operador < de estos ejemplos pertenece a cmd.
     Sustituir ADMIN_MYSQL y las rutas por los valores locales apropiados.
     La opcion --password sin valor solicita la clave administrativa:
     no escribir contrasenas en la linea de comandos.

     mysql --host=localhost --user=ADMIN_MYSQL --password --default-character-set=utf8mb4 --skip-force < "C:\ruta-privada\llavor_academia-provision.sql"

     No usar --force ni un cliente que continue despues de errores.
     No es un script idempotente: CREATE falla si la base o la cuenta existe.
     Las instrucciones DDL no se deshacen con ROLLBACK; si hay un fallo parcial,
     detenerse e inspeccionar antes de cualquier nuevo intento.
     Los scripts de Portal y Academia se ejecutan por separado.

  4. Ejecutar despues las verificaciones completas, como administrador:

     mysql --host=localhost --user=ADMIN_MYSQL --password --default-character-set=utf8mb4 --skip-force < "C:\ruta-al-proyecto\database\setup\02-verify-mysql8.sql"

     Comprobar utf8mb4, una unica cuenta en localhost y SHOW GRANTS limitado
     a SELECT, INSERT, UPDATE, DELETE sobre la base propia. No debe aparecer
     WITH GRANT OPTION ni ningun otro privilegio o rol.
     Si existen roles obligatorios, SHOW GRANTS de la cuenta por si solo
     no basta: revisar tambien los permisos heredados antes de usarla.

  5. Comprobacion opcional de aislamiento, entrando con la cuenta nueva:
     mysql --host=localhost --user=llavor_academia_user --password --database=llavor_academia
     En esa sesion:
       SELECT CURRENT_USER(), DATABASE();
       SHOW TABLES;
       SHOW TABLES FROM `llavor_portal`;
     La ultima consulta debe denegar el acceso una vez creada la otra base.
     No se necesitan tablas ni escrituras de prueba.

  6. Retirar de forma segura la copia privada con la clave al terminar.
     Mantener en Git solo las plantillas y estas instrucciones.

No se crean tablas funcionales ni se modifican Payara o recursos JDBC.
No se incluye FLUSH PRIVILEGES: CREATE USER y GRANT son efectivos directamente.

Referencias oficiales
  https://dev.mysql.com/doc/refman/8.0/en/grant.html
  https://dev.mysql.com/doc/refman/8.0/en/mysql-command-options.html