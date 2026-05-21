import { useState, useEffect, useMemo } from "react";
import { useAssets } from "@/hooks/use-api";
import { inferShapeFromName } from "@/lib/data";
import { Card, CardContent } from "@/components/ui/card";
import { BeaconIcon } from "@/components/BeaconIcon";
import { StatusDot } from "@/components/StatusDot";
import { Skeleton } from "@/components/ui/skeleton";
import { Input } from "@/components/ui/input";
import { Link } from "react-router-dom";
import { ArrowRight, Search, X } from "lucide-react";
import { cn } from "@/lib/utils";

type StatusFilter = "all" | "active" | "idle" | "offline";

export default function Assets() {
  const { data: assets = [], isLoading } = useAssets();
  const [searchText, setSearchText] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [debouncedSearch, setDebouncedSearch] = useState("");

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchText), 200);
    return () => clearTimeout(timer);
  }, [searchText]);

  const filtered = useMemo(() => {
    return assets.filter((a) => {
      const matchSearch = !debouncedSearch || a.name.toLowerCase().includes(debouncedSearch.toLowerCase());
      const matchStatus = statusFilter === "all" || a.status === statusFilter;
      return matchSearch && matchStatus;
    });
  }, [assets, debouncedSearch, statusFilter]);

  const counts = useMemo(() => ({
    active:  assets.filter((a) => a.status === "active").length,
    idle:    assets.filter((a) => a.status === "idle").length,
    offline: assets.filter((a) => a.status === "offline").length,
  }), [assets]);

  const pills: { key: StatusFilter; label: string }[] = [
    { key: "all",     label: "All" },
    { key: "active",  label: `Active (${counts.active})` },
    { key: "idle",    label: `Idle (${counts.idle})` },
    { key: "offline", label: `Offline (${counts.offline})` },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">Assets</h1>
        <p className="text-sm text-muted-foreground mt-1">
          {isLoading ? "Loading..." : `${assets.length} beacons registered`}
        </p>
      </div>

      <div className="space-y-3">
        {/* Search */}
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
          <Input
            placeholder="Search assets..."
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            className="pl-9 pr-9"
          />
          {searchText && (
            <button
              onClick={() => setSearchText("")}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>

        {/* Filter pills */}
        <div className="flex gap-2 overflow-x-auto pb-0.5">
          {pills.map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setStatusFilter(key)}
              className={cn(
                "px-3 py-1.5 rounded-full text-xs font-medium whitespace-nowrap border transition-colors",
                statusFilter === key
                  ? "border-primary bg-primary/10 text-primary"
                  : "border-border text-muted-foreground hover:text-foreground hover:border-border/80"
              )}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {isLoading
          ? Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-20 rounded-xl" />
            ))
          : filtered.map((asset, i) => (
              <Link key={asset.id} to={`/assets/${asset.id}`} className="block group">
                <Card
                  className="transition-shadow hover:shadow-md opacity-0 animate-fade-in"
                  style={{ animationDelay: `${i * 60}ms` }}
                >
                  <CardContent className="p-4 flex items-center gap-3">
                    <BeaconIcon shape={inferShapeFromName(asset.name)} status={asset.status} size={32} />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium truncate">{asset.name}</span>
                        <StatusDot status={asset.status} className="relative flex-shrink-0" />
                      </div>
                      <p className="text-xs text-muted-foreground mt-0.5">
                        {asset.battery !== undefined && asset.battery !== null ? `${asset.battery}%` : "no battery"}
                        {" · "}
                        {asset.last_seen_relative}
                      </p>
                    </div>
                    <ArrowRight className="h-3.5 w-3.5 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0" />
                  </CardContent>
                </Card>
              </Link>
            ))}
      </div>

      {!isLoading && filtered.length === 0 && (
        <p className="py-12 text-center text-sm text-muted-foreground">No assets found.</p>
      )}
    </div>
  );
}
