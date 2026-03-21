import { Link } from "react-router-dom";
import { Card, CardContent } from "@/components/ui/card";
import { BeaconIcon } from "@/components/BeaconIcon";
import { Activity } from "lucide-react";
import { cn } from "@/lib/utils";
import type { Zone, Asset } from "@/lib/api";
import type { BeaconShape, AssetStatus } from "@/lib/api";

interface ZoneCardProps {
  zone: Zone & { assets?: Asset[] };
  index: number;
}

export function ZoneCard({ zone, index }: ZoneCardProps) {
  const assets = zone.assets ?? [];

  return (
    <Link to={`/zones/${zone.id}`} className="group block">
      <Card
        className={cn(
          "relative overflow-hidden transition-shadow duration-300 hover:shadow-md",
          "opacity-0 animate-fade-in"
        )}
        style={{ animationDelay: `${index * 80}ms` }}
      >
        {/* Activity indicator bar */}
        {zone.is_active && (
          <div className="absolute top-0 left-0 right-0 h-0.5 bg-primary/60" />
        )}

        <CardContent className="p-5">
          <div className="flex items-start justify-between mb-4">
            <div>
              <h3 className="text-sm font-semibold">{zone.name}</h3>
              <p className="text-xs text-muted-foreground mt-0.5 font-mono">{zone.scanner_id}</p>
            </div>
            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <Activity className="h-3.5 w-3.5" />
              <span className="tabular-nums">{zone.movement_count}</span>
            </div>
          </div>

          {/* Assets in zone (shown when zone detail embeds assets) */}
          <div className="flex items-center gap-2 mb-4">
            {assets.length > 0 ? (
              assets.slice(0, 5).map((asset) => (
                <BeaconIcon key={asset.id} shape={asset.shape as BeaconShape} status={asset.status as AssetStatus} size={28} />
              ))
            ) : (
              <span className="text-xs text-muted-foreground italic">No assets</span>
            )}
          </div>

          <div className="flex items-center justify-between">
            <span className="text-xs text-muted-foreground">
              {zone.asset_count} asset{zone.asset_count !== 1 ? "s" : ""}
            </span>
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
