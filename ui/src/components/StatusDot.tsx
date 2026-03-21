import { cn } from "@/lib/utils";
import type { AssetStatus } from "@/lib/data";

const dotClass: Record<AssetStatus, string> = {
  active: "bg-status-active",
  idle: "bg-status-idle",
  offline: "bg-status-offline",
};

export function StatusDot({ status, className }: { status: AssetStatus; className?: string }) {
  return (
    <span className={cn("inline-block h-2 w-2 rounded-full", dotClass[status], className)}>
      {status === "active" && (
        <span className={cn("absolute inset-0 rounded-full animate-ping opacity-40", dotClass[status])} />
      )}
    </span>
  );
}
