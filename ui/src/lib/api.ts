/**
 * api.ts — Central API client for BleX UI.
 *
 * Base URL comes from VITE_API_BASE_URL env (empty = same origin in prod).
 * In dev the Vite proxy (vite.config.ts) forwards /api → http://localhost:8001.
 */

const BASE = import.meta.env.VITE_API_BASE_URL ?? "";

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`);
  if (!res.ok) {
    throw new Error(`API error ${res.status}: ${path}`);
  }
  return res.json() as Promise<T>;
}

async function post<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`API error ${res.status}: ${path}`);
  return res.json() as Promise<T>;
}

// ------------------------------------------------------------------ types --
export type BeaconShape = "triangle" | "card" | "pebble";
export type AssetStatus = "active" | "idle" | "offline";
export type ScannerStatus = "online" | "offline" | "unknown";
export type NotificationPriority = "high" | "medium" | "low";
export type NotificationType = "alert" | "system" | "info";
export type LogType = "enter" | "exit" | "move";

export interface Zone {
  id: string;
  name: string;
  description?: string;
  scanner_id: string;
  asset_count: number;
  movement_count: number;
  is_active: boolean;
  assets?: Asset[];
}

export interface Asset {
  id: string;
  mac: string;
  name: string;
  shape: BeaconShape;
  status: AssetStatus;
  battery?: number;
  rssi?: number;
  last_seen?: string;
  last_seen_relative: string;
  zone_id?: string;
  zone_name: string;
}

export interface LogEntry {
  id: string;
  asset_id?: string;
  asset_name: string;
  mac: string;
  timestamp: string;
  from_zone_id?: string;
  from_zone: string;
  to_zone_id?: string;
  to_zone: string;
  rssi?: number;
  type: LogType;
}

export interface Scanner {
  id: string;
  mac: string;
  name: string;
  type: string;
  zone_id?: string;
  zone_name: string;
  last_heartbeat?: string;
  status: ScannerStatus;
  is_online: boolean;
}

export interface Notification {
  id: string;
  title: string;
  message: string;
  type: NotificationType;
  priority: NotificationPriority;
  read: boolean;
  timestamp: string;
  meta?: Record<string, string>;
}

export interface NotificationsResponse {
  total: number;
  unread: number;
  notifications: Notification[];
}

export interface HealthSummary {
  scanners: { total: number; online: number; offline: number };
  beacons: { total: number; alive: number; dead: number; low_battery: number };
}

// -------------------------------------------------------------- API calls --
export const api = {
  // Zones
  getZones: () => get<Zone[]>("/api/zones"),
  getZone: (id: string) => get<Zone>(`/api/zones/${id}`),

  // Assets
  getAssets: () => get<Asset[]>("/api/assets/current"),
  getAsset: (id: string) => get<Asset>(`/api/assets/${id}`),
  getAssetHistory: (id: string) => get<LogEntry[]>(`/api/assets/${id}/history`),

  // Logs (global feed)
  getLogs: (params?: { start_date?: string; limit?: number }) => {
    const qs = new URLSearchParams();
    if (params?.start_date) qs.set("start_date", params.start_date);
    if (params?.limit) qs.set("limit", String(params.limit));
    const query = qs.toString() ? `?${qs}` : "";
    return get<LogEntry[]>(`/api/assets/history${query}`);
  },

  // Scanners
  getScanners: () => get<Scanner[]>("/api/scanners"),

  // Health
  getScannerHealth: () => get<Scanner[]>("/api/health/scanners"),
  getBeaconHealth: () => get<Asset[]>("/api/health/beacons"),
  getHealthSummary: () => get<HealthSummary>("/api/health/summary"),

  // Notifications
  getNotifications: () => get<NotificationsResponse>("/api/notifications"),
  markRead: (id: string) => post<{ ok: boolean }>(`/api/notifications/${id}/read`),
  markAllRead: () => post<{ ok: boolean }>("/api/notifications/read-all"),
};
