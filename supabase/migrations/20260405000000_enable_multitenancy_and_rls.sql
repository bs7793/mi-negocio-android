CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS app;

CREATE OR REPLACE FUNCTION app.jwt_claim(claim_key TEXT)
RETURNS TEXT
LANGUAGE SQL
STABLE
AS $$
  SELECT COALESCE(current_setting('request.jwt.claims', true), '{}')::jsonb ->> claim_key;
$$;

CREATE OR REPLACE FUNCTION app.current_firebase_uid()
RETURNS TEXT
LANGUAGE SQL
STABLE
AS $$
  SELECT COALESCE(app.jwt_claim('firebase_uid'), app.jwt_claim('sub'));
$$;

CREATE OR REPLACE FUNCTION app.current_workspace_id()
RETURNS UUID
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
  workspace_raw TEXT;
BEGIN
  workspace_raw := app.jwt_claim('workspace_id');
  IF workspace_raw IS NULL OR workspace_raw = '' THEN
    RETURN NULL;
  END IF;
  BEGIN
    RETURN workspace_raw::UUID;
  EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
  END;
END;
$$;

CREATE TABLE IF NOT EXISTS public.workspaces (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.workspace_memberships (
  workspace_id UUID NOT NULL REFERENCES public.workspaces(id) ON DELETE CASCADE,
  firebase_uid TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('owner', 'admin', 'member')),
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'invited', 'disabled')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (workspace_id, firebase_uid)
);

CREATE INDEX IF NOT EXISTS idx_workspace_memberships_firebase_uid
  ON public.workspace_memberships(firebase_uid);

CREATE OR REPLACE FUNCTION app.is_active_workspace_member(target_workspace_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.workspace_memberships membership
    WHERE membership.workspace_id = target_workspace_id
      AND membership.firebase_uid = app.current_firebase_uid()
      AND membership.status = 'active'
  );
$$;

CREATE OR REPLACE FUNCTION app.is_workspace_editor(target_workspace_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.workspace_memberships membership
    WHERE membership.workspace_id = target_workspace_id
      AND membership.firebase_uid = app.current_firebase_uid()
      AND membership.status = 'active'
      AND membership.role IN ('owner', 'admin', 'member')
  );
$$;

CREATE OR REPLACE FUNCTION app.is_workspace_admin(target_workspace_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.workspace_memberships membership
    WHERE membership.workspace_id = target_workspace_id
      AND membership.firebase_uid = app.current_firebase_uid()
      AND membership.status = 'active'
      AND membership.role IN ('owner', 'admin')
  );
$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_trigger
    WHERE tgname = 'update_workspaces_updated_at'
  ) THEN
    CREATE TRIGGER update_workspaces_updated_at
    BEFORE UPDATE ON public.workspaces
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_trigger
    WHERE tgname = 'update_workspace_memberships_updated_at'
  ) THEN
    CREATE TRIGGER update_workspace_memberships_updated_at
    BEFORE UPDATE ON public.workspace_memberships
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
  END IF;
END $$;

INSERT INTO public.workspaces (id, name)
VALUES ('11111111-1111-1111-1111-111111111111', 'Default Workspace')
ON CONFLICT (id) DO NOTHING;

ALTER TABLE public.categories
  ADD COLUMN IF NOT EXISTS workspace_id UUID;

ALTER TABLE public.categories
  ADD COLUMN IF NOT EXISTS created_by_firebase_uid TEXT;

UPDATE public.categories
SET workspace_id = '11111111-1111-1111-1111-111111111111'
WHERE workspace_id IS NULL;

UPDATE public.categories
SET created_by_firebase_uid = COALESCE(created_by_firebase_uid, 'legacy-seed')
WHERE created_by_firebase_uid IS NULL;

ALTER TABLE public.categories
  ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE public.categories
  ALTER COLUMN created_by_firebase_uid SET DEFAULT app.current_firebase_uid();

CREATE INDEX IF NOT EXISTS idx_categories_workspace_id
  ON public.categories(workspace_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_workspace_name_normalized
  ON public.categories(workspace_id, LOWER(BTRIM(name)));

ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS categories_select_policy ON public.categories;
CREATE POLICY categories_select_policy
  ON public.categories
  FOR SELECT
  USING (
    workspace_id = app.current_workspace_id()
    AND app.is_active_workspace_member(workspace_id)
  );

DROP POLICY IF EXISTS categories_insert_policy ON public.categories;
CREATE POLICY categories_insert_policy
  ON public.categories
  FOR INSERT
  WITH CHECK (
    workspace_id = app.current_workspace_id()
    AND app.is_workspace_editor(workspace_id)
    AND COALESCE(created_by_firebase_uid, app.current_firebase_uid()) = app.current_firebase_uid()
  );

DROP POLICY IF EXISTS categories_update_policy ON public.categories;
CREATE POLICY categories_update_policy
  ON public.categories
  FOR UPDATE
  USING (
    workspace_id = app.current_workspace_id()
    AND app.is_workspace_admin(workspace_id)
  )
  WITH CHECK (
    workspace_id = app.current_workspace_id()
    AND app.is_workspace_admin(workspace_id)
  );

DROP POLICY IF EXISTS categories_delete_policy ON public.categories;
CREATE POLICY categories_delete_policy
  ON public.categories
  FOR DELETE
  USING (
    workspace_id = app.current_workspace_id()
    AND app.is_workspace_admin(workspace_id)
  );

GRANT SELECT, INSERT, UPDATE, DELETE ON public.categories TO authenticated;
GRANT SELECT ON public.workspaces TO authenticated;
GRANT SELECT ON public.workspace_memberships TO authenticated;
REVOKE ALL ON public.categories FROM anon;
