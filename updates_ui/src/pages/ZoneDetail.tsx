import { useParams, Link } from "react-router-dom";
import { getZoneById, getAssetsForZone, getScannersForZone } from "@/lib/data";
import { BeaconIcon } from "@/components/BeaconIcon";
import { StatusDot } from "@/components/StatusDot";
import { Card, CardContent } from "@/components/ui/card";
import { ArrowLeft, ArrowRight, Signal, Radio } from "lucide-react";

export default function ZoneDetail() {
  const { zoneId } = useParams<{ zoneId: string }>();
  const zone = getZoneById(zoneId || "");
  const assets = getAssetsForZone(zoneId || "");
  const scanners = getScannersForZone(zoneId || "");

  if (!zone) {
    return (
      <div className="py-16 text-center text-muted-foreground">
        <p>Zone not found.</p>
        <Link to="/" className="text-primary text-sm mt-2 inline-block hover:underline">Back to dashboard</Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Link to="/" className="rounded-md p-1.5 hover:bg-muted transition-colors">
          <ArrowLeft className="h-4 w-4" />
        </Link>
        <div>
          <h1 className="text-lg font-semibold">{zone.name}</h1>
          <div className="flex items-center gap-1.5 mt-0.5">
            {scanners.map((scn) => (
              <span
                key={scn.id}
                className={`inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] font-mono tracking-wide ${
                  scn.status === "online"
                    ? "bg-primary/10 text-primary"
                    : "bg-destructive/10 text-destructive"
                }`}
              >
                <Radio className="h-2.5 w-2.5" />
                {scn.name}
              </span>
            ))}
          </div>
        </div>
      </div>

      {/* Stats */}
      <div className="flex gap-6 text-sm">
        <div>
          <span className="text-muted-foreground">Assets</span>
          <p className="text-lg font-semibold tabular-nums">{assets.length}</p>
        </div>
        <div>
          <span className="text-muted-foreground">Movements</span>
          <p className="text-lg font-semibold tabular-nums">{zone.movementCount}</p>
        </div>
      </div>

      {/* Asset list */}
      <div className="space-y-2">
        {assets.length === 0 && (
          <p className="text-sm text-muted-foreground italic py-8 text-center">No assets in this zone right now.</p>
        )}
        {assets.map((asset, i) => (
          <Link key={asset.id} to={`/assets/${asset.id}`} className="block group">
            <Card
              className="transition-shadow hover:shadow-md opacity-0 animate-fade-in"
              style={{ animationDelay: `${i * 60}ms` }}
            >
              <CardContent className="p-4 flex items-center gap-4">
                <BeaconIcon shape={asset.shape} status={asset.status} size={36} />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium truncate">{asset.name}</span>
                    <StatusDot status={asset.status} className="relative flex-shrink-0" />
                  </div>
                  <div className="flex items-center gap-3 text-xs text-muted-foreground mt-0.5">
                    <span className="font-mono">{asset.rssi} dBm</span>
                    <span>{asset.battery}%</span>
                    <span>{asset.lastSeen}</span>
                  </div>
                </div>
                <Signal className="h-4 w-4 text-muted-foreground flex-shrink-0" />
                <ArrowRight className="h-3.5 w-3.5 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity" />
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
