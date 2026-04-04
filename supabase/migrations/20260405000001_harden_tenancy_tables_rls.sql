ALTER TABLE public.workspaces ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.workspaces FORCE ROW LEVEL SECURITY;

ALTER TABLE public.workspace_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.workspace_memberships FORCE ROW LEVEL SECURITY;

REVOKE ALL ON public.workspaces FROM anon;
REVOKE ALL ON public.workspace_memberships FROM anon;

REVOKE ALL ON public.workspaces FROM authenticated;
REVOKE ALL ON public.workspace_memberships FROM authenticated;

GRANT SELECT ON public.workspaces TO authenticated;
GRANT SELECT ON public.workspace_memberships TO authenticated;

DROP POLICY IF EXISTS workspaces_select_policy ON public.workspaces;
CREATE POLICY workspaces_select_policy
  ON public.workspaces
  FOR SELECT
  USING (
    EXISTS (
      SELECT 1
      FROM public.workspace_memberships membership
      WHERE membership.workspace_id = public.workspaces.id
        AND membership.firebase_uid = app.current_firebase_uid()
        AND membership.status = 'active'
    )
  );

DROP POLICY IF EXISTS workspace_memberships_select_policy ON public.workspace_memberships;
CREATE POLICY workspace_memberships_select_policy
  ON public.workspace_memberships
  FOR SELECT
  USING (
    firebase_uid = app.current_firebase_uid()
    OR EXISTS (
      SELECT 1
      FROM public.workspace_memberships admin_membership
      WHERE admin_membership.workspace_id = public.workspace_memberships.workspace_id
        AND admin_membership.firebase_uid = app.current_firebase_uid()
        AND admin_membership.status = 'active'
        AND admin_membership.role IN ('owner', 'admin')
    )
  );
