import { Navigate } from "react-router-dom";
import { useAuth, AuthUser } from "@/lib/auth-context";

// Backwards-compat: some old call sites import clearAuthCache. The new auth-context
// is the source of truth and clears its own state on logout, so this is a no-op.
export function clearAuthCache() {
  /* deprecated — auth-context handles this internally */
}

interface ProtectedRouteProps {
  children: React.ReactNode;
  onUser?: (user: AuthUser) => void;
}

export function ProtectedRoute({ children, onUser }: ProtectedRouteProps) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin" />
          <p className="text-sm text-muted-foreground">Authenticating…</p>
        </div>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  // Admins should use /admin routes, not tenant dashboard
  if (user.role === "admin") {
    return <Navigate to="/admin" replace />;
  }

  if (onUser) onUser(user);
  return <>{children}</>;
}
