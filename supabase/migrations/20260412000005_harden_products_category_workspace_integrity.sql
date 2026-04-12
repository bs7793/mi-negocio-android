-- Enforce product/category workspace consistency for direct inserts/updates.

CREATE OR REPLACE FUNCTION app.enforce_products_category_workspace_match()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  v_category_workspace_id UUID;
BEGIN
  IF NEW.category_id IS NULL THEN
    RETURN NEW;
  END IF;

  SELECT c.workspace_id
  INTO v_category_workspace_id
  FROM public.categories c
  WHERE c.id = NEW.category_id;

  IF v_category_workspace_id IS NULL THEN
    RAISE EXCEPTION 'Invalid category_id % for products', NEW.category_id;
  END IF;

  IF NEW.workspace_id <> v_category_workspace_id THEN
    RAISE EXCEPTION 'Workspace mismatch: products.category_id belongs to a different workspace';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_enforce_products_category_workspace_match ON public.products;
CREATE TRIGGER trg_enforce_products_category_workspace_match
BEFORE INSERT OR UPDATE ON public.products
FOR EACH ROW
EXECUTE FUNCTION app.enforce_products_category_workspace_match();
