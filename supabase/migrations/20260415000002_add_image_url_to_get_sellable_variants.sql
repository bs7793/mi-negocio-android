CREATE OR REPLACE FUNCTION public.get_sellable_variants(
  p_search TEXT DEFAULT NULL,
  p_limit INT DEFAULT 30,
  p_warehouse_id BIGINT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_workspace_id UUID;
  v_search TEXT := NULLIF(TRIM(p_search), '');
  v_limit INT := LEAST(GREATEST(COALESCE(p_limit, 30), 1), 100);
BEGIN
  v_workspace_id := public.get_my_primary_workspace_id();
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Workspace could not be resolved';
  END IF;

  IF NOT app.is_active_workspace_member(v_workspace_id) THEN
    RAISE EXCEPTION 'Not authorized to read variants in workspace %', v_workspace_id;
  END IF;

  IF p_warehouse_id IS NOT NULL AND NOT EXISTS (
    SELECT 1
    FROM public.warehouses w
    WHERE w.id = p_warehouse_id
      AND w.workspace_id = v_workspace_id
  ) THEN
    RAISE EXCEPTION 'Validation: warehouse_id % does not belong to workspace', p_warehouse_id;
  END IF;

  RETURN (
    WITH variant_rows AS (
      SELECT
        pv.id AS variant_id,
        p.id AS product_id,
        p.name AS product_name,
        p.image_url AS image_url,
        pv.sku,
        pv.barcode,
        pv.unit_price,
        pv.cost_price,
        COALESCE((
          SELECT SUM(vi.quantity)
          FROM public.variant_inventory vi
          WHERE vi.variant_id = pv.id
            AND (p_warehouse_id IS NULL OR vi.warehouse_id = p_warehouse_id)
        ), 0) AS stock_total
      FROM public.product_variants pv
      JOIN public.products p ON p.id = pv.product_id
      WHERE pv.workspace_id = v_workspace_id
        AND pv.is_active = TRUE
        AND p.is_active = TRUE
        AND (
          v_search IS NULL
          OR p.name ILIKE '%' || v_search || '%'
          OR pv.sku ILIKE '%' || v_search || '%'
          OR COALESCE(pv.barcode, '') ILIKE '%' || v_search || '%'
        )
      ORDER BY p.name ASC, pv.sku ASC
      LIMIT v_limit
    )
    SELECT jsonb_build_object(
      'items', COALESCE((
        SELECT jsonb_agg(
          jsonb_build_object(
            'variant_id', vr.variant_id,
            'product_id', vr.product_id,
            'product_name', vr.product_name,
            'image_url', vr.image_url,
            'sku', vr.sku,
            'barcode', vr.barcode,
            'unit_price', vr.unit_price,
            'cost_price', vr.cost_price,
            'stock_total', vr.stock_total,
            'options', COALESCE((
              SELECT jsonb_agg(
                jsonb_build_object(
                  'type', pot.name,
                  'value', pov.value
                )
                ORDER BY pot.name, pov.value
              )
              FROM public.product_variant_option_values pvov
              JOIN public.product_option_types pot ON pot.id = pvov.option_type_id
              JOIN public.product_option_values pov ON pov.id = pvov.option_value_id
              WHERE pvov.variant_id = vr.variant_id
            ), '[]'::JSONB)
          )
        )
        FROM variant_rows vr
      ), '[]'::JSONB)
    )
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_sellable_variants(TEXT, INT, BIGINT) TO authenticated;
