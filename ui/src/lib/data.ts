/**
 * data.ts — Type definitions only.
 * All mock data has been removed. Real data comes from the API (lib/api.ts).
 * These re-exports exist so existing imports don't break during migration.
 */

// Re-export from the canonical API types file
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
// These helper functions now operate on real data passed in from hooks.
// They are kept here for backwards compatibility but should be phased out
// in favour of using the hook data directly.

export function getActiveZones(zones: import("@/lib/api").Zone[]) {
  return zones
    .filter((z) => z.is_active)
    .sort((a, b) => b.movement_count - a.movement_count)
    .slice(0, 4);
}

export function getUnreadNotificationCount(
  notifications: import("@/lib/api").Notification[]
) {
  return notifications.filter((n) => !n.read).length;
}
