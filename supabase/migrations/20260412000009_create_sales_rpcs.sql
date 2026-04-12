-- Sales RPCs: checkout transaction, daily summary, and sellable variants listing.

CREATE OR REPLACE FUNCTION public.create_sale_with_lines_and_payments(p_payload JSONB)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_current_user_id UUID := auth.uid();
  v_workspace_id UUID;
  v_warehouse_id BIGINT;
  v_customer_name TEXT;
  v_notes TEXT;
  v_discount_total NUMERIC(12, 2);
  v_tax_total NUMERIC(12, 2);
  v_subtotal NUMERIC(12, 2) := 0;
  v_total NUMERIC(12, 2) := 0;
  v_paid_total NUMERIC(12, 2) := 0;
  v_change_total NUMERIC(12, 2) := 0;
  v_sale_id BIGINT;
  v_line_count INT := 0;
  v_line JSONB;
  v_payment JSONB;
  v_variant_id BIGINT;
  v_quantity NUMERIC(14, 3);
  v_applied_unit_price NUMERIC(12, 2);
  v_applied_cost_price NUMERIC(12, 2);
  v_variant_workspace_id UUID;
  v_variant_unit_price NUMERIC(12, 2);
  v_variant_cost_price NUMERIC(12, 2);
  v_variant_active BOOLEAN;
  v_stock_available NUMERIC(14, 3);
  v_line_subtotal NUMERIC(12, 2);
  v_line_total NUMERIC(12, 2);
  v_variant_inventory_id BIGINT;
  v_payment_method TEXT;
  v_payment_amount NUMERIC(12, 2);
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
    RAISE EXCEPTION 'Not authorized to create sales in workspace %', v_workspace_id;
  END IF;

  v_warehouse_id := NULLIF(TRIM(p_payload->>'warehouse_id'), '')::BIGINT;
  IF v_warehouse_id IS NULL THEN
    RAISE EXCEPTION 'Validation: warehouse_id is required';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM public.warehouses w
    WHERE w.id = v_warehouse_id
      AND w.workspace_id = v_workspace_id
  ) THEN
    RAISE EXCEPTION 'Validation: warehouse_id % does not belong to workspace', v_warehouse_id;
  END IF;

  IF jsonb_typeof(p_payload->'lines') <> 'array' OR jsonb_array_length(p_payload->'lines') = 0 THEN
    RAISE EXCEPTION 'Validation: at least one line is required';
  END IF;

  IF jsonb_typeof(p_payload->'payments') <> 'array' OR jsonb_array_length(p_payload->'payments') = 0 THEN
    RAISE EXCEPTION 'Validation: at least one payment is required';
  END IF;

  v_customer_name := NULLIF(TRIM(p_payload->>'customer_name'), '');
  v_customer_name := COALESCE(v_customer_name, 'Public');
  v_notes := NULLIF(TRIM(p_payload->>'notes'), '');
  v_discount_total := COALESCE(NULLIF(TRIM(p_payload->>'discount_total'), '')::NUMERIC, 0);
  v_tax_total := COALESCE(NULLIF(TRIM(p_payload->>'tax_total'), '')::NUMERIC, 0);

  IF v_discount_total < 0 THEN
    RAISE EXCEPTION 'Validation: discount_total must be >= 0';
  END IF;
  IF v_tax_total < 0 THEN
    RAISE EXCEPTION 'Validation: tax_total must be >= 0';
  END IF;

  INSERT INTO public.sales (
    workspace_id,
    created_by,
    warehouse_id,
    customer_name,
    status,
    notes,
    subtotal,
    discount_total,
    tax_total,
    total,
    paid_total,
    change_total,
    metadata,
    sold_at
  )
  VALUES (
    v_workspace_id,
    v_current_user_id,
    v_warehouse_id,
    v_customer_name,
    'completed',
    v_notes,
    0,
    v_discount_total,
    v_tax_total,
    0,
    0,
    0,
    COALESCE(p_payload->'metadata', '{}'::JSONB),
    NOW()
  )
  RETURNING id INTO v_sale_id;

  FOR v_line IN
    SELECT * FROM jsonb_array_elements(p_payload->'lines')
  LOOP
    v_variant_id := NULLIF(TRIM(v_line->>'variant_id'), '')::BIGINT;
    v_quantity := COALESCE(NULLIF(TRIM(v_line->>'quantity'), '')::NUMERIC, 0);

    IF v_variant_id IS NULL THEN
      RAISE EXCEPTION 'Validation: line variant_id is required';
    END IF;
    IF v_quantity <= 0 THEN
      RAISE EXCEPTION 'Validation: line quantity must be > 0';
    END IF;

    SELECT
      pv.workspace_id,
      pv.unit_price,
      pv.cost_price,
      pv.is_active
    INTO
      v_variant_workspace_id,
      v_variant_unit_price,
      v_variant_cost_price,
      v_variant_active
    FROM public.product_variants pv
    WHERE pv.id = v_variant_id;

    IF v_variant_workspace_id IS NULL THEN
      RAISE EXCEPTION 'Validation: variant_id % does not exist', v_variant_id;
    END IF;
    IF v_variant_workspace_id <> v_workspace_id THEN
      RAISE EXCEPTION 'Validation: variant_id % does not belong to workspace', v_variant_id;
    END IF;
    IF v_variant_active IS NOT TRUE THEN
      RAISE EXCEPTION 'Validation: variant_id % is not active', v_variant_id;
    END IF;

    v_applied_unit_price := COALESCE(
      NULLIF(TRIM(v_line->>'applied_unit_price'), '')::NUMERIC,
      v_variant_unit_price
    );
    v_applied_cost_price := COALESCE(
      NULLIF(TRIM(v_line->>'applied_cost_price'), '')::NUMERIC,
      v_variant_cost_price
    );

    IF v_applied_unit_price < 0 THEN
      RAISE EXCEPTION 'Validation: applied_unit_price must be >= 0';
    END IF;
    IF v_applied_cost_price IS NOT NULL AND v_applied_cost_price < 0 THEN
      RAISE EXCEPTION 'Validation: applied_cost_price must be >= 0';
    END IF;

    SELECT vi.id, vi.quantity
    INTO v_variant_inventory_id, v_stock_available
    FROM public.variant_inventory vi
    WHERE vi.workspace_id = v_workspace_id
      AND vi.variant_id = v_variant_id
      AND vi.warehouse_id = v_warehouse_id
    LIMIT 1;

    IF v_variant_inventory_id IS NULL THEN
      RAISE EXCEPTION 'Validation: inventory row missing for variant_id % in warehouse_id %', v_variant_id, v_warehouse_id;
    END IF;
    IF v_stock_available < v_quantity THEN
      RAISE EXCEPTION 'Validation: insufficient stock for variant_id %, available %, requested %',
        v_variant_id, v_stock_available, v_quantity;
    END IF;

    v_line_subtotal := ROUND((v_quantity * v_applied_unit_price)::NUMERIC, 2);
    v_line_total := v_line_subtotal;

    INSERT INTO public.sale_lines (
      workspace_id,
      sale_id,
      variant_id,
      created_by,
      quantity,
      applied_unit_price,
      applied_cost_price,
      line_subtotal,
      line_total,
      notes
    )
    VALUES (
      v_workspace_id,
      v_sale_id,
      v_variant_id,
      v_current_user_id,
      v_quantity,
      v_applied_unit_price,
      v_applied_cost_price,
      v_line_subtotal,
      v_line_total,
      NULLIF(TRIM(v_line->>'notes'), '')
    );

    UPDATE public.variant_inventory
    SET quantity = quantity - v_quantity
    WHERE id = v_variant_inventory_id;

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
      v_variant_inventory_id,
      v_variant_id,
      v_current_user_id,
      'exit',
      v_quantity,
      v_warehouse_id,
      NULL,
      CONCAT('Sale #', v_sale_id)
    );

    v_subtotal := v_subtotal + v_line_subtotal;
    v_line_count := v_line_count + 1;
  END LOOP;

  v_subtotal := ROUND(v_subtotal, 2);
  v_total := ROUND(v_subtotal - v_discount_total + v_tax_total, 2);
  IF v_total < 0 THEN
    RAISE EXCEPTION 'Validation: total cannot be negative';
  END IF;

  FOR v_payment IN
    SELECT * FROM jsonb_array_elements(p_payload->'payments')
  LOOP
    v_payment_method := COALESCE(NULLIF(TRIM(v_payment->>'method'), ''), 'other');
    v_payment_amount := COALESCE(NULLIF(TRIM(v_payment->>'amount'), '')::NUMERIC, 0);

    IF v_payment_method NOT IN ('cash', 'card', 'transfer', 'other') THEN
      RAISE EXCEPTION 'Validation: unsupported payment method %', v_payment_method;
    END IF;
    IF v_payment_amount <= 0 THEN
      RAISE EXCEPTION 'Validation: payment amount must be > 0';
    END IF;

    INSERT INTO public.sale_payments (
      workspace_id,
      sale_id,
      created_by,
      payment_method,
      amount,
      reference_text
    )
    VALUES (
      v_workspace_id,
      v_sale_id,
      v_current_user_id,
      v_payment_method,
      v_payment_amount,
      NULLIF(TRIM(v_payment->>'reference'), '')
    );

    v_paid_total := v_paid_total + v_payment_amount;
  END LOOP;

  v_paid_total := ROUND(v_paid_total, 2);
  IF ABS(v_paid_total - v_total) > 0.01 THEN
    RAISE EXCEPTION 'Validation: payment total % must match sale total %', v_paid_total, v_total;
  END IF;

  v_change_total := GREATEST(v_paid_total - v_total, 0);

  UPDATE public.sales s
  SET
    subtotal = v_subtotal,
    total = v_total,
    paid_total = v_paid_total,
    change_total = v_change_total
  WHERE s.id = v_sale_id;

  RETURN jsonb_build_object(
    'sale_id', v_sale_id,
    'workspace_id', v_workspace_id,
    'subtotal', v_subtotal,
    'discount_total', v_discount_total,
    'tax_total', v_tax_total,
    'total', v_total,
    'paid_total', v_paid_total,
    'change_total', v_change_total,
    'line_count', v_line_count
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.get_sales_daily_summary(
  p_warehouse_id BIGINT DEFAULT NULL,
  p_start_at TIMESTAMPTZ DEFAULT date_trunc('day', NOW()),
  p_end_at TIMESTAMPTZ DEFAULT date_trunc('day', NOW()) + INTERVAL '1 day'
)
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
    RAISE EXCEPTION 'Not authorized to read sales summary in workspace %', v_workspace_id;
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
    WITH filtered_sales AS (
      SELECT s.id, s.total
      FROM public.sales s
      WHERE s.workspace_id = v_workspace_id
        AND s.status = 'completed'
        AND (p_warehouse_id IS NULL OR s.warehouse_id = p_warehouse_id)
        AND s.sold_at >= p_start_at
        AND s.sold_at < p_end_at
    )
    SELECT jsonb_build_object(
      'sales_count', (SELECT COUNT(*) FROM filtered_sales),
      'units_sold', COALESCE((
        SELECT SUM(sl.quantity)
        FROM public.sale_lines sl
        JOIN filtered_sales fs ON fs.id = sl.sale_id
      ), 0),
      'gross_total', COALESCE((SELECT SUM(fs.total) FROM filtered_sales fs), 0),
      'cash_total', COALESCE((
        SELECT SUM(sp.amount)
        FROM public.sale_payments sp
        JOIN filtered_sales fs ON fs.id = sp.sale_id
        WHERE sp.payment_method = 'cash'
      ), 0),
      'card_total', COALESCE((
        SELECT SUM(sp.amount)
        FROM public.sale_payments sp
        JOIN filtered_sales fs ON fs.id = sp.sale_id
        WHERE sp.payment_method = 'card'
      ), 0),
      'transfer_total', COALESCE((
        SELECT SUM(sp.amount)
        FROM public.sale_payments sp
        JOIN filtered_sales fs ON fs.id = sp.sale_id
        WHERE sp.payment_method = 'transfer'
      ), 0),
      'other_total', COALESCE((
        SELECT SUM(sp.amount)
        FROM public.sale_payments sp
        JOIN filtered_sales fs ON fs.id = sp.sale_id
        WHERE sp.payment_method = 'other'
      ), 0)
    )
  );
END;
$$;

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

GRANT EXECUTE ON FUNCTION public.create_sale_with_lines_and_payments(JSONB) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_sales_daily_summary(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_sellable_variants(TEXT, INT, BIGINT) TO authenticated;
