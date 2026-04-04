-- workspace_memberships_select_policy uses EXISTS over workspace_memberships again,
-- so plain reads under RLS re-enter that policy → infinite recursion.
-- Membership helpers and the policy below run as the function owner (bypass RLS)
-- while still using auth.uid() for the caller.

CREATE OR REPLACE FUNCTION app.is_active_workspace_member(target_workspace_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.workspace_memberships membership
    WHERE membership.workspace_id = target_workspace_id
      AND membership.user_id = (SELECT auth.uid())
      AND membership.status = 'active'
  );
$$;

CREATE OR REPLACE FUNCTION app.is_workspace_editor(target_workspace_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.workspace_memberships membership
    WHERE membership.workspace_id = target_workspace_id
      AND membership.user_id = (SELECT auth.uid())
      AND membership.status = 'active'
      AND membership.role IN ('owner', 'admin', 'member')
  );
$$;

CREATE OR REPLACE FUNCTION app.is_workspace_admin(target_workspace_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.workspace_memberships membership
    WHERE membership.workspace_id = target_workspace_id
      AND membership.user_id = (SELECT auth.uid())
      AND membership.status = 'active'
      AND membership.role IN ('owner', 'admin')
  );
$$;

-- Same visibility rules as before, without self-referential RLS on workspace_memberships.
CREATE OR REPLACE FUNCTION app.can_read_workspace_membership_row(p_workspace_id UUID, p_row_user_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    p_row_user_id = (SELECT auth.uid())
    OR EXISTS (
      SELECT 1
      FROM public.workspace_memberships m
      WHERE m.workspace_id = p_workspace_id
        AND m.user_id = (SELECT auth.uid())
        AND m.status = 'active'
        AND m.role IN ('owner', 'admin')
    );
$$;

DROP POLICY IF EXISTS workspaces_select_policy ON public.workspaces;
CREATE POLICY workspaces_select_policy
  ON public.workspaces
  FOR SELECT
  USING (app.is_active_workspace_member(public.workspaces.id));

DROP POLICY IF EXISTS workspace_memberships_select_policy ON public.workspace_memberships;
CREATE POLICY workspace_memberships_select_policy
  ON public.workspace_memberships
  FOR SELECT
  USING (app.can_read_workspace_membership_row(workspace_id, user_id));

GRANT EXECUTE ON FUNCTION app.can_read_workspace_membership_row(UUID, UUID) TO authenticated;
