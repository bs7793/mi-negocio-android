-- Remove legacy no-arg overload to avoid PostgREST ambiguity on list_workspace_members.
DROP FUNCTION IF EXISTS public.list_workspace_members();

-- Keep execute grant for the UUID signature used by the app.
GRANT EXECUTE ON FUNCTION public.list_workspace_members(UUID) TO authenticated;
