/**
 * api-client.ts — single fetch wrapper for all authenticated API calls.
 *
 * - Reads the in-memory access token (provided by AuthProvider via configureApiClient).
 * - Sends `Authorization: Bearer <token>` on every request.
 * - On 401: tries POST /auth/refresh once (with credentials so the refresh cookie is sent).
 *   If refresh succeeds, updates the in-memory token via the setter and retries the request.
 *   If refresh fails, calls the unauthorized callback (which clears auth + triggers redirect).
 * - Never sends cookies on normal API calls (credentials: "omit"); only the refresh endpoint
 *   uses credentials: "include".
 */

const API = "/asset/api";

let _accessTokenGetter: () => string | null = () => null;
let _setAccessToken: (t: string | null) => void = () => {};
let _onUnauthorized: () => void = () => {};

export function configureApiClient(
  getter: () => string | null,
  setter: (t: string | null) => void,
  onUnauth: () => void
) {
  _accessTokenGetter = getter;
  _setAccessToken = setter;
  _onUnauthorized = onUnauth;
}

let _refreshInflight: Promise<string | null> | null = null;

async function tryRefresh(): Promise<string | null> {
  if (_refreshInflight) return _refreshInflight;
  _refreshInflight = (async () => {
    try {
      const r = await fetch(`${API}/auth/refresh`, {
        method: "POST",
        credentials: "include",
      });
      if (!r.ok) return null;
      const data = await r.json();
      const tok = data.access_token as string;
      _setAccessToken(tok);
      return tok;
    } catch {
      return null;
    } finally {
      // Clear inflight after microtask so concurrent callers all get the same result.
      setTimeout(() => {
        _refreshInflight = null;
      }, 0);
    }
  })();
  return _refreshInflight;
}

function buildHeaders(init: RequestInit, token: string | null): Headers {
  const headers = new Headers(init.headers ?? {});
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  return headers;
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const tok = _accessTokenGetter();
  const headers = buildHeaders(init, tok);
  const url = path.startsWith("http") ? path : `${API}${path}`;
  const res = await fetch(url, { ...init, headers, credentials: "omit" });
  if (res.status !== 401) return res;

  // Try refresh once, then retry the original request with the new token.
  const newTok = await tryRefresh();
  if (!newTok) {
    _onUnauthorized();
    return res;
  }
  const retryHeaders = buildHeaders(init, newTok);
  return fetch(url, { ...init, headers: retryHeaders, credentials: "omit" });
}

export async function apiJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const r = await apiFetch(path, init);
  if (!r.ok) {
    const text = await r.text().catch(() => "");
    throw new Error(`${r.status} ${r.statusText}: ${text}`);
  }
  return r.json() as Promise<T>;
}
