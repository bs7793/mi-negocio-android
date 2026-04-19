-- P0 hardening: explicit workspace contract for critical mutations and employee admin RPCs.

CREATE OR REPLACE FUNCTION public.update_product_basic(p_payload JSONB)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_workspace_id UUID;
  v_payload_workspace_id UUID;
  v_product_workspace_id UUID;
  v_product_id BIGINT;
  v_name TEXT;
  v_description TEXT;
  v_category_id BIGINT;
  v_image_url TEXT;
  v_current_user_id UUID := auth.uid();
BEGIN
  IF v_current_user_id IS NULL THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;

  IF p_payload IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;

  v_product_id := NULLIF(TRIM(p_payload->>'product_id'), '')::BIGINT;
  IF v_product_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;

  v_payload_workspace_id := NULLIF(TRIM(p_payload->>'workspace_id'), '')::UUID;
  v_workspace_id := COALESCE(v_payload_workspace_id, public.get_my_primary_workspace_id());
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_workspace_editor(v_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;

  SELECT p.workspace_id
  INTO v_product_workspace_id
  FROM public.products p
  WHERE p.id = v_product_id
    AND p.is_active = TRUE
  LIMIT 1;

  IF v_product_workspace_id IS NULL THEN
    RAISE EXCEPTION 'cross_workspace_reference';
  END IF;
  IF v_product_workspace_id <> v_workspace_id THEN
    RAISE EXCEPTION 'cross_workspace_reference';
  END IF;

  v_name := NULLIF(TRIM(p_payload->>'name'), '');
  IF v_name IS NULL THEN
    RAISE EXCEPTION 'Validation: product name is required';
  END IF;

  v_description := NULLIF(TRIM(p_payload->>'description'), '');
  v_image_url := NULLIF(TRIM(p_payload->>'image_url'), '');

  IF p_payload ? 'category_id' AND NULLIF(TRIM(p_payload->>'category_id'), '') IS NOT NULL THEN
    v_category_id := (p_payload->>'category_id')::BIGINT;
    IF NOT EXISTS (
      SELECT 1
      FROM public.categories c
      WHERE c.id = v_category_id
        AND c.workspace_id = v_workspace_id
    ) THEN
      RAISE EXCEPTION 'cross_workspace_reference';
    END IF;
  END IF;

  UPDATE public.products p
  SET
    name = v_name,
    description = v_description,
    category_id = v_category_id,
    image_url = CASE
      WHEN p_payload ? 'image_url' THEN v_image_url
      ELSE p.image_url
    END
  WHERE p.id = v_product_id
    AND p.workspace_id = v_workspace_id;

  RETURN (
    SELECT jsonb_build_object(
      'product_id', p.id,
      'name', p.name,
      'description', p.description,
      'image_url', p.image_url,
      'category_id', p.category_id,
      'category_name', c.name,
      'total_stock', COALESCE((
        SELECT SUM(vi.quantity)
        FROM public.product_variants v
        JOIN public.variant_inventory vi ON vi.variant_id = v.id
        WHERE v.product_id = p.id
      ), 0),
      'variants_count', (
        SELECT COUNT(*)
        FROM public.product_variants v
        WHERE v.product_id = p.id
          AND v.is_active = TRUE
      )
    )
    FROM public.products p
    LEFT JOIN public.categories c ON c.id = p.category_id
    WHERE p.id = v_product_id
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.list_workspace_members(p_workspace_id UUID DEFAULT NULL)
RETURNS TABLE (
  user_id UUID,
  email TEXT,
  full_name TEXT,
  role TEXT,
  status TEXT
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_workspace_id UUID := COALESCE(p_workspace_id, public.get_my_primary_workspace_id());
BEGIN
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_active_workspace_member(v_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;

  RETURN QUERY
  SELECT
    m.user_id,
    users.email,
    COALESCE(users.raw_user_meta_data ->> 'full_name', users.raw_user_meta_data ->> 'name', users.email, '') AS full_name,
    m.role,
    m.status
  FROM public.workspace_memberships m
  JOIN auth.users users ON users.id = m.user_id
  WHERE m.workspace_id = v_workspace_id
  ORDER BY m.created_at DESC;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_workspace_invite_code(
  p_role TEXT DEFAULT 'member',
  p_expires_in_hours INT DEFAULT 72,
  p_workspace_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_workspace_id UUID := COALESCE(p_workspace_id, public.get_my_primary_workspace_id());
  v_caller_id UUID := auth.uid();
  v_role TEXT := LOWER(BTRIM(p_role));
  v_hours INT := GREATEST(1, LEAST(p_expires_in_hours, 168));
  v_code TEXT;
  v_token TEXT;
  v_expires_at TIMESTAMPTZ := NOW() + make_interval(hours => v_hours);
BEGIN
  IF v_caller_id IS NULL THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_workspace_admin(v_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;
  IF v_role NOT IN ('admin', 'member') THEN
    RAISE EXCEPTION 'Validation: role must be admin or member';
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

CREATE OR REPLACE FUNCTION public.list_workspace_invite_codes(p_workspace_id UUID DEFAULT NULL)
RETURNS TABLE (
  invite_id UUID,
  invite_code TEXT,
  role TEXT,
  status TEXT,
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_workspace_id UUID := COALESCE(p_workspace_id, public.get_my_primary_workspace_id());
BEGIN
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_workspace_admin(v_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;

  RETURN QUERY
  SELECT
    wi.id AS invite_id,
    wi.invite_code,
    wi.role,
    wi.status,
    wi.expires_at,
    wi.created_at
  FROM public.workspace_invites wi
  WHERE wi.workspace_id = v_workspace_id
    AND wi.email IS NULL
  ORDER BY wi.created_at DESC;
END;
$$;

CREATE OR REPLACE FUNCTION public.revoke_workspace_invite_code(
  p_invite_code TEXT,
  p_workspace_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_workspace_id UUID := COALESCE(p_workspace_id, public.get_my_primary_workspace_id());
  v_code TEXT := UPPER(BTRIM(p_invite_code));
BEGIN
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_workspace_admin(v_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;
  IF v_code IS NULL OR v_code = '' THEN
    RAISE EXCEPTION 'Validation: invite code is required';
  END IF;

  UPDATE public.workspace_invites
  SET status = 'cancelled',
      updated_at = NOW()
  WHERE workspace_id = v_workspace_id
    AND invite_code = v_code
    AND status = 'pending';

  IF NOT FOUND THEN
    RAISE EXCEPTION 'cross_workspace_reference';
  END IF;

  RETURN jsonb_build_object(
    'success', true,
    'message', 'Invite code revoked successfully'
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.update_workspace_member_role_status(
  target_user_id UUID,
  role TEXT,
  status TEXT,
  p_workspace_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_workspace_id UUID := COALESCE(p_workspace_id, public.get_my_primary_workspace_id());
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
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF target_user_id IS NULL THEN
    RAISE EXCEPTION 'Validation: target user is required';
  END IF;

  SELECT wm.role
  INTO v_caller_role
  FROM public.workspace_memberships wm
  WHERE wm.workspace_id = v_workspace_id
    AND wm.user_id = v_caller_id
    AND wm.status = 'active'
  LIMIT 1;

  IF v_caller_role IS NULL OR v_caller_role NOT IN ('owner', 'admin') THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;
  IF v_role NOT IN ('owner', 'admin', 'member') THEN
    RAISE EXCEPTION 'Validation: role must be owner, admin, or member';
  END IF;
  IF v_status NOT IN ('active', 'invited', 'disabled') THEN
    RAISE EXCEPTION 'Validation: status must be active, invited, or disabled';
  END IF;

  SELECT wm.role
  INTO v_current_role
  FROM public.workspace_memberships wm
  WHERE wm.workspace_id = v_workspace_id
    AND wm.user_id = target_user_id
  LIMIT 1;

  IF v_current_role IS NULL THEN
    RAISE EXCEPTION 'cross_workspace_reference';
  END IF;
  IF v_current_role = 'owner' AND v_role <> 'owner' THEN
    RAISE EXCEPTION 'Validation: owner role cannot be downgraded';
  END IF;
  IF v_current_role = 'owner' AND v_status <> 'active' THEN
    RAISE EXCEPTION 'Validation: owner cannot be disabled or invited';
  END IF;
  IF v_role = 'owner' AND v_caller_role <> 'owner' THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;
  IF target_user_id = v_caller_id AND (v_role <> v_current_role OR v_status <> 'active') THEN
    RAISE EXCEPTION 'Validation: you cannot modify your own privileged membership';
  END IF;

  IF v_current_role = 'owner' THEN
    SELECT COUNT(*)
    INTO v_owner_count
    FROM public.workspace_memberships
    WHERE workspace_id = v_workspace_id
      AND role = 'owner'
      AND status = 'active';
    IF v_owner_count <= 1 AND (v_role <> 'owner' OR v_status <> 'active') THEN
      RAISE EXCEPTION 'Validation: workspace must keep at least one active owner';
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

CREATE OR REPLACE FUNCTION public.create_sale_with_lines_and_payments(
  p_payload JSONB,
  p_workspace_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_workspace_id UUID := COALESCE(p_workspace_id, NULLIF(TRIM(p_payload->>'workspace_id'), '')::UUID, public.get_my_primary_workspace_id());
  v_payload JSONB := COALESCE(p_payload, '{}'::JSONB);
BEGIN
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;

  v_payload := v_payload || jsonb_build_object('workspace_id', v_workspace_id::TEXT);
  RETURN public.create_sale_with_lines_and_payments(v_payload);
END;
$$;

CREATE OR REPLACE FUNCTION public.create_product_with_variants(
  p_payload JSONB,
  p_workspace_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_workspace_id UUID := COALESCE(p_workspace_id, NULLIF(TRIM(p_payload->>'workspace_id'), '')::UUID, public.get_my_primary_workspace_id());
  v_payload JSONB := COALESCE(p_payload, '{}'::JSONB);
BEGIN
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;

  v_payload := v_payload || jsonb_build_object('workspace_id', v_workspace_id::TEXT);
  RETURN public.create_product_with_variants(v_payload);
END;
$$;

CREATE OR REPLACE FUNCTION public.update_product_basic(
  p_payload JSONB,
  p_workspace_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_workspace_id UUID := COALESCE(p_workspace_id, NULLIF(TRIM(p_payload->>'workspace_id'), '')::UUID, public.get_my_primary_workspace_id());
  v_payload JSONB := COALESCE(p_payload, '{}'::JSONB);
BEGIN
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;

  v_payload := v_payload || jsonb_build_object('workspace_id', v_workspace_id::TEXT);
  RETURN public.update_product_basic(v_payload);
END;
$$;

GRANT EXECUTE ON FUNCTION public.list_workspace_members(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_workspace_invite_code(TEXT, INT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.list_workspace_invite_codes(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.revoke_workspace_invite_code(TEXT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_workspace_member_role_status(UUID, TEXT, TEXT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_sale_with_lines_and_payments(JSONB, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_product_with_variants(JSONB, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_product_basic(JSONB, UUID) TO authenticated;
