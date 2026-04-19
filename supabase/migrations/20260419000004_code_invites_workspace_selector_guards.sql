ALTER TABLE public.workspace_invites
  ALTER COLUMN email DROP NOT NULL;

ALTER TABLE public.workspace_invites
  ADD COLUMN IF NOT EXISTS invite_code TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_workspace_invites_invite_code
  ON public.workspace_invites(invite_code)
  WHERE invite_code IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.user_workspace_preferences (
  user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  active_workspace_id UUID NOT NULL REFERENCES public.workspaces(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_trigger
    WHERE tgname = 'update_user_workspace_preferences_updated_at'
  ) THEN
    CREATE TRIGGER update_user_workspace_preferences_updated_at
    BEFORE UPDATE ON public.user_workspace_preferences
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
  END IF;
END $$;

ALTER TABLE public.user_workspace_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_workspace_preferences FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS user_workspace_preferences_select_policy ON public.user_workspace_preferences;
CREATE POLICY user_workspace_preferences_select_policy
  ON public.user_workspace_preferences
  FOR SELECT
  USING (user_id = auth.uid());

DROP POLICY IF EXISTS user_workspace_preferences_upsert_policy ON public.user_workspace_preferences;
CREATE POLICY user_workspace_preferences_upsert_policy
  ON public.user_workspace_preferences
  FOR INSERT
  WITH CHECK (
    user_id = auth.uid()
    AND app.is_active_workspace_member(active_workspace_id)
  );

DROP POLICY IF EXISTS user_workspace_preferences_update_policy ON public.user_workspace_preferences;
CREATE POLICY user_workspace_preferences_update_policy
  ON public.user_workspace_preferences
  FOR UPDATE
  USING (user_id = auth.uid())
  WITH CHECK (
    user_id = auth.uid()
    AND app.is_active_workspace_member(active_workspace_id)
  );

CREATE OR REPLACE FUNCTION app.generate_invite_code()
RETURNS TEXT
LANGUAGE plpgsql
VOLATILE
AS $$
DECLARE
  v_code TEXT;
BEGIN
  LOOP
    v_code := UPPER(SUBSTRING(encode(gen_random_bytes(5), 'hex') FROM 1 FOR 10));
    EXIT WHEN NOT EXISTS (
      SELECT 1
      FROM public.workspace_invites wi
      WHERE wi.invite_code = v_code
        AND wi.status = 'pending'
        AND wi.expires_at > NOW()
    );
  END LOOP;
  RETURN v_code;
END;
$$;

CREATE OR REPLACE FUNCTION public.get_my_primary_workspace_id()
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id UUID := auth.uid();
  v_preferred_workspace UUID;
BEGIN
  IF v_user_id IS NULL THEN
    RETURN NULL;
  END IF;

  SELECT p.active_workspace_id
  INTO v_preferred_workspace
  FROM public.user_workspace_preferences p
  WHERE p.user_id = v_user_id
  LIMIT 1;

  IF v_preferred_workspace IS NOT NULL
     AND app.is_active_workspace_member(v_preferred_workspace) THEN
    RETURN v_preferred_workspace;
  END IF;

  RETURN app.ensure_personal_workspace(v_user_id);
END;
$$;

CREATE OR REPLACE FUNCTION public.list_my_workspaces()
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
    w.id = public.get_my_primary_workspace_id() AS is_active
  FROM public.workspace_memberships m
  JOIN public.workspaces w ON w.id = m.workspace_id
  WHERE m.user_id = auth.uid()
    AND m.status = 'active'
  ORDER BY is_active DESC, w.created_at ASC;
$$;

CREATE OR REPLACE FUNCTION public.set_my_active_workspace_id(p_workspace_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id UUID := auth.uid();
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Workspace id is required';
  END IF;
  IF NOT app.is_active_workspace_member(p_workspace_id) THEN
    RAISE EXCEPTION 'You are not an active member of this workspace';
  END IF;

  INSERT INTO public.user_workspace_preferences (user_id, active_workspace_id)
  VALUES (v_user_id, p_workspace_id)
  ON CONFLICT (user_id)
  DO UPDATE
    SET active_workspace_id = EXCLUDED.active_workspace_id,
        updated_at = NOW();

  RETURN jsonb_build_object(
    'success', true,
    'active_workspace_id', p_workspace_id
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.create_workspace_invite_code(
  p_role TEXT DEFAULT 'member',
  p_expires_in_hours INT DEFAULT 72
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_workspace_id UUID := public.get_my_primary_workspace_id();
  v_caller_id UUID := auth.uid();
  v_role TEXT := LOWER(BTRIM(p_role));
  v_hours INT := GREATEST(1, LEAST(p_expires_in_hours, 168));
  v_code TEXT;
  v_token TEXT;
  v_expires_at TIMESTAMPTZ := NOW() + make_interval(hours => v_hours);
BEGIN
  IF v_caller_id IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Workspace not found for current user';
  END IF;
  IF NOT app.is_workspace_admin(v_workspace_id) THEN
    RAISE EXCEPTION 'Only workspace admins can create invite codes';
  END IF;
  IF v_role NOT IN ('admin', 'member') THEN
    RAISE EXCEPTION 'Role must be admin or member';
  END IF;

  v_code := app.generate_invite_code();
  v_token := encode(gen_random_bytes(24), 'hex');

  INSERT INTO public.workspace_invites (
    workspace_id,
    email,
    role,
    status,
    token_hash,
    invite_code,
    expires_at,
    invited_by
  ) VALUES (
    v_workspace_id,
    NULL,
    v_role,
    'pending',
    encode(digest(v_token, 'sha256'), 'hex'),
    v_code,
    v_expires_at,
    v_caller_id
  );

  RETURN jsonb_build_object(
    'success', true,
    'message', 'Invite code created successfully',
    'invite_code', v_code,
    'role', v_role,
    'expires_at', v_expires_at
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.list_workspace_invite_codes()
RETURNS TABLE (
  invite_id UUID,
  invite_code TEXT,
  role TEXT,
  status TEXT,
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    wi.id AS invite_id,
    wi.invite_code,
    wi.role,
    wi.status,
    wi.expires_at,
    wi.created_at
  FROM public.workspace_invites wi
  WHERE wi.workspace_id = public.get_my_primary_workspace_id()
    AND wi.email IS NULL
  ORDER BY wi.created_at DESC;
$$;

CREATE OR REPLACE FUNCTION public.revoke_workspace_invite_code(p_invite_code TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_workspace_id UUID := public.get_my_primary_workspace_id();
  v_code TEXT := UPPER(BTRIM(p_invite_code));
BEGIN
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Workspace not found for current user';
  END IF;
  IF NOT app.is_workspace_admin(v_workspace_id) THEN
    RAISE EXCEPTION 'Only workspace admins can revoke invite codes';
  END IF;
  IF v_code IS NULL OR v_code = '' THEN
    RAISE EXCEPTION 'Invite code is required';
  END IF;

  UPDATE public.workspace_invites
  SET status = 'cancelled',
      updated_at = NOW()
  WHERE workspace_id = v_workspace_id
    AND invite_code = v_code
    AND status = 'pending';

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Pending invite code not found';
  END IF;

  RETURN jsonb_build_object(
    'success', true,
    'message', 'Invite code revoked successfully'
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.accept_workspace_invite(invite_token TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id UUID := auth.uid();
  v_invite public.workspace_invites%ROWTYPE;
  v_input TEXT := UPPER(BTRIM(invite_token));
  v_hash TEXT := encode(digest(BTRIM(invite_token), 'sha256'), 'hex');
  v_existing_role TEXT;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;
  IF invite_token IS NULL OR BTRIM(invite_token) = '' THEN
    RAISE EXCEPTION 'Invite token is required';
  END IF;

  SELECT *
  INTO v_invite
  FROM public.workspace_invites invites
  WHERE invites.status = 'pending'
    AND (invites.token_hash = v_hash OR invites.invite_code = v_input)
  LIMIT 1;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Invite not found or already used';
  END IF;
  IF v_invite.expires_at < NOW() THEN
    UPDATE public.workspace_invites
    SET status = 'expired',
        updated_at = NOW()
    WHERE id = v_invite.id;
    RAISE EXCEPTION 'Invite expired';
  END IF;

  SELECT role
  INTO v_existing_role
  FROM public.workspace_memberships
  WHERE workspace_id = v_invite.workspace_id
    AND user_id = v_user_id
  LIMIT 1;

  IF v_existing_role = 'owner' AND v_invite.role <> 'owner' THEN
    RAISE EXCEPTION 'Owner role cannot be downgraded by invite';
  END IF;

  INSERT INTO public.workspace_memberships (workspace_id, user_id, role, status)
  VALUES (v_invite.workspace_id, v_user_id, v_invite.role, 'active')
  ON CONFLICT (workspace_id, user_id)
  DO UPDATE
    SET role = CASE
      WHEN public.workspace_memberships.role = 'owner' THEN public.workspace_memberships.role
      ELSE EXCLUDED.role
    END,
    status = 'active',
    updated_at = NOW();

  UPDATE public.workspace_invites
  SET status = 'accepted',
      accepted_by = v_user_id,
      accepted_at = NOW(),
      updated_at = NOW()
  WHERE id = v_invite.id;

  INSERT INTO public.user_workspace_preferences (user_id, active_workspace_id)
  VALUES (v_user_id, v_invite.workspace_id)
  ON CONFLICT (user_id)
  DO UPDATE
    SET active_workspace_id = EXCLUDED.active_workspace_id,
        updated_at = NOW();

  RETURN jsonb_build_object(
    'success', true,
    'message', 'Invite accepted successfully',
    'workspace_id', v_invite.workspace_id
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.update_workspace_member_role_status(
  target_user_id UUID,
  role TEXT,
  status TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_workspace_id UUID := public.get_my_primary_workspace_id();
  v_caller_id UUID := auth.uid();
  v_caller_role TEXT;
  v_role TEXT := LOWER(BTRIM(role));
  v_status TEXT := LOWER(BTRIM(status));
  v_member public.workspace_memberships%ROWTYPE;
  v_current_role TEXT;
  v_owner_count BIGINT;
  v_email TEXT;
  v_full_name TEXT;
BEGIN
  IF v_caller_id IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Workspace not found for current user';
  END IF;
  IF target_user_id IS NULL THEN
    RAISE EXCEPTION 'Target user is required';
  END IF;

  SELECT role
  INTO v_caller_role
  FROM public.workspace_memberships
  WHERE workspace_id = v_workspace_id
    AND user_id = v_caller_id
    AND status = 'active'
  LIMIT 1;

  IF v_caller_role IS NULL OR v_caller_role NOT IN ('owner', 'admin') THEN
    RAISE EXCEPTION 'Only workspace admins can update employees';
  END IF;
  IF v_role NOT IN ('owner', 'admin', 'member') THEN
    RAISE EXCEPTION 'Role must be owner, admin, or member';
  END IF;
  IF v_status NOT IN ('active', 'invited', 'disabled') THEN
    RAISE EXCEPTION 'Status must be active, invited, or disabled';
  END IF;

  SELECT role
  INTO v_current_role
  FROM public.workspace_memberships
  WHERE workspace_id = v_workspace_id
    AND user_id = target_user_id
  LIMIT 1;

  IF v_current_role IS NULL THEN
    RAISE EXCEPTION 'Employee membership not found';
  END IF;
  IF v_current_role = 'owner' AND v_role <> 'owner' THEN
    RAISE EXCEPTION 'Owner role cannot be downgraded';
  END IF;
  IF v_current_role = 'owner' AND v_status <> 'active' THEN
    RAISE EXCEPTION 'Owner cannot be disabled or invited';
  END IF;
  IF v_role = 'owner' AND v_caller_role <> 'owner' THEN
    RAISE EXCEPTION 'Only owner can assign owner role';
  END IF;
  IF target_user_id = v_caller_id AND (v_role <> v_current_role OR v_status <> 'active') THEN
    RAISE EXCEPTION 'You cannot modify your own privileged membership';
  END IF;

  IF v_current_role = 'owner' THEN
    SELECT COUNT(*)
    INTO v_owner_count
    FROM public.workspace_memberships
    WHERE workspace_id = v_workspace_id
      AND role = 'owner'
      AND status = 'active';
    IF v_owner_count <= 1 AND (v_role <> 'owner' OR v_status <> 'active') THEN
      RAISE EXCEPTION 'Workspace must keep at least one active owner';
    END IF;
  END IF;

  UPDATE public.workspace_memberships
  SET role = v_role,
      status = v_status,
      updated_at = NOW()
  WHERE workspace_id = v_workspace_id
    AND user_id = target_user_id;

  SELECT *
  INTO v_member
  FROM public.workspace_memberships
  WHERE workspace_id = v_workspace_id
    AND user_id = target_user_id;

  SELECT users.email,
         COALESCE(users.raw_user_meta_data ->> 'full_name', users.raw_user_meta_data ->> 'name', users.email, '')
  INTO v_email, v_full_name
  FROM auth.users users
  WHERE users.id = target_user_id;

  RETURN jsonb_build_object(
    'success', true,
    'message', 'Employee updated successfully',
    'member', jsonb_build_object(
      'user_id', v_member.user_id,
      'email', v_email,
      'full_name', v_full_name,
      'role', v_member.role,
      'status', v_member.status
    )
  );
END;
$$;

GRANT SELECT, INSERT, UPDATE ON public.user_workspace_preferences TO authenticated;
GRANT EXECUTE ON FUNCTION public.list_my_workspaces() TO authenticated;
GRANT EXECUTE ON FUNCTION public.set_my_active_workspace_id(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_workspace_invite_code(TEXT, INT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.list_workspace_invite_codes() TO authenticated;
GRANT EXECUTE ON FUNCTION public.revoke_workspace_invite_code(TEXT) TO authenticated;
