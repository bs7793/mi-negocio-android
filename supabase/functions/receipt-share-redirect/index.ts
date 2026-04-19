import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.8";

const SIGNED_URL_EXPIRATION_SECONDS = 10 * 60;
const RECEIPT_BUCKET = "sale-receipts";
const SHARE_ID_PATTERN = /^[a-zA-Z0-9_-]{8,64}$/;

const response = (status: number, payload: Record<string, unknown>) =>
  new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const getEnv = (name: string): string | null => {
  const value = Deno.env.get(name)?.trim();
  return value && value.length > 0 ? value : null;
};

const resolveShareId = (url: URL): string | null => {
  const queryShareId = url.searchParams.get("share_id")?.trim();
  if (queryShareId) return queryShareId;
  const segments = url.pathname.split("/").filter(Boolean);
  const functionNameIndex = segments.findIndex((segment) => segment === "receipt-share-redirect");
  if (functionNameIndex < 0 || functionNameIndex + 1 >= segments.length) {
    return null;
  }
  const pathShareId = segments[functionNameIndex + 1]?.trim();
  return pathShareId?.length ? pathShareId : null;
};

Deno.serve(async (request: Request) => {
  if (request.method !== "GET") {
    return response(405, { message: "Only GET is allowed" });
  }

  const supabaseUrl = getEnv("SUPABASE_URL");
  const serviceRoleKey = getEnv("SUPABASE_SERVICE_ROLE_KEY");

  if (!supabaseUrl || !serviceRoleKey) {
    return response(500, { message: "Server is missing required Supabase configuration" });
  }

  const requestUrl = new URL(request.url);
  const shareId = resolveShareId(requestUrl);
  if (!shareId) {
    return response(400, { message: "share_id is required" });
  }
  if (!SHARE_ID_PATTERN.test(shareId)) {
    return response(400, { message: "share_id format is invalid" });
  }

  const adminClient = createClient(supabaseUrl, serviceRoleKey, {
    auth: {
      autoRefreshToken: false,
      persistSession: false,
    },
  });

  const { data: shareData, error: shareError } = await adminClient
    .from("sale_receipt_shares")
    .select("storage_path,status,expires_at")
    .eq("share_id", shareId)
    .maybeSingle();

  if (shareError) {
    return response(500, { message: "Failed to resolve share link" });
  }
  if (!shareData) {
    return response(404, { message: "Receipt link was not found" });
  }
  if (shareData.status !== "active") {
    return response(410, { message: "Receipt link is no longer active" });
  }

  const expiresAtMs = Date.parse(shareData.expires_at);
  if (Number.isNaN(expiresAtMs) || expiresAtMs <= Date.now()) {
    return response(410, { message: "Receipt link has expired" });
  }

  const { data: signedData, error: signedError } = await adminClient.storage
    .from(RECEIPT_BUCKET)
    .createSignedUrl(shareData.storage_path, SIGNED_URL_EXPIRATION_SECONDS);

  if (signedError || !signedData?.signedUrl) {
    return response(500, { message: "Failed to create receipt access URL" });
  }

  return Response.redirect(signedData.signedUrl, 302);
});
