# Entorno local

## Requisitos

- JDK 25 (el que fija `pom.xml`).
- Docker Desktop con Docker Compose.

## Arranque

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

No hacen falta variables de entorno: `compose.yaml` usa los mismos valores que
`src/main/resources/application.properties`.

| Servicio | Valor |
|---|---|
| PostgreSQL | `localhost:5432`, base `alivia_db`, usuario `postgres`, contraseña `postgres` |
| MongoDB | `mongodb://localhost:27017`, base `alivia_db`, sin autenticación |

`compose.yaml` es solo para desarrollo local: MongoDB no tiene autenticación y las
credenciales de PostgreSQL son triviales. No usar estos valores fuera del equipo de desarrollo.

## Verificación

- `docker compose ps` muestra ambos servicios como `healthy`.
- `curl http://localhost:8080/actuator/health` devuelve `UP` con los componentes `db` y `mongo`.
  En el perfil `dev` los detalles son visibles sin token; en producción requieren autenticación.

## Sin demonio de Docker

Si Docker no está disponible, arranca la aplicación contra servicios locales y desactiva la
integración de Spring Boot con Compose:

```powershell
$env:SPRING_DOCKER_COMPOSE_ENABLED = "false"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Los valores de conexión por defecto son los mismos, así que una instalación local de PostgreSQL
y MongoDB en los puertos 5432 y 27017 también funciona.

## Migraciones

Flyway aplica las migraciones de `src/main/resources/db/migration` al arrancar. El
procedimiento de verificación está en [database-migrations.md](database-migrations.md).

