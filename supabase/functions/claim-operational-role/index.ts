import { createClient } from "npm:@supabase/supabase-js@2.108.2";

type SelfServiceRole = "MERCHANT" | "CAPTAIN";

const ALLOWED_ROLES = new Set<SelfServiceRole>(["MERCHANT", "CAPTAIN"]);

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
  return value;
}

function firstKey(jsonEnv: string | undefined): string | null {
  if (!jsonEnv) return null;
  try {
    const parsed = JSON.parse(jsonEnv) as Record<string, string>;
    return parsed.default ?? Object.values(parsed).find(Boolean) ?? null;
  } catch {
    return null;
  }
}

function publishableKey(): string {
  return firstKey(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS"))
    ?? requiredEnv("SUPABASE_ANON_KEY");
}

function secretKey(): string {
  return firstKey(Deno.env.get("SUPABASE_SECRET_KEYS"))
    ?? requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
}

function normalizeExistingRole(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const role = value.trim().toUpperCase();
  if (!role) return null;
  return role === "PROVIDER" ? "MERCHANT" : role;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });

  const authorization = req.headers.get("authorization")?.trim();
  if (!authorization?.toLowerCase().startsWith("bearer ")) {
    return json(401, { error: "Authenticated user token required" });
  }

  try {
    const requested = (await req.json()) as { role?: unknown };
    const requestedRole = typeof requested.role === "string"
      ? requested.role.trim().toUpperCase()
      : "";
    if (!ALLOWED_ROLES.has(requestedRole as SelfServiceRole)) {
      return json(400, { error: "Only MERCHANT or CAPTAIN can be self-registered" });
    }

    const supabaseUrl = requiredEnv("SUPABASE_URL");
    const token = authorization.slice("Bearer ".length).trim();
    const callerClient = createClient(supabaseUrl, publishableKey(), {
      auth: { persistSession: false, autoRefreshToken: false },
      global: { headers: { Authorization: authorization } },
    });
    const { data: callerData, error: callerError } = await callerClient.auth.getUser(token);
    if (callerError || !callerData.user) {
      return json(401, { error: "Invalid or expired user token" });
    }

    const user = callerData.user;
    const existingRole = normalizeExistingRole(user.app_metadata?.role);
    if (existingRole === "ADMIN") {
      return json(403, { error: "Administrator roles cannot be changed through self-service" });
    }
    if (existingRole && existingRole !== "CUSTOMER" && existingRole !== requestedRole) {
      return json(409, { error: `Account already has operational role ${existingRole}` });
    }
    if (existingRole === requestedRole) {
      return json(200, { role: existingRole, changed: false });
    }

    const adminClient = createClient(supabaseUrl, secretKey(), {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { error: updateError } = await adminClient.auth.admin.updateUserById(user.id, {
      app_metadata: {
        ...user.app_metadata,
        role: requestedRole,
      },
    });
    if (updateError) {
      console.error("Operational role update failed", updateError.message);
      return json(502, { error: "Could not provision operational role" });
    }

    return json(200, { role: requestedRole, changed: true });
  } catch (error) {
    console.error("Operational role claim failed", error);
    return json(500, { error: "Operational role provisioning failed" });
  }
});
