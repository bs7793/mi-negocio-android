-- Finalize mutation RPC public signatures without rewriting previous migrations.
-- Keep public mutation entrypoints workspace-scoped and hide payload-only implementations as internal core functions.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname = 'create_sale_with_lines_and_payments'
      AND pg_get_function_identity_arguments(p.oid) = 'p_payload jsonb'
  ) AND NOT EXISTS (
    SELECT 1
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname = 'create_sale_with_lines_and_payments_core'
      AND pg_get_function_identity_arguments(p.oid) = 'p_payload jsonb'
  ) THEN
    EXECUTE 'ALTER FUNCTION public.create_sale_with_lines_and_payments(JSONB) RENAME TO create_sale_with_lines_and_payments_core';
  END IF;
END;
$$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname = 'create_product_with_variants'
      AND pg_get_function_identity_arguments(p.oid) = 'p_payload jsonb'
  ) AND NOT EXISTS (
    SELECT 1
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname = 'create_product_with_variants_core'
      AND pg_get_function_identity_arguments(p.oid) = 'p_payload jsonb'
  ) THEN
    EXECUTE 'ALTER FUNCTION public.create_product_with_variants(JSONB) RENAME TO create_product_with_variants_core';
  END IF;
END;
$$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname = 'update_product_basic'
      AND pg_get_function_identity_arguments(p.oid) = 'p_payload jsonb'
  ) AND NOT EXISTS (
    SELECT 1
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname = 'update_product_basic_core'
      AND pg_get_function_identity_arguments(p.oid) = 'p_payload jsonb'
  ) THEN
    EXECUTE 'ALTER FUNCTION public.update_product_basic(JSONB) RENAME TO update_product_basic_core';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_sale_with_lines_and_payments(
  p_payload JSONB,
  p_workspace_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_payload JSONB := COALESCE(p_payload, '{}'::JSONB);
  v_payload_workspace_id UUID;
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_active_workspace_member(p_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;

  v_payload_workspace_id := NULLIF(TRIM(v_payload->>'workspace_id'), '')::UUID;
  IF v_payload_workspace_id IS NOT NULL AND v_payload_workspace_id <> p_workspace_id THEN
    RAISE EXCEPTION 'cross_workspace_reference';
  END IF;

  v_payload := v_payload || jsonb_build_object('workspace_id', p_workspace_id::TEXT);
  RETURN public.create_sale_with_lines_and_payments_core(v_payload);
END;
$$;

CREATE OR REPLACE FUNCTION public.create_product_with_variants(
  p_payload JSONB,
  p_workspace_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_payload JSONB := COALESCE(p_payload, '{}'::JSONB);
  v_payload_workspace_id UUID;
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_active_workspace_member(p_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;

  v_payload_workspace_id := NULLIF(TRIM(v_payload->>'workspace_id'), '')::UUID;
  IF v_payload_workspace_id IS NOT NULL AND v_payload_workspace_id <> p_workspace_id THEN
    RAISE EXCEPTION 'cross_workspace_reference';
  END IF;

  v_payload := v_payload || jsonb_build_object('workspace_id', p_workspace_id::TEXT);
  RETURN public.create_product_with_variants_core(v_payload);
END;
$$;

CREATE OR REPLACE FUNCTION public.update_product_basic(
  p_payload JSONB,
  p_workspace_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_payload JSONB := COALESCE(p_payload, '{}'::JSONB);
  v_payload_workspace_id UUID;
BEGIN
  IF p_workspace_id IS NULL THEN
    RAISE EXCEPTION 'workspace_required';
  END IF;
  IF NOT app.is_active_workspace_member(p_workspace_id) THEN
    RAISE EXCEPTION 'workspace_forbidden';
  END IF;

  v_payload_workspace_id := NULLIF(TRIM(v_payload->>'workspace_id'), '')::UUID;
  IF v_payload_workspace_id IS NOT NULL AND v_payload_workspace_id <> p_workspace_id THEN
    RAISE EXCEPTION 'cross_workspace_reference';
  END IF;

  v_payload := v_payload || jsonb_build_object('workspace_id', p_workspace_id::TEXT);
  RETURN public.update_product_basic_core(v_payload);
END;
$$;

REVOKE ALL ON FUNCTION public.create_sale_with_lines_and_payments_core(JSONB) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.create_product_with_variants_core(JSONB) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.update_product_basic_core(JSONB) FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION public.create_sale_with_lines_and_payments(JSONB, UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.create_product_with_variants(JSONB, UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.update_product_basic(JSONB, UUID) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.create_sale_with_lines_and_payments(JSONB, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_product_with_variants(JSONB, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_product_basic(JSONB, UUID) TO authenticated;
