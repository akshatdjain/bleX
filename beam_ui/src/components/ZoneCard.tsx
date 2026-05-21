import { Link, useNavigate } from "react-router-dom";
import { useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { BeaconIcon } from "@/components/BeaconIcon";
import { getAssetsForZone, getScannersForZone, inferShapeFromName, type Zone } from "@/lib/data";
import { useScanners, useAssets } from "@/hooks/use-api";
import { ArrowRight, Activity, Radio } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { ScannerSheet } from "@/components/ScannerSheet";
import type { Scanner } from "@/lib/api";

function rssiBar(rssi?: number | null): string {
  if (rssi == null) return "·";
  if (rssi > -65) return "▄▄▄▄";
  if (rssi > -75) return "▃▃▃";
  if (rssi > -85) return "▂▂";
  return "▁";
}

interface ZoneCardProps {
  zone: Zone;
  index: number;
}

export function ZoneCard({ zone, index }: ZoneCardProps) {
  const { data: allAssets = [] } = useAssets();
  const { data: allScanners = [] } = useScanners();
  const navigate = useNavigate();
  const [selectedScanner, setSelectedScanner] = useState<Scanner | null>(null);

  // Use helpers for data filtering
  const assets = getAssetsForZone(allAssets, zone.id);
  const scanners = getScannersForZone(allScanners, zone.id);

  return (
    <>
      <Link to={`/zones/${zone.id}`} className="group block">
        <Card
          className={cn(
            "relative overflow-hidden transition-shadow duration-300 hover:shadow-md",
            "opacity-0 animate-fade-in"
          )}
          style={{ animationDelay: `${index * 80}ms` }}
        >
          {zone.is_active && (
            <div className="absolute top-0 left-0 right-0 h-0.5 bg-primary/60" />
          )}

          <CardContent className="p-3.5">
            {/* Header: zone name + movement count */}
            <div className="flex items-start justify-between mb-1">
              <div>
                <h3 className="text-sm font-semibold">{zone.name}</h3>
              </div>
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <Activity className="h-3.5 w-3.5" />
                <span className="tabular-nums">{zone.movement_count}</span>
              </div>
            </div>

            {/* Scanner badges — clickable */}
            <div className="flex items-center gap-1.5 mb-4">
              {scanners.map((scn) => (
                <button
                  key={scn.id}
                  onClick={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    setSelectedScanner(scn);
                  }}
                  className={cn(
                    "inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] font-mono tracking-wide",
                    "transition-colors duration-150 cursor-pointer",
                    "hover:ring-1 hover:ring-ring/30",
                    "active:scale-[0.96]",
                    scn.status === "active"
                      ? "bg-status-active/10 text-status-active"
                      : scn.status === "idle"
                      ? "bg-yellow-500/10 text-yellow-600 dark:text-yellow-400"
                      : "bg-destructive/10 text-destructive"
                  )}
                >
                  <Radio className="h-2.5 w-2.5" />
                  {scn.name}
                </button>
              ))}
              {scanners.length === 0 && (
                <span className="text-[10px] text-muted-foreground italic opacity-50">No scanners</span>
              )}
            </div>

            {/* Assets — beacon chips capped at 3 with RSSI bars */}
            <TooltipProvider delayDuration={200}>
              <div className="flex flex-wrap items-center gap-2 mb-4">
                {assets.length > 0 ? (
                  <>
                    {assets.slice(0, 3).map((asset) => (
                      <Tooltip key={asset.id}>
                        <TooltipTrigger asChild>
                          <button
                            onClick={(e) => {
                              e.preventDefault();
                              e.stopPropagation();
                              navigate(`/assets/${asset.id}`);
                            }}
                            className={cn(
                              "flex items-center gap-2 rounded-xl px-2.5 py-1.5",
                              "bg-transparent border",
                              "transition-all duration-200",
                              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                              "active:scale-[0.97]",
                              asset.status === "active"
                                ? "border-status-active/30 bg-status-active/5 hover:bg-status-active/10"
                                : asset.status === "offline"
                                ? "border-muted-foreground/20 hover:bg-muted/50 text-muted-foreground"
                                : "border-border/50 hover:bg-muted/50"
                            )}
                          >
                            <BeaconIcon shape={inferShapeFromName(asset.name)} status={asset.status} size={18} />
                            <div className="flex flex-col text-left">
                              <span className={cn(
                                "text-[11px] font-bold leading-none tracking-wide",
                                asset.status === "offline" ? "text-muted-foreground" : "text-foreground"
                              )}>
                                {asset.name}
                              </span>
                              <span className="text-[9px] text-muted-foreground tabular-nums leading-none mt-1 font-mono">
                                {rssiBar(asset.rssi)}
                              </span>
                            </div>
                          </button>
                        </TooltipTrigger>
                        <TooltipContent side="top" className="text-xs font-mono">
                          {asset.name} · {asset.rssi ?? "?"} dBm · {asset.battery ?? "?"}%
                        </TooltipContent>
                      </Tooltip>
                    ))}
                    {assets.length > 3 && (
                      <span className="text-[10px] font-medium text-muted-foreground px-1.5 py-0.5">
                        +{assets.length - 3} more
                      </span>
                    )}
                  </>
                ) : (
                  <span className="text-xs text-muted-foreground italic">No assets</span>
                )}
              </div>
            </TooltipProvider>

            <div className="flex items-center justify-between mt-auto">
              <span className="text-xs text-muted-foreground font-medium">
                {zone.asset_count} asset{zone.asset_count !== 1 ? "s" : ""}
              </span>
              <ArrowRight className="h-3.5 w-3.5 text-muted-foreground opacity-0 -translate-x-1 transition-all duration-200 group-hover:opacity-100 group-hover:translate-x-0" />
            </div>
          </CardContent>
        </Card>
      </Link>

      <ScannerSheet
        scanner={selectedScanner}
        open={!!selectedScanner}
        onOpenChange={(open) => { if (!open) setSelectedScanner(null); }}
      />
    </>
  );
}
