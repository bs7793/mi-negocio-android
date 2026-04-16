-- Contract checks for Sales RPCs and critical business invariants.

CREATE OR REPLACE FUNCTION app.assert_contract(condition BOOLEAN, message TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
  IF NOT condition THEN
    RAISE EXCEPTION 'Sales contract failed: %', message;
  END IF;
END;
$$;

DO $$
DECLARE
  v_function_body TEXT;
BEGIN
  -- Workspace isolation checks on sales tables.
  PERFORM app.assert_contract(
    EXISTS (
      SELECT 1 FROM pg_trigger t
      JOIN pg_class c ON c.oid = t.tgrelid
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public'
        AND c.relname = 'sales'
        AND t.tgname = 'trg_enforce_sales_workspace_match'
    ),
    'Missing trigger trg_enforce_sales_workspace_match on public.sales'
  );

  PERFORM app.assert_contract(
    EXISTS (
      SELECT 1 FROM pg_trigger t
      JOIN pg_class c ON c.oid = t.tgrelid
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public'
        AND c.relname = 'sale_lines'
        AND t.tgname = 'trg_enforce_sale_lines_workspace_match'
    ),
    'Missing trigger trg_enforce_sale_lines_workspace_match on public.sale_lines'
  );

  PERFORM app.assert_contract(
    EXISTS (
      SELECT 1 FROM pg_trigger t
      JOIN pg_class c ON c.oid = t.tgrelid
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public'
        AND c.relname = 'sale_payments'
        AND t.tgname = 'trg_enforce_sale_payments_workspace_match'
    ),
    'Missing trigger trg_enforce_sale_payments_workspace_match on public.sale_payments'
  );

  -- Stock cannot go negative on sale (guard exists in sales checkout RPC).
  SELECT pg_get_functiondef(p.oid)
  INTO v_function_body
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public'
    AND p.proname = 'create_sale_with_lines_and_payments'
  LIMIT 1;

  PERFORM app.assert_contract(
    v_function_body IS NOT NULL,
    'Missing function public.create_sale_with_lines_and_payments'
  );

  PERFORM app.assert_contract(
    POSITION('insufficient stock for variant_id' IN v_function_body) > 0,
    'create_sale_with_lines_and_payments must enforce stock availability before updating inventory'
  );

  -- Payment total must match sale total (strict checkout balancing).
  PERFORM app.assert_contract(
    POSITION('payment total % must match sale total %' IN v_function_body) > 0,
    'create_sale_with_lines_and_payments must validate payment total matches sale total'
  );

  -- Mandatory output keys consumed by UI contracts.
  PERFORM app.assert_contract(
    POSITION('''sale_id''' IN v_function_body) > 0,
    'create_sale_with_lines_and_payments output must include sale_id'
  );
  PERFORM app.assert_contract(
    POSITION('''total''' IN v_function_body) > 0,
    'create_sale_with_lines_and_payments output must include total'
  );
  PERFORM app.assert_contract(
    POSITION('''paid_total''' IN v_function_body) > 0,
    'create_sale_with_lines_and_payments output must include paid_total'
  );
  PERFORM app.assert_contract(
    POSITION('''change_total''' IN v_function_body) > 0,
    'create_sale_with_lines_and_payments output must include change_total'
  );

  SELECT pg_get_functiondef(p.oid)
  INTO v_function_body
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public'
    AND p.proname = 'get_sales_daily_summary'
  LIMIT 1;

  PERFORM app.assert_contract(
    v_function_body IS NOT NULL,
    'Missing function public.get_sales_daily_summary'
  );

  PERFORM app.assert_contract(
    POSITION('''sales_count''' IN v_function_body) > 0
    AND POSITION('''units_sold''' IN v_function_body) > 0
    AND POSITION('''gross_total''' IN v_function_body) > 0
    AND POSITION('''cash_total''' IN v_function_body) > 0
    AND POSITION('''card_total''' IN v_function_body) > 0
    AND POSITION('''transfer_total''' IN v_function_body) > 0
    AND POSITION('''other_total''' IN v_function_body) > 0,
    'get_sales_daily_summary output must include mandatory summary keys'
  );

  SELECT pg_get_functiondef(p.oid)
  INTO v_function_body
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public'
    AND p.proname = 'get_sellable_variants'
  LIMIT 1;

  PERFORM app.assert_contract(
    v_function_body IS NOT NULL,
    'Missing function public.get_sellable_variants'
  );

  PERFORM app.assert_contract(
    POSITION('''items''' IN v_function_body) > 0
    AND POSITION('''variant_id''' IN v_function_body) > 0
    AND POSITION('''product_id''' IN v_function_body) > 0
    AND POSITION('''product_name''' IN v_function_body) > 0
    AND POSITION('''image_url''' IN v_function_body) > 0
    AND POSITION('''sku''' IN v_function_body) > 0
    AND POSITION('''unit_price''' IN v_function_body) > 0
    AND POSITION('''stock_total''' IN v_function_body) > 0
    AND POSITION('''options''' IN v_function_body) > 0,
    'get_sellable_variants output must include mandatory item keys'
  );
END;
$$;
