-- When a personal workspace is created for a new user, add one category, one warehouse,
-- and default product option types/values (via existing seed helper).

CREATE OR REPLACE FUNCTION app.ensure_workspace_onboarding_defaults(
  p_workspace_id UUID,
  p_user_id UUID
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF p_workspace_id IS NULL OR p_user_id IS NULL THEN
    RETURN;
  END IF;

  INSERT INTO public.categories (workspace_id, name, created_by)
  SELECT p_workspace_id, 'General', p_user_id
  WHERE NOT EXISTS (
    SELECT 1 FROM public.categories c WHERE c.workspace_id = p_workspace_id
  );

  INSERT INTO public.warehouses (workspace_id, name, created_by)
  SELECT p_workspace_id, 'Main', p_user_id
  WHERE NOT EXISTS (
    SELECT 1 FROM public.warehouses w WHERE w.workspace_id = p_workspace_id
  );

  PERFORM app.seed_default_product_options_for_workspace(p_workspace_id);
END;
$$;

REVOKE ALL ON FUNCTION app.ensure_workspace_onboarding_defaults(UUID, UUID) FROM PUBLIC;

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

  PERFORM app.ensure_workspace_onboarding_defaults(v_ws, p_user_id);

  RETURN v_ws;
END;
$$;
