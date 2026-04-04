CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS app;

TRUNCATE TABLE public.categories RESTART IDENTITY CASCADE;
TRUNCATE TABLE public.workspace_memberships CASCADE;
TRUNCATE TABLE public.workspaces CASCADE;

DROP POLICY IF EXISTS categories_select_policy ON public.categories;
DROP POLICY IF EXISTS categories_insert_policy ON public.categories;
DROP POLICY IF EXISTS categories_update_policy ON public.categories;
DROP POLICY IF EXISTS categories_delete_policy ON public.categories;
DROP POLICY IF EXISTS workspaces_select_policy ON public.workspaces;
DROP POLICY IF EXISTS workspace_memberships_select_policy ON public.workspace_memberships;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
DROP FUNCTION IF EXISTS app.handle_new_auth_user();

DROP FUNCTION IF EXISTS app.is_active_workspace_member(UUID);
DROP FUNCTION IF EXISTS app.is_workspace_editor(UUID);
DROP FUNCTION IF EXISTS app.is_workspace_admin(UUID);
DROP FUNCTION IF EXISTS app.current_workspace_id() CASCADE;
DROP FUNCTION IF EXISTS app.current_firebase_uid() CASCADE;
DROP FUNCTION IF EXISTS app.jwt_claim(TEXT) CASCADE;

ALTER TABLE public.categories
  DROP COLUMN IF EXISTS created_by_firebase_uid;

ALTER TABLE public.categories
  ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES auth.users(id) ON DELETE SET NULL;

UPDATE public.categories
SET created_by = auth.uid()
WHERE created_by IS NULL;

ALTER TABLE public.categories
  ALTER COLUMN created_by SET DEFAULT auth.uid();

ALTER TABLE public.categories
  ALTER COLUMN created_by SET NOT NULL;

DROP TABLE IF EXISTS public.workspace_memberships;
CREATE TABLE public.workspace_memberships (
  workspace_id UUID NOT NULL REFERENCES public.workspaces(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  role TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('owner', 'admin', 'member')),
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'invited', 'disabled')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (workspace_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_workspace_memberships_user_id
  ON public.workspace_memberships(user_id);

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

CREATE OR REPLACE FUNCTION app.current_user_id()
RETURNS UUID
LANGUAGE SQL
STABLE
AS $$
  SELECT auth.uid();
$$;

CREATE OR REPLACE FUNCTION app.is_active_workspace_member(target_workspace_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.workspace_memberships membership
    WHERE membership.workspace_id = target_workspace_id
      AND membership.user_id = app.current_user_id()
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
      AND membership.user_id = app.current_user_id()
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
      AND membership.user_id = app.current_user_id()
      AND membership.status = 'active'
      AND membership.role IN ('owner', 'admin')
  );
$$;

CREATE OR REPLACE FUNCTION app.handle_new_auth_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.workspace_memberships (workspace_id, user_id, role, status)
  VALUES ('11111111-1111-1111-1111-111111111111', NEW.id, 'member', 'active')
  ON CONFLICT (workspace_id, user_id) DO NOTHING;
  RETURN NEW;
END;
$$;

CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW
EXECUTE FUNCTION app.handle_new_auth_user();

INSERT INTO public.workspace_memberships (workspace_id, user_id, role, status)
SELECT '11111111-1111-1111-1111-111111111111', users.id, 'member', 'active'
FROM auth.users users
ON CONFLICT (workspace_id, user_id) DO NOTHING;

ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories FORCE ROW LEVEL SECURITY;
ALTER TABLE public.workspaces ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.workspaces FORCE ROW LEVEL SECURITY;
ALTER TABLE public.workspace_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.workspace_memberships FORCE ROW LEVEL SECURITY;

CREATE POLICY categories_select_policy
  ON public.categories
  FOR SELECT
  USING (
    app.is_active_workspace_member(workspace_id)
  );

CREATE POLICY categories_insert_policy
  ON public.categories
  FOR INSERT
  WITH CHECK (
    app.is_workspace_editor(workspace_id)
    AND COALESCE(created_by, app.current_user_id()) = app.current_user_id()
  );

CREATE POLICY categories_update_policy
  ON public.categories
  FOR UPDATE
  USING (
    app.is_workspace_admin(workspace_id)
  )
  WITH CHECK (
    app.is_workspace_admin(workspace_id)
  );

CREATE POLICY categories_delete_policy
  ON public.categories
  FOR DELETE
  USING (
    app.is_workspace_admin(workspace_id)
  );

CREATE POLICY workspaces_select_policy
  ON public.workspaces
  FOR SELECT
  USING (
    EXISTS (
      SELECT 1
      FROM public.workspace_memberships membership
      WHERE membership.workspace_id = public.workspaces.id
        AND membership.user_id = app.current_user_id()
        AND membership.status = 'active'
    )
  );

CREATE POLICY workspace_memberships_select_policy
  ON public.workspace_memberships
  FOR SELECT
  USING (
    user_id = app.current_user_id()
    OR EXISTS (
      SELECT 1
      FROM public.workspace_memberships admin_membership
      WHERE admin_membership.workspace_id = public.workspace_memberships.workspace_id
        AND admin_membership.user_id = app.current_user_id()
        AND admin_membership.status = 'active'
        AND admin_membership.role IN ('owner', 'admin')
    )
  );

GRANT SELECT, INSERT, UPDATE, DELETE ON public.categories TO authenticated;
GRANT SELECT ON public.workspaces TO authenticated;
GRANT SELECT ON public.workspace_memberships TO authenticated;

REVOKE ALL ON public.categories FROM anon;
REVOKE ALL ON public.workspaces FROM anon;
REVOKE ALL ON public.workspace_memberships FROM anon;
