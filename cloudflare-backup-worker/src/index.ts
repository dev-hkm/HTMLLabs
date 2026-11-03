interface Env {
  HTML_BACKUPS: KVNamespace;
}

interface BackupEnvelope {
  backupJson: string;
  updatedAt: string;
  size: number;
}

const TOKEN_REGEX = /^hkm-[A-Za-z0-9]{36}$/;
const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
};

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: JSON_HEADERS,
  });
}

function validateToken(token: string | null): string | null {
  if (!token) return null;
  return TOKEN_REGEX.test(token) ? token : null;
}

function keyForToken(token: string): string {
  return `backup:${token}`;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const method = request.method.toUpperCase();

    if (method === "GET" && url.pathname === "/health") {
      return json({
        ok: true,
        service: "html-labs-backup",
        date: new Date().toISOString(),
      });
    }

    if (method === "POST" && url.pathname === "/backup") {
      const body = await request.json<{
        token?: string;
        backupJson?: string;
      }>().catch(() => null);

      const token = validateToken(body?.token ?? null);
      const backupJson = body?.backupJson?.trim() ?? "";

      if (!token) {
        return json({ ok: false, error: "invalid_token" }, 400);
      }

      if (!backupJson) {
        return json({ ok: false, error: "missing_backup_json" }, 400);
      }

      const payload: BackupEnvelope = {
        backupJson,
        updatedAt: new Date().toISOString(),
        size: backupJson.length,
      };

      await env.HTML_BACKUPS.put(keyForToken(token), JSON.stringify(payload));
      return json({
        ok: true,
        updatedAt: payload.updatedAt,
        size: payload.size,
      });
    }

    if (method === "GET" && url.pathname.startsWith("/backup/")) {
      const token = validateToken(url.pathname.split("/").pop() ?? null);
      if (!token) {
        return json({ ok: false, error: "invalid_token" }, 400);
      }

      const stored = await env.HTML_BACKUPS.get(keyForToken(token));
      if (!stored) {
        return json({ ok: false, error: "not_found" }, 404);
      }

      const payload = JSON.parse(stored) as BackupEnvelope;
      return json({
        ok: true,
        updatedAt: payload.updatedAt,
        size: payload.size,
        backupJson: payload.backupJson,
      });
    }

    if (method === "DELETE" && url.pathname.startsWith("/backup/")) {
      const token = validateToken(url.pathname.split("/").pop() ?? null);
      if (!token) {
        return json({ ok: false, error: "invalid_token" }, 400);
      }

      await env.HTML_BACKUPS.delete(keyForToken(token));
      return json({ ok: true });
    }

    return json({ ok: false, error: "not_found" }, 404);
  },
};
