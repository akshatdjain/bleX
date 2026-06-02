import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { getMe, AuthUser } from "@/lib/auth";

// Module-level cache — survives re-mounts from back/forward navigation
// Cleared on explicit logout (logout() in auth.ts sets this to null)
let _cachedUser: AuthUser | null | undefined = undefined; // undefined = not checked yet

export function clearAuthCache() {
  _cachedUser = undefined;
}

interface ProtectedRouteProps {
  children: React.ReactNode;
  onUser?: (user: AuthUser) => void;
}

export function ProtectedRoute({ children, onUser }: ProtectedRouteProps) {
  // If already cached, skip the loading state entirely
  const [state, setState] = useState<"loading" | "auth" | "unauth">(
    _cachedUser !== undefined ? (_cachedUser ? "auth" : "unauth") : "loading"
  );

  useEffect(() => {
    // Already resolved from cache — no network call needed
    if (_cachedUser !== undefined) {
      if (_cachedUser) onUser?.(_cachedUser);
      return;
    }

    getMe().then((user) => {
      _cachedUser = user ?? null;
      if (user) {
        onUser?.(user);
        setState("auth");
      } else {
        setState("unauth");
      }
    });
  }, []);

  if (state === "loading") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin" />
          <p className="text-sm text-muted-foreground">Authenticating…</p>
        </div>
      </div>
    );
  }

  if (state === "unauth") {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
