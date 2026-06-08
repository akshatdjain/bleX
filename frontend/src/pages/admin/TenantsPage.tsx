import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { listTenants, Tenant } from "@/lib/tenants";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Search } from "lucide-react";
import { TenantEditDrawer } from "./TenantEditDrawer";
import { StatusBadge } from "./AdminOverview";

export default function TenantsPage() {
  const { data: tenants = [], isLoading, refetch } = useQuery({
    queryKey: ["admin-tenants"],
    queryFn: listTenants,
  });
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<Tenant | null>(null);

  const filtered = tenants.filter((t) => {
    if (!query) return true;
    const q = query.toLowerCase();
    return (
      t.tenant_id.toLowerCase().includes(q) ||
      t.name.toLowerCase().includes(q) ||
      (t.contact_email ?? "").toLowerCase().includes(q)
    );
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Tenants</h1>
          <p className="text-sm text-muted-foreground">
            {tenants.length} tenant{tenants.length === 1 ? "" : "s"} on BleX
          </p>
        </div>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search tenants…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="pl-9 w-72"
          />
        </div>
      </div>

      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b bg-muted/40 text-xs uppercase tracking-wider text-muted-foreground">
                <tr>
                  <th className="text-left px-4 py-3 font-semibold">Name</th>
                  <th className="text-left px-4 py-3 font-semibold">Tenant ID</th>
                  <th className="text-left px-4 py-3 font-semibold">Mode</th>
                  <th className="text-left px-4 py-3 font-semibold">Plan</th>
                  <th className="text-left px-4 py-3 font-semibold">Status</th>
                  <th className="text-left px-4 py-3 font-semibold">Limits</th>
                  <th className="text-left px-4 py-3 font-semibold">Email</th>
                  <th className="text-left px-4 py-3 font-semibold">Created</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {isLoading ? (
                  <tr>
                    <td colSpan={8} className="px-4 py-8 text-center text-muted-foreground">
                      Loading…
                    </td>
                  </tr>
                ) : filtered.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-4 py-8 text-center text-muted-foreground">
                      {query ? `No tenants match "${query}"` : "No tenants yet."}
                    </td>
                  </tr>
                ) : (
                  filtered.map((t) => (
                    <tr
                      key={t.tenant_id}
                      onClick={() => setSelected(t)}
                      className="hover:bg-muted/40 cursor-pointer transition-colors"
                    >
                      <td className="px-4 py-3 font-medium">{t.name}</td>
                      <td className="px-4 py-3 font-mono text-xs">{t.tenant_id}</td>
                      <td className="px-4 py-3">
                        <ModePill mode={t.mode} />
                      </td>
                      <td className="px-4 py-3">{t.plan}</td>
                      <td className="px-4 py-3">
                        <StatusBadge status={t.status} />
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
                        {t.scanner_limit}s · {t.asset_limit}a
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
                        {t.contact_email || "—"}
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
                        {t.created_at
                          ? new Date(t.created_at).toLocaleDateString()
                          : "—"}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      <TenantEditDrawer
        tenant={selected}
        open={!!selected}
        onClose={() => setSelected(null)}
        onSaved={() => {
          refetch();
          setSelected(null);
        }}
      />
    </div>
  );
}

function ModePill({ mode }: { mode: Tenant["mode"] }) {
  if (!mode) return <span className="text-muted-foreground text-xs">—</span>;
  const cls =
    mode === "cloud"
      ? "bg-blue-100 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300"
      : "bg-purple-100 text-purple-700 dark:bg-purple-950/60 dark:text-purple-300";
  return (
    <span
      className={`px-2 py-0.5 rounded-md text-[10px] font-semibold uppercase tracking-wide ${cls}`}
    >
      {mode}
    </span>
  );
}
