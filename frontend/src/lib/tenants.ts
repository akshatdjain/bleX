/**
 * tenants.ts — Admin tenant management SDK.
 * All calls go through the shared apiJson client (Bearer-token + 401-refresh-retry).
 */
import { apiJson } from "./api-client";

export interface Tenant {
  tenant_id: string;
  name: string;
  mqtt_prefix: string;
  plan: string;
  tier: string;
  master_tier: string;
  status: "active" | "suspended" | "churned";
  db_schema: string | null;
  scanner_limit: number;
  asset_limit: number;
  contact_email: string | null;
  created_at: string | null;
  metadata: Record<string, unknown>;
  // Deployment fields (set in admin panel; consumed by Pi provisioner)
  mode: "local" | "cloud" | null;
  tablet_host: string | null;
  tablet_port: number | null;
  mqtt_username: string | null;
  mqtt_password: string | null;
}

export interface TenantStats {
  tenant_id: string;
  scanners: number;
  assets: number;
  zones: number;
  movements: number;
  active_scanners?: number;
}

export interface TenantEvent {
  id: number;
  event_type: string;
  actor: string;
  payload: Record<string, unknown>;
  created_at: string;
}

export interface TenantUpdate {
  name?: string;
  status?: "active" | "suspended" | "churned";
  plan?: string;
  scanner_limit?: number;
  asset_limit?: number;
  contact_email?: string;
  mode?: "local" | "cloud";
  tablet_host?: string;
  tablet_port?: number;
  mqtt_username?: string;
  mqtt_password?: string;
}

export async function listTenants(): Promise<Tenant[]> {
  return apiJson<Tenant[]>("/tenants");
}

export async function getTenant(tenantId: string): Promise<Tenant> {
  // No dedicated single-tenant endpoint — fetch list and pick. Cheap at current scale.
  const all = await listTenants();
  const t = all.find((x) => x.tenant_id === tenantId);
  if (!t) throw new Error(`Tenant ${tenantId} not found`);
  return t;
}

export async function getTenantStats(tenantId: string): Promise<TenantStats> {
  return apiJson<TenantStats>(`/tenants/${tenantId}/stats`);
}

export async function getTenantEvents(
  tenantId: string,
  limit: number = 50
): Promise<TenantEvent[]> {
  return apiJson<TenantEvent[]>(`/tenants/${tenantId}/events?limit=${limit}`);
}

export async function updateTenant(
  tenantId: string,
  patch: TenantUpdate
): Promise<{ ok: boolean; updated: string[] }> {
  return apiJson(`/tenants/${tenantId}`, {
    method: "PATCH",
    body: JSON.stringify(patch),
  });
}
