import { useQuery } from "@tanstack/react-query";
import { Users, Activity, AlertCircle, CheckCircle2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { listTenants } from "@/lib/tenants";
import { Link } from "react-router-dom";

export default function AdminOverview() {
  const { data: tenants = [], isLoading } = useQuery({
    queryKey: ["admin-tenants"],
    queryFn: listTenants,
  });

  const total = tenants.length;
  const active = tenants.filter((t) => t.status === "active").length;
  const suspended = tenants.filter((t) => t.status === "suspended").length;
  const cloud = tenants.filter((t) => t.mode === "cloud").length;
  const local = tenants.filter((t) => t.mode === "local").length;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Admin Overview</h1>
        <p className="text-sm text-muted-foreground">
          Tenant operations dashboard for BleX
        </p>
      </div>

      {/* Stat tiles */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          label="Total Tenants"
          value={total}
          icon={Users}
          loading={isLoading}
        />
        <StatCard
          label="Active"
          value={active}
          icon={CheckCircle2}
          tone="success"
          loading={isLoading}
        />
        <StatCard
          label="Suspended"
          value={suspended}
          icon={AlertCircle}
          tone="warn"
          loading={isLoading}
        />
        <StatCard
          label="Cloud / Local"
          value={`${cloud} / ${local}`}
          icon={Activity}
          loading={isLoading}
        />
      </div>

      {/* Quick actions */}
      <Card>
        <CardHeader>
          <CardTitle>Quick Actions</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-3">
          <Link
            to="/admin/tenants"
            className="inline-flex items-center gap-2 rounded-md bg-primary text-primary-foreground px-4 py-2 text-sm font-semibold hover:opacity-90 transition-opacity"
          >
            <Users className="h-4 w-4" />
            Manage Tenants
          </Link>
          <Link
            to="/admin/audit"
            className="inline-flex items-center gap-2 rounded-md border px-4 py-2 text-sm font-semibold hover:bg-accent transition-colors"
          >
            <ScrollIcon />
            View Audit Log
          </Link>
        </CardContent>
      </Card>

      {/* Recent tenants */}
      <Card>
        <CardHeader>
          <CardTitle>Recent Tenants</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-muted-foreground">Loading…</p>
          ) : tenants.length === 0 ? (
            <p className="text-sm text-muted-foreground">No tenants yet.</p>
          ) : (
            <ul className="divide-y">
              {tenants.slice(0, 5).map((t) => (
                <li
                  key={t.tenant_id}
                  className="flex items-center justify-between py-2"
                >
                  <div>
                    <p className="text-sm font-medium">{t.name}</p>
                    <p className="text-xs text-muted-foreground font-mono">
                      {t.tenant_id} · {t.mode || "—"} · {t.plan}
                    </p>
                  </div>
                  <StatusBadge status={t.status} />
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function StatCard({
  label,
  value,
  icon: Icon,
  tone = "default",
  loading,
}: {
  label: string;
  value: number | string;
  icon: any;
  tone?: "default" | "success" | "warn";
  loading?: boolean;
}) {
  const toneCls =
    tone === "success"
      ? "text-emerald-600 bg-emerald-50 dark:bg-emerald-950/40"
      : tone === "warn"
      ? "text-amber-600 bg-amber-50 dark:bg-amber-950/40"
      : "text-primary bg-primary/10";
  return (
    <Card>
      <CardContent className="p-5 flex items-center gap-4">
        <div className={`w-11 h-11 rounded-lg flex items-center justify-center ${toneCls}`}>
          <Icon className="h-5 w-5" />
        </div>
        <div>
          <p className="text-xs uppercase tracking-wider text-muted-foreground font-medium">
            {label}
          </p>
          <p className="text-2xl font-bold mt-0.5">
            {loading ? "…" : value}
          </p>
        </div>
      </CardContent>
    </Card>
  );
}

export function StatusBadge({ status }: { status: string }) {
  const cls =
    status === "active"
      ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300"
      : status === "suspended"
      ? "bg-amber-100 text-amber-700 dark:bg-amber-950/60 dark:text-amber-300"
      : "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300";
  return (
    <span
      className={`px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide ${cls}`}
    >
      {status}
    </span>
  );
}

function ScrollIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M8 21h12a2 2 0 0 0 2-2v-2H10v2a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v3h4" />
      <path d="M19 17V5a2 2 0 0 0-2-2H4" />
    </svg>
  );
}
