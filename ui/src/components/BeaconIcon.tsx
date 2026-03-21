import { cn } from "@/lib/utils";
import type { AssetStatus, BeaconShape } from "@/lib/data";

const statusColorMap: Record<AssetStatus, string> = {
  active: "text-status-active",
  idle: "text-status-idle",
  offline: "text-status-offline",
};

const statusGlowMap: Record<AssetStatus, string> = {
  active: "drop-shadow-[0_0_6px_hsl(152,60%,42%,0.4)]",
  idle: "drop-shadow-[0_0_4px_hsl(38,92%,50%,0.3)]",
  offline: "",
};

interface BeaconIconProps {
  shape: BeaconShape;
  status: AssetStatus;
  size?: number;
  className?: string;
}

export function BeaconIcon({ shape, status, size = 32, className }: BeaconIconProps) {
  const color = statusColorMap[status];
  const glow = statusGlowMap[status];

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 32 32"
      fill="none"
      className={cn(color, glow, className)}
    >
      {shape === "triangle" && (
        <path
          d="M16 4L28 26H4L16 4Z"
          fill="currentColor"
          fillOpacity={0.15}
          stroke="currentColor"
          strokeWidth={1.5}
          strokeLinejoin="round"
        />
      )}
      {shape === "card" && (
        <rect
          x="5"
          y="8"
          width="22"
          height="16"
          rx="3"
          fill="currentColor"
          fillOpacity={0.15}
          stroke="currentColor"
          strokeWidth={1.5}
        />
      )}
      {shape === "pebble" && (
        <ellipse
          cx="16"
          cy="16"
          rx="11"
          ry="9"
          fill="currentColor"
          fillOpacity={0.15}
          stroke="currentColor"
          strokeWidth={1.5}
        />
      )}
      {status === "active" && (
        <circle cx="24" cy="8" r="3" fill="currentColor" className="animate-pulse-soft" />
      )}
    </svg>
  );
}
