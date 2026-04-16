DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND policyname = 'product_images_owner_update'
  ) THEN
    DROP POLICY product_images_owner_update ON storage.objects;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND policyname = 'product_images_owner_delete'
  ) THEN
    DROP POLICY product_images_owner_delete ON storage.objects;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND policyname = 'product_images_workspace_member_update'
  ) THEN
    CREATE POLICY product_images_workspace_member_update
      ON storage.objects
      FOR UPDATE
      TO authenticated
      USING (
        bucket_id = 'product-images'
        AND EXISTS (
          SELECT 1
          FROM public.workspace_memberships membership
          WHERE membership.user_id = auth.uid()
            AND membership.status = 'active'
        )
      )
      WITH CHECK (
        bucket_id = 'product-images'
        AND EXISTS (
          SELECT 1
          FROM public.workspace_memberships membership
          WHERE membership.user_id = auth.uid()
            AND membership.status = 'active'
        )
      );
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND policyname = 'product_images_workspace_member_delete'
  ) THEN
    CREATE POLICY product_images_workspace_member_delete
      ON storage.objects
      FOR DELETE
      TO authenticated
      USING (
        bucket_id = 'product-images'
        AND EXISTS (
          SELECT 1
          FROM public.workspace_memberships membership
          WHERE membership.user_id = auth.uid()
            AND membership.status = 'active'
        )
      );
  END IF;
END $$;
