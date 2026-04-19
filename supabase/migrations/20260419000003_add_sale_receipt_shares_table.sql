-- Canonical short links for sale receipt sharing.

CREATE TABLE IF NOT EXISTS public.sale_receipt_shares (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  share_id text NOT NULL UNIQUE,
  workspace_id uuid NOT NULL REFERENCES public.workspaces(id) ON DELETE CASCADE,
  storage_path text NOT NULL,
  created_by uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'revoked')),
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS sale_receipt_shares_workspace_id_idx
  ON public.sale_receipt_shares (workspace_id);

CREATE INDEX IF NOT EXISTS sale_receipt_shares_expires_at_idx
  ON public.sale_receipt_shares (expires_at);

ALTER TABLE public.sale_receipt_shares ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS sale_receipt_shares_select_by_workspace ON public.sale_receipt_shares;
CREATE POLICY sale_receipt_shares_select_by_workspace
  ON public.sale_receipt_shares
  FOR SELECT
  USING (app.is_active_workspace_member(workspace_id));

DROP POLICY IF EXISTS sale_receipt_shares_insert_by_workspace ON public.sale_receipt_shares;
CREATE POLICY sale_receipt_shares_insert_by_workspace
  ON public.sale_receipt_shares
  FOR INSERT
  WITH CHECK (
    created_by = auth.uid()
    AND app.is_active_workspace_member(workspace_id)
  );

DROP POLICY IF EXISTS sale_receipt_shares_update_by_workspace ON public.sale_receipt_shares;
CREATE POLICY sale_receipt_shares_update_by_workspace
  ON public.sale_receipt_shares
  FOR UPDATE
  USING (app.is_active_workspace_member(workspace_id))
  WITH CHECK (app.is_active_workspace_member(workspace_id));
