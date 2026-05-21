import { useState, useMemo } from "react";
import { useLogs } from "@/hooks/use-api";
import { Card, CardContent } from "@/components/ui/card";
import { ArrowRight, LogIn, LogOut, MoveRight, CalendarDays, ArrowUpDown, ChevronDown } from "lucide-react";
import { Link } from "react-router-dom";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Calendar } from "@/components/ui/calendar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { format, startOfDay, isToday, isYesterday } from "date-fns";
import { formatTs } from "@/lib/utils";

const typeIcon = { move: MoveRight, enter: LogIn, exit: LogOut };
const typeLabel = { move: "Moved", enter: "Entered", exit: "Exited" };

type SortDir = "newest" | "oldest";

function dayLabel(dayKey: string): string {
  const d = new Date(dayKey);
  if (isToday(d)) return "Today";
  if (isYesterday(d)) return "Yesterday";
  return format(d, "MMMM d, yyyy");
}

export default function Logs() {
  const [selectedDate, setSelectedDate] = useState<Date | undefined>(undefined);
  const [sortDir, setSortDir] = useState<SortDir>("newest");
  const [openDays, setOpenDays] = useState<Set<string>>(new Set());

  const { data: rawLogs = [], isLoading } = useLogs({
    start_date: selectedDate ? format(selectedDate, "yyyy-MM-dd") : undefined,
    limit: 500,
  });

  const sortedLogs = useMemo(() => {
    return [...rawLogs].sort((a, b) => {
      const diff = new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime();
      return sortDir === "newest" ? diff : -diff;
    });
  }, [rawLogs, sortDir]);

  // Group by calendar day
  const grouped = useMemo(() => {
    const map: Record<string, typeof rawLogs> = {};
    for (const log of sortedLogs) {
      const key = startOfDay(new Date(log.timestamp)).toISOString();
      if (!map[key]) map[key] = [];
      map[key].push(log);
    }
    return map;
  }, [sortedLogs]);

  const dayKeys = useMemo(() =>
    Object.keys(grouped).sort((a, b) =>
      sortDir === "newest"
        ? new Date(b).getTime() - new Date(a).getTime()
        : new Date(a).getTime() - new Date(b).getTime()
    ),
    [grouped, sortDir]
  );

  // Auto-open today on first load
  useMemo(() => {
    const todayKey = startOfDay(new Date()).toISOString();
    if (grouped[todayKey]) {
      setOpenDays(new Set([todayKey]));
    }
  }, [Object.keys(grouped).join(",")]);

  function toggleDay(key: string) {
    setOpenDays((prev) => {
      const next = new Set(prev);
      next.has(key) ? next.delete(key) : next.add(key);
      return next;
    });
  }

  const dateLabel = selectedDate ? format(selectedDate, "MMM d, yyyy") : "All dates";

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Movement Logs</h1>
          <p className="text-sm text-muted-foreground mt-1">
            {isLoading ? "Loading..." : `${sortedLogs.length} entries${selectedDate ? ` on ${dateLabel}` : ""}`}
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Popover>
            <PopoverTrigger asChild>
              <Button variant="outline" size="sm" className="h-8 gap-1.5 text-xs font-normal">
                <CalendarDays className="h-3.5 w-3.5" />
                {dateLabel}
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-auto p-0" align="end">
              <Calendar mode="single" selected={selectedDate} onSelect={setSelectedDate} initialFocus />
              {selectedDate && (
                <div className="border-t px-3 py-2">
                  <Button variant="ghost" size="sm" className="w-full text-xs" onClick={() => setSelectedDate(undefined)}>
                    Clear filter
                  </Button>
                </div>
              )}
            </PopoverContent>
          </Popover>

          <Button
            variant="outline" size="sm" className="h-8 gap-1.5 text-xs font-normal"
            onClick={() => setSortDir((d) => d === "newest" ? "oldest" : "newest")}
          >
            <ArrowUpDown className="h-3.5 w-3.5" />
            {sortDir === "newest" ? "Newest" : "Oldest"}
          </Button>
        </div>
      </div>

      {isLoading && (
        <div className="space-y-2">
          {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-16 rounded-lg" />)}
        </div>
      )}

      {!isLoading && sortedLogs.length === 0 && (
        <p className="py-12 text-center text-sm text-muted-foreground">No logs for this date.</p>
      )}

      {!isLoading && (
        <div className="space-y-4">
          {dayKeys.map((dayKey) => {
            const logs = grouped[dayKey];
            const isOpen = openDays.has(dayKey);
            return (
              <Collapsible key={dayKey} open={isOpen} onOpenChange={() => toggleDay(dayKey)}>
                <CollapsibleTrigger className="flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors py-1 w-full text-left">
                  <ChevronDown
                    className="h-4 w-4 transition-transform duration-200 flex-shrink-0"
                    style={{ transform: isOpen ? "rotate(0deg)" : "rotate(-90deg)" }}
                  />
                  {dayLabel(dayKey)}
                  <span className="text-xs font-normal opacity-60 ml-1">{logs.length} entries</span>
                </CollapsibleTrigger>
                <CollapsibleContent className="space-y-2 mt-2">
                  {logs.map((log, i) => {
                    const Icon = typeIcon[log.type];
                    return (
                      <Card key={log.id} className="opacity-0 animate-fade-in" style={{ animationDelay: `${i * 40}ms` }}>
                        <CardContent className="p-4 flex items-center gap-4">
                          <div className="flex h-8 w-8 items-center justify-center rounded-md bg-muted flex-shrink-0">
                            <Icon className="h-4 w-4 text-muted-foreground" />
                          </div>
                          <div className="flex-1 min-w-0">
                            <Link
                              to={log.asset_id ? `/assets/${log.asset_id}` : "#"}
                              className="text-sm font-medium hover:underline truncate block"
                            >
                              {log.asset_name}
                            </Link>
                            <div className="flex items-center gap-1.5 text-xs text-muted-foreground mt-0.5">
                              <span>{typeLabel[log.type]}</span>
                              {log.type === "move" && (
                                <>
                                  <span className="truncate">{log.from_zone}</span>
                                  <ArrowRight className="h-3 w-3 flex-shrink-0" />
                                  <span className="truncate">{log.to_zone}</span>
                                </>
                              )}
                              {log.type === "enter" && <span className="truncate">{log.to_zone}</span>}
                              {log.type === "exit" && <span className="truncate">{log.from_zone}</span>}
                            </div>
                          </div>
                          <span className="text-xs text-muted-foreground tabular-nums font-mono whitespace-nowrap flex-shrink-0">
                            {formatTs(log.timestamp)}
                          </span>
                        </CardContent>
                      </Card>
                    );
                  })}
                </CollapsibleContent>
              </Collapsible>
            );
          })}
        </div>
      )}
    </div>
  );
}
