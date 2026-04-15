-- Extend anonymous trial purge to include sales created by expired anonymous users.
-- Keep return contract unchanged for existing callers.

CREATE OR REPLACE FUNCTION public.purge_expired_anonymous_trial_data()
RETURNS TABLE (categories_deleted BIGINT)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  n BIGINT := 0;
BEGIN
  DELETE FROM public.categories c
  USING auth.users u
  WHERE c.created_by = u.id
    AND u.created_at < NOW() - INTERVAL '30 days'
    AND (
      u.is_anonymous IS TRUE
      OR COALESCE((u.raw_app_meta_data->>'is_anonymous')::boolean, false) = true
      OR (u.raw_app_meta_data->>'provider') = 'anonymous'
    );
  GET DIAGNOSTICS n = ROW_COUNT;

  DELETE FROM public.warehouses w
  USING auth.users u
  WHERE w.created_by = u.id
    AND u.created_at < NOW() - INTERVAL '30 days'
    AND (
      u.is_anonymous IS TRUE
      OR COALESCE((u.raw_app_meta_data->>'is_anonymous')::boolean, false) = true
      OR (u.raw_app_meta_data->>'provider') = 'anonymous'
    );

  DELETE FROM public.sales s
  USING auth.users u
  WHERE s.created_by = u.id
    AND u.created_at < NOW() - INTERVAL '30 days'
    AND (
      u.is_anonymous IS TRUE
      OR COALESCE((u.raw_app_meta_data->>'is_anonymous')::boolean, false) = true
      OR (u.raw_app_meta_data->>'provider') = 'anonymous'
    );

  DELETE FROM public.workspace_memberships wm
  USING auth.users u
  WHERE wm.user_id = u.id
    AND u.created_at < NOW() - INTERVAL '30 days'
    AND (
      u.is_anonymous IS TRUE
      OR COALESCE((u.raw_app_meta_data->>'is_anonymous')::boolean, false) = true
      OR (u.raw_app_meta_data->>'provider') = 'anonymous'
    );

  DELETE FROM public.workspaces w
  WHERE NOT EXISTS (
    SELECT 1 FROM public.workspace_memberships m WHERE m.workspace_id = w.id
  );

  RETURN QUERY SELECT n;
END;
$$;
