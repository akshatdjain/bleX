import { useParams, Link } from "react-router-dom";
import { useZone } from "@/hooks/use-api";
import { BeaconIcon } from "@/components/BeaconIcon";
import { inferShapeFromName } from "@/lib/data";
import { StatusDot } from "@/components/StatusDot";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ArrowLeft, ArrowRight, Activity, Radio } from "lucide-react";
import { cn } from "@/lib/utils";

export default function ZoneDetail() {
  const { zoneId } = useParams<{ zoneId: string }>();
  const { data: zone, isLoading } = useZone(zoneId || "");

  if (isLoading) {
    return (
      <div className="space-y-6 max-w-2xl">
        <Skeleton className="h-9 w-48" />
        <div className="flex gap-4">
          <Skeleton className="h-16 w-24 rounded-xl" />
          <Skeleton className="h-16 w-24 rounded-xl" />
        </div>
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-16 rounded-lg" />)}
        </div>
      </div>
    );
  }

  if (!zone) {
    return (
      <div className="py-16 text-center text-muted-foreground">
        <p>Zone not found.</p>
        <Link to="/dashboard" className="text-primary text-sm mt-2 inline-block hover:underline">
          Back to dashboard
        </Link>
      </div>
    );
  }

  const assets = zone.assets ?? [];
  const scanners = zone.scanners ?? [];

  return (
    <div className="space-y-6 max-w-2xl">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Link to="/dashboard" className="rounded-md p-1.5 hover:bg-muted transition-colors">
          <ArrowLeft className="h-4 w-4" />
        </Link>
        <div>
          <h1 className="text-lg font-semibold">{zone.name}</h1>
          {zone.description && (
            <p className="text-xs text-muted-foreground">{zone.description}</p>
          )}
        </div>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-2 gap-3">
        <Card>
          <CardContent className="p-4">
            <div className="flex items-center gap-1.5 text-muted-foreground mb-1">
              <Radio className="h-3.5 w-3.5" />
              <span className="text-xs">Assets</span>
            </div>
            <p className="text-2xl font-bold tabular-nums">{assets.length}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <div className="flex items-center gap-1.5 text-muted-foreground mb-1">
              <Activity className="h-3.5 w-3.5" />
              <span className="text-xs">Movements</span>
            </div>
            <p className="text-2xl font-bold tabular-nums">{zone.movement_count}</p>
          </CardContent>
        </Card>
      </div>

      {/* Scanners */}
      {scanners.length > 0 && (
        <div>
          <h2 className="text-sm font-medium text-muted-foreground mb-2">Scanners</h2>
          <div className="flex flex-wrap gap-2">
            {scanners.map((scn: any) => (
              <div
                key={scn.id}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-mono border",
                  scn.status === "active"  ? "bg-green-500/10 border-green-500/30 text-green-700 dark:text-green-400"
                  : scn.status === "idle"  ? "bg-yellow-400/10 border-yellow-400/30 text-yellow-700 dark:text-yellow-400"
                  : "bg-muted border-border text-muted-foreground"
                )}
              >
                <span className={cn(
                  "w-1.5 h-1.5 rounded-full flex-shrink-0",
                  scn.status === "active" ? "bg-green-500" : scn.status === "idle" ? "bg-yellow-400" : "bg-muted-foreground"
                )} />
                {scn.name || scn.mac}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Assets */}
      <div>
        <h2 className="text-sm font-medium text-muted-foreground mb-3">
          {assets.length > 0 ? `${assets.length} asset${assets.length !== 1 ? "s" : ""} in zone` : "No assets in this zone"}
        </h2>
        <div className="space-y-2">
          {assets.map((asset: any, i: number) => (
            <Link key={asset.id} to={`/assets/${asset.id}`} className="block group">
              <Card
                className="transition-shadow hover:shadow-md opacity-0 animate-fade-in"
                style={{ animationDelay: `${i * 60}ms` }}
              >
                <CardContent className="p-4 flex items-center gap-4">
                  <BeaconIcon shape={inferShapeFromName(asset.name)} status={asset.status} size={36} />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium truncate">{asset.name}</span>
                      <StatusDot status={asset.status} className="relative flex-shrink-0" />
                    </div>
                    <div className="flex items-center gap-3 text-xs text-muted-foreground mt-0.5">
                      {asset.rssi && <span className="font-mono">{asset.rssi} dBm</span>}
                      {asset.battery !== undefined && asset.battery !== null && <span>{asset.battery}%</span>}
                      <span>{asset.last_seen_relative}</span>
                    </div>
                  </div>
                  <ArrowRight className="h-3.5 w-3.5 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0" />
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
