CREATE TABLE IF NOT EXISTS public.workspace_invites (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES public.workspaces(id) ON DELETE CASCADE,
  email TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('admin', 'member')),
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'cancelled', 'expired')),
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '7 days'),
  invited_by UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
  accepted_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
  accepted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_workspace_invites_workspace_id
  ON public.workspace_invites(workspace_id);

CREATE INDEX IF NOT EXISTS idx_workspace_invites_email_workspace
  ON public.workspace_invites(workspace_id, LOWER(BTRIM(email)));

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_trigger
    WHERE tgname = 'update_workspace_invites_updated_at'
  ) THEN
    CREATE TRIGGER update_workspace_invites_updated_at
    BEFORE UPDATE ON public.workspace_invites
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
  END IF;
END $$;

ALTER TABLE public.workspace_invites ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.workspace_invites FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS workspace_invites_select_policy ON public.workspace_invites;
CREATE POLICY workspace_invites_select_policy
  ON public.workspace_invites
  FOR SELECT
  USING (
    app.is_workspace_admin(workspace_id)
    OR accepted_by = auth.uid()
  );

DROP POLICY IF EXISTS workspace_invites_insert_policy ON public.workspace_invites;
CREATE POLICY workspace_invites_insert_policy
  ON public.workspace_invites
  FOR INSERT
  WITH CHECK (app.is_workspace_admin(workspace_id));

DROP POLICY IF EXISTS workspace_invites_update_policy ON public.workspace_invites;
CREATE POLICY workspace_invites_update_policy
  ON public.workspace_invites
  FOR UPDATE
  USING (
    app.is_workspace_admin(workspace_id)
    OR accepted_by = auth.uid()
  )
  WITH CHECK (
    app.is_workspace_admin(workspace_id)
    OR accepted_by = auth.uid()
  );

CREATE OR REPLACE FUNCTION public.list_workspace_members()
RETURNS TABLE (
  user_id UUID,
  email TEXT,
  full_name TEXT,
  role TEXT,
  status TEXT
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    m.user_id,
    users.email,
    COALESCE(users.raw_user_meta_data ->> 'full_name', users.raw_user_meta_data ->> 'name', users.email, '') AS full_name,
    m.role,
    m.status
  FROM public.workspace_memberships m
  JOIN auth.users users ON users.id = m.user_id
  WHERE m.workspace_id = public.get_my_primary_workspace_id()
    AND app.is_active_workspace_member(m.workspace_id)
  ORDER BY m.created_at DESC;
$$;

CREATE OR REPLACE FUNCTION public.invite_workspace_member(email TEXT, role TEXT DEFAULT 'member')
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_workspace_id UUID := public.get_my_primary_workspace_id();
  v_caller_id UUID := auth.uid();
  v_normalized_email TEXT := LOWER(BTRIM(email));
  v_role TEXT := LOWER(BTRIM(role));
  v_target_user_id UUID;
  v_token TEXT;
  v_member public.workspace_memberships%ROWTYPE;
BEGIN
  IF v_caller_id IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Workspace not found for current user';
  END IF;
  IF NOT app.is_workspace_admin(v_workspace_id) THEN
    RAISE EXCEPTION 'Only workspace admins can invite employees';
  END IF;
  IF v_normalized_email IS NULL OR v_normalized_email = '' THEN
    RAISE EXCEPTION 'Email is required';
  END IF;
  IF v_role NOT IN ('admin', 'member') THEN
    RAISE EXCEPTION 'Role must be admin or member';
  END IF;

  SELECT users.id
  INTO v_target_user_id
  FROM auth.users users
  WHERE LOWER(users.email) = v_normalized_email
  LIMIT 1;

  v_token := encode(gen_random_bytes(24), 'hex');

  INSERT INTO public.workspace_invites (
    workspace_id,
    email,
    role,
    status,
    token_hash,
    expires_at,
    invited_by
  )
  VALUES (
    v_workspace_id,
    v_normalized_email,
    v_role,
    'pending',
    encode(digest(v_token, 'sha256'), 'hex'),
    NOW() + INTERVAL '7 days',
    v_caller_id
  );

  IF v_target_user_id IS NULL THEN
    RETURN jsonb_build_object(
      'success', true,
      'message', 'Invite created. User will join after sign-up.',
      'invite_token', v_token,
      'member', NULL
    );
  END IF;

  INSERT INTO public.workspace_memberships (workspace_id, user_id, role, status)
  VALUES (v_workspace_id, v_target_user_id, v_role, 'active')
  ON CONFLICT (workspace_id, user_id)
  DO UPDATE
    SET role = EXCLUDED.role,
        status = 'active',
        updated_at = NOW();

  SELECT *
  INTO v_member
  FROM public.workspace_memberships
  WHERE workspace_id = v_workspace_id
    AND user_id = v_target_user_id;

  UPDATE public.workspace_invites
  SET status = 'accepted',
      accepted_by = v_target_user_id,
      accepted_at = NOW()
  WHERE workspace_id = v_workspace_id
    AND email = v_normalized_email
    AND status = 'pending';

  RETURN jsonb_build_object(
    'success', true,
    'message', 'Employee invited successfully',
    'member', jsonb_build_object(
      'user_id', v_member.user_id,
      'email', v_normalized_email,
      'full_name', v_normalized_email,
      'role', v_member.role,
      'status', v_member.status
    )
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
  v_role TEXT := LOWER(BTRIM(role));
  v_status TEXT := LOWER(BTRIM(status));
  v_member public.workspace_memberships%ROWTYPE;
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
  IF NOT app.is_workspace_admin(v_workspace_id) THEN
    RAISE EXCEPTION 'Only workspace admins can update employees';
  END IF;
  IF v_role NOT IN ('owner', 'admin', 'member') THEN
    RAISE EXCEPTION 'Role must be owner, admin, or member';
  END IF;
  IF v_status NOT IN ('active', 'invited', 'disabled') THEN
    RAISE EXCEPTION 'Status must be active, invited, or disabled';
  END IF;

  UPDATE public.workspace_memberships
  SET role = v_role,
      status = v_status,
      updated_at = NOW()
  WHERE workspace_id = v_workspace_id
    AND user_id = target_user_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Employee membership not found';
  END IF;

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

CREATE OR REPLACE FUNCTION public.accept_workspace_invite(invite_token TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id UUID := auth.uid();
  v_invite public.workspace_invites%ROWTYPE;
  v_hash TEXT;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;
  IF invite_token IS NULL OR BTRIM(invite_token) = '' THEN
    RAISE EXCEPTION 'Invite token is required';
  END IF;

  v_hash := encode(digest(BTRIM(invite_token), 'sha256'), 'hex');

  SELECT *
  INTO v_invite
  FROM public.workspace_invites invites
  WHERE invites.token_hash = v_hash
    AND invites.status = 'pending'
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

  INSERT INTO public.workspace_memberships (workspace_id, user_id, role, status)
  VALUES (v_invite.workspace_id, v_user_id, v_invite.role, 'active')
  ON CONFLICT (workspace_id, user_id)
  DO UPDATE
    SET role = EXCLUDED.role,
        status = 'active',
        updated_at = NOW();

  UPDATE public.workspace_invites
  SET status = 'accepted',
      accepted_by = v_user_id,
      accepted_at = NOW(),
      updated_at = NOW()
  WHERE id = v_invite.id;

  RETURN jsonb_build_object(
    'success', true,
    'message', 'Invite accepted successfully'
  );
END;
$$;

GRANT SELECT, INSERT, UPDATE ON public.workspace_invites TO authenticated;
GRANT EXECUTE ON FUNCTION public.list_workspace_members() TO authenticated;
GRANT EXECUTE ON FUNCTION public.invite_workspace_member(TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_workspace_member_role_status(UUID, TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.accept_workspace_invite(TEXT) TO authenticated;
