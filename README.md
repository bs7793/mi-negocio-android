# Mi Negocio

Aplicación **Android** nativa en **Kotlin** con interfaz en **Jetpack Compose** (Material 3). El backend y autenticación se gestionan con [**Supabase**](https://supabase.com/) (Auth y PostgREST vía cliente oficial). También se integran **Firebase** (Google Services / Crashlytics) para telemetría y servicios complementarios.

## Supabase

El proyecto de Supabase está asociado a la cuenta **mrbarillas30@gmail.com**.

La app espera la URL del proyecto y la clave anónima en `local.properties` (no versionar este archivo):

```properties
SUPABASE_URL=https://<tu-proyecto>.supabase.co
SUPABASE_ANON_KEY=<tu-anon-key>
```

Opcionalmente puedes definir `SUPABASE_FUNCTIONS_URL`; si no está, se deriva de `SUPABASE_URL` como `<SUPABASE_URL>/functions/v1`.

## Requisitos

- Android Studio (recomendado) o entorno con JDK y Android SDK
- `minSdk` 26, `targetSdk` 36 (según `app/build.gradle.kts`)

## Compilar

Desde la raíz del repositorio:

```bash
./gradlew assembleDebug
```

En Windows (PowerShell o CMD):

```bat
gradlew.bat assembleDebug
```

## Estructura principal

- `app/` — módulo de la aplicación (`applicationId`: `superapps.minegocio`)
- Código fuente Kotlin bajo `app/src/main/java/superapps/minegocio/`
