# Supabase Edge Functions

## Project Functions

- `generate-sale-receipt`
  - **Route:** `${SUPABASE_FUNCTIONS_URL}/generate-sale-receipt`
  - **Method:** `POST`
  - **Auth:** `Authorization: Bearer <access_token>` required
  - **Input JSON:** `{ "sale_id": <number> }`
  - **Success JSON:** `{ "receipt_url": "<signed_url>", "path": "<storage_path>", "expires_in_seconds": 1800 }`
  - **Storage dependency:** private bucket `sale-receipts`

This function fetches sale detail using user-scoped access (RLS), generates a PDF receipt, uploads it to Storage, and returns a signed URL valid for 30 minutes.

JWT gateway verification is disabled for this function (`verify_jwt=false`), and JWT validation is performed inside the function code.
For publishable key usage, set the `SB_PUBLISHABLE_KEY` secret (preferred) or use the `SUPABASE_ANON_KEY` fallback.

## Maintenance

- Anonymous trial data older than 30 days can be removed with the SQL function `public.purge_expired_anonymous_trial_data()` (see migration `20260405000003_personal_workspace_and_trial_retention.sql`). Schedule it with pg_cron, Supabase Scheduled Functions, or any job runner that can call SQL with sufficient privileges (for example the `service_role` key).
