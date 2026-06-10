import { createContext, useContext, useEffect, useState, ReactNode, useCallback } from "react";
import { configureApiClient, tryRefresh } from "./api-client";

const API = "/asset/api";

export interface AuthUser {
  id: number;
  email: string;
  name: string;
  role: "admin" | "user";
  tenant_id: string;
  org_name?: string;
}

interface AuthState {
  user: AuthUser | null;
  accessToken: string | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<{ ok: true } | { ok: false; error: string }>;
  logout: () => Promise<void>;
  setAccessToken: (t: string | null) => void;
  setUser: (u: AuthUser | null) => void;
}

const AuthCtx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [accessToken, setAccessTokenState] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const setAccessToken = useCallback((t: string | null) => {
    setAccessTokenState(t);
  }, []);

  // Wire api-client to read the latest token via a ref-like getter and to update the
  // in-memory token after a silent refresh inside apiFetch.
  useEffect(() => {
    configureApiClient(
      () => accessToken,
      (t: string | null) => setAccessTokenState(t),
      () => {
        // On unauthorized: clear in-memory auth; ProtectedRoute will redirect.
        setAccessTokenState(null);
        setUser(null);
      }
    );
  }, [accessToken]);

  // Bootstrap: try the refresh cookie on mount.
  // Uses shared tryRefresh so it deduplicates with any concurrent apiFetch
  // retries — prevents double-refresh / family revocation on page reload.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const tok = await tryRefresh();
        if (tok && !cancelled) {
          setAccessTokenState(tok);
          const me = await fetch(`${API}/auth/me`, {
            headers: { Authorization: `Bearer ${tok}` },
          });
          if (me.ok && !cancelled) {
            setUser(await me.json());
          }
        }
      } catch {
        /* not logged in */
      }
      if (!cancelled) setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    // Single login endpoint — server dispatches based on email lookup (admin table first, then users)
    const r = await fetch(`${API}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ email, password }),
    });
    if (!r.ok) {
      const err = await r.json().catch(() => ({} as any));
      return { ok: false as const, error: err.detail ?? err.message ?? "Login failed" };
    }
    const data = await r.json();
    setAccessTokenState(data.access_token);
    setUser(data.user);
    return { ok: true as const };
  }, []);

  const logout = useCallback(async () => {
    try {
      await fetch(`${API}/auth/logout`, {
        method: "POST",
        credentials: "include",
      });
    } catch {
      /* ignore */
    }
    setAccessTokenState(null);
    setUser(null);
  }, []);

  return (
    <AuthCtx.Provider
      value={{ user, accessToken, loading, login, logout, setAccessToken, setUser }}
    >
      {children}
    </AuthCtx.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthCtx);
  if (!ctx) throw new Error("useAuth must be inside AuthProvider");
  return ctx;
}
