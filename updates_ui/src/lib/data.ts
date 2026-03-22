export type BeaconShape = "oval" | "badge" | "card";
export type AssetStatus = "active" | "idle" | "offline";

export interface Asset {
  id: string;
  name: string;
  shape: BeaconShape;
  status: AssetStatus;
  rssi: number;
  battery: number;
  lastSeen: string;
  zoneId: string;
}

export interface Scanner {
  id: string;
  name: string;
  mac: string;
  status: "online" | "offline";
  rssi: number;
  lastHeartbeat: string;
  zoneId: string;
}

export interface Zone {
  id: string;
  name: string;
  scannerIds: string[];
  assetCount: number;
  movementCount: number;
  isActive: boolean;
}

export interface LogEntry {
  id: string;
  assetId: string;
  assetName: string;
  fromZone: string;
  toZone: string;
  timestamp: string;
  type: "enter" | "exit" | "move";
}

export interface Notification {
  id: string;
  title: string;
  message: string;
  priority: "high" | "medium" | "low";
  read: boolean;
  timestamp: string;
  type: "alert" | "system" | "info";
}

export const scanners: Scanner[] = [
  { id: "scn1", name: "SCN-01", mac: "AA:BB:CC:01:23:45", status: "online", rssi: -42, lastHeartbeat: "10 sec ago", zoneId: "z1" },
  { id: "scn2", name: "SCN-02", mac: "AA:BB:CC:01:23:46", status: "online", rssi: -38, lastHeartbeat: "8 sec ago", zoneId: "z1" },
  { id: "scn3", name: "SCN-03", mac: "AA:BB:CC:01:23:47", status: "online", rssi: -45, lastHeartbeat: "12 sec ago", zoneId: "z2" },
  { id: "scn4", name: "SCN-04", mac: "AA:BB:CC:01:23:48", status: "online", rssi: -51, lastHeartbeat: "5 sec ago", zoneId: "z3" },
  { id: "scn5", name: "SCN-05", mac: "AA:BB:CC:01:23:49", status: "online", rssi: -48, lastHeartbeat: "15 sec ago", zoneId: "z3" },
  { id: "scn6", name: "SCN-06", mac: "AA:BB:CC:01:23:4A", status: "online", rssi: -44, lastHeartbeat: "3 sec ago", zoneId: "z4" },
  { id: "scn7", name: "SCN-07", mac: "AA:BB:CC:01:23:4B", status: "offline", rssi: 0, lastHeartbeat: "2 hrs ago", zoneId: "z5" },
  { id: "scn8", name: "SCN-08", mac: "AA:BB:CC:01:23:4C", status: "online", rssi: -50, lastHeartbeat: "7 sec ago", zoneId: "z6" },
];

export const zones: Zone[] = [
  { id: "z1", name: "Emergency Ward", scannerIds: ["scn1", "scn2"], assetCount: 3, movementCount: 12, isActive: true },
  { id: "z2", name: "ICU", scannerIds: ["scn3"], assetCount: 2, movementCount: 8, isActive: true },
  { id: "z3", name: "Radiology", scannerIds: ["scn4", "scn5"], assetCount: 2, movementCount: 5, isActive: true },
  { id: "z4", name: "Pharmacy", scannerIds: ["scn6"], assetCount: 1, movementCount: 3, isActive: true },
  { id: "z5", name: "Operating Theatre", scannerIds: ["scn7"], assetCount: 0, movementCount: 0, isActive: false },
  { id: "z6", name: "Staff Lounge", scannerIds: ["scn8"], assetCount: 0, movementCount: 0, isActive: false },
];

export const assets: Asset[] = [
  { id: "a1", name: "Oval1-BD", shape: "oval", status: "active", rssi: -62, battery: 87, lastSeen: "2 min ago", zoneId: "z1" },
  { id: "a2", name: "Card3-7A", shape: "card", status: "active", rssi: -58, battery: 94, lastSeen: "1 min ago", zoneId: "z1" },
  { id: "a3", name: "Badge2-2F", shape: "badge", status: "idle", rssi: -71, battery: 63, lastSeen: "14 min ago", zoneId: "z1" },
  { id: "a4", name: "Oval2-B1", shape: "oval", status: "active", rssi: -55, battery: 91, lastSeen: "30 sec ago", zoneId: "z2" },
  { id: "a5", name: "Badge1-C4", shape: "badge", status: "idle", rssi: -68, battery: 45, lastSeen: "22 min ago", zoneId: "z2" },
  { id: "a6", name: "Card1-9E", shape: "card", status: "active", rssi: -60, battery: 78, lastSeen: "3 min ago", zoneId: "z3" },
  { id: "a7", name: "Oval3-D7", shape: "oval", status: "active", rssi: -53, battery: 99, lastSeen: "45 sec ago", zoneId: "z3" },
  { id: "a8", name: "Badge3-F1", shape: "badge", status: "idle", rssi: -74, battery: 52, lastSeen: "38 min ago", zoneId: "z4" },
];

export const logs: LogEntry[] = [
  { id: "l1", assetId: "a1", assetName: "Oval1-BD", fromZone: "ICU", toZone: "Emergency Ward", timestamp: "2026-03-21T09:42:00", type: "move" },
  { id: "l2", assetId: "a4", assetName: "Oval2-B1", fromZone: "Pharmacy", toZone: "ICU", timestamp: "2026-03-21T09:38:00", type: "move" },
  { id: "l3", assetId: "a6", assetName: "Card1-9E", fromZone: "—", toZone: "Radiology", timestamp: "2026-03-21T09:31:00", type: "enter" },
  { id: "l4", assetId: "a2", assetName: "Card3-7A", fromZone: "ICU", toZone: "Emergency Ward", timestamp: "2026-03-21T09:25:00", type: "move" },
  { id: "l5", assetId: "a7", assetName: "Oval3-D7", fromZone: "—", toZone: "Radiology", timestamp: "2026-03-21T09:14:00", type: "enter" },
  { id: "l6", assetId: "a8", assetName: "Badge3-F1", fromZone: "ICU", toZone: "Pharmacy", timestamp: "2026-03-21T09:02:00", type: "move" },
  { id: "l7", assetId: "a3", assetName: "Badge2-2F", fromZone: "Radiology", toZone: "Emergency Ward", timestamp: "2026-03-21T08:48:00", type: "move" },
  { id: "l8", assetId: "a5", assetName: "Badge1-C4", fromZone: "Emergency Ward", toZone: "ICU", timestamp: "2026-03-21T08:33:00", type: "move" },
  { id: "l9", assetId: "a1", assetName: "Oval1-BD", fromZone: "Radiology", toZone: "ICU", timestamp: "2026-03-20T16:42:00", type: "move" },
  { id: "l10", assetId: "a4", assetName: "Oval2-B1", fromZone: "Emergency Ward", toZone: "Pharmacy", timestamp: "2026-03-20T15:20:00", type: "move" },
  { id: "l11", assetId: "a2", assetName: "Card3-7A", fromZone: "Pharmacy", toZone: "ICU", timestamp: "2026-03-20T14:05:00", type: "move" },
  { id: "l12", assetId: "a6", assetName: "Card1-9E", fromZone: "Emergency Ward", toZone: "—", timestamp: "2026-03-20T11:30:00", type: "exit" },
];

export const notifications: Notification[] = [
  { id: "n1", title: "Scanner Offline", message: "SCN-05 in Operating Theatre has been offline for 2 hours", priority: "high", read: false, timestamp: "2026-03-21T09:15:00", type: "alert" },
  { id: "n2", title: "Low Battery", message: "Oxygen Tank #9 battery at 45% — replace soon", priority: "medium", read: false, timestamp: "2026-03-21T08:50:00", type: "alert" },
  { id: "n3", title: "System Update", message: "Firmware v2.4.1 available for SCN-01 and SCN-03", priority: "low", read: false, timestamp: "2026-03-21T07:00:00", type: "system" },
  { id: "n4", title: "Asset Details Required", message: "Med Cart #4 missing calibration date — update record", priority: "medium", read: true, timestamp: "2026-03-20T18:00:00", type: "info" },
];

export function getAssetsForZone(zoneId: string): Asset[] {
  return assets.filter((a) => a.zoneId === zoneId);
}

export function getActiveZones(): Zone[] {
  return zones.filter((z) => z.isActive).sort((a, b) => b.movementCount - a.movementCount).slice(0, 4);
}

export function getAssetById(id: string): Asset | undefined {
  return assets.find((a) => a.id === id);
}

export function getZoneById(id: string): Zone | undefined {
  return zones.find((z) => z.id === id);
}

export function getLogsForAsset(assetId: string): LogEntry[] {
  return logs.filter((l) => l.assetId === assetId);
}

export function getLogsForDate(date: string): LogEntry[] {
  return logs.filter((l) => l.timestamp.startsWith(date));
}

export function getScannersForZone(zoneId: string): Scanner[] {
  return scanners.filter((s) => s.zoneId === zoneId);
}

export function getScannerById(id: string): Scanner | undefined {
  return scanners.find((s) => s.id === id);
}

/** Infer beacon shape from asset name prefix (e.g. "Oval1-BD" → "oval") */
export function inferShapeFromName(name: string): BeaconShape {
  const lower = name.toLowerCase();
  if (lower.startsWith("oval")) return "oval";
  if (lower.startsWith("badge")) return "badge";
  if (lower.startsWith("card")) return "card";
  return "oval"; // default fallback
}

export function getUnreadNotificationCount(): number {
  return notifications.filter((n) => !n.read).length;
}
