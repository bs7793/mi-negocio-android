# Supabase Edge Functions

No project-specific edge functions are required in the Supabase Auth + PostgREST architecture.

Categories CRUD is handled directly from the Android client using Supabase Auth sessions and RLS.

## Maintenance

- Anonymous trial data older than 30 days can be removed with the SQL function `public.purge_expired_anonymous_trial_data()` (see migration `20260405000003_personal_workspace_and_trial_retention.sql`). Schedule it with pg_cron, Supabase Scheduled Functions, or any job runner that can call SQL with sufficient privileges (for example the `service_role` key).
