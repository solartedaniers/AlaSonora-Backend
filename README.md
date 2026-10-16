# AlaSonora · Backend

API REST de AlaSonora con Spring Boot 4 y Java 21. Valida los JWT de Supabase, guarda detecciones y
perfiles en PostgreSQL (Supabase), firma las URLs privadas de Storage y orquesta la clasificación:
reenvía cada audio al motor de IA por HTTP y enriquece el resultado (nombre común en español, foto
de la especie y vocalizaciones de referencia).

## Requisitos

- Java 21 (el wrapper `./mvnw` descarga Maven)
- Un proyecto de Supabase (base de datos, Auth y Storage)
- El motor de IA corriendo (repo AlaSonora-IA) para clasificar audio

## Ejecución

```bash
cp .env.example .env    # completa las credenciales
./mvnw spring-boot:run
```

El backend queda en `http://localhost:8083` (en Render el puerto llega en `PORT`). Spring lee el
`.env` gracias a `spring.config.import` en `application.properties`.

## Variables de entorno

Obligatorias:

| Variable | Uso |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión JDBC al pooler de Supabase |
| `SUPABASE_JWKS_URI` | Claves públicas para validar los JWT (ES256) |
| `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` | Admin API y firmado de URLs de Storage |
| `AI_ENGINE_API_KEY` | Clave interna; igual a `INTERNAL_API_KEY` del motor de IA |
| `XENO_CANTO_API_KEY` | Vocalizaciones de referencia por especie |

Con valor por defecto (las más usadas):

| Variable | Por defecto | Uso |
|---|---|---|
| `AI_ENGINE_BASE_URL` | `http://localhost:8000` | URL del motor de IA |
| `app.cors.allowed-origins` | `http://localhost:4200,http://localhost:4000` | Orígenes permitidos (en producción, la URL del frontend). También funciona como `APP_CORS_ALLOWEDORIGINS` |
| `PORT` | `8083` | Puerto HTTP |

El resto (timeouts, reintentos y calentamiento del motor, límites de audio, limpieza de grabaciones
huérfanas, notificaciones) está documentado en `src/main/resources/application.properties`, cada
una con su valor por defecto.

## Pruebas

```bash
cp .env.example .env
./mvnw test
```

`contextLoads` levanta el contexto completo contra la base real, así que solo corre si `DB_URL`
tiene valor (en el entorno o en el `.env`); con el `.env.example` se salta con ese motivo. Las demás
pruebas son unitarias y no necesitan red. El workflow `.github/workflows/ci.yml` corre lo mismo en
cada push a `main`.

## API REST

Todas las rutas van bajo `/api` y, salvo las públicas, exigen `Authorization: Bearer <JWT de Supabase>`.

| Método y ruta | Acceso | Uso |
|---|---|---|
| `GET /species`, `GET /species/{id}` | público | Catálogo de especies |
| `GET /network-stats` | público | Estadísticas de la red de observadores |
| `GET /detections` | público | Detecciones públicas |
| `GET /usernames/availability` | público | Disponibilidad del nombre de usuario (registro) |
| `GET /detections/feed`, `GET /detections/{id}` | sesión | Feed y detalle de detecciones |
| `GET /detections/mine`, `GET /detections/mine/stats` | sesión | Historial y estadísticas propias |
| `POST /detections` | sesión | Guarda una detección confirmada |
| `PUT /detections/{id}/observer-photo` | sesión | Asocia la foto del observador |
| `POST /detections/classify` | sesión | Clasifica un audio con el motor de IA (asíncrono) |
| `POST /photo-classifications` | sesión | Identifica un ave por foto |
| `GET /ai/status` | sesión | Estado del motor de IA; también lo despierta |
| `GET /profile/me`, `PUT /profile/me` | sesión | Perfil propio |
| `GET /notifications`, `POST /notifications/read` | sesión | Avisos de la campana |
| `/admin/**` | rol ADMIN | Gestión de usuarios y moderación de detecciones |

## Despliegue en Render (Docker)

El `Dockerfile` compila el jar con el wrapper sobre JDK 21 y lo corre sobre un JRE 21 liviano, con
`-XX:MaxRAMPercentage=75` para instancias de 512 MB. El servicio vive en la raíz de este
repositorio, así que el Root Directory queda vacío.

| Ajuste | Valor |
|---|---|
| Runtime | Docker |
| Root Directory | *(vacío)* |
| Dockerfile Path | `./Dockerfile` |

Variables: las obligatorias de arriba, más `AI_ENGINE_BASE_URL` (URL pública del motor de IA) y
`app.cors.allowed-origins` (URL del frontend; Render acepta el nombre con puntos y guion tal cual, y
`APP_CORS_ALLOWEDORIGINS` también funciona). Render inyecta `PORT`.

## Proyecto AlaSonora

| Parte | Repositorio |
|---|---|
| Frontend (Angular, PWA con SSR) | https://github.com/solartedaniers/AlaSonora-Frontend |
| Backend (este repositorio) | https://github.com/solartedaniers/AlaSonora-Backend |
| Motor de IA (FastAPI + BirdNET) | https://github.com/solartedaniers/AlaSonora-IA |

App desplegada: https://alasonora-frontend.onrender.com · API: https://alasonora-backend.onrender.com/api
