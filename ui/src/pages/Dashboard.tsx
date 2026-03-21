import { useZones } from "@/hooks/use-api";
import { ZoneCard } from "@/components/ZoneCard";
import { Link } from "react-router-dom";
import { ChevronRight, Box } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

export default function Dashboard() {
  const { data: zones = [], isLoading } = useZones();

  const activeZones = [...zones]
    .filter((z) => z.is_active)
    .sort((a, b) => b.movement_count - a.movement_count)
    .slice(0, 4);

  const totalAssets = zones.reduce((s, z) => s + z.asset_count, 0);
  const totalMovements = zones.reduce((s, z) => s + z.movement_count, 0);

  return (
    <div className="space-y-8">
      {/* Page header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-balance">Active Zones</h1>
          <p className="text-sm text-muted-foreground mt-1">
            {totalAssets} assets tracked · {totalMovements} movements today
          </p>
        </div>

        {/* 3D Visualize CTA (coming soon) */}
        <Card className="border-dashed opacity-80 hover:opacity-100 transition-opacity cursor-default">
          <CardContent className="flex items-center gap-2.5 px-4 py-2.5">
            <Box className="h-4 w-4 text-muted-foreground" />
            <div>
              <p className="text-xs font-medium">3D Visualize</p>
              <p className="text-[11px] text-muted-foreground">Coming soon</p>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Loading skeletons */}
      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-36 rounded-xl" />
          ))}
        </div>
      )}

      {/* Active zones grid */}
      {!isLoading && activeZones.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {activeZones.map((zone, i) => (
            <ZoneCard key={zone.id} zone={zone as any} index={i} />
          ))}
        </div>
      )}

      {/* View all zones link */}
      {zones.length > 4 && (
        <div className="pt-2">
          <Link
            to="/zones"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            View all {zones.length} zones
            <ChevronRight className="h-3.5 w-3.5" />
          </Link>
        </div>
      )}

      {/* All zones list */}
      <div>
        <h2 className="text-sm font-medium text-muted-foreground mb-3">All Zones</h2>
        {isLoading ? (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-24 rounded-xl" />
            ))}
          </div>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {zones.map((zone, i) => (
              <ZoneCard key={zone.id} zone={zone as any} index={i + 4} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
