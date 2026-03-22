import { type Scanner } from "@/lib/data";
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
    { label: "Last Heartbeat", value: scanner.lastHeartbeat },
  ];

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-80 sm:w-96">
        <SheetHeader className="pb-6">
          <div className="flex items-center gap-2">
            <Radio className="h-4 w-4 text-muted-foreground" />
            <SheetTitle className="text-base">{scanner.name}</SheetTitle>
          </div>
          <SheetDescription className="sr-only">Scanner details</SheetDescription>
          <div className="flex items-center gap-1.5 mt-1">
            <StatusDot status={scanner.status === "online" ? "active" : "offline"} />
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
        </div>
      </SheetContent>
    </Sheet>
  );
}
