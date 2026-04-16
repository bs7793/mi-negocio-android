DO $$
DECLARE
  v_function_body TEXT;
BEGIN
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
    'get_sellable_variants output must include mandatory item keys (including image_url)'
  );
END;
$$;
