-- Remove overloaded list_my_workspaces() signature to avoid PostgREST HTTP 300 ambiguity.
DROP FUNCTION IF EXISTS public.list_my_workspaces();

CREATE OR REPLACE FUNCTION public.list_my_workspaces(p_selected_workspace_id UUID DEFAULT NULL)
RETURNS TABLE (
  workspace_id UUID,
  workspace_name TEXT,
  role TEXT,
  status TEXT,
  is_active BOOLEAN
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    w.id AS workspace_id,
    w.name AS workspace_name,
    m.role,
    m.status,
    COALESCE(w.id = p_selected_workspace_id, FALSE) AS is_active
  FROM public.workspace_memberships m
  JOIN public.workspaces w ON w.id = m.workspace_id
  WHERE m.user_id = auth.uid()
    AND m.status = 'active'
  ORDER BY is_active DESC, w.created_at ASC;
$$;

GRANT EXECUTE ON FUNCTION public.list_my_workspaces(UUID) TO authenticated;
