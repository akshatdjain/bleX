import type { Asset, Scanner, Zone, Notification } from "@/lib/api";

/**
 * data.ts — Type definitions and data transformation helpers.
 * Real data comes from the API (lib/api.ts) via hooks.
 */

export type {
  BeaconShape,
  AssetStatus,
  Zone,
  Asset,
  LogEntry,
  Notification,
  Scanner,
} from "@/lib/api";

// ------------------------------------------------------------------ helpers --

/**
 * Infer beacon shape from asset name prefix (e.g. "Oval1-BD" → "oval")
 */
export function inferShapeFromName(name: string): "oval" | "badge" | "card" {
  const lower = name.toLowerCase();
  if (lower.startsWith("oval")) return "oval";
  if (lower.startsWith("badge")) return "badge";
  if (lower.startsWith("card")) return "card";
  return "oval"; // default fallback
}

/**
 * Filter and sort zones for the "Active Zones" dashboard view.
 */
export function getActiveZones(zones: Zone[]) {
  return zones
    .filter((z) => z.is_active)
    .sort((a, b) => b.movement_count - a.movement_count)
    .slice(0, 4);
}

/**
 * Filter assets belonging to a specific zone.
 */
export function getAssetsForZone(assets: Asset[], zoneId: string): Asset[] {
  return assets.filter((a) => a.zone_id === zoneId);
}

/**
 * Filter scanners assigned to a specific zone.
 */
export function getScannersForZone(scanners: Scanner[], zoneId: string): Scanner[] {
  return scanners.filter((s) => s.zone_id === zoneId);
}

export function getUnreadNotificationCount(notifications: Notification[]) {
  return notifications.filter((n) => !n.read).length;
}
