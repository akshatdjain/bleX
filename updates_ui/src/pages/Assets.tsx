import { assets } from "@/lib/data";
import { Card, CardContent } from "@/components/ui/card";
import { BeaconIcon } from "@/components/BeaconIcon";
import { StatusDot } from "@/components/StatusDot";
import { Link } from "react-router-dom";
import { ArrowRight } from "lucide-react";

export default function Assets() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">All Assets</h1>
        <p className="text-sm text-muted-foreground mt-1">{assets.length} beacons registered</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {assets.map((asset, i) => (
          <Link key={asset.id} to={`/assets/${asset.id}`} className="block group">
            <Card
              className="transition-shadow hover:shadow-md opacity-0 animate-fade-in"
              style={{ animationDelay: `${i * 60}ms` }}
            >
              <CardContent className="p-4 flex items-center gap-3">
                <BeaconIcon shape={asset.shape} status={asset.status} size={32} />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium truncate">{asset.name}</span>
                    <StatusDot status={asset.status} className="relative flex-shrink-0" />
                  </div>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    {asset.battery}% · {asset.lastSeen}
                  </p>
                </div>
                <ArrowRight className="h-3.5 w-3.5 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0" />
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
