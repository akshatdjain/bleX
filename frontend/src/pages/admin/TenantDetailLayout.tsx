import { Link, NavLink, Outlet, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ChevronRight, ArrowLeft } from "lucide-react";
import { getTenant } from "@/lib/tenants";
import { StatusBadge } from "./AdminOverview";
import { cn } from "@/lib/utils";

const TABS = [
  { to: "overview", label: "Overview" },
  { to: "devices", label: "Devices" },
  { to: "users", label: "Users" },
  { to: "audit", label: "Audit" },
  { to: "settings", label: "Settings" },
];

export default function TenantDetailLayout() {
  const { tenantId = "" } = useParams<{ tenantId: string }>();
  const { data: tenant, isLoading, error } = useQuery({
    queryKey: ["tenant", tenantId],
    queryFn: () => getTenant(tenantId),
    enabled: !!tenantId,
  });

  return (
    <div className="space-y-5">
      {/* Breadcrumbs */}
      <nav className="flex items-center text-xs text-muted-foreground">
        <Link to="/admin" className="hover:text-foreground transition-colors">
          Admin
        </Link>
        <ChevronRight className="h-3.5 w-3.5 mx-1.5 opacity-50" />
        <Link to="/admin/tenants" className="hover:text-foreground transition-colors">
          Tenants
        </Link>
        <ChevronRight className="h-3.5 w-3.5 mx-1.5 opacity-50" />
        <span className="font-mono text-foreground">{tenantId}</span>
      </nav>

      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-center gap-3 min-w-0">
          <Link
            to="/admin/tenants"
            className="flex items-center justify-center w-8 h-8 rounded-md border hover:bg-muted transition-colors flex-shrink-0"
            title="Back to tenants"
          >
            <ArrowLeft className="h-4 w-4" />
          </Link>
          <div className="min-w-0">
            <h1 className="text-2xl font-bold tracking-tight truncate">
              {isLoading ? "Loading…" : tenant?.name ?? "Unknown tenant"}
            </h1>
            <p className="text-xs text-muted-foreground font-mono mt-0.5">
              {tenantId}
              {tenant?.plan ? ` · ${tenant.plan}` : ""}
              {tenant?.mode ? ` · ${tenant.mode}` : ""}
            </p>
          </div>
        </div>
        {tenant && <StatusBadge status={tenant.status} />}
      </div>

      {/* Tabs */}
      <div className="border-b">
        <nav className="flex gap-1">
          {TABS.map((t) => (
            <NavLink
              key={t.to}
              to={t.to}
              className={({ isActive }) =>
                cn(
                  "px-4 py-2.5 text-sm font-medium border-b-2 transition-colors -mb-px",
                  isActive
                    ? "border-primary text-foreground"
                    : "border-transparent text-muted-foreground hover:text-foreground hover:border-muted-foreground/30"
                )
              }
            >
              {t.label}
            </NavLink>
          ))}
        </nav>
      </div>

      {/* Tab content */}
      <div>
        {error ? (
          <div className="p-6 text-sm text-destructive">
            Failed to load tenant: {(error as Error).message}
          </div>
        ) : (
          <Outlet />
        )}
      </div>
    </div>
  );
}
