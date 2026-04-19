-- Per-device workspace stabilization:
-- remove global active workspace dependency and require explicit workspace context.

CREATE OR REPLACE FUNCTION public.get_my_primary_workspace_id()
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id UUID := auth.uid();
  v_override TEXT := NULLIF(BTRIM(current_setting('app.workspace_id', true)), '');
  v_workspace_id UUID;
BEGIN
  IF v_user_id IS NULL THEN
    RETURN NULL;
  END IF;

  IF v_override IS NOT NULL THEN
    v_workspace_id := v_override::UUID;
    IF NOT app.is_active_workspace_member(v_workspace_id) THEN
      RAISE EXCEPTION 'workspace_forbidden';
    END IF;
    RETURN v_workspace_id;
  END IF;

  -- Legacy fallback for old callers that still rely on this function.
  RETURN app.ensure_personal_workspace(v_user_id);
END;
$$;

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
    w.id = p_selected_workspace_id AS is_active
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
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_active_workspace_member(p_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;

  -- Deprecated for per-device model: keep RPC for backward compatibility.
  RETURN jsonb_build_object(
    'success', true,
    'active_workspace_id', p_workspace_id
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
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;
  IF invite_token IS NULL OR BTRIM(invite_token) = '' THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;

  SELECT *
  INTO v_invite
  FROM public.workspace_invites wi
  WHERE wi.status = 'pending'
    AND (
      wi.token_hash = v_hash
      OR wi.invite_code = v_input
    )
  ORDER BY wi.created_at DESC
  LIMIT 1;

  IF v_invite.id IS NULL THEN
    RAISE EXCEPTION 'cross_workspace_reference';
  END IF;
  IF v_invite.expires_at <= NOW() THEN
    UPDATE public.workspace_invites
    SET status = 'expired',
        updated_at = NOW()
    WHERE id = v_invite.id;
    RAISE EXCEPTION 'cross_workspace_reference';
  END IF;

  SELECT role
  INTO v_existing_role
  FROM public.workspace_memberships
  WHERE workspace_id = v_invite.workspace_id
    AND user_id = v_user_id
  LIMIT 1;

  IF v_existing_role = 'owner' AND v_invite.role <> 'owner' THEN
    RAISE EXCEPTION 'workspace_forbidden';
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

  RETURN jsonb_build_object(
    'success', true,
    'message', 'Invite accepted successfully',
    'workspace_id', v_invite.workspace_id
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
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_active_workspace_member(p_workspace_id) THEN
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
  WHERE m.workspace_id = p_workspace_id
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
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.create_workspace_invite_code(p_role, p_expires_in_hours);
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
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN QUERY
  SELECT * FROM public.list_workspace_invite_codes();
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
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.revoke_workspace_invite_code(p_invite_code);
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
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.update_workspace_member_role_status(target_user_id, role, status);
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
  v_payload JSONB := COALESCE(p_payload, '{}'::JSONB);
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  v_payload := v_payload || jsonb_build_object('workspace_id', p_workspace_id::TEXT);
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
  v_payload JSONB := COALESCE(p_payload, '{}'::JSONB);
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  v_payload := v_payload || jsonb_build_object('workspace_id', p_workspace_id::TEXT);
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
  v_payload JSONB := COALESCE(p_payload, '{}'::JSONB);
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  v_payload := v_payload || jsonb_build_object('workspace_id', p_workspace_id::TEXT);
  RETURN public.update_product_basic(v_payload);
END;
$$;

CREATE OR REPLACE FUNCTION public.get_sellable_variants(
  p_search TEXT DEFAULT NULL,
  p_limit INT DEFAULT 30,
  p_warehouse_id BIGINT DEFAULT NULL,
  p_workspace_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_sellable_variants(p_search, p_limit, p_warehouse_id);
END;
$$;

CREATE OR REPLACE FUNCTION public.get_products_list(
  p_limit INT DEFAULT 20,
  p_offset INT DEFAULT 0,
  p_search TEXT DEFAULT NULL,
  p_category_id BIGINT DEFAULT NULL,
  p_warehouse_id BIGINT DEFAULT NULL,
  p_workspace_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_products_list(p_limit, p_offset, p_search, p_category_id, p_warehouse_id);
END;
$$;

CREATE OR REPLACE FUNCTION public.get_product_options_catalog(p_workspace_id UUID DEFAULT NULL)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_product_options_catalog();
END;
$$;

CREATE OR REPLACE FUNCTION public.get_sales_daily_summary(
  p_warehouse_id BIGINT DEFAULT NULL,
  p_start_at TIMESTAMPTZ DEFAULT date_trunc('day', NOW()),
  p_end_at TIMESTAMPTZ DEFAULT date_trunc('day', NOW()) + INTERVAL '1 day',
  p_workspace_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_sales_daily_summary(p_warehouse_id, p_start_at, p_end_at);
END;
$$;

CREATE OR REPLACE FUNCTION public.get_dashboard_sales_feed(
  p_warehouse_id BIGINT DEFAULT NULL,
  p_start_at TIMESTAMPTZ DEFAULT date_trunc('month', NOW()),
  p_end_at TIMESTAMPTZ DEFAULT date_trunc('month', NOW()) + INTERVAL '1 month',
  p_limit INT DEFAULT 50,
  p_workspace_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_dashboard_sales_feed(p_warehouse_id, p_start_at, p_end_at, p_limit);
END;
$$;

CREATE OR REPLACE FUNCTION public.get_income_statement_monthly_summary(
  p_warehouse_id BIGINT DEFAULT NULL,
  p_start_at TIMESTAMPTZ DEFAULT date_trunc('month', NOW()),
  p_end_at TIMESTAMPTZ DEFAULT date_trunc('month', NOW()) + INTERVAL '1 month',
  p_workspace_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_income_statement_monthly_summary(p_warehouse_id, p_start_at, p_end_at);
END;
$$;

CREATE OR REPLACE FUNCTION public.get_dashboard_sale_detail(
  p_sale_id BIGINT,
  p_workspace_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_dashboard_sale_detail(p_sale_id);
END;
$$;

GRANT EXECUTE ON FUNCTION public.list_my_workspaces(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_sellable_variants(TEXT, INT, BIGINT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_products_list(INT, INT, TEXT, BIGINT, BIGINT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_product_options_catalog(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_sales_daily_summary(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_dashboard_sales_feed(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, INT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_income_statement_monthly_summary(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_dashboard_sale_detail(BIGINT, UUID) TO authenticated;
