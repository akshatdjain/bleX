/**
 * auth.ts — Auth helpers for BleX UI.
 * All calls use credentials: 'include' so the httpOnly blex_token cookie is sent.
 */

export interface AuthUser {
  tenant_id: string;
  name: string;
  email: string;
  org_name: string;
}

export interface AuthError {
  detail?: string;
  message?: string;
}

async function authFetch(path: string, options: RequestInit = {}): Promise<Response> {
  return fetch(path, {
    ...options,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers ?? {}),
    },
  });
}

export async function getMe(): Promise<AuthUser | null> {
  try {
    const res = await authFetch("/asset/api/auth/me");
    if (!res.ok) return null;
    return res.json() as Promise<AuthUser>;
  } catch {
    return null;
  }
}

export async function login(
  email: string,
  password: string
): Promise<{ user: AuthUser } | { error: string }> {
  try {
    const res = await authFetch("/asset/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    if (!res.ok) {
      const err: AuthError = await res.json().catch(() => ({}));
      return { error: err.detail ?? err.message ?? "Login failed" };
    }
    const user: AuthUser = await res.json();
    // Clear stale cache so ProtectedRoute re-checks on next navigation
    const { clearAuthCache } = await import("@/components/ProtectedRoute");
    clearAuthCache();
    return { user };
  } catch {
    return { error: "Network error — please try again" };
  }
}

export async function register(
  name: string,
  email: string,
  password: string,
  org_name: string
): Promise<{ user: AuthUser } | { error: string }> {
  try {
    const res = await authFetch("/asset/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ name, email, password, org_name }),
    });
    if (!res.ok) {
      const err: AuthError = await res.json().catch(() => ({}));
      return { error: err.detail ?? err.message ?? "Registration failed" };
    }
    const user: AuthUser = await res.json();
    return { user };
  } catch {
    return { error: "Network error — please try again" };
  }
}

export async function logout(): Promise<void> {
  // Clear module-level auth cache so next ProtectedRoute check goes to server
  const { clearAuthCache } = await import("@/components/ProtectedRoute");
  clearAuthCache();
  await authFetch("/asset/api/auth/logout", { method: "POST" }).catch(() => {});
}
