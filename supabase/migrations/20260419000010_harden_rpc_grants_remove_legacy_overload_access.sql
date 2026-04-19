-- Harden RPC grants by revoking access to legacy overloads and
-- explicitly allowing only app-intended UUID-aware signatures.
DO $$
DECLARE
    target_function RECORD;
BEGIN
    FOR target_function IN
        SELECT p.oid::regprocedure AS function_signature
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
          AND p.proname IN (
              'list_workspace_members',
              'list_workspace_invite_codes',
              'revoke_workspace_invite_code',
              'create_workspace_invite_code',
              'update_workspace_member_role_status',
              'create_sale_with_lines_and_payments',
              'create_product_with_variants',
              'update_product_basic',
              'get_sellable_variants',
              'get_products_list',
              'get_product_options_catalog',
              'get_sales_daily_summary',
              'get_dashboard_sales_feed',
              'get_income_statement_monthly_summary',
              'get_dashboard_sale_detail',
              'list_my_workspaces'
          )
    LOOP
        EXECUTE format(
            'REVOKE ALL ON FUNCTION %s FROM PUBLIC, anon, authenticated',
            target_function.function_signature
        );
    END LOOP;
END
$$;

GRANT EXECUTE ON FUNCTION public.list_my_workspaces(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.list_workspace_members(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_workspace_invite_code(TEXT, INT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.list_workspace_invite_codes(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.revoke_workspace_invite_code(TEXT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_workspace_member_role_status(UUID, TEXT, TEXT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_sale_with_lines_and_payments(JSONB, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_product_with_variants(JSONB, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_product_basic(JSONB, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_sellable_variants(TEXT, INT, BIGINT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_products_list(INT, INT, TEXT, BIGINT, BIGINT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_product_options_catalog(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_sales_daily_summary(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_dashboard_sales_feed(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, INT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_income_statement_monthly_summary(BIGINT, TIMESTAMPTZ, TIMESTAMPTZ, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_dashboard_sale_detail(BIGINT, UUID) TO authenticated;

-- Required auth/session RPCs.
GRANT EXECUTE ON FUNCTION public.accept_workspace_invite(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.set_my_active_workspace_id(UUID) TO authenticated;
