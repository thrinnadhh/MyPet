import { Webhook } from "https://esm.sh/standardwebhooks@1.0.0";

type SendSmsHookPayload = {
  user: {
    id: string;
    phone: string;
  };
  sms: {
    otp: string;
  };
};

const MSG91_FLOW_URL = "https://control.msg91.com/api/v5/flow";

function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`Missing required secret: ${name}`);
  return value;
}

function normalizeMsg91Mobile(phone: string): string {
  const digits = phone.replace(/\D/g, "");
  if (digits.length < 10 || digits.length > 15) throw new Error("Invalid phone number in Send SMS hook payload");
  return digits;
}

function errorResponse(status: number, message: string): Response {
  return new Response(
    JSON.stringify({ error: { http_code: status, message } }),
    { status, headers: { "Content-Type": "application/json" } },
  );
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return errorResponse(405, "Method not allowed");

  try {
    const rawPayload = await req.text();
    const hookSecret = requiredEnv("SEND_SMS_HOOK_SECRET").replace("v1,whsec_", "");
    const webhook = new Webhook(hookSecret);
    const payload = webhook.verify(rawPayload, Object.fromEntries(req.headers)) as SendSmsHookPayload;

    if (!/^\d{6}$/.test(payload.sms.otp)) return errorResponse(400, "Invalid OTP payload");

    const authKey = requiredEnv("MSG91_AUTH_KEY");
    const templateId = requiredEnv("MSG91_SMS_TEMPLATE_ID");
    const otpVariable = Deno.env.get("MSG91_OTP_VARIABLE")?.trim() || "VAR1";
    const mobile = normalizeMsg91Mobile(payload.user.phone);
    const correlationId = `mypet-auth-${payload.user.id}-${crypto.randomUUID()}`;

    const recipient: Record<string, string> = {
      mobiles: mobile,
      CRQID: correlationId,
      [otpVariable]: payload.sms.otp,
    };

    const response = await fetch(MSG91_FLOW_URL, {
      method: "POST",
      headers: {
        accept: "application/json",
        authkey: authKey,
        "content-type": "application/json",
      },
      body: JSON.stringify({
        template_id: templateId,
        short_url: "0",
        realTimeResponse: "1",
        recipients: [recipient],
      }),
      signal: AbortSignal.timeout(4_000),
    });

    if (!response.ok) {
      const body = (await response.text()).slice(0, 1000);
      console.error("MSG91 SMS send failed", response.status, body);
      return errorResponse(response.status >= 500 ? 502 : response.status, "SMS provider rejected the request");
    }

    return new Response(JSON.stringify({}), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (error) {
    console.error("Send SMS hook failure", error);
    const message = error instanceof Error ? error.message : "Unknown Send SMS hook failure";
    const isSignatureFailure = message.toLowerCase().includes("signature") || message.toLowerCase().includes("webhook");
    return errorResponse(isSignatureFailure ? 401 : 500, isSignatureFailure ? "Invalid hook signature" : "SMS delivery failed");
  }
});
