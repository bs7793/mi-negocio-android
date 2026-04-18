CREATE OR REPLACE FUNCTION public.get_income_statement_monthly_summary(
  p_warehouse_id BIGINT DEFAULT NULL,
  p_start_at TIMESTAMPTZ DEFAULT date_trunc('month', NOW()),
  p_end_at TIMESTAMPTZ DEFAULT date_trunc('month', NOW()) + INTERVAL '1 month'
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
    RAISE EXCEPTION 'Not authorized to read income statement in workspace %', v_workspace_id;
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

GRANT EXECUTE ON FUNCTION public.get_income_statement_monthly_summary(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ) TO authenticated;
