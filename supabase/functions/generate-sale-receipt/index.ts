import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.8";
import { PDFDocument, StandardFonts, rgb } from "https://esm.sh/pdf-lib@1.17.1";

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const RECEIPT_BUCKET = "sale-receipts";
const SIGNED_URL_EXPIRATION_SECONDS = 30 * 60;
const MAX_RECEIPT_LINES = 10;

type SaleLine = {
  product_name?: string | null;
  quantity?: number | string | null;
  applied_unit_price?: number | string | null;
  line_total?: number | string | null;
};

type SalePayment = {
  payment_method?: string | null;
  amount?: number | string | null;
};

type SaleDetail = {
  sale_id?: number | string | null;
  sold_at?: string | null;
  customer_name?: string | null;
  status?: string | null;
  subtotal?: number | string | null;
  discount_total?: number | string | null;
  tax_total?: number | string | null;
  total?: number | string | null;
  paid_total?: number | string | null;
  change_total?: number | string | null;
  lines?: SaleLine[] | null;
  payments?: SalePayment[] | null;
};

const response = (status: number, payload: Record<string, unknown>) =>
  new Response(JSON.stringify(payload), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
  });

const toSafeText = (value: unknown, fallback = "N/A"): string => {
  if (typeof value === "string") {
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : fallback;
  }
  if (typeof value === "number") {
    return String(value);
  }
  return fallback;
};

const toNumeric = (value: unknown): number => {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string") {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return 0;
};

const formatMoney = (value: unknown): string => {
  const number = toNumeric(value);
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(number);
};

const formatDateTime = (value: unknown): string => {
  if (typeof value !== "string" || value.trim().length === 0) {
    return "N/A";
  }
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return value;
  return date.toISOString();
};

const getEnv = (name: string): string | null => {
  const value = Deno.env.get(name)?.trim();
  return value && value.length > 0 ? value : null;
};

const createReceiptPdf = async (detail: SaleDetail): Promise<Uint8Array> => {
  const pdfDoc = await PDFDocument.create();
  const page = pdfDoc.addPage([595, 842]);
  const font = await pdfDoc.embedFont(StandardFonts.Helvetica);
  const boldFont = await pdfDoc.embedFont(StandardFonts.HelveticaBold);

  const pageHeight = page.getHeight();
  const margin = 50;
  const lineGap = 16;
  let cursorY = pageHeight - margin;

  const drawLine = (
    text: string,
    opts?: { bold?: boolean; size?: number; color?: [number, number, number] },
  ) => {
    const size = opts?.size ?? 11;
    if (cursorY < margin) return;
    page.drawText(text, {
      x: margin,
      y: cursorY,
      size,
      font: opts?.bold ? boldFont : font,
      color: opts?.color ? rgb(opts.color[0], opts.color[1], opts.color[2]) : rgb(0, 0, 0),
    });
    cursorY -= lineGap;
  };

  drawLine("Receipt", { bold: true, size: 22, color: [0.08, 0.08, 0.08] });
  cursorY -= 6;
  drawLine(`Sale ID: ${toSafeText(detail.sale_id)}`);
  drawLine(`Date: ${formatDateTime(detail.sold_at)}`);
  drawLine(`Customer: ${toSafeText(detail.customer_name, "Walk-in customer")}`);
  drawLine(`Status: ${toSafeText(detail.status)}`);
  cursorY -= 6;

  drawLine("Amounts", { bold: true, size: 13 });
  drawLine(`Subtotal: ${formatMoney(detail.subtotal)}`);
  drawLine(`Discount: ${formatMoney(detail.discount_total)}`);
  drawLine(`Tax: ${formatMoney(detail.tax_total)}`);
  drawLine(`Total: ${formatMoney(detail.total)}`);
  drawLine(`Paid: ${formatMoney(detail.paid_total)}`);
  drawLine(`Change: ${formatMoney(detail.change_total)}`);
  cursorY -= 6;

  drawLine("Items", { bold: true, size: 13 });
  const lines = Array.isArray(detail.lines) ? detail.lines : [];
  if (lines.length === 0) {
    drawLine("- No items");
  } else {
    for (const item of lines.slice(0, MAX_RECEIPT_LINES)) {
      const itemName = toSafeText(item.product_name, "Unnamed item");
      const quantity = toNumeric(item.quantity);
      const unitPrice = formatMoney(item.applied_unit_price);
      const lineTotal = formatMoney(item.line_total);
      drawLine(`- ${itemName} | Qty: ${quantity} | Unit: ${unitPrice} | Total: ${lineTotal}`);
    }
    if (lines.length > MAX_RECEIPT_LINES) {
      drawLine(`- ...and ${lines.length - MAX_RECEIPT_LINES} more item(s)`);
    }
  }
  cursorY -= 6;

  drawLine("Payments", { bold: true, size: 13 });
  const payments = Array.isArray(detail.payments) ? detail.payments : [];
  if (payments.length === 0) {
    drawLine("- No payments");
  } else {
    for (const payment of payments.slice(0, MAX_RECEIPT_LINES)) {
      drawLine(`- ${toSafeText(payment.payment_method)}: ${formatMoney(payment.amount)}`);
    }
    if (payments.length > MAX_RECEIPT_LINES) {
      drawLine(`- ...and ${payments.length - MAX_RECEIPT_LINES} more payment(s)`);
    }
  }

  return await pdfDoc.save();
};

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: CORS_HEADERS });
  }

  if (request.method !== "POST") {
    return response(400, { message: "Only POST is allowed" });
  }

  const supabaseUrl = getEnv("SUPABASE_URL");
  const publishableOrAnonKey = getEnv("SB_PUBLISHABLE_KEY") ?? getEnv("SUPABASE_ANON_KEY");
  const serviceRoleKey = getEnv("SUPABASE_SERVICE_ROLE_KEY");

  if (!supabaseUrl) {
    return response(500, { message: "Missing required environment variable: SUPABASE_URL" });
  }
  if (!publishableOrAnonKey) {
    return response(500, {
      message:
        "Missing required client key: set SB_PUBLISHABLE_KEY (preferred) or SUPABASE_ANON_KEY",
    });
  }
  if (!serviceRoleKey) {
    return response(500, { message: "Missing required environment variable: SUPABASE_SERVICE_ROLE_KEY" });
  }

  const authorization = request.headers.get("Authorization") ??
    request.headers.get("authorization");
  if (!authorization || !authorization.toLowerCase().startsWith("bearer ")) {
    return response(401, { message: "Authorization Bearer token is required" });
  }

  const accessToken = authorization.slice(7).trim();
  if (!accessToken) {
    return response(401, { message: "Authorization token is empty" });
  }

  let saleId: number;
  try {
    const payload = await request.json();
    const input = payload?.sale_id;
    if (typeof input !== "number" || !Number.isFinite(input)) {
      return response(400, { message: "sale_id must be a valid number" });
    }
    saleId = Math.trunc(input);
    if (saleId <= 0) {
      return response(400, { message: "sale_id must be greater than 0" });
    }
  } catch (_error) {
    return response(400, { message: "Invalid JSON body" });
  }

  const userClient = createClient(supabaseUrl, publishableOrAnonKey, {
    global: {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
    auth: {
      autoRefreshToken: false,
      persistSession: false,
    },
  });

  const { data: userData, error: userError } = await userClient.auth.getUser(accessToken);
  if (userError || !userData.user) {
    return response(401, { message: "Unauthorized user session" });
  }

  const { data: saleRow, error: saleError } = await userClient
    .from("sales")
    .select("workspace_id")
    .eq("id", saleId)
    .maybeSingle();

  if (saleError) {
    return response(500, { message: "Failed to resolve sale workspace" });
  }

  const workspaceId = saleRow?.workspace_id;
  if (!workspaceId) {
    return response(404, { message: "Sale not found" });
  }

  const { data: detailData, error: detailError } = await userClient.rpc(
    "get_dashboard_sale_detail",
    { p_sale_id: saleId },
  );
  if (detailError || !detailData || typeof detailData !== "object") {
    return response(500, { message: "Failed to fetch sale detail" });
  }

  let pdfBytes: Uint8Array;
  try {
    pdfBytes = await createReceiptPdf(detailData as SaleDetail);
  } catch (_error) {
    return response(500, { message: "Failed to generate receipt PDF" });
  }

  const adminClient = createClient(supabaseUrl, serviceRoleKey, {
    auth: {
      autoRefreshToken: false,
      persistSession: false,
    },
  });

  const timestamp = new Date().toISOString().replace(/[-:.TZ]/g, "");
  const path = `${workspaceId}/sale-${saleId}/receipt-${timestamp}.pdf`;

  const { error: uploadError } = await adminClient.storage
    .from(RECEIPT_BUCKET)
    .upload(path, pdfBytes, {
      contentType: "application/pdf",
      upsert: false,
    });

  if (uploadError) {
    return response(500, { message: "Failed to upload receipt PDF" });
  }

  const { data: signedData, error: signedError } = await adminClient.storage
    .from(RECEIPT_BUCKET)
    .createSignedUrl(path, SIGNED_URL_EXPIRATION_SECONDS);

  if (signedError || !signedData?.signedUrl) {
    return response(500, { message: "Failed to generate signed URL" });
  }

  return response(200, {
    receipt_url: signedData.signedUrl,
    path,
    expires_in_seconds: SIGNED_URL_EXPIRATION_SECONDS,
  });
});
