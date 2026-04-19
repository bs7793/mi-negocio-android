CREATE OR REPLACE FUNCTION public.get_sellable_variants(
  p_search TEXT DEFAULT NULL,
  p_limit INT DEFAULT 30,
  p_warehouse_id BIGINT DEFAULT NULL,
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
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_products_list(p_limit, p_offset, p_search, p_category_id, p_warehouse_id);
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
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_product_options_catalog();
END;
$$;

CREATE OR REPLACE FUNCTION public.get_sales_daily_summary(
  p_warehouse_id BIGINT DEFAULT NULL,
  p_start_at TIMESTAMPTZ DEFAULT NULL,
  p_end_at TIMESTAMPTZ DEFAULT NULL,
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
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_sales_daily_summary(p_warehouse_id, p_start_at, p_end_at);
END;
$$;

CREATE OR REPLACE FUNCTION public.get_dashboard_sales_feed(
  p_warehouse_id BIGINT DEFAULT NULL,
  p_start_at TIMESTAMPTZ DEFAULT NULL,
  p_end_at TIMESTAMPTZ DEFAULT NULL,
  p_limit INT DEFAULT 50,
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
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_dashboard_sales_feed(p_warehouse_id, p_start_at, p_end_at, p_limit);
END;
$$;

CREATE OR REPLACE FUNCTION public.get_income_statement_monthly_summary(
  p_warehouse_id BIGINT DEFAULT NULL,
  p_start_at TIMESTAMPTZ DEFAULT NULL,
  p_end_at TIMESTAMPTZ DEFAULT NULL,
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
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_income_statement_monthly_summary(p_warehouse_id, p_start_at, p_end_at);
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
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_dashboard_sale_detail(p_sale_id);
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_sellable_variants(TEXT, INT, BIGINT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_products_list(INT, INT, TEXT, BIGINT, BIGINT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_product_options_catalog(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_sales_daily_summary(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_dashboard_sales_feed(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, INT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_income_statement_monthly_summary(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_dashboard_sale_detail(BIGINT, UUID) TO authenticated;
