import { type Scanner } from "@/lib/api";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from "@/components/ui/sheet";
import { Radio } from "lucide-react";
import { cn } from "@/lib/utils";
import { StatusDot } from "@/components/StatusDot";

interface ScannerSheetProps {
  scanner: Scanner | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ScannerSheet({ scanner, open, onOpenChange }: ScannerSheetProps) {
  if (!scanner) return null;

  const rows = [
    { label: "Name", value: scanner.name },
    { label: "MAC Address", value: scanner.mac },
    { label: "Model / Type", value: scanner.type },
    { label: "Last Heartbeat", value: scanner.last_heartbeat || "Never" },
  ];

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-80 sm:w-96">
        <div className="flex flex-col h-full">
          <SheetHeader className="pb-6">
            <div className="flex items-center gap-2">
              <Radio className="h-4 w-4 text-muted-foreground" />
              <SheetTitle className="text-base">{scanner.name}</SheetTitle>
            </div>
            <SheetDescription className="sr-only">Scanner details for {scanner.mac}</SheetDescription>
            <div className="flex items-center gap-1.5 mt-1">
              <StatusDot status={scanner.is_online ? "active" : "offline"} />
              <span className="text-xs text-muted-foreground capitalize">{scanner.status}</span>
            </div>
          </SheetHeader>

          <div className="space-y-4">
            {rows.map((row) => (
              <div key={row.label} className="flex items-baseline justify-between border-b border-border/40 pb-3">
                <span className="text-xs text-muted-foreground">{row.label}</span>
                <span className="text-sm font-mono tabular-nums">{row.value}</span>
              </div>
            ))}
            
            <div className="pt-4">
              <p className="text-[10px] text-muted-foreground uppercase tracking-widest font-semibold mb-2">Location Context</p>
              <div className="rounded-lg bg-muted/50 p-3">
                <span className="text-xs text-muted-foreground">Assigned Zone</span>
                <p className="text-sm font-medium">{scanner.zone_name}</p>
              </div>
            </div>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
