DROP FUNCTION IF EXISTS public.get_sellable_variants(UUID, TEXT, INT, BIGINT);
DROP FUNCTION IF EXISTS public.get_products_list(UUID, INT, INT, TEXT, BIGINT, BIGINT);
DROP FUNCTION IF EXISTS public.get_product_options_catalog(UUID);
DROP FUNCTION IF EXISTS public.get_sales_daily_summary(UUID, BIGINT, TIMESTAMPTZ, TIMESTAMPTZ);
DROP FUNCTION IF EXISTS public.get_dashboard_sales_feed(UUID, BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, INT);
DROP FUNCTION IF EXISTS public.get_income_statement_monthly_summary(UUID, BIGINT, TIMESTAMPTZ, TIMESTAMPTZ);
DROP FUNCTION IF EXISTS public.get_dashboard_sale_detail(BIGINT, UUID);

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
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_sellable_variants(p_search, p_limit, p_warehouse_id);
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
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_sales_daily_summary(p_warehouse_id, p_start_at, p_end_at);
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
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  PERFORM set_config('app.workspace_id', p_workspace_id::TEXT, true);
  RETURN public.get_dashboard_sales_feed(p_warehouse_id, p_start_at, p_end_at, p_limit);
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

GRANT EXECUTE ON FUNCTION public.get_sellable_variants(UUID, TEXT, INT, BIGINT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_products_list(UUID, INT, INT, TEXT, BIGINT, BIGINT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_product_options_catalog(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_sales_daily_summary(UUID, BIGINT, TIMESTAMPTZ, TIMESTAMPTZ) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_dashboard_sales_feed(UUID, BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, INT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_income_statement_monthly_summary(UUID, BIGINT, TIMESTAMPTZ, TIMESTAMPTZ) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_dashboard_sale_detail(BIGINT, UUID) TO authenticated;
