-- Chronological sales feed for the dashboard (completed sales in a time range).

CREATE OR REPLACE FUNCTION public.get_dashboard_sales_feed(
  p_warehouse_id BIGINT DEFAULT NULL,
  p_start_at TIMESTAMPTZ DEFAULT date_trunc('month', NOW()),
  p_end_at TIMESTAMPTZ DEFAULT date_trunc('month', NOW()) + INTERVAL '1 month',
  p_limit INT DEFAULT 50
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_workspace_id UUID;
  v_limit INT;
BEGIN
  v_workspace_id := public.get_my_primary_workspace_id();
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Workspace could not be resolved';
  END IF;

  IF NOT app.is_active_workspace_member(v_workspace_id) THEN
    RAISE EXCEPTION 'Not authorized to read sales feed in workspace %', v_workspace_id;
  END IF;

  IF p_warehouse_id IS NOT NULL AND NOT EXISTS (
    SELECT 1
    FROM public.warehouses w
    WHERE w.id = p_warehouse_id
      AND w.workspace_id = v_workspace_id
  ) THEN
    RAISE EXCEPTION 'Validation: warehouse_id % does not belong to workspace', p_warehouse_id;
  END IF;

  v_limit := LEAST(GREATEST(COALESCE(p_limit, 50), 1), 200);

  RETURN (
    WITH base AS (
      SELECT s.id, s.sold_at, s.total, s.customer_name
      FROM public.sales s
      WHERE s.workspace_id = v_workspace_id
        AND s.status = 'completed'
        AND (p_warehouse_id IS NULL OR s.warehouse_id = p_warehouse_id)
        AND s.sold_at >= p_start_at
        AND s.sold_at < p_end_at
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

GRANT EXECUTE ON FUNCTION public.get_dashboard_sales_feed(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, INT) TO authenticated;
