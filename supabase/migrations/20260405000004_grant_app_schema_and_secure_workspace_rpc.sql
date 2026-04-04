-- Fix "permission denied for schema app": RLS and helpers under app.* require USAGE + EXECUTE.
-- Lock down app.ensure_personal_workspace so clients cannot pass arbitrary user ids.

GRANT USAGE ON SCHEMA app TO authenticated;

GRANT EXECUTE ON FUNCTION app.current_user_id() TO authenticated;
GRANT EXECUTE ON FUNCTION app.is_active_workspace_member(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION app.is_workspace_editor(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION app.is_workspace_admin(UUID) TO authenticated;

-- Do not allow direct calls with an arbitrary user id (default is often EXECUTE for PUBLIC).
REVOKE ALL ON FUNCTION app.ensure_personal_workspace(UUID) FROM PUBLIC;

-- Resolve workspace using definer rights so only this RPC entry point is needed for clients.
CREATE OR REPLACE FUNCTION public.get_my_primary_workspace_id()
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  RETURN app.ensure_personal_workspace(auth.uid());
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_my_primary_workspace_id() TO authenticated;
