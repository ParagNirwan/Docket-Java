export interface AuthUser {
  email: string;
  role?: string;
  tenantId?: string;
  userType?: string;
  emailVerified?: boolean;
  exp?: number;
  iat?: number;
}

export function parseJwt(token: string): AuthUser | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    let base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    while (base64.length % 4) {
      base64 += "=";
    }
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split("")
        .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
        .join("")
    );
    const parsed = JSON.parse(jsonPayload);
    return {
      email: parsed.sub || parsed.email || "",
      role: parsed.role,
      tenantId: parsed.tenantId,
      userType: parsed.userType,
      emailVerified: parsed.emailVerified,
      exp: parsed.exp,
      iat: parsed.iat,
    };
  } catch {
    return null;
  }
}

export function isTokenExpired(user: AuthUser | null): boolean {
  if (!user || !user.exp) return false;
  return user.exp * 1000 < Date.now();
}

