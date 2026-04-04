-- Seed: sample categories (fixed ids for reproducible dev).
-- After seed, verify sequence alignment: next insert should use max(id)+1.
--   SELECT MAX(id) FROM public.categories;
--   SELECT last_value FROM pg_get_serial_sequence('public.categories', 'id')::regclass;

INSERT INTO categories (id, name, description)
VALUES
  (1, 'Default', 'Sin clasificar / categoría por defecto'),
  (2, 'Food', 'Alimentación'),
  (3, 'Beverages', 'Bebidas'),
  (4, 'Cleaning', 'Limpieza e higiene'),
  (5, 'Electronics', 'Electrónica y accesorios')
ON CONFLICT (id) DO NOTHING;

-- Keep GENERATED IDENTITY sequence in sync after explicit ids (avoids duplicate key on next INSERT).
SELECT setval(
  pg_get_serial_sequence('public.categories', 'id'),
  COALESCE((SELECT MAX(id) FROM public.categories), 1),
  true
);
