/**
 * devices.ts — Pi device management SDK.
 *
 * The `api_token` field on DeviceIssueResult is only populated by the issue endpoint
 * and is shown to the operator exactly once. Subsequent listings never include it.
 */
import { apiJson } from "./api-client";

export interface Device {
  id: number;
  device_id: string;
  mac: string;
  tenant_id: string;
  role: "scanner" | "master";
  is_active: boolean;
  last_seen: string | null;
  created_at: string | null;
}

export interface DeviceIssueResult extends Device {
  api_token: string;
}

export async function listDevices(tenantId?: string): Promise<Device[]> {
  const q = tenantId ? `?tenant_id=${encodeURIComponent(tenantId)}` : "";
  return apiJson<Device[]>(`/devices${q}`);
}

export async function issueDevice(
  tenantId: string,
  mac: string,
  role: "scanner" | "master" = "scanner"
): Promise<DeviceIssueResult> {
  return apiJson<DeviceIssueResult>("/devices", {
    method: "POST",
    body: JSON.stringify({ tenant_id: tenantId, mac, role }),
  });
}

export async function revokeDevice(id: number): Promise<{ ok: boolean }> {
  return apiJson(`/devices/${id}`, { method: "DELETE" });
}
