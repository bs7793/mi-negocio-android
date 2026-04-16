CREATE OR REPLACE FUNCTION public.update_product_basic(p_payload JSONB)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_workspace_id UUID;
  v_product_id BIGINT;
  v_name TEXT;
  v_description TEXT;
  v_category_id BIGINT;
  v_image_url TEXT;
  v_current_user_id UUID := auth.uid();
BEGIN
  IF v_current_user_id IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;

  IF p_payload IS NULL THEN
    RAISE EXCEPTION 'Payload is required';
  END IF;

  v_product_id := NULLIF(TRIM(p_payload->>'product_id'), '')::BIGINT;
  IF v_product_id IS NULL THEN
    RAISE EXCEPTION 'Validation: product_id is required';
  END IF;

  SELECT p.workspace_id
  INTO v_workspace_id
  FROM public.products p
  WHERE p.id = v_product_id
    AND p.is_active = TRUE
  LIMIT 1;

  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Validation: product_id % does not exist', v_product_id;
  END IF;

  IF NOT app.is_workspace_editor(v_workspace_id) THEN
    RAISE EXCEPTION 'Not authorized to edit products in workspace %', v_workspace_id;
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
      RAISE EXCEPTION 'Validation: category_id % does not belong to workspace', v_category_id;
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

GRANT EXECUTE ON FUNCTION public.update_product_basic(JSONB) TO authenticated;
