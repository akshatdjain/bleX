import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { listTenants, getTenantEvents, TenantEvent } from "@/lib/tenants";
import { Card, CardContent } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

export default function AuditLogPage() {
  const tenants = useQuery({ queryKey: ["admin-tenants"], queryFn: listTenants });
  const [tenantFilter, setTenantFilter] = useState<string>("all");

  const events = useQuery({
    queryKey: ["audit-events", tenantFilter],
    queryFn: async () => {
      if (tenantFilter !== "all") {
        return getTenantEvents(tenantFilter, 100);
      }
      // No global endpoint — fan out across all tenants and merge.
      const all: (TenantEvent & { tenant_id: string })[] = [];
      const list = tenants.data ?? [];
      for (const t of list) {
        try {
          const evs = await getTenantEvents(t.tenant_id, 25);
          for (const e of evs) {
            all.push({ ...e, tenant_id: t.tenant_id });
          }
        } catch {
          /* ignore */
        }
      }
      all.sort((a, b) => (a.created_at < b.created_at ? 1 : -1));
      return all;
    },
    enabled: !!tenants.data,
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Audit Log</h1>
          <p className="text-sm text-muted-foreground">
            Tenant lifecycle and admin actions
          </p>
        </div>

        <Select value={tenantFilter} onValueChange={setTenantFilter}>
          <SelectTrigger className="w-[220px]">
            <SelectValue placeholder="Filter by tenant" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All tenants</SelectItem>
            {(tenants.data ?? []).map((t) => (
              <SelectItem key={t.tenant_id} value={t.tenant_id}>
                {t.name} ({t.tenant_id})
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Card>
        <CardContent className="p-0">
          <table className="w-full text-sm">
            <thead className="border-b bg-muted/40 text-xs uppercase tracking-wider text-muted-foreground">
              <tr>
                <th className="text-left px-4 py-3 font-semibold">Time</th>
                <th className="text-left px-4 py-3 font-semibold">Tenant</th>
                <th className="text-left px-4 py-3 font-semibold">Event</th>
                <th className="text-left px-4 py-3 font-semibold">Actor</th>
                <th className="text-left px-4 py-3 font-semibold">Payload</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {events.isLoading ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-muted-foreground">
                    Loading…
                  </td>
                </tr>
              ) : !events.data || events.data.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-muted-foreground">
                    No events recorded.
                  </td>
                </tr>
              ) : (
                events.data.map((e) => (
                  <tr key={`${(e as any).tenant_id}-${e.id}`} className="hover:bg-muted/30">
                    <td className="px-4 py-2 text-xs text-muted-foreground whitespace-nowrap">
                      {new Date(e.created_at).toLocaleString()}
                    </td>
                    <td className="px-4 py-2 font-mono text-xs">
                      {(e as any).tenant_id ?? tenantFilter}
                    </td>
                    <td className="px-4 py-2">
                      <span className="px-2 py-0.5 rounded-md bg-primary/10 text-primary text-[10px] font-semibold uppercase">
                        {e.event_type}
                      </span>
                    </td>
                    <td className="px-4 py-2 text-xs">{e.actor}</td>
                    <td className="px-4 py-2 font-mono text-xs text-muted-foreground max-w-md truncate">
                      {Object.keys(e.payload || {}).length > 0
                        ? JSON.stringify(e.payload)
                        : "—"}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  );
}
