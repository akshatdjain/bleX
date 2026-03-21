import { useState, useMemo } from "react";
import { useLogs } from "@/hooks/use-api";
import { Card, CardContent } from "@/components/ui/card";
import { ArrowRight, LogIn, LogOut, MoveRight, CalendarDays, ArrowUpDown } from "lucide-react";
import { cn } from "@/lib/utils";
import { Link } from "react-router-dom";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Calendar } from "@/components/ui/calendar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { format } from "date-fns";

const typeIcon = {
  move: MoveRight,
  enter: LogIn,
  exit: LogOut,
};

const typeLabel = {
  move: "Moved",
  enter: "Entered",
  exit: "Exited",
};

function formatTime(ts: string) {
  return new Date(ts).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

type SortDir = "newest" | "oldest";

export default function Logs() {
  const [selectedDate, setSelectedDate] = useState<Date | undefined>(undefined);
  const [sortDir, setSortDir] = useState<SortDir>("newest");

  const { data: rawLogs = [], isLoading } = useLogs({
    start_date: selectedDate ? format(selectedDate, "yyyy-MM-dd") : undefined,
    limit: 500,
  });

  const filteredLogs = useMemo(() => {
    const result = [...rawLogs];
    result.sort((a, b) => {
      const diff = new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime();
      return sortDir === "newest" ? diff : -diff;
    });
    return result;
  }, [rawLogs, sortDir]);

  const dateLabel = selectedDate ? format(selectedDate, "MMM d, yyyy") : "All dates";

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Movement Logs</h1>
          <p className="text-sm text-muted-foreground mt-1">
            {isLoading ? "Loading…" : `${filteredLogs.length} entries${selectedDate ? ` on ${dateLabel}` : ""}`}
          </p>
        </div>

        <div className="flex items-center gap-2">
          {/* Date picker */}
          <Popover>
            <PopoverTrigger asChild>
              <Button variant="outline" size="sm" className="h-8 gap-1.5 text-xs font-normal">
                <CalendarDays className="h-3.5 w-3.5" />
                {dateLabel}
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-auto p-0" align="end">
              <Calendar
                mode="single"
                selected={selectedDate}
                onSelect={setSelectedDate}
                initialFocus
              />
              {selectedDate && (
                <div className="border-t px-3 py-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="w-full text-xs"
                    onClick={() => setSelectedDate(undefined)}
                  >
                    Clear filter
                  </Button>
                </div>
              )}
            </PopoverContent>
          </Popover>

          {/* Sort */}
          <Button
            variant="outline"
            size="sm"
            className="h-8 gap-1.5 text-xs font-normal"
            onClick={() => setSortDir((d) => (d === "newest" ? "oldest" : "newest"))}
          >
            <ArrowUpDown className="h-3.5 w-3.5" />
            {sortDir === "newest" ? "Newest" : "Oldest"}
          </Button>
        </div>
      </div>

      <div className="space-y-2">
        {isLoading &&
          Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-16 rounded-lg" />)}

        {!isLoading && filteredLogs.length === 0 && (
          <p className="py-12 text-center text-sm text-muted-foreground italic">No logs for this date.</p>
        )}

        {filteredLogs.map((log, i) => {
          const Icon = typeIcon[log.type];
          return (
            <Card
              key={log.id}
              className="opacity-0 animate-fade-in"
              style={{ animationDelay: `${i * 50}ms` }}
            >
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
                <div className="text-right flex-shrink-0">
                  <span className="text-xs text-muted-foreground tabular-nums font-mono block">
                    {formatTime(log.timestamp)}
                  </span>
                  {!selectedDate && (
                    <span className="text-[11px] text-muted-foreground/60 font-mono">
                      {format(new Date(log.timestamp), "MMM d")}
                    </span>
                  )}
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
