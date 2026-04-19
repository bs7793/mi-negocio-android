# Supabase Edge Functions

## Project Functions

- `generate-sale-receipt`
  - **Route:** `${SUPABASE_FUNCTIONS_URL}/generate-sale-receipt`
  - **Method:** `POST`
  - **Auth:** `Authorization: Bearer <access_token>` required
  - **Input JSON:** `{ "sale_id": <number> }`
  - **Success JSON:** `{ "share_url": "<short_url>", "share_expires_at": "<iso_datetime>", "receipt_url": "<signed_url>", "path": "<storage_path>", "expires_in_seconds": 1800 }`
  - **Storage dependency:** private bucket `sale-receipts`

This function fetches sale detail using user-scoped access (RLS), generates a PDF receipt, uploads it to Storage, stores a 60-day short share link mapping, and returns both the short share URL plus a fallback signed URL valid for 30 minutes.

- `receipt-share-redirect`
  - **Route:** `${SUPABASE_FUNCTIONS_URL}/receipt-share-redirect/<share_id>` (also supports `?share_id=<id>`)
  - **Method:** `GET`
  - **Auth:** Public route (no user token required)
  - **Success behavior:** `302` redirect to a fresh signed Storage URL valid for 10 minutes
  - **Failure behavior:** JSON `404/410` when share link is missing, revoked, or expired

JWT gateway verification is disabled for both routes (`verify_jwt=false`).
- `generate-sale-receipt` still validates the Bearer token inside function code.
- `receipt-share-redirect` is intentionally public and validates share link status/expiry.
For publishable key usage, set the `SB_PUBLISHABLE_KEY` secret (preferred) or use the `SUPABASE_ANON_KEY` fallback.

## Maintenance

- Anonymous trial data older than 30 days can be removed with the SQL function `public.purge_expired_anonymous_trial_data()` (see migration `20260405000003_personal_workspace_and_trial_retention.sql`). Schedule it with pg_cron, Supabase Scheduled Functions, or any job runner that can call SQL with sufficient privileges (for example the `service_role` key).
