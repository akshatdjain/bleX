import { useState, useEffect, useRef } from "react";
import { useHealthSummary, useScanners } from "@/hooks/use-api";
import { useIsMobile } from "@/hooks/use-mobile";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet";
import { StatusDot } from "@/components/StatusDot";
import { cn } from "@/lib/utils";
import { ChevronDown } from "lucide-react";

function timeAgoShort(ts: string | null | undefined): string {
  if (!ts) return "never";
  const diff = Math.floor((Date.now() - new Date(ts).getTime()) / 1000);
  if (diff < 60) return `${diff}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  return `${Math.floor(diff / 3600)}h ago`;
}

export function HealthBar() {
  const { data: health, dataUpdatedAt } = useHealthSummary();
  const { data: scanners = [] } = useScanners();
  const [sheetOpen, setSheetOpen] = useState(false);
  const isMobile = useIsMobile();
  const [updatedAgo, setUpdatedAgo] = useState("just now");

  useEffect(() => {
    const update = () => {
      if (!dataUpdatedAt) return;
      const diff = Math.floor((Date.now() - dataUpdatedAt) / 1000);
      setUpdatedAgo(diff < 5 ? "just now" : `${diff}s ago`);
    };
    update();
    const id = setInterval(update, 1000);
    return () => clearInterval(id);
  }, [dataUpdatedAt]);

  if (!health) return null;

  const online = health.scanners?.online ?? 0;
  const total = health.scanners?.total ?? 0;
  const offline = total - online;
  const assetCount = health.beacons?.alive ?? 0;

  const dotClass =
    offline === 0 ? "bg-green-500" : offline === 1 ? "bg-yellow-400" : "bg-red-500";

  if (isMobile) {
    return (
      <>
        <button
          onClick={() => setSheetOpen(true)}
          className="flex items-center gap-1.5 px-2 py-1 text-xs text-muted-foreground hover:text-foreground transition-colors rounded-md"
        >
          <span className={cn("inline-block w-2 h-2 rounded-full flex-shrink-0", dotClass)} />
          <span className="tabular-nums font-medium">{online}/{total}</span>
          <span className="opacity-50">·</span>
          <span className="tabular-nums">{assetCount}</span>
        </button>
        <ScannerSheet open={sheetOpen} onOpenChange={setSheetOpen} scanners={scanners} online={online} total={total} />
      </>
    );
  }

  return (
    <>
      <button
        onClick={() => setSheetOpen(true)}
        className="flex items-center gap-2 px-2.5 py-1.5 text-xs text-muted-foreground hover:text-foreground hover:bg-muted/60 rounded-md transition-colors"
      >
        <span className={cn("inline-block w-2 h-2 rounded-full flex-shrink-0", dotClass)} />
        <span className="font-medium tabular-nums">{online}/{total} Scanners</span>
        <span className="opacity-40">·</span>
        <span className="tabular-nums">{assetCount} Asset{assetCount !== 1 ? "s" : ""}</span>
        <span className="opacity-40">·</span>
        <span className="text-[11px] opacity-60">Updated {updatedAgo}</span>
        <ChevronDown className="h-3 w-3 opacity-40 ml-0.5" />
      </button>
      <ScannerSheet open={sheetOpen} onOpenChange={setSheetOpen} scanners={scanners} online={online} total={total} />
    </>
  );
}

function ScannerSheet({ open, onOpenChange, scanners, online, total }: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  scanners: any[];
  online: number;
  total: number;
}) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-80">
        <SheetHeader className="pb-4">
          <SheetTitle className="text-base">Scanner Health</SheetTitle>
          <SheetDescription className="text-xs text-muted-foreground">
            {online} of {total} scanners online
          </SheetDescription>
        </SheetHeader>
        <div className="space-y-2 overflow-y-auto">
          {scanners.length === 0 && (
            <p className="text-sm text-muted-foreground text-center py-8">No scanners registered.</p>
          )}
          {scanners.map((scanner) => (
            <div key={scanner.id} className="flex items-center justify-between rounded-lg border border-border/40 px-3 py-2.5">
              <div className="flex items-center gap-2 min-w-0">
                <StatusDot status={scanner.status} />
                <div className="min-w-0">
                  <p className="text-sm font-medium truncate">{scanner.name || scanner.mac_id}</p>
                  <p className="text-[11px] text-muted-foreground font-mono">
                    {scanner.last_heartbeat ? timeAgoShort(scanner.last_heartbeat) : "never"}
                  </p>
                </div>
              </div>
              <span className={cn(
                "text-[10px] font-medium px-1.5 py-0.5 rounded capitalize",
                scanner.status === "online" ? "bg-green-500/10 text-green-600" :
                scanner.status === "idle" ? "bg-yellow-400/10 text-yellow-600" :
                "bg-muted text-muted-foreground"
              )}>
                {scanner.status}
              </span>
            </div>
          ))}
        </div>
      </SheetContent>
    </Sheet>
  );
}
