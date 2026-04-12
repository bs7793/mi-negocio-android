-- RPCs for creating products with variants and listing products with pagination.

CREATE OR REPLACE FUNCTION public.create_product_with_variants(p_payload JSONB)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_workspace_id UUID;
  v_name TEXT;
  v_description TEXT;
  v_category_id BIGINT;
  v_product_id BIGINT;
  v_variant JSONB;
  v_variant_id BIGINT;
  v_variant_sku TEXT;
  v_variant_price NUMERIC(12, 2);
  v_variant_cost NUMERIC(12, 2);
  v_option_item JSONB;
  v_option_type_name TEXT;
  v_option_value_text TEXT;
  v_option_type_id BIGINT;
  v_option_value_id BIGINT;
  v_inventory_item JSONB;
  v_inventory_id BIGINT;
  v_warehouse_id BIGINT;
  v_quantity NUMERIC(14, 3);
  v_reorder_level NUMERIC(14, 3);
  v_alert_active BOOLEAN;
  v_alert_method TEXT;
  v_current_user_id UUID := auth.uid();
BEGIN
  IF v_current_user_id IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;

  IF p_payload IS NULL THEN
    RAISE EXCEPTION 'Payload is required';
  END IF;

  v_workspace_id := COALESCE(
    NULLIF(TRIM(p_payload->>'workspace_id'), '')::UUID,
    public.get_my_primary_workspace_id()
  );

  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Workspace could not be resolved';
  END IF;

  IF NOT app.is_workspace_editor(v_workspace_id) THEN
    RAISE EXCEPTION 'Not authorized to create products in workspace %', v_workspace_id;
  END IF;

  v_name := NULLIF(TRIM(p_payload->>'name'), '');
  IF v_name IS NULL THEN
    RAISE EXCEPTION 'Validation: Product name is required';
  END IF;

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

  IF jsonb_typeof(p_payload->'variants') <> 'array' OR jsonb_array_length(p_payload->'variants') = 0 THEN
    RAISE EXCEPTION 'Validation: at least one variant is required';
  END IF;

  v_description := NULLIF(TRIM(p_payload->>'description'), '');

  INSERT INTO public.products (
    workspace_id,
    created_by,
    category_id,
    name,
    description,
    is_active
  )
  VALUES (
    v_workspace_id,
    v_current_user_id,
    v_category_id,
    v_name,
    v_description,
    TRUE
  )
  RETURNING id INTO v_product_id;

  -- Ensure trial defaults are always available for this workspace.
  PERFORM app.seed_default_product_options_for_workspace(v_workspace_id);

  FOR v_variant IN
    SELECT * FROM jsonb_array_elements(p_payload->'variants')
  LOOP
    v_variant_sku := NULLIF(TRIM(v_variant->>'sku'), '');
    v_variant_price := COALESCE((v_variant->>'unit_price')::NUMERIC, 0);
    v_variant_cost := NULLIF(v_variant->>'cost_price', '')::NUMERIC;

    IF v_variant_sku IS NULL THEN
      RAISE EXCEPTION 'Validation: each variant requires sku';
    END IF;

    IF v_variant_price < 0 THEN
      RAISE EXCEPTION 'Validation: unit_price must be >= 0';
    END IF;

    IF v_variant_cost IS NOT NULL AND v_variant_cost < 0 THEN
      RAISE EXCEPTION 'Validation: cost_price must be >= 0';
    END IF;

    INSERT INTO public.product_variants (
      workspace_id,
      product_id,
      created_by,
      sku,
      barcode,
      unit_price,
      cost_price,
      is_active
    )
    VALUES (
      v_workspace_id,
      v_product_id,
      v_current_user_id,
      v_variant_sku,
      NULLIF(TRIM(v_variant->>'barcode'), ''),
      v_variant_price,
      v_variant_cost,
      TRUE
    )
    RETURNING id INTO v_variant_id;

    IF jsonb_typeof(v_variant->'option_values') = 'array' THEN
      FOR v_option_item IN
        SELECT * FROM jsonb_array_elements(v_variant->'option_values')
      LOOP
        v_option_type_name := NULLIF(TRIM(v_option_item->>'type'), '');
        v_option_value_text := NULLIF(TRIM(v_option_item->>'value'), '');

        IF v_option_type_name IS NULL OR v_option_value_text IS NULL THEN
          RAISE EXCEPTION 'Validation: option_values requires non-empty type and value';
        END IF;

        IF LENGTH(v_option_type_name) > 50 THEN
          RAISE EXCEPTION 'Validation: option type must be 50 chars or less';
        END IF;

        IF LENGTH(v_option_value_text) > 100 THEN
          RAISE EXCEPTION 'Validation: option value must be 100 chars or less';
        END IF;

        INSERT INTO public.product_option_types (
          workspace_id,
          created_by,
          name,
          input_kind,
          is_active
        )
        SELECT
          v_workspace_id,
          v_current_user_id,
          v_option_type_name,
          'text',
          TRUE
        WHERE NOT EXISTS (
          SELECT 1
          FROM public.product_option_types pot
          WHERE pot.workspace_id = v_workspace_id
            AND LOWER(BTRIM(pot.name)) = LOWER(BTRIM(v_option_type_name))
        );

        SELECT id
        INTO v_option_type_id
        FROM public.product_option_types
        WHERE workspace_id = v_workspace_id
          AND LOWER(BTRIM(name)) = LOWER(BTRIM(v_option_type_name))
        LIMIT 1;

        INSERT INTO public.product_option_values (
          workspace_id,
          option_type_id,
          created_by,
          value,
          is_active
        )
        SELECT
          v_workspace_id,
          v_option_type_id,
          v_current_user_id,
          v_option_value_text,
          TRUE
        WHERE NOT EXISTS (
          SELECT 1
          FROM public.product_option_values pov
          WHERE pov.workspace_id = v_workspace_id
            AND pov.option_type_id = v_option_type_id
            AND LOWER(BTRIM(pov.value)) = LOWER(BTRIM(v_option_value_text))
        );

        SELECT id
        INTO v_option_value_id
        FROM public.product_option_values
        WHERE workspace_id = v_workspace_id
          AND option_type_id = v_option_type_id
          AND LOWER(BTRIM(value)) = LOWER(BTRIM(v_option_value_text))
        LIMIT 1;

        INSERT INTO public.product_variant_option_values (
          workspace_id,
          variant_id,
          option_type_id,
          option_value_id,
          created_by
        )
        VALUES (
          v_workspace_id,
          v_variant_id,
          v_option_type_id,
          v_option_value_id,
          v_current_user_id
        )
        ON CONFLICT (variant_id, option_type_id) DO UPDATE
        SET option_value_id = EXCLUDED.option_value_id;
      END LOOP;
    END IF;

    IF jsonb_typeof(v_variant->'inventory') <> 'array' OR jsonb_array_length(v_variant->'inventory') = 0 THEN
      RAISE EXCEPTION 'Validation: each variant requires at least one inventory row';
    END IF;

    FOR v_inventory_item IN
      SELECT * FROM jsonb_array_elements(v_variant->'inventory')
    LOOP
      v_warehouse_id := (v_inventory_item->>'warehouse_id')::BIGINT;
      v_quantity := COALESCE((v_inventory_item->>'quantity')::NUMERIC, 0);
      v_reorder_level := NULLIF(v_inventory_item->>'reorder_level', '')::NUMERIC;
      v_alert_active := COALESCE((v_inventory_item->>'alert_active')::BOOLEAN, FALSE);
      v_alert_method := NULLIF(TRIM(v_inventory_item->>'alert_method'), '');

      IF v_warehouse_id IS NULL THEN
        RAISE EXCEPTION 'Validation: warehouse_id is required in inventory rows';
      END IF;

      IF NOT EXISTS (
        SELECT 1
        FROM public.warehouses w
        WHERE w.id = v_warehouse_id
          AND w.workspace_id = v_workspace_id
      ) THEN
        RAISE EXCEPTION 'Validation: warehouse_id % does not belong to workspace', v_warehouse_id;
      END IF;

      IF v_quantity < 0 THEN
        RAISE EXCEPTION 'Validation: quantity must be >= 0';
      END IF;

      IF v_reorder_level IS NOT NULL AND v_reorder_level < 0 THEN
        RAISE EXCEPTION 'Validation: reorder_level must be >= 0';
      END IF;

      INSERT INTO public.variant_inventory (
        workspace_id,
        variant_id,
        warehouse_id,
        created_by,
        quantity,
        reorder_level,
        alert_active,
        alert_method
      )
      VALUES (
        v_workspace_id,
        v_variant_id,
        v_warehouse_id,
        v_current_user_id,
        v_quantity,
        v_reorder_level,
        v_alert_active,
        v_alert_method
      )
      RETURNING id INTO v_inventory_id;

      IF v_quantity > 0 THEN
        INSERT INTO public.variant_inventory_movements (
          workspace_id,
          variant_inventory_id,
          variant_id,
          created_by,
          movement_type,
          quantity_moved,
          source_warehouse_id,
          destination_warehouse_id,
          reason
        )
        VALUES (
          v_workspace_id,
          v_inventory_id,
          v_variant_id,
          v_current_user_id,
          'entry',
          v_quantity,
          NULL,
          v_warehouse_id,
          'Initial inventory'
        );
      END IF;
    END LOOP;
  END LOOP;

  RETURN (
    SELECT jsonb_build_object(
      'product_id', p.id,
      'workspace_id', p.workspace_id,
      'name', p.name,
      'description', p.description,
      'category_id', p.category_id,
      'category_name', c.name,
      'created_at', p.created_at,
      'variants', COALESCE((
        SELECT jsonb_agg(
          jsonb_build_object(
            'variant_id', v.id,
            'sku', v.sku,
            'barcode', v.barcode,
            'unit_price', v.unit_price,
            'cost_price', v.cost_price,
            'options', COALESCE((
              SELECT jsonb_agg(
                jsonb_build_object(
                  'type', ot.name,
                  'value', ov.value
                )
                ORDER BY ot.name, ov.value
              )
              FROM public.product_variant_option_values vov
              JOIN public.product_option_types ot ON ot.id = vov.option_type_id
              JOIN public.product_option_values ov ON ov.id = vov.option_value_id
              WHERE vov.variant_id = v.id
            ), '[]'::jsonb),
            'inventory', COALESCE((
              SELECT jsonb_agg(
                jsonb_build_object(
                  'variant_inventory_id', vi.id,
                  'warehouse_id', vi.warehouse_id,
                  'warehouse_name', w.name,
                  'quantity', vi.quantity,
                  'reorder_level', vi.reorder_level,
                  'alert_active', vi.alert_active,
                  'alert_method', vi.alert_method
                )
                ORDER BY w.name
              )
              FROM public.variant_inventory vi
              JOIN public.warehouses w ON w.id = vi.warehouse_id
              WHERE vi.variant_id = v.id
            ), '[]'::jsonb)
          )
          ORDER BY v.created_at DESC, v.id DESC
        )
        FROM public.product_variants v
        WHERE v.product_id = p.id
      ), '[]'::jsonb)
    )
    FROM public.products p
    LEFT JOIN public.categories c ON c.id = p.category_id
    WHERE p.id = v_product_id
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.get_product_options_catalog()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_workspace_id UUID;
BEGIN
  v_workspace_id := public.get_my_primary_workspace_id();

  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Workspace could not be resolved';
  END IF;

  IF NOT app.is_active_workspace_member(v_workspace_id) THEN
    RAISE EXCEPTION 'Not authorized to read product options in workspace %', v_workspace_id;
  END IF;

  RETURN (
    SELECT jsonb_build_object(
      'option_types',
      COALESCE((
        SELECT jsonb_agg(
          jsonb_build_object(
            'id', pot.id,
            'name', pot.name,
            'input_kind', pot.input_kind,
            'values', COALESCE((
              SELECT jsonb_agg(
                jsonb_build_object(
                  'id', pov.id,
                  'value', pov.value,
                  'sort_order', pov.sort_order
                )
                ORDER BY pov.sort_order ASC, LOWER(pov.value) ASC
              )
              FROM public.product_option_values pov
              WHERE pov.workspace_id = v_workspace_id
                AND pov.option_type_id = pot.id
                AND pov.is_active = TRUE
            ), '[]'::jsonb)
          )
          ORDER BY LOWER(pot.name) ASC
        )
        FROM public.product_option_types pot
        WHERE pot.workspace_id = v_workspace_id
          AND pot.is_active = TRUE
      ), '[]'::jsonb)
    )
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.get_products_list(
  p_limit INT DEFAULT 20,
  p_offset INT DEFAULT 0,
  p_search TEXT DEFAULT NULL,
  p_category_id BIGINT DEFAULT NULL,
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
  v_limit INT := LEAST(GREATEST(COALESCE(p_limit, 20), 1), 100);
  v_offset INT := GREATEST(COALESCE(p_offset, 0), 0);
BEGIN
  v_workspace_id := public.get_my_primary_workspace_id();
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Workspace could not be resolved';
  END IF;

  IF NOT app.is_active_workspace_member(v_workspace_id) THEN
    RAISE EXCEPTION 'Not authorized to read products in workspace %', v_workspace_id;
  END IF;

  RETURN (
    WITH filtered_products AS (
      SELECT p.id
      FROM public.products p
      WHERE p.workspace_id = v_workspace_id
        AND p.is_active = TRUE
        AND (p_category_id IS NULL OR p.category_id = p_category_id)
        AND (
          v_search IS NULL
          OR p.name ILIKE '%' || v_search || '%'
          OR EXISTS (
            SELECT 1
            FROM public.product_variants v
            WHERE v.product_id = p.id
              AND v.sku ILIKE '%' || v_search || '%'
          )
        )
        AND (
          p_warehouse_id IS NULL
          OR EXISTS (
            SELECT 1
            FROM public.product_variants v
            JOIN public.variant_inventory vi ON vi.variant_id = v.id
            WHERE v.product_id = p.id
              AND vi.warehouse_id = p_warehouse_id
          )
        )
    ),
    paged_products AS (
      SELECT p.id, p.name, p.description, p.category_id, p.created_at
      FROM public.products p
      JOIN filtered_products fp ON fp.id = p.id
      ORDER BY p.created_at DESC, p.id DESC
      LIMIT v_limit
      OFFSET v_offset
    ),
    items AS (
      SELECT jsonb_build_object(
        'product_id', p.id,
        'name', p.name,
        'description', p.description,
        'category_id', p.category_id,
        'category_name', c.name,
        'total_stock', COALESCE((
          SELECT SUM(vi.quantity)
          FROM public.product_variants v
          JOIN public.variant_inventory vi ON vi.variant_id = v.id
          WHERE v.product_id = p.id
            AND (p_warehouse_id IS NULL OR vi.warehouse_id = p_warehouse_id)
        ), 0),
        'variants_count', (
          SELECT COUNT(*)
          FROM public.product_variants v
          WHERE v.product_id = p.id
            AND v.is_active = TRUE
        ),
        'variants', COALESCE((
          SELECT jsonb_agg(
            jsonb_build_object(
              'variant_id', v.id,
              'sku', v.sku,
              'barcode', v.barcode,
              'unit_price', v.unit_price,
              'cost_price', v.cost_price,
              'stock_total', COALESCE((
                SELECT SUM(vi.quantity)
                FROM public.variant_inventory vi
                WHERE vi.variant_id = v.id
                  AND (p_warehouse_id IS NULL OR vi.warehouse_id = p_warehouse_id)
              ), 0),
              'options', COALESCE((
                SELECT jsonb_agg(
                  jsonb_build_object(
                    'type', ot.name,
                    'value', ov.value
                  )
                  ORDER BY ot.name, ov.value
                )
                FROM public.product_variant_option_values vov
                JOIN public.product_option_types ot ON ot.id = vov.option_type_id
                JOIN public.product_option_values ov ON ov.id = vov.option_value_id
                WHERE vov.variant_id = v.id
              ), '[]'::jsonb)
            )
            ORDER BY v.created_at DESC, v.id DESC
          )
          FROM public.product_variants v
          WHERE v.product_id = p.id
            AND v.is_active = TRUE
        ), '[]'::jsonb)
      ) AS row_json
      FROM paged_products p
      LEFT JOIN public.categories c ON c.id = p.category_id
    )
    SELECT jsonb_build_object(
      'total', (SELECT COUNT(*) FROM filtered_products),
      'limit', v_limit,
      'offset', v_offset,
      'items', COALESCE((SELECT jsonb_agg(row_json) FROM items), '[]'::jsonb)
    )
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.create_product_with_variants(JSONB) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_product_options_catalog() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_products_list(INT, INT, TEXT, BIGINT, BIGINT) TO authenticated;
