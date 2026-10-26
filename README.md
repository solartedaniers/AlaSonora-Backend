# AlaSonora · Backend

AlaSonora es una app web para identificar aves por su canto: grabas o subes un audio, la app te dice
qué especie es y la detección queda en un mapa compartido con otros observadores. Está en
https://alasonora.com.

El sistema tiene tres repositorios:

| Parte | Repositorio | Papel |
|---|---|---|
| Frontend | [AlaSonora-Frontend](https://github.com/solartedaniers/AlaSonora-Frontend) | PWA en Angular con SSR: grabación, espectrograma, mapa y modo offline |
| Backend | este repositorio | API REST en Spring Boot: sesiones, base de datos, Storage y orquestación de la IA |
| Motor de IA | [AlaSonora-IA](https://github.com/solartedaniers/AlaSonora-IA) | Microservicio en FastAPI que identifica la especie con BirdNET |

Este repositorio es el backend, el punto central del sistema. Valido los JWT que emite Supabase Auth,
guardo detecciones, perfiles y especies en PostgreSQL (Supabase) y firmo las URLs privadas de Storage.
También orquesto la clasificación: le paso cada audio al motor de IA y enriquezco su respuesta con el
nombre común en español (Wikidata), la foto de la especie (Wikimedia Commons) y las vocalizaciones de
referencia (Xeno-canto). La API pública vive en https://alasonora-backend.onrender.com/api.

## Identificación por foto: mejora futura, apagada a propósito

La función principal de AlaSonora es identificar aves por su canto con BirdNET, y está activa. La
identificación por foto la dejé como mejora futura y la apagué a propósito: es una decisión, no un
error. El motor ya tiene el endpoint `/classify-photo`, pero en producción está desactivado con
`PHOTO_CLASSIFICATION_ENABLED=false` en el motor y `photoIdentificationEnabled=false` en el frontend.
Lo hice porque el servidor gratis de Render tiene 512 MB de RAM: el audio usa unos 360 MB y con fotos
el proceso llegaría a unos 750 MB.

## Tecnologías

| Pieza | Versión |
|---|---|
| Java | 21 (`java.version` en `pom.xml`; imágenes `eclipse-temurin:21`) |
| Spring Boot | 4.1.1 (Web MVC, Data JPA, Security, OAuth2 Resource Server, Validation) |
| Maven | 3.9.16, vía el wrapper `./mvnw` |
| PostgreSQL | Supabase, con el driver `org.postgresql` |
| Lombok | la que gestiona Spring Boot 4.1.1 (el `pom.xml` no la fija) |
| JUnit 5 y Logback | los del starter de pruebas |

## Requisitos previos

- **JDK 21**. Lo verificas con `java -version`. Si tienes varias versiones, apunta `JAVA_HOME` al
  JDK 21 antes de usar el wrapper. Con otra versión, `./mvnw -v` lo delata en la línea `Java version`.
- **No necesitas instalar Maven**: `./mvnw` descarga Maven 3.9.16 la primera vez.
- **Un proyecto de Supabase** con base de datos, Auth y Storage (buckets `recordings` y `observer-photos`).
- **El motor de IA corriendo** si quieres clasificar audio ([AlaSonora-IA](https://github.com/solartedaniers/AlaSonora-IA)).
- **Docker**, solo si quieres construir la misma imagen que corre en Render (`docker --version`).

## Variables de entorno

Spring lee el `.env` de la raíz gracias a `spring.config.import` en `application.properties`. El
ejemplo está en `.env.example`. Ninguno de estos valores es real.

| Variable | Para qué sirve | Obligatoria | Ejemplo |
|---|---|---|---|
| `DB_URL` | URL JDBC del pooler de Supabase | Sí | `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres` |
| `DB_USERNAME` | Usuario de la base | Sí | `postgres.<tu-project-ref>` |
| `DB_PASSWORD` | Contraseña de la base | Sí | `<contraseña de la base>` |
| `SUPABASE_JWKS_URI` | Claves públicas para validar los JWT (ES256) | Sí | `https://<tu-project-ref>.supabase.co/auth/v1/.well-known/jwks.json` |
| `SUPABASE_URL` | Admin API de Supabase y firmado de URLs de Storage | Sí | `https://<tu-project-ref>.supabase.co` |
| `SUPABASE_SERVICE_ROLE_KEY` | Clave `service_role` (nunca la anon) | Sí | `<service_role key>` |
| `AI_ENGINE_API_KEY` | Clave interna del motor. Igual a `INTERNAL_API_KEY` de AlaSonora-IA | Sí | `una-clave-larga-local` |
| `XENO_CANTO_API_KEY` | Vocalizaciones de referencia por especie (clave gratuita en xeno-canto.org) | Sí | `<tu clave de Xeno-canto>` |
| `AI_ENGINE_BASE_URL` | URL del motor de IA | No (`http://localhost:8000`) | `https://alasonora-ai-engine.onrender.com` |
| `app.cors.allowed-origins` (o `APP_CORS_ALLOWEDORIGINS`) | Orígenes del frontend, separados por coma y sin barra final | No (`http://localhost:4200,http://localhost:4000`) | `https://alasonora.com,https://www.alasonora.com` |
| `PORT` | Puerto HTTP. Render lo inyecta | No (`8083`) | `8083` |
| `JPA_SHOW_SQL` | Escribe en el log cada consulta SQL. Solo para desarrollo | No (`false`) | `true` |

El resto tiene un valor por defecto y está documentado en `src/main/resources/application.properties`:
tiempos y reintentos del motor (`AI_ENGINE_*`), despertar del motor (`AI_ENGINE_WAKE_*`,
`AI_ENGINE_STATUS_FRESH_MS`), tamaño del pool de IA (`AI_EXECUTOR_*`), límite de audio
(`AUDIO_UPLOAD_MAX_BYTES`), limpieza de grabaciones huérfanas (`ORPHAN_RECORDINGS_*`), buckets y vida
de las URLs firmadas (`SUPABASE_*_BUCKET`, `SUPABASE_SIGNED_URL_TTL_SECONDS`), notificaciones y
traducción de nombres.

## Correrlo en local

```bash
git clone https://github.com/solartedaniers/AlaSonora-Backend.git
cd AlaSonora-Backend
cp .env.example .env          # completa las credenciales de tu proyecto de Supabase
./mvnw spring-boot:run        # en Windows (cmd o PowerShell): mvnw.cmd spring-boot:run
```

El backend queda en `http://localhost:8083/api`. Para comprobarlo sin sesión, pide una ruta pública:

```bash
curl http://localhost:8083/api/species
```

Al arrancar dejo en el log el host efectivo del motor de IA. Si la URL no sirve, solo aviso: el
backend arranca igual.

### Conectarlo con los otros dos servicios

1. Arranco el motor de IA en `http://localhost:8000` con su `INTERNAL_API_KEY`.
2. En el `.env` de aquí pongo `AI_ENGINE_BASE_URL=http://localhost:8000` y el mismo valor en
   `AI_ENGINE_API_KEY`.
3. Arranco el frontend con `npm start` (`http://localhost:4200`). En desarrollo ya llama a
   `http://localhost:8083/api`, y los orígenes `4200` (ng serve) y `4000` (servidor SSR) ya están en
   CORS por defecto.

## Pruebas y build

```bash
cp .env.example .env
./mvnw test                   # pruebas
./mvnw -DskipTests package    # genera target/backend-0.0.1-SNAPSHOT.jar
```

`contextLoads` levanta el contexto completo contra la base real, así que solo corre si `DB_URL` tiene
valor. Con el `.env.example` aparece como omitida. Las demás son unitarias y no necesitan red: las
del cliente del motor levantan un servidor HTTP falso en `127.0.0.1`. El workflow
`.github/workflows/ci.yml` corre `./mvnw -B test` con Temurin 21 en cada push a `main`.

## Arquitectura

```
controller  →  service  →  repository (JPA)  →  PostgreSQL (Supabase)
                  │
                  ├── ai/           cliente HTTP del motor, reintentos y despertar
                  ├── translation/  Wikidata, Wikimedia Commons y Xeno-canto
                  ├── storage/      URLs firmadas y limpieza de grabaciones huérfanas
                  └── admin/        Admin API de Supabase (alta y suspensión de usuarios)
```

- **Seguridad**: soy un resource server OAuth2. Valido los JWT de Supabase con su JWKS (ES256) y saco
  el rol del perfil, así puedo usar `hasRole("ADMIN")` en `SecurityConfig`.
- **Clasificación asíncrona**: `POST /api/detections/classify` corre en un pool aparte
  (`aiTaskExecutor`), así una clasificación lenta no ocupa los hilos de Tomcat. Si el pool y su cola
  (`AI_EXECUTOR_*`) están llenos, respondo 503 con `AI_ENGINE_BUSY` en vez de correrla en el hilo de Tomcat. Si el motor falla
  rápido (conexión rechazada, 502, 503, 504), reintento una vez mientras quepa en el
  `spring.mvc.async.request-timeout`.
- **Grabaciones huérfanas**: una tarea diaria borra del bucket `recordings` los audios que se subieron
  pero nunca se guardaron como detección, solo si tienen más de 24 h.
- **Logs del motor**: cada fallo queda con tipo de error, host, status y tiempo, nunca con el cuerpo
  de la respuesta ni con la clave.

## Endpoints principales

Todas las rutas van bajo `/api`. Salvo las públicas, piden `Authorization: Bearer <JWT de Supabase>`.

| Método y ruta | Acceso | Qué hace |
|---|---|---|
| `GET /species`, `GET /species/{id}` | Público | Catálogo de especies |
| `GET /network-stats` | Público | Estadísticas de la red de observadores |
| `GET /detections` | Público | Detecciones públicas para el mapa |
| `GET /usernames/availability` | Público | Si un nombre de usuario está libre |
| `GET /detections/feed`, `GET /detections/{id}` | Sesión | Feed y detalle de detecciones |
| `GET /detections/mine`, `GET /detections/mine/stats` | Sesión | Mi historial y mis estadísticas |
| `POST /detections/classify` | Sesión | Clasifica un audio con el motor de IA |
| `POST /detections` | Sesión | Guarda una detección confirmada |
| `PUT /detections/{id}/observer-photo` | Sesión | Asocia la foto del observador |
| `POST /photo-classifications` | Sesión | Identifica un ave por foto (apagado en producción) |
| `GET /ai/status` | Sesión | Estado del motor al instante. Si no está listo, lo despierta en segundo plano |
| `GET /profile/me`, `PUT /profile/me` | Sesión | Mi perfil |
| `GET /notifications`, `POST /notifications/read` | Sesión | Avisos de la campana |
| `/admin/**` | Rol ADMIN | Usuarios (crear, editar, rol, suspender, activar) y moderación de detecciones |

## Despliegue en Render

| Ajuste | Valor |
|---|---|
| Tipo de servicio | Web Service, runtime Docker |
| Root Directory | *(vacío)* |
| Dockerfile Path | `./Dockerfile` |
| Build | Lo hace el `Dockerfile`: `./mvnw clean package -DskipTests -B` sobre `eclipse-temurin:21-jdk` |
| Start | `java -XX:MaxRAMPercentage=75 -jar app.jar` sobre `eclipse-temurin:21-jre` |
| Variables | Las obligatorias de la tabla, `AI_ENGINE_BASE_URL` con la URL pública del motor y `app.cors.allowed-origins=https://alasonora.com,https://www.alasonora.com,https://alasonora-frontend.onrender.com` |

Render inyecta `PORT`. `-XX:MaxRAMPercentage=75` existe porque, por defecto, la JVM solo toma el 25 %
de la RAM para el heap, y en una instancia de 512 MB eso no le alcanza a Spring con Hibernate. El
backend no tiene un endpoint de salud dedicado. Si configuras un Health Check Path, usa una ruta
pública como `/api/species`.

### Encendido del motor de IA sin keep-alive

No hay keep-alive, cron ni consultas programadas al motor:

- `GET /api/ai/status` responde al instante con el último estado conocido. Si el motor no está
  listo, `AiEngineWakeUpService` lanza un solo despertar en segundo plano, que repite `GET /health`
  hasta que responde o pasan 5 minutos (`ai.wake.*`).
- Un "listo" vale 10 minutos (`ai.wake.ready-fresh-ms`), menos que los 15 que tarda Render en apagar
  el motor, así que nunca es falso.
- Render no deja que este servicio despierte al motor y responde 429. Lo registro una sola vez por
  ciclo, como INFO (`waiting for the engine to be woken by client traffic`). Lo despierta el
  navegador del usuario con un `GET /health` directo, mientras la app muestra "encendiendo…".

## Estructura

```
src/main/java/com/alasonora/backend/
  controller/   Endpoints REST
  service/      Lógica de negocio (detecciones, perfiles, notificaciones, estadísticas)
  ai/           Cliente del motor, validación de audio, estado y despertar
  config/       Seguridad, CORS y propiedades tipadas
  entity/, repository/, dto/
  translation/  Nombre común, foto y vocalizaciones de cada especie
  storage/      URLs firmadas y limpieza de Storage
  admin/        Admin API de Supabase
  geo/          Zona aproximada de una detección
  async/        Pool de hilos de la IA
src/main/resources/application.properties
Dockerfile
```

## Problemas comunes

| Síntoma | Causa | Solución |
|---|---|---|
| `release version 21 not supported` o el wrapper usa otra Java | `JAVA_HOME` apunta a otro JDK | Apunta `JAVA_HOME` al JDK 21 y revisa con `./mvnw -v` |
| `Could not resolve placeholder 'SUPABASE_JWKS_URI'` (u otra variable) al arrancar | Falta el `.env` o le faltan valores | `cp .env.example .env` y completa las credenciales |
| Todo token responde 401 con `Unsupported algorithm` | Spring solo acepta RS256 por defecto | Ya está `jws-algorithms=ES256`. Revisa que `SUPABASE_JWKS_URI` sea la de tu proyecto |
| El navegador bloquea las peticiones por CORS | El origen del frontend no está permitido | Agrégalo a `app.cors.allowed-origins`, sin barra final |
| Clasificar responde que el motor no está disponible | El motor está apagado o la clave no coincide | Arranca el motor y pon el mismo valor en `AI_ENGINE_API_KEY` e `INTERNAL_API_KEY` |
| El log muestra `status=429` hacia el motor en Render | Render no deja que el backend lo despierte | Es lo esperado. Lo despierta el navegador del usuario al entrar a la app |
| `contextLoads` aparece como omitida | `DB_URL` está vacía | Normal en CI. Pon credenciales reales si quieres correrla |
