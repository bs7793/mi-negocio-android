-- Consolidate read RPCs into single explicit-workspace signatures.
-- Removes legacy overloads that relied on implicit workspace resolution.

DROP FUNCTION IF EXISTS public.get_sellable_variants(TEXT, INT, BIGINT);
DROP FUNCTION IF EXISTS public.get_products_list(INT, INT, TEXT, BIGINT, BIGINT);
DROP FUNCTION IF EXISTS public.get_product_options_catalog();
DROP FUNCTION IF EXISTS public.get_sales_daily_summary(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ);
DROP FUNCTION IF EXISTS public.get_dashboard_sales_feed(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, INT);
DROP FUNCTION IF EXISTS public.get_income_statement_monthly_summary(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ);
DROP FUNCTION IF EXISTS public.get_dashboard_sale_detail(BIGINT);
DROP FUNCTION IF EXISTS public.get_sellable_variants(TEXT, INT, BIGINT, UUID);
DROP FUNCTION IF EXISTS public.get_products_list(INT, INT, TEXT, BIGINT, BIGINT, UUID);
DROP FUNCTION IF EXISTS public.get_sales_daily_summary(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, UUID);
DROP FUNCTION IF EXISTS public.get_dashboard_sales_feed(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, INT, UUID);
DROP FUNCTION IF EXISTS public.get_income_statement_monthly_summary(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, UUID);

CREATE OR REPLACE FUNCTION public.get_sellable_variants(
  p_workspace_id UUID,
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
  v_search TEXT := NULLIF(TRIM(p_search), '');
  v_limit INT := LEAST(GREATEST(COALESCE(p_limit, 30), 1), 100);
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_active_workspace_member(p_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;

  IF p_warehouse_id IS NOT NULL AND NOT EXISTS (
    SELECT 1
    FROM public.warehouses w
    WHERE w.id = p_warehouse_id
      AND w.workspace_id = p_workspace_id
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
      WHERE pv.workspace_id = p_workspace_id
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

CREATE OR REPLACE FUNCTION public.get_products_list(
  p_workspace_id UUID,
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
  v_search TEXT := NULLIF(TRIM(p_search), '');
  v_limit INT := LEAST(GREATEST(COALESCE(p_limit, 20), 1), 100);
  v_offset INT := GREATEST(COALESCE(p_offset, 0), 0);
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_active_workspace_member(p_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;

  RETURN (
    WITH filtered_products AS (
      SELECT p.id
      FROM public.products p
      WHERE p.workspace_id = p_workspace_id
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
      SELECT p.id, p.name, p.description, p.image_url, p.category_id, p.created_at
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
        'image_url', p.image_url,
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

CREATE OR REPLACE FUNCTION public.get_product_options_catalog(
  p_workspace_id UUID
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
  IF NOT app.is_active_workspace_member(p_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
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
              WHERE pov.workspace_id = p_workspace_id
                AND pov.option_type_id = pot.id
                AND pov.is_active = TRUE
            ), '[]'::jsonb)
          )
          ORDER BY LOWER(pot.name) ASC
        )
        FROM public.product_option_types pot
        WHERE pot.workspace_id = p_workspace_id
          AND pot.is_active = TRUE
      ), '[]'::jsonb)
    )
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.get_sales_daily_summary(
  p_workspace_id UUID,
  p_warehouse_id BIGINT DEFAULT NULL,
  p_start_at TIMESTAMPTZ DEFAULT NULL,
  p_end_at TIMESTAMPTZ DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_start_at TIMESTAMPTZ := COALESCE(p_start_at, date_trunc('day', NOW()));
  v_end_at TIMESTAMPTZ := COALESCE(p_end_at, date_trunc('day', NOW()) + INTERVAL '1 day');
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_active_workspace_member(p_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;

  IF p_warehouse_id IS NOT NULL AND NOT EXISTS (
    SELECT 1
    FROM public.warehouses w
    WHERE w.id = p_warehouse_id
      AND w.workspace_id = p_workspace_id
  ) THEN
    RAISE EXCEPTION 'Validation: warehouse_id % does not belong to workspace', p_warehouse_id;
  END IF;

  RETURN (
    WITH filtered_sales AS (
      SELECT s.id, s.total
      FROM public.sales s
      WHERE s.workspace_id = p_workspace_id
        AND s.status = 'completed'
        AND (p_warehouse_id IS NULL OR s.warehouse_id = p_warehouse_id)
        AND s.sold_at >= v_start_at
        AND s.sold_at < v_end_at
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

CREATE OR REPLACE FUNCTION public.get_dashboard_sales_feed(
  p_workspace_id UUID,
  p_warehouse_id BIGINT DEFAULT NULL,
  p_start_at TIMESTAMPTZ DEFAULT NULL,
  p_end_at TIMESTAMPTZ DEFAULT NULL,
  p_limit INT DEFAULT 50
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_start_at TIMESTAMPTZ := COALESCE(p_start_at, date_trunc('month', NOW()));
  v_end_at TIMESTAMPTZ := COALESCE(p_end_at, date_trunc('month', NOW()) + INTERVAL '1 month');
  v_limit INT;
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_active_workspace_member(p_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;

  IF p_warehouse_id IS NOT NULL AND NOT EXISTS (
    SELECT 1
    FROM public.warehouses w
    WHERE w.id = p_warehouse_id
      AND w.workspace_id = p_workspace_id
  ) THEN
    RAISE EXCEPTION 'Validation: warehouse_id % does not belong to workspace', p_warehouse_id;
  END IF;

  v_limit := LEAST(GREATEST(COALESCE(p_limit, 50), 1), 200);

  RETURN (
    WITH base AS (
      SELECT s.id, s.sold_at, s.total, s.customer_name
      FROM public.sales s
      WHERE s.workspace_id = p_workspace_id
        AND s.status = 'completed'
        AND (p_warehouse_id IS NULL OR s.warehouse_id = p_warehouse_id)
        AND s.sold_at >= v_start_at
        AND s.sold_at < v_end_at
      ORDER BY s.sold_at DESC
      LIMIT v_limit
    ),
    dominant_payment AS (
      SELECT DISTINCT ON (sp.sale_id)
        sp.sale_id,
        sp.payment_method
      FROM public.sale_payments sp
      INNER JOIN base b ON b.id = sp.sale_id
      ORDER BY sp.sale_id, sp.amount DESC, sp.id ASC
    )
    SELECT COALESCE(
      jsonb_agg(
        jsonb_build_object(
          'sale_id', b.id,
          'sold_at', b.sold_at,
          'total', ROUND(b.total::numeric, 2),
          'customer_name', b.customer_name,
          'payment_method', dp.payment_method
        )
        ORDER BY b.sold_at DESC
      ),
      '[]'::jsonb
    )
    FROM base b
    LEFT JOIN dominant_payment dp ON dp.sale_id = b.id
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.get_income_statement_monthly_summary(
  p_workspace_id UUID,
  p_warehouse_id BIGINT DEFAULT NULL,
  p_start_at TIMESTAMPTZ DEFAULT NULL,
  p_end_at TIMESTAMPTZ DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_start_at TIMESTAMPTZ := COALESCE(p_start_at, date_trunc('month', NOW()));
  v_end_at TIMESTAMPTZ := COALESCE(p_end_at, date_trunc('month', NOW()) + INTERVAL '1 month');
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_active_workspace_member(p_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;

  IF p_warehouse_id IS NOT NULL AND NOT EXISTS (
    SELECT 1
    FROM public.warehouses w
    WHERE w.id = p_warehouse_id
      AND w.workspace_id = p_workspace_id
  ) THEN
    RAISE EXCEPTION 'Validation: warehouse_id % does not belong to workspace', p_warehouse_id;
  END IF;

  RETURN (
    WITH filtered_sales AS (
      SELECT s.id, s.total
      FROM public.sales s
      WHERE s.workspace_id = p_workspace_id
        AND s.status = 'completed'
        AND (p_warehouse_id IS NULL OR s.warehouse_id = p_warehouse_id)
        AND s.sold_at >= v_start_at
        AND s.sold_at < v_end_at
    ),
    income_cost AS (
      SELECT
        COALESCE((SELECT SUM(fs.total) FROM filtered_sales fs), 0) AS income_total,
        COALESCE((
          SELECT SUM(sl.quantity * COALESCE(sl.applied_cost_price, 0))
          FROM public.sale_lines sl
          JOIN filtered_sales fs ON fs.id = sl.sale_id
        ), 0) AS cost_total
    )
    SELECT jsonb_build_object(
      'income_total', ROUND(ic.income_total, 2),
      'cost_total', ROUND(ic.cost_total, 2),
      'profit_total', ROUND(ic.income_total - ic.cost_total, 2)
    )
    FROM income_cost ic
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.get_dashboard_sale_detail(
  p_sale_id BIGINT,
  p_workspace_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_sale public.sales%ROWTYPE;
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_active_workspace_member(p_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;

  SELECT s.*
  INTO v_sale
  FROM public.sales s
  WHERE s.id = p_sale_id
    AND s.workspace_id = p_workspace_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Sale not found or not accessible';
  END IF;

  RETURN jsonb_build_object(
    'sale_id', v_sale.id,
    'sold_at', v_sale.sold_at,
    'customer_name', v_sale.customer_name,
    'notes', v_sale.notes,
    'subtotal', ROUND(v_sale.subtotal::numeric, 2),
    'discount_total', ROUND(v_sale.discount_total::numeric, 2),
    'tax_total', ROUND(v_sale.tax_total::numeric, 2),
    'total', ROUND(v_sale.total::numeric, 2),
    'paid_total', ROUND(v_sale.paid_total::numeric, 2),
    'change_total', ROUND(v_sale.change_total::numeric, 2),
    'status', v_sale.status,
    'lines', COALESCE((
      SELECT jsonb_agg(
        jsonb_build_object(
          'variant_id', sl.variant_id,
          'product_name', p.name,
          'sku', pv.sku,
          'quantity', ROUND(sl.quantity::numeric, 3),
          'applied_unit_price', ROUND(sl.applied_unit_price::numeric, 2),
          'line_total', ROUND(sl.line_total::numeric, 2),
          'notes', sl.notes,
          'image_url', p.image_url
        )
        ORDER BY sl.id
      )
      FROM public.sale_lines sl
      JOIN public.product_variants pv ON pv.id = sl.variant_id
      JOIN public.products p ON p.id = pv.product_id
      WHERE sl.sale_id = p_sale_id
        AND sl.workspace_id = p_workspace_id
    ), '[]'::jsonb),
    'payments', COALESCE((
      SELECT jsonb_agg(
        jsonb_build_object(
          'payment_method', sp.payment_method,
          'amount', ROUND(sp.amount::numeric, 2),
          'reference_text', sp.reference_text
        )
        ORDER BY sp.id
      )
      FROM public.sale_payments sp
      WHERE sp.sale_id = p_sale_id
        AND sp.workspace_id = p_workspace_id
    ), '[]'::jsonb)
  );
END;
$$;

REVOKE ALL ON FUNCTION public.get_sellable_variants(UUID, TEXT, INT, BIGINT) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_products_list(UUID, INT, INT, TEXT, BIGINT, BIGINT) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_product_options_catalog(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_sales_daily_summary(UUID, BIGINT, TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_dashboard_sales_feed(UUID, BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, INT) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_income_statement_monthly_summary(UUID, BIGINT, TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_dashboard_sale_detail(BIGINT, UUID) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.get_sellable_variants(UUID, TEXT, INT, BIGINT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_products_list(UUID, INT, INT, TEXT, BIGINT, BIGINT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_product_options_catalog(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_sales_daily_summary(UUID, BIGINT, TIMESTAMPTZ, TIMESTAMPTZ) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_dashboard_sales_feed(UUID, BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, INT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_income_statement_monthly_summary(UUID, BIGINT, TIMESTAMPTZ, TIMESTAMPTZ) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_dashboard_sale_detail(BIGINT, UUID) TO authenticated;
