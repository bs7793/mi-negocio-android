# Backend Supabase (referencia para “futuro yo”)

Este documento resume **qué hace el SQL del repo** y **por qué** existen las piezas principales. La fuente de verdad sigue siendo `supabase/migrations/` en orden cronológico.

## Orden de las migraciones (línea de tiempo)

| Archivo | Rol |
|--------|-----|
| `20260404000000_create_categories_table.sql` | Tabla `categories` inicial; función y trigger **`update_updated_at_column`** para mantener `updated_at` en cada `UPDATE`. |
| `20260405000000_enable_multitenancy_and_rls.sql` | Multitenancy (`workspaces`, `workspace_memberships`, `workspace_id` en categorías); **RLS** y políticas sobre categorías. |
| `20260405000001_harden_tenancy_tables_rls.sql` | Refuerzo de RLS en tablas de tenancy. |
| `20260405000002_switch_to_supabase_auth_rls.sql` | Modelo alineado con **Supabase Auth** (`auth.users`): elimina restos del enfoque Firebase UID en SQL; políticas con `auth.uid()`; trigger **`app.handle_new_auth_user`** (versión de esa migración). |
| `20260405000003_personal_workspace_and_trial_retention.sql` | Workspace **personal** por usuario (`ensure_personal_workspace`), RPC **`get_my_primary_workspace_id`**, trigger en `auth.users`, y **`purge_expired_anonymous_trial_data`**. |
| `20260405000004_grant_app_schema_and_secure_workspace_rpc.sql` | Permisos al schema **`app`**; **`get_my_primary_workspace_id`** como **única entrada segura** (`SECURITY DEFINER`, solo `auth.uid()`); revoca llamadas públicas a `ensure_personal_workspace` con UUID arbitrario. |
| `20260405000005_fix_rls_recursion_membership_helpers.sql` | Evita **recursión infinita** en políticas RLS que leían `workspace_memberships` bajo RLS; helpers con **definer** / bypass donde aplica. |
| `20260411000000_create_warehouses_table_rls.sql` | Tabla **`warehouses`** por `workspace_id` (mismo modelo RLS que `categories`: lectura miembro, escritura editor/admin según política); índice único por workspace + nombre; **`purge_expired_anonymous_trial_data`** también borra `warehouses` de usuarios anónimos expirados. |

Aplicar migraciones en el **mismo orden** en tu proyecto Supabase (CLI o SQL).

## Row Level Security (RLS)

- **`ALTER TABLE … ENABLE ROW LEVEL SECURITY`** en `categories`, `warehouses`, `workspaces`, `workspace_memberships` (según migración).
- Las **políticas** limitan quién ve o modifica filas según membresía al workspace y `workspace_id` en categorías y bodegas.
- Si algo “no aparece” en la app, revisar JWT (usuario correcto) y que las políticas coincidan con el diseño actual.

## Funciones y RPC importantes

### `public.get_my_primary_workspace_id()`

- **Para qué:** el cliente Android necesita un **UUID de workspace** para filtrar/insertar categorías y bodegas; no debe poder llamar `ensure_personal_workspace(otro_uuid)`.
- **Qué hace:** en la versión final (`…000004`), función **`SECURITY DEFINER`** que devuelve `app.ensure_personal_workspace(auth.uid())` (crea workspace personal si hace falta).
- **Quién puede ejecutarla:** rol `authenticated` (ver `GRANT` en la migración).

### `app.ensure_personal_workspace(p_user_id uuid)`

- Crea workspace “Personal” y membresía **owner** si el usuario aún no tiene uno.
- Tras `…000004`, **no** está pensada para uso libre desde el cliente con cualquier UUID; el camino soportado es la RPC pública anterior.

### `app.handle_new_auth_user` (trigger en `auth.users`)

- Al crearse un usuario en Auth, asegura su workspace personal (ver cuerpo en `…000003` / evolución en migraciones anteriores).

### `public.purge_expired_anonymous_trial_data()`

- **Qué borra:** filas de `categories`, **`warehouses`** y `workspace_memberships` asociadas a usuarios **anónimos** en `auth.users` con **`created_at` anterior a ~30 días**; luego `workspaces` sin membresías.
- **No se ejecuta sola:** hay que **programarla** (pg_cron, Supabase Scheduler, job con `service_role`, etc.). La app **no** la invoca.
- Detalle: `supabase/functions/README.md`.

## Trigger `updated_at`

- Función **`update_updated_at_column()`** (trigger `BEFORE UPDATE`): pone `NEW.updated_at = now()`.
- Primero en `categories`; luego reutilizada en otras tablas en migraciones posteriores.

## Dónde mira el Android

| Concern | Sitio en código |
|--------|------------------|
| Sesión y token | `AuthSessionManager`, `AuthViewModel` |
| URL REST + cliente Supabase | `SupabaseProvider` |
| Categorías + workspace | `CategoriesRepository` → RPC `get_my_primary_workspace_id`, REST `/categories` |
| Bodegas + workspace | `WarehousesRepository` → misma RPC, REST `/warehouses` |

## Cuándo actualizar este doc

- Tras cambiar **políticas RLS**, **nombres de funciones** o **flujo de signup/workspace**.
- Tras añadir migraciones nuevas: añade una fila a la tabla de orden y una subsección si introduce un concepto nuevo.
