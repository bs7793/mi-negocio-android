-- Seed default product option types/values per workspace for low-friction trial onboarding.

CREATE OR REPLACE FUNCTION app.seed_default_product_options_for_workspace(p_workspace_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_type_id BIGINT;
  v_seed_user_id UUID;
  v_default_types JSONB := '[
    {"name":"Size","input_kind":"text"},
    {"name":"Color","input_kind":"text"},
    {"name":"Volume","input_kind":"text"},
    {"name":"Length","input_kind":"text"},
    {"name":"Material","input_kind":"text"}
  ]'::jsonb;
  v_default_values JSONB := '{
    "Size":["XS","S","M","L","XL"],
    "Color":["Black","White","Red","Blue","Green"],
    "Volume":["250ml","500ml","1L","2L"],
    "Length":["1m","2m","3m"],
    "Material":["Cotton","Polyester","Wood","Metal"]
  }'::jsonb;
  v_type JSONB;
  v_value TEXT;
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Workspace id is required';
  END IF;

  SELECT wm.user_id
  INTO v_seed_user_id
  FROM public.workspace_memberships wm
  WHERE wm.workspace_id = p_workspace_id
    AND wm.status = 'active'
  ORDER BY
    CASE wm.role
      WHEN 'owner' THEN 1
      WHEN 'admin' THEN 2
      ELSE 3
    END,
    wm.created_at
  LIMIT 1;

  IF v_seed_user_id IS NULL AND auth.uid() IS NULL THEN
    -- Some legacy/system workspaces may exist without active memberships.
    -- Skip seeding for those rows to avoid failing the whole migration batch.
    RETURN;
  END IF;

  FOR v_type IN SELECT * FROM jsonb_array_elements(v_default_types)
  LOOP
    INSERT INTO public.product_option_types (
      workspace_id,
      created_by,
      name,
      input_kind,
      is_active
    )
    SELECT
      p_workspace_id,
      COALESCE(auth.uid(), v_seed_user_id),
      v_type->>'name',
      COALESCE(v_type->>'input_kind', 'text'),
      TRUE
    WHERE NOT EXISTS (
      SELECT 1
      FROM public.product_option_types pot
      WHERE pot.workspace_id = p_workspace_id
        AND LOWER(BTRIM(pot.name)) = LOWER(BTRIM(v_type->>'name'))
    );

    SELECT pot.id
    INTO v_type_id
    FROM public.product_option_types pot
    WHERE pot.workspace_id = p_workspace_id
      AND LOWER(BTRIM(pot.name)) = LOWER(BTRIM(v_type->>'name'))
    LIMIT 1;

    IF v_type_id IS NULL THEN
      CONTINUE;
    END IF;

    FOR v_value IN
      SELECT jsonb_array_elements_text(v_default_values -> (v_type->>'name'))
    LOOP
      INSERT INTO public.product_option_values (
        workspace_id,
        option_type_id,
        created_by,
        value,
        is_active
      )
      SELECT
        p_workspace_id,
        v_type_id,
        COALESCE(auth.uid(), v_seed_user_id),
        v_value,
        TRUE
      WHERE NOT EXISTS (
        SELECT 1
        FROM public.product_option_values pov
        WHERE pov.workspace_id = p_workspace_id
          AND pov.option_type_id = v_type_id
          AND LOWER(BTRIM(pov.value)) = LOWER(BTRIM(v_value))
      );
    END LOOP;
  END LOOP;
END;
$$;

GRANT EXECUTE ON FUNCTION app.seed_default_product_options_for_workspace(UUID) TO authenticated;
