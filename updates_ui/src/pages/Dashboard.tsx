import { getActiveZones, zones } from "@/lib/data";
import { ZoneCard } from "@/components/ZoneCard";
import { ChevronRight, Box } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";

export default function Dashboard() {
  const activeZones = getActiveZones();
  const totalAssets = zones.reduce((s, z) => s + z.assetCount, 0);
  const totalMovements = zones.reduce((s, z) => s + z.movementCount, 0);

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

        {/* Visualize CTA */}
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

      {/* Active zones grid — top 4 by movement */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {activeZones.map((zone, i) => (
          <ZoneCard key={zone.id} zone={zone} index={i} />
        ))}
      </div>

      {/* All zones link */}
      {/* All zones scroll link */}
      <div className="pt-2">
        <button
          onClick={() => document.getElementById("all-zones")?.scrollIntoView({ behavior: "smooth" })}
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          View all {zones.length} zones
          <ChevronRight className="h-3.5 w-3.5" />
        </button>
      </div>

      {/* All zones list */}
      <div id="all-zones">
        <h2 className="text-sm font-medium text-muted-foreground mb-3">All Zones</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {zones.map((zone, i) => (
            <ZoneCard key={zone.id} zone={zone} index={i + 4} />
          ))}
        </div>
      </div>
    </div>
  );
}
