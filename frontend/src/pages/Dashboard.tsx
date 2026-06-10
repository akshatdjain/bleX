import { useState } from "react";
import { useZones } from "@/hooks/use-api";
import { ZoneCard } from "@/components/ZoneCard";
import { Skeleton } from "@/components/ui/skeleton";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { ChevronDown } from "lucide-react";

export default function Dashboard() {
  const { data: zones = [], isLoading } = useZones();
  const [inactiveOpen, setInactiveOpen] = useState(false);

  const sorted = [...zones].sort((a, b) => b.movement_count - a.movement_count);
  const activeZones   = sorted.filter((z) => z.is_active);
  const inactiveZones = sorted.filter((z) => !z.is_active);

  const totalAssets = zones.reduce((s, z) => s + z.asset_count, 0);
  const totalMovements = zones.reduce((s, z) => s + z.movement_count, 0);

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">Zones</h1>
        <p className="text-sm text-muted-foreground mt-1">
          {totalAssets} assets tracked · {totalMovements} movements (24h)
        </p>
      </div>

      {isLoading && (
        <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-40 rounded-xl" />
          ))}
        </div>
      )}

      {!isLoading && activeZones.length > 0 && (
        <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
          {activeZones.map((zone, i) => (
            <ZoneCard key={zone.id} zone={zone} index={i} />
          ))}
        </div>
      )}

      {!isLoading && activeZones.length === 0 && (
        <div className="py-12 text-center text-muted-foreground">
          <p className="text-sm">No active zones with assets.</p>
        </div>
      )}

      {!isLoading && inactiveZones.length > 0 && (
        <Collapsible open={inactiveOpen} onOpenChange={setInactiveOpen}>
          <CollapsibleTrigger className="flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors py-1">
            <ChevronDown
              className="h-4 w-4 transition-transform duration-200"
              style={{ transform: inactiveOpen ? "rotate(0deg)" : "rotate(-90deg)" }}
            />
            Inactive Zones ({inactiveZones.length})
          </CollapsibleTrigger>
          <CollapsibleContent className="mt-4">
            <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
              {inactiveZones.map((zone, i) => (
                <ZoneCard key={zone.id} zone={zone} index={activeZones.length + i} />
              ))}
            </div>
          </CollapsibleContent>
        </Collapsible>
      )}
    </div>
  );
}
