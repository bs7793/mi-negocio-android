-- Hardening: enforce workspace consistency across products-related FK graphs.

CREATE OR REPLACE FUNCTION app.enforce_product_option_value_workspace_match()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  v_option_type_workspace_id UUID;
BEGIN
  SELECT pot.workspace_id
  INTO v_option_type_workspace_id
  FROM public.product_option_types pot
  WHERE pot.id = NEW.option_type_id;

  IF v_option_type_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Invalid option_type_id % for product_option_values', NEW.option_type_id;
  END IF;

  IF NEW.workspace_id <> v_option_type_workspace_id THEN
    RAISE EXCEPTION 'Workspace mismatch: product_option_values.workspace_id must match product_option_types.workspace_id';
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION app.enforce_product_variant_workspace_match()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  v_product_workspace_id UUID;
BEGIN
  SELECT p.workspace_id
  INTO v_product_workspace_id
  FROM public.products p
  WHERE p.id = NEW.product_id;

  IF v_product_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Invalid product_id % for product_variants', NEW.product_id;
  END IF;

  IF NEW.workspace_id <> v_product_workspace_id THEN
    RAISE EXCEPTION 'Workspace mismatch: product_variants.workspace_id must match products.workspace_id';
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION app.enforce_variant_option_workspace_match()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  v_variant_workspace_id UUID;
  v_option_type_workspace_id UUID;
  v_option_value_workspace_id UUID;
  v_option_value_type_id BIGINT;
BEGIN
  SELECT v.workspace_id
  INTO v_variant_workspace_id
  FROM public.product_variants v
  WHERE v.id = NEW.variant_id;

  SELECT ot.workspace_id
  INTO v_option_type_workspace_id
  FROM public.product_option_types ot
  WHERE ot.id = NEW.option_type_id;

  SELECT ov.workspace_id, ov.option_type_id
  INTO v_option_value_workspace_id, v_option_value_type_id
  FROM public.product_option_values ov
  WHERE ov.id = NEW.option_value_id;

  IF v_variant_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Invalid variant_id % for product_variant_option_values', NEW.variant_id;
  END IF;
  IF v_option_type_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Invalid option_type_id % for product_variant_option_values', NEW.option_type_id;
  END IF;
  IF v_option_value_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Invalid option_value_id % for product_variant_option_values', NEW.option_value_id;
  END IF;

  IF NEW.workspace_id <> v_variant_workspace_id
     OR NEW.workspace_id <> v_option_type_workspace_id
     OR NEW.workspace_id <> v_option_value_workspace_id THEN
    RAISE EXCEPTION 'Workspace mismatch in product_variant_option_values relation';
  END IF;

  IF v_option_value_type_id <> NEW.option_type_id THEN
    RAISE EXCEPTION 'Option value % does not belong to option type %', NEW.option_value_id, NEW.option_type_id;
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION app.enforce_variant_inventory_workspace_match()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  v_variant_workspace_id UUID;
  v_warehouse_workspace_id UUID;
BEGIN
  SELECT v.workspace_id
  INTO v_variant_workspace_id
  FROM public.product_variants v
  WHERE v.id = NEW.variant_id;

  SELECT w.workspace_id
  INTO v_warehouse_workspace_id
  FROM public.warehouses w
  WHERE w.id = NEW.warehouse_id;

  IF v_variant_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Invalid variant_id % for variant_inventory', NEW.variant_id;
  END IF;
  IF v_warehouse_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Invalid warehouse_id % for variant_inventory', NEW.warehouse_id;
  END IF;

  IF NEW.workspace_id <> v_variant_workspace_id
     OR NEW.workspace_id <> v_warehouse_workspace_id THEN
    RAISE EXCEPTION 'Workspace mismatch in variant_inventory relation';
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION app.enforce_variant_inventory_movement_workspace_match()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  v_variant_workspace_id UUID;
  v_variant_inventory_workspace_id UUID;
  v_variant_inventory_variant_id BIGINT;
  v_source_warehouse_workspace_id UUID;
  v_destination_warehouse_workspace_id UUID;
BEGIN
  SELECT v.workspace_id
  INTO v_variant_workspace_id
  FROM public.product_variants v
  WHERE v.id = NEW.variant_id;

  IF v_variant_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Invalid variant_id % for variant_inventory_movements', NEW.variant_id;
  END IF;

  IF NEW.workspace_id <> v_variant_workspace_id THEN
    RAISE EXCEPTION 'Workspace mismatch: movement workspace must match variant workspace';
  END IF;

  IF NEW.variant_inventory_id IS NOT NULL THEN
    SELECT vi.workspace_id, vi.variant_id
    INTO v_variant_inventory_workspace_id, v_variant_inventory_variant_id
    FROM public.variant_inventory vi
    WHERE vi.id = NEW.variant_inventory_id;

    IF v_variant_inventory_workspace_id IS NULL THEN
      RAISE EXCEPTION 'Invalid variant_inventory_id % for variant_inventory_movements', NEW.variant_inventory_id;
    END IF;

    IF NEW.workspace_id <> v_variant_inventory_workspace_id THEN
      RAISE EXCEPTION 'Workspace mismatch: movement workspace must match variant_inventory workspace';
    END IF;

    IF NEW.variant_id <> v_variant_inventory_variant_id THEN
      RAISE EXCEPTION 'variant_inventory_id % does not belong to variant_id %', NEW.variant_inventory_id, NEW.variant_id;
    END IF;
  END IF;

  IF NEW.source_warehouse_id IS NOT NULL THEN
    SELECT w.workspace_id
    INTO v_source_warehouse_workspace_id
    FROM public.warehouses w
    WHERE w.id = NEW.source_warehouse_id;

    IF v_source_warehouse_workspace_id IS NULL THEN
      RAISE EXCEPTION 'Invalid source_warehouse_id % for variant_inventory_movements', NEW.source_warehouse_id;
    END IF;
    IF NEW.workspace_id <> v_source_warehouse_workspace_id THEN
      RAISE EXCEPTION 'Workspace mismatch: source warehouse belongs to a different workspace';
    END IF;
  END IF;

  IF NEW.destination_warehouse_id IS NOT NULL THEN
    SELECT w.workspace_id
    INTO v_destination_warehouse_workspace_id
    FROM public.warehouses w
    WHERE w.id = NEW.destination_warehouse_id;

    IF v_destination_warehouse_workspace_id IS NULL THEN
      RAISE EXCEPTION 'Invalid destination_warehouse_id % for variant_inventory_movements', NEW.destination_warehouse_id;
    END IF;
    IF NEW.workspace_id <> v_destination_warehouse_workspace_id THEN
      RAISE EXCEPTION 'Workspace mismatch: destination warehouse belongs to a different workspace';
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_enforce_product_option_value_workspace_match ON public.product_option_values;
CREATE TRIGGER trg_enforce_product_option_value_workspace_match
BEFORE INSERT OR UPDATE ON public.product_option_values
FOR EACH ROW
EXECUTE FUNCTION app.enforce_product_option_value_workspace_match();

DROP TRIGGER IF EXISTS trg_enforce_product_variant_workspace_match ON public.product_variants;
CREATE TRIGGER trg_enforce_product_variant_workspace_match
BEFORE INSERT OR UPDATE ON public.product_variants
FOR EACH ROW
EXECUTE FUNCTION app.enforce_product_variant_workspace_match();

DROP TRIGGER IF EXISTS trg_enforce_variant_option_workspace_match ON public.product_variant_option_values;
CREATE TRIGGER trg_enforce_variant_option_workspace_match
BEFORE INSERT OR UPDATE ON public.product_variant_option_values
FOR EACH ROW
EXECUTE FUNCTION app.enforce_variant_option_workspace_match();

DROP TRIGGER IF EXISTS trg_enforce_variant_inventory_workspace_match ON public.variant_inventory;
CREATE TRIGGER trg_enforce_variant_inventory_workspace_match
BEFORE INSERT OR UPDATE ON public.variant_inventory
FOR EACH ROW
EXECUTE FUNCTION app.enforce_variant_inventory_workspace_match();

DROP TRIGGER IF EXISTS trg_enforce_variant_inventory_movement_workspace_match ON public.variant_inventory_movements;
CREATE TRIGGER trg_enforce_variant_inventory_movement_workspace_match
BEFORE INSERT OR UPDATE ON public.variant_inventory_movements
FOR EACH ROW
EXECUTE FUNCTION app.enforce_variant_inventory_movement_workspace_match();
