import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Radio, Box, Map, Activity } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { getTenantStats, getTenantEvents } from "@/lib/tenants";

export default function TenantOverviewTab() {
  const { tenantId = "" } = useParams<{ tenantId: string }>();

  const stats = useQuery({
    queryKey: ["tenant-stats", tenantId],
    queryFn: () => getTenantStats(tenantId),
    enabled: !!tenantId,
  });

  const events = useQuery({
    queryKey: ["tenant-events-overview", tenantId],
    queryFn: () => getTenantEvents(tenantId, 10),
    enabled: !!tenantId,
  });

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Tile label="Scanners" value={stats.data?.scanners ?? 0} icon={Radio} loading={stats.isLoading} />
        <Tile label="Assets" value={stats.data?.assets ?? 0} icon={Box} loading={stats.isLoading} />
        <Tile label="Zones" value={stats.data?.zones ?? 0} icon={Map} loading={stats.isLoading} />
        <Tile label="Movements" value={stats.data?.movements ?? 0} icon={Activity} loading={stats.isLoading} />
      </div>

      <Card>
        <CardContent className="p-5">
          <h2 className="text-sm font-semibold mb-3">Recent Activity</h2>
          {events.isLoading ? (
            <p className="text-xs text-muted-foreground">Loading…</p>
          ) : !events.data || events.data.length === 0 ? (
            <p className="text-xs text-muted-foreground">No recent events.</p>
          ) : (
            <ul className="divide-y">
              {events.data.slice(0, 10).map((e) => (
                <li key={e.id} className="py-2 flex items-center justify-between text-xs">
                  <div>
                    <span className="px-2 py-0.5 rounded bg-primary/10 text-primary font-semibold uppercase tracking-wide text-[10px] mr-2">
                      {e.event_type}
                    </span>
                    <span className="text-muted-foreground">by {e.actor}</span>
                  </div>
                  <span className="text-muted-foreground">
                    {new Date(e.created_at).toLocaleString()}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function Tile({
  label,
  value,
  icon: Icon,
  loading,
}: {
  label: string;
  value: number | string;
  icon: any;
  loading?: boolean;
}) {
  return (
    <Card>
      <CardContent className="p-5 flex items-center gap-4">
        <div className="w-11 h-11 rounded-lg bg-primary/10 text-primary flex items-center justify-center">
          <Icon className="h-5 w-5" />
        </div>
        <div>
          <p className="text-xs uppercase tracking-wider text-muted-foreground font-medium">{label}</p>
          <p className="text-2xl font-bold mt-0.5">{loading ? "…" : value}</p>
        </div>
      </CardContent>
    </Card>
  );
}
