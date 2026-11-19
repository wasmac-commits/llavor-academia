# Llavor Academia

Proyecto Maven autónomo para Java 21 y Payara 6 / Jakarta EE 10.
No depende del otro proyecto ni de las referencias PraxIA.

## Compilación

Desde este directorio:

```powershell
mvn "-Dmaven.repo.local=.m2/repository" package
```

Resultado: `target/llavor-academia.war`.
La compilación no necesita Payara ni una base de datos y no realiza despliegues.
La caché Maven local queda dentro del proyecto y está excluida de Git.

## Identificadores reservados

- Coordenadas: `academy.llavor:llavor-academia:0.1.0-SNAPSHOT`.
- Context root: `/llavor-academia`.
- Unidad de persistencia: `LlavorAcademiaPU`.
- Datasource JNDI requerido: `jdbc/LlavorAcademia`.
- Cookie de sesión: `LLAVOR_ACADEMIA_SESSION`.

Solo se declara la referencia JNDI: no se crean pools, recursos JDBC ni bases
de datos. Antes del despliegue deberá aprovisionarse el datasource exclusivo.
La página inicial podrá comprobarse entonces en `/llavor-academia/index.xhtml`.

## Dependencias y entorno

- Jakarta EE API 10.0.0 (`provided`).
- PrimeFaces 15.0.15, classifier `jakarta`.
- JSF, CDI 4, JPA y EclipseLink: proporcionados por Payara; no se duplican en el WAR.
- Driver JDBC: deberá configurarse en el servidor para MySQL 8 en desarrollo
  o MariaDB en producción; no se incluye en el WAR.
- La versión concreta de Payara 6/EclipseLink y la compatibilidad con Java 21
  y MariaDB quedan pendientes de validación en despliegue.
- JSF está en modo `Production`. HTTPS y cookies seguras deberán configurarse
  en el entorno de despliegue.
- No hay credenciales, endpoints externos ni configuración global.

## Organización

Los paquetes se organizan por áreas funcionales. Cuando se implemente cada
área, se separarán internamente `entity`, `repository`, `service` y `dto`
según corresponda. Por ahora contienen únicamente documentación de paquete;
no hay entidades, servicios de negocio ni endpoints.

La generación automática de esquemas y de scripts DDL está desactivada.
Las migraciones y el modelo de datos se definirán en una fase posterior.
Cada instancia utilizará su propia base de datos y configuración. El aislamiento
entre aulas todavía no está implementado.

## Repositorio

El proyecto puede alojarse en su propio repositorio Git. No se ha inicializado
Git ni se ha creado ningún repositorio remoto. El workspace de VS Code es
externo a los dos proyectos y solo contiene referencias relativas a ellos.

