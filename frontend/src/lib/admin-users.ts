/**
 * admin-users.ts — Admin user management SDK.
 * All calls go through the shared apiJson client (Bearer-token + 401-refresh-retry).
 */
import { apiJson } from "./api-client";

export interface AdminUser {
  id: number;
  email: string;
  name: string;
  role: string;
  tenant_id: string;
  is_active: boolean;
  created_at: string | null;
  last_login: string | null;
}

export interface UserCreate {
  email: string;
  name: string;
  password: string;
  role: "admin" | "user";
  tenant_id: string;
  is_active?: boolean;
}

export interface UserUpdate {
  name?: string;
  role?: "admin" | "user";
  is_active?: boolean;
  password?: string;
}

export async function listUsers(tenantId?: string): Promise<AdminUser[]> {
  const q = tenantId ? `?tenant_id=${encodeURIComponent(tenantId)}` : "";
  return apiJson<AdminUser[]>(`/admin/users${q}`);
}

export async function getUser(id: number): Promise<AdminUser> {
  return apiJson<AdminUser>(`/admin/users/${id}`);
}

export async function createUser(
  payload: UserCreate
): Promise<{ ok: boolean; id: number; email: string }> {
  return apiJson("/admin/users", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function updateUser(
  id: number,
  patch: UserUpdate
): Promise<{ ok: boolean; updated: string[] }> {
  return apiJson(`/admin/users/${id}`, {
    method: "PATCH",
    body: JSON.stringify(patch),
  });
}

export async function deleteUser(
  id: number
): Promise<{ ok: boolean; deleted: number }> {
  return apiJson(`/admin/users/${id}`, { method: "DELETE" });
}
