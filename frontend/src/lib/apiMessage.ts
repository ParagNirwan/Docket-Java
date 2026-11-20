// Reads the server's error message so admins see "Webhook URLs must use https" rather
// than "Request failed (400)". Falls back when the body isn't JSON.
export async function apiMessage(response: Response, fallback: string): Promise<string> {
  try {
    const body: unknown = await response.json();
    if (body && typeof body === "object") {
      const record = body as Record<string, unknown>;
      for (const key of ["message", "detail", "error"]) {
        const value = record[key];
        if (typeof value === "string" && value.trim()) return value;
      }
    }
  } catch {
    // not JSON
  }
  return `${fallback} (${response.status})`;
}
