import { useEffect, useRef, useState } from "react";
import { ArrowRight, Zap } from "lucide-react";
import { cn } from "@/lib/utils";
import { useLogs } from "@/hooks/use-api";
import type { LogEntry } from "@/lib/api";

export function MovementToast() {
  const [current, setCurrent] = useState<Pick<LogEntry, "id" | "asset_name" | "from_zone" | "to_zone"> | null>(null);
  const [visible, setVisible] = useState(false);
  const seenIds = useRef<Set<string>>(new Set());
  const hideTimer = useRef<ReturnType<typeof setTimeout>>();

  // Poll the last 20 log entries every 5s for new movements
  const { data: logs = [] } = useLogs({ limit: 20 });

  useEffect(() => {
    if (logs.length === 0) return;

    // On first load, seed the seen set without showing toasts
    if (seenIds.current.size === 0) {
      logs.forEach((l) => seenIds.current.add(l.id));
      return;
    }

    // Find new entries not yet shown
    const newEntries = logs.filter((l) => !seenIds.current.has(l.id) && l.type === "move");
    newEntries.forEach((l) => seenIds.current.add(l.id));

    if (newEntries.length === 0) return;

    // Show the newest one
    const entry = newEntries[0];
    setCurrent({ id: entry.id, asset_name: entry.asset_name, from_zone: entry.from_zone, to_zone: entry.to_zone });
    setVisible(true);

    if (hideTimer.current) clearTimeout(hideTimer.current);
    hideTimer.current = setTimeout(() => setVisible(false), 3500);
  }, [logs]);

  if (!current) return null;

  return (
    <div
      className={cn(
        "fixed bottom-6 right-6 z-50 max-w-xs rounded-lg border bg-card shadow-lg",
        "transition-all duration-500 ease-out",
        visible
          ? "opacity-100 translate-y-0 translate-x-0"
          : "opacity-0 translate-y-4 translate-x-2 pointer-events-none"
      )}
    >
      <div className="flex items-center gap-3 px-4 py-3">
        <div className="flex h-7 w-7 items-center justify-center rounded-md bg-primary/10 shrink-0">
          <Zap className="h-3.5 w-3.5 text-primary" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium leading-tight truncate">{current.asset_name}</p>
          <div className="flex items-center gap-1 text-xs text-muted-foreground mt-0.5">
            <span className="truncate">{current.from_zone}</span>
            <ArrowRight className="h-3 w-3 shrink-0" />
            <span className="truncate">{current.to_zone}</span>
          </div>
        </div>
      </div>
    </div>
  );
}
