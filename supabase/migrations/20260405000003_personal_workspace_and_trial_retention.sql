-- Personal workspace per auth user (trial isolation) + helpers + anonymous retention cleanup.

CREATE OR REPLACE FUNCTION app.ensure_personal_workspace(p_user_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_ws UUID;
BEGIN
  SELECT m.workspace_id
  INTO v_ws
  FROM public.workspace_memberships m
  WHERE m.user_id = p_user_id
    AND m.role = 'owner'
    AND m.status = 'active'
  ORDER BY m.created_at ASC
  LIMIT 1;

  IF v_ws IS NOT NULL THEN
    RETURN v_ws;
  END IF;

  INSERT INTO public.workspaces (id, name)
  VALUES (gen_random_uuid(), 'Personal workspace')
  RETURNING id INTO v_ws;

  INSERT INTO public.workspace_memberships (workspace_id, user_id, role, status)
  VALUES (v_ws, p_user_id, 'owner', 'active');

  RETURN v_ws;
END;
$$;

CREATE OR REPLACE FUNCTION app.current_primary_workspace_id()
RETURNS UUID
LANGUAGE sql
STABLE
AS $$
  SELECT m.workspace_id
  FROM public.workspace_memberships m
  WHERE m.user_id = app.current_user_id()
    AND m.role = 'owner'
    AND m.status = 'active'
  ORDER BY m.created_at ASC
  LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION app.handle_new_auth_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM app.ensure_personal_workspace(NEW.id);
  RETURN NEW;
END;
$$;

-- Recreate trigger (definition replaced above).
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW
EXECUTE FUNCTION app.handle_new_auth_user();

-- Callable from PostgREST so the client can resolve workspace without a hard-coded id.
-- Ensures a personal workspace exists (idempotent) so first API calls after sign-in are safe.
CREATE OR REPLACE FUNCTION public.get_my_primary_workspace_id()
RETURNS UUID
LANGUAGE sql
SECURITY INVOKER
SET search_path = public
AS $$
  SELECT app.ensure_personal_workspace(auth.uid());
$$;

GRANT EXECUTE ON FUNCTION public.get_my_primary_workspace_id() TO authenticated;

-- Deletes business rows for anonymous auth users older than 30 days. Intended for pg_cron / Dashboard SQL.
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
      COALESCE((u.raw_app_meta_data->>'is_anonymous')::boolean, false) = true
      OR (u.raw_app_meta_data->>'provider') = 'anonymous'
    );
  GET DIAGNOSTICS n = ROW_COUNT;

  DELETE FROM public.workspace_memberships wm
  USING auth.users u
  WHERE wm.user_id = u.id
    AND u.created_at < NOW() - INTERVAL '30 days'
    AND (
      COALESCE((u.raw_app_meta_data->>'is_anonymous')::boolean, false) = true
      OR (u.raw_app_meta_data->>'provider') = 'anonymous'
    );

  DELETE FROM public.workspaces w
  WHERE NOT EXISTS (
    SELECT 1 FROM public.workspace_memberships m WHERE m.workspace_id = w.id
  );

  RETURN QUERY SELECT n;
END;
$$;

REVOKE ALL ON FUNCTION public.purge_expired_anonymous_trial_data() FROM PUBLIC;
-- Supabase Dashboard SQL / cron as postgres can still execute; expose to service role for Edge/cron callers.
GRANT EXECUTE ON FUNCTION public.purge_expired_anonymous_trial_data() TO service_role;
