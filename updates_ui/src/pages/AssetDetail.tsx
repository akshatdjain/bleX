import { useParams, Link } from "react-router-dom";
import { getAssetById, getLogsForAsset, getZoneById } from "@/lib/data";
import { BeaconIcon } from "@/components/BeaconIcon";
import { StatusDot } from "@/components/StatusDot";
import { Card, CardContent } from "@/components/ui/card";
import { ArrowLeft, ArrowRight, Battery, Signal, Clock, MapPin } from "lucide-react";

export default function AssetDetail() {
  const { assetId } = useParams<{ assetId: string }>();
  const asset = getAssetById(assetId || "");
  const assetLogs = getLogsForAsset(assetId || "");
  const zone = asset ? getZoneById(asset.zoneId) : undefined;

  if (!asset) {
    return (
      <div className="py-16 text-center text-muted-foreground">
        <p>Asset not found.</p>
        <Link to="/assets" className="text-primary text-sm mt-2 inline-block hover:underline">Back to assets</Link>
      </div>
    );
  }

  const stats = [
    { icon: MapPin, label: "Zone", value: zone?.name || "Unknown" },
    { icon: Signal, label: "RSSI", value: `${asset.rssi} dBm` },
    { icon: Battery, label: "Battery", value: `${asset.battery}%` },
    { icon: Clock, label: "Last Seen", value: asset.lastSeen },
  ];

  return (
    <div className="space-y-6 max-w-2xl">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Link to="/assets" className="rounded-md p-1.5 hover:bg-muted transition-colors">
          <ArrowLeft className="h-4 w-4" />
        </Link>
        <BeaconIcon shape={asset.shape} status={asset.status} size={40} />
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-lg font-semibold">{asset.name}</h1>
            <StatusDot status={asset.status} className="relative" />
          </div>
          <p className="text-xs text-muted-foreground capitalize">{asset.shape} beacon · {asset.status}</p>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {stats.map(({ icon: Icon, label, value }, i) => (
          <Card key={label} className="opacity-0 animate-fade-in" style={{ animationDelay: `${i * 60}ms` }}>
            <CardContent className="p-4">
              <div className="flex items-center gap-1.5 text-muted-foreground mb-1">
                <Icon className="h-3.5 w-3.5" />
                <span className="text-xs">{label}</span>
              </div>
              <p className="text-sm font-semibold tabular-nums">{value}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Movement history */}
      <div>
        <h2 className="text-sm font-medium text-muted-foreground mb-3">Movement History</h2>
        {assetLogs.length === 0 ? (
          <p className="text-sm text-muted-foreground italic py-4 text-center">No movement recorded.</p>
        ) : (
          <div className="space-y-2">
            {assetLogs.map((log, i) => (
              <Card
                key={log.id}
                className="opacity-0 animate-fade-in"
                style={{ animationDelay: `${(i + 4) * 60}ms` }}
              >
                <CardContent className="p-3 flex items-center gap-3 text-sm">
                  <span className="text-xs text-muted-foreground font-mono tabular-nums w-12 flex-shrink-0">
                    {new Date(log.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                  </span>
                  <span className="text-muted-foreground truncate">{log.fromZone}</span>
                  <ArrowRight className="h-3 w-3 text-muted-foreground flex-shrink-0" />
                  <span className="truncate font-medium">{log.toZone}</span>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
