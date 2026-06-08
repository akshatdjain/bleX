import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Card, CardContent } from "@/components/ui/card";
import { getTenantEvents } from "@/lib/tenants";

export default function TenantAuditTab() {
  const { tenantId = "" } = useParams<{ tenantId: string }>();
  const { data: events = [], isLoading } = useQuery({
    queryKey: ["tenant-audit", tenantId],
    queryFn: () => getTenantEvents(tenantId, 200),
    enabled: !!tenantId,
  });

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold">Audit Log</h2>
        <p className="text-xs text-muted-foreground">
          Lifecycle and admin actions for this tenant
        </p>
      </div>

      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b bg-muted/40 text-xs uppercase tracking-wider text-muted-foreground">
                <tr>
                  <th className="text-left px-4 py-3 font-semibold">Time</th>
                  <th className="text-left px-4 py-3 font-semibold">Event</th>
                  <th className="text-left px-4 py-3 font-semibold">Actor</th>
                  <th className="text-left px-4 py-3 font-semibold">Payload</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {isLoading ? (
                  <tr>
                    <td colSpan={4} className="px-4 py-8 text-center text-muted-foreground">
                      Loading…
                    </td>
                  </tr>
                ) : events.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-4 py-8 text-center text-muted-foreground">
                      No events yet.
                    </td>
                  </tr>
                ) : (
                  events.map((e) => (
                    <tr key={e.id} className="hover:bg-muted/30">
                      <td className="px-4 py-2 text-xs text-muted-foreground whitespace-nowrap">
                        {new Date(e.created_at).toLocaleString()}
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
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
