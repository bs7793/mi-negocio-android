# Mi Negocio

Aplicación **Android** nativa en **Kotlin** con **Jetpack Compose** (Material 3). Los datos y la identidad se gestionan con [**Supabase**](https://supabase.com/): **Auth** y acceso a Postgres vía **PostgREST** (REST) usando el cliente oficial de Supabase en la app.

**Firebase** en este repo se usa para **Google Services** y **Crashlytics** (telemetría de errores). La autenticación de la aplicación **no** usa Firebase Authentication; las sesiones son de **Supabase Auth**.

## Arquitectura resumida

| Área | Implementación |
|------|----------------|
| Sesión | Supabase Auth: usuario **anónimo** al iniciar; opcional **Google** mediante *Sign-In* de Play Services y **ID token** enviado a Supabase (`signInWith` / enlace de identidad si ya era anónimo). |
| Datos | Tablas en Postgres (p. ej. categorías con `workspace_id`), expuestas por PostgREST. La app usa el **JWT** de Supabase en las peticiones HTTP. |
| Workspace | El workspace principal del usuario se resuelve en el servidor (p. ej. RPC `get_my_primary_workspace_id`); las políticas **RLS** y el modelo multi-tenant están en las migraciones bajo `supabase/migrations/`. |
| Errores | **Firebase Crashlytics** (plugin y SDK). |

## Configuración

### Supabase (`local.properties`)

No versiones `local.properties`. Define al menos:

```properties
SUPABASE_URL=https://<tu-proyecto>.supabase.co
SUPABASE_ANON_KEY=<tu-anon-key>
```

Opcional: `SUPABASE_FUNCTIONS_URL`. Si no existe, el build deriva `<SUPABASE_URL>/functions/v1` (ver `app/build.gradle.kts`).

El proyecto Gradle puede compartir el mismo proyecto Supabase que otras apps (p. ej. *inventory-app*); las migraciones SQL aplican en tu instancia de Supabase.

### Firebase (Crashlytics)

Añade `google-services.json` del proyecto Firebase asociado a la app (habitualmente sin commitear secretos; sigue la guía de Firebase Console).

### Google Sign-In

Para iniciar sesión con Google hace falta el **Web client ID** de OAuth en recursos de la app (`default_web_client_id` en `strings`; debe ser un ID real, no el placeholder).

## Requisitos

- Android Studio (recomendado) o JDK + Android SDK
- `minSdk` 26, `targetSdk` 36 (ver `app/build.gradle.kts`)

## Compilar

Desde la raíz del repositorio:

```bash
./gradlew assembleDebug
```

En Windows (PowerShell o CMD):

```bat
gradlew.bat assembleDebug
```

## Estructura del repositorio

- `app/` — módulo de la aplicación (`applicationId`: `superapps.minegocio`)
- `app/src/main/java/superapps/minegocio/` — código fuente Kotlin (UI, auth, categorías, etc.)
- `supabase/migrations/` — esquema Postgres, RLS y funciones relacionadas con workspaces y categorías
- `supabase/functions/README.md` — notas sobre funciones edge (no obligatorias para el flujo actual Auth + PostgREST)
- [`docs/supabase-backend.md`](docs/supabase-backend.md) — referencia del backend SQL (orden de migraciones, RLS, RPC, retención anónima, punteros al código Android)
