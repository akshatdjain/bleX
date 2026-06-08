import { Link, useLocation, useNavigate } from "react-router-dom";
import {
  LayoutDashboard,
  Users,
  ScrollText,
  Settings,
  LogOut,
  ArrowLeft,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useState } from "react";
import { useAuth } from "@/lib/auth-context";

interface NavItem {
  to: string;
  label: string;
  icon: any;
}

const ADMIN_NAV: NavItem[] = [
  { to: "/admin", label: "Overview", icon: LayoutDashboard },
  { to: "/admin/tenants", label: "Tenants", icon: Users },
  { to: "/admin/audit", label: "Audit Log", icon: ScrollText },
  { to: "/admin/settings", label: "Settings", icon: Settings },
];

export function AdminLayout({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const [collapsed, setCollapsed] = useState(true);

  async function handleLogout() {
    await logout();
    navigate("/login", { replace: true });
  }

  return (
    <div className="flex h-screen bg-background">
      <aside
        onMouseEnter={() => setCollapsed(false)}
        onMouseLeave={() => setCollapsed(true)}
        className={cn(
          "h-full bg-sidebar text-sidebar-foreground flex flex-col border-r transition-all duration-300 ease-in-out overflow-hidden",
          collapsed ? "w-16" : "w-64"
        )}
      >
        {/* Header */}
        <div className="px-3 py-4 border-b flex items-center h-14">
          <div className="w-10 h-10 rounded-lg bg-primary/10 text-primary flex items-center justify-center font-bold flex-shrink-0">
            B
          </div>
          <div
            className={cn(
              "ml-3 transition-opacity duration-200",
              collapsed ? "opacity-0" : "opacity-100"
            )}
          >
            <p className="text-sm font-semibold whitespace-nowrap">BleX Admin</p>
            <p className="text-xs text-muted-foreground whitespace-nowrap">
              Tenant Management
            </p>
          </div>
        </div>

        {/* Back to app */}
        <div className="px-3 py-2">
          <Link
            to="/dashboard"
            title={collapsed ? "Back to App" : undefined}
            className="flex items-center gap-3 px-2 py-2 rounded-md text-sm text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground transition-colors"
          >
            <ArrowLeft className="h-4 w-4 flex-shrink-0" />
            <span
              className={cn(
                "whitespace-nowrap transition-opacity duration-200",
                collapsed ? "opacity-0" : "opacity-100"
              )}
            >
              Back to App
            </span>
          </Link>
        </div>

        {/* Nav */}
        <nav className="flex-1 px-3 py-2 overflow-y-auto">
          <p
            className={cn(
              "px-2 text-[10px] font-semibold tracking-widest text-muted-foreground uppercase mb-1 transition-opacity duration-200",
              collapsed ? "opacity-0 h-0" : "opacity-100 h-4"
            )}
          >
            ADMIN
          </p>
          <ul className="space-y-1">
            {ADMIN_NAV.map(({ to, label, icon: Icon }) => {
              const isActive =
                to === "/admin"
                  ? location.pathname === "/admin"
                  : location.pathname.startsWith(to);
              return (
                <li key={to}>
                  <Link
                    to={to}
                    title={collapsed ? label : undefined}
                    className={cn(
                      "flex items-center gap-3 px-2 py-2.5 rounded-md text-sm transition-colors",
                      isActive
                        ? "bg-sidebar-accent text-sidebar-accent-foreground font-semibold"
                        : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
                    )}
                  >
                    <Icon className="h-4 w-4 flex-shrink-0" />
                    <span
                      className={cn(
                        "whitespace-nowrap transition-opacity duration-200",
                        collapsed ? "opacity-0" : "opacity-100"
                      )}
                    >
                      {label}
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>

        {/* Footer — user + logout */}
        <div className="px-3 py-3 border-t space-y-1">
          {user && (
            <div
              className={cn(
                "flex items-center gap-3 px-2 py-1.5 transition-opacity duration-200",
                collapsed ? "opacity-0" : "opacity-100"
              )}
            >
              <div className="w-7 h-7 rounded-full bg-primary/15 flex items-center justify-center text-primary text-xs font-bold flex-shrink-0">
                {user.name.charAt(0).toUpperCase()}
              </div>
              <div className="overflow-hidden">
                <p className="text-xs font-semibold truncate">{user.name}</p>
                <p className="text-[10px] text-muted-foreground truncate">
                  {user.tenant_id}
                </p>
              </div>
            </div>
          )}
          <button
            onClick={handleLogout}
            title={collapsed ? "Sign Out" : undefined}
            className="w-full flex items-center gap-3 px-2 py-2 rounded-md text-sm text-muted-foreground hover:bg-destructive/10 hover:text-destructive transition-colors"
          >
            <LogOut className="h-4 w-4 flex-shrink-0" />
            <span
              className={cn(
                "whitespace-nowrap transition-opacity duration-200",
                collapsed ? "opacity-0" : "opacity-100"
              )}
            >
              Sign Out
            </span>
          </button>
        </div>
      </aside>

      <main className="flex-1 overflow-auto">
        <div className="container py-6">{children}</div>
      </main>
    </div>
  );
}
