-- Add product image URL to each line in dashboard sale detail response.

CREATE OR REPLACE FUNCTION public.get_dashboard_sale_detail(p_sale_id BIGINT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_workspace_id UUID;
  v_sale public.sales%ROWTYPE;
BEGIN
  v_workspace_id := public.get_my_primary_workspace_id();
  IF v_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Workspace could not be resolved';
  END IF;

  IF NOT app.is_active_workspace_member(v_workspace_id) THEN
    RAISE EXCEPTION 'Not authorized to read sale detail in workspace %', v_workspace_id;
  END IF;

  SELECT s.*
  INTO v_sale
  FROM public.sales s
  WHERE s.id = p_sale_id
    AND s.workspace_id = v_workspace_id;

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
        AND sl.workspace_id = v_workspace_id
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
        AND sp.workspace_id = v_workspace_id
    ), '[]'::jsonb)
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_dashboard_sale_detail(BIGINT) TO authenticated;
