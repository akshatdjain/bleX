import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";

const REFETCH_INTERVAL = 10_000; // 10 seconds live refresh

// ---------------------------------------------------------------- Zones --
export function useZones() {
  return useQuery({
    queryKey: ["zones"],
    queryFn: api.getZones,
    refetchInterval: REFETCH_INTERVAL,
  });
}

export function useZone(id: string) {
  return useQuery({
    queryKey: ["zones", id],
    queryFn: () => api.getZone(id),
    refetchInterval: REFETCH_INTERVAL,
    enabled: !!id,
  });
}

// --------------------------------------------------------------- Assets --
export function useAssets() {
  return useQuery({
    queryKey: ["assets"],
    queryFn: api.getAssets,
    refetchInterval: REFETCH_INTERVAL,
  });
}

export function useAsset(id: string) {
  return useQuery({
    queryKey: ["assets", id],
    queryFn: () => api.getAsset(id),
    refetchInterval: REFETCH_INTERVAL,
    enabled: !!id,
  });
}

export function useAssetHistory(id: string) {
  return useQuery({
    queryKey: ["assets", id, "history"],
    queryFn: () => api.getAssetHistory(id),
    refetchInterval: REFETCH_INTERVAL,
    enabled: !!id,
  });
}

// ------------------------------------------------------------------ Logs --
export function useLogs(params?: { start_date?: string; limit?: number }) {
  return useQuery({
    queryKey: ["logs", params],
    queryFn: () => api.getLogs(params),
    refetchInterval: REFETCH_INTERVAL,
  });
}

// ------------------------------------------------------------- Scanners --
export function useScanners() {
  return useQuery({
    queryKey: ["scanners"],
    queryFn: api.getScanners,
    refetchInterval: REFETCH_INTERVAL,
  });
}

// --------------------------------------------------------------- Health --
export function useScannerHealth() {
  return useQuery({
    queryKey: ["health", "scanners"],
    queryFn: api.getScannerHealth,
    refetchInterval: REFETCH_INTERVAL,
  });
}

export function useBeaconHealth() {
  return useQuery({
    queryKey: ["health", "beacons"],
    queryFn: api.getBeaconHealth,
    refetchInterval: REFETCH_INTERVAL,
  });
}

export function useHealthSummary() {
  return useQuery({
    queryKey: ["health", "summary"],
    queryFn: api.getHealthSummary,
    refetchInterval: REFETCH_INTERVAL,
  });
}

// --------------------------------------------------------- Notifications --
export function useNotifications() {
  return useQuery({
    queryKey: ["notifications"],
    queryFn: api.getNotifications,
    refetchInterval: 15_000, // slightly slower poll for notifications
  });
}

export function useMarkRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.markRead(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] }),
  });
}

export function useMarkAllRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: api.markAllRead,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] }),
  });
}
