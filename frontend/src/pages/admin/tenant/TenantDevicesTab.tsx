import { useState } from "react";
import { useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Copy, Check, Trash2, AlertTriangle } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { listDevices, issueDevice, revokeDevice, DeviceIssueResult } from "@/lib/devices";
import { useToast } from "@/hooks/use-toast";

export default function TenantDevicesTab() {
  const { tenantId = "" } = useParams<{ tenantId: string }>();
  const qc = useQueryClient();
  const { toast } = useToast();

  const { data: devices = [], isLoading } = useQuery({
    queryKey: ["devices", tenantId],
    queryFn: () => listDevices(tenantId),
    enabled: !!tenantId,
  });

  const [issueOpen, setIssueOpen] = useState(false);
  const [mac, setMac] = useState("");
  const [role, setRole] = useState<"scanner" | "master">("scanner");
  const [issuing, setIssuing] = useState(false);
  const [issued, setIssued] = useState<DeviceIssueResult | null>(null);
  const [copied, setCopied] = useState(false);

  async function handleIssue() {
    if (!mac.trim()) {
      toast({ title: "MAC required", variant: "destructive" });
      return;
    }
    setIssuing(true);
    try {
      const result = await issueDevice(tenantId, mac.trim(), role);
      setIssued(result);
      setIssueOpen(false);
      setMac("");
      setRole("scanner");
      qc.invalidateQueries({ queryKey: ["devices", tenantId] });
    } catch (e) {
      toast({
        title: "Failed to issue device",
        description: (e as Error).message,
        variant: "destructive",
      });
    } finally {
      setIssuing(false);
    }
  }

  async function handleRevoke(id: number, deviceId: string) {
    if (!confirm(`Revoke device ${deviceId}? This cannot be undone.`)) return;
    try {
      await revokeDevice(id);
      toast({ title: "Device revoked" });
      qc.invalidateQueries({ queryKey: ["devices", tenantId] });
    } catch (e) {
      toast({
        title: "Revoke failed",
        description: (e as Error).message,
        variant: "destructive",
      });
    }
  }

  function copyToken() {
    if (issued?.api_token) {
      navigator.clipboard.writeText(issued.api_token);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">Devices</h2>
          <p className="text-xs text-muted-foreground">
            Pi devices registered to this tenant
          </p>
        </div>
        <Button onClick={() => setIssueOpen(true)}>
          <Plus className="h-4 w-4 mr-1.5" />
          Issue Device Token
        </Button>
      </div>

      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b bg-muted/40 text-xs uppercase tracking-wider text-muted-foreground">
                <tr>
                  <th className="text-left px-4 py-3 font-semibold">Device ID</th>
                  <th className="text-left px-4 py-3 font-semibold">MAC</th>
                  <th className="text-left px-4 py-3 font-semibold">Role</th>
                  <th className="text-left px-4 py-3 font-semibold">Status</th>
                  <th className="text-left px-4 py-3 font-semibold">Last Seen</th>
                  <th className="text-left px-4 py-3 font-semibold">Created</th>
                  <th className="text-right px-4 py-3 font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {isLoading ? (
                  <tr>
                    <td colSpan={7} className="px-4 py-8 text-center text-muted-foreground">
                      Loading…
                    </td>
                  </tr>
                ) : devices.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-4 py-8 text-center text-muted-foreground">
                      No devices issued yet.
                    </td>
                  </tr>
                ) : (
                  devices.map((d) => (
                    <tr key={d.id} className="hover:bg-muted/30">
                      <td className="px-4 py-3 font-mono text-xs">{d.device_id}</td>
                      <td className="px-4 py-3 font-mono text-xs">{d.mac}</td>
                      <td className="px-4 py-3">
                        <span className="px-2 py-0.5 rounded-md text-[10px] font-semibold uppercase tracking-wide bg-primary/10 text-primary">
                          {d.role}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase ${
                            d.is_active
                              ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300"
                              : "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300"
                          }`}
                        >
                          {d.is_active ? "active" : "revoked"}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
                        {d.last_seen ? new Date(d.last_seen).toLocaleString() : "—"}
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
                        {d.created_at ? new Date(d.created_at).toLocaleDateString() : "—"}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {d.is_active && (
                          <button
                            onClick={() => handleRevoke(d.id, d.device_id)}
                            className="inline-flex items-center gap-1 text-xs text-destructive hover:bg-destructive/10 px-2 py-1 rounded-md transition-colors"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                            Revoke
                          </button>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* Issue device dialog */}
      <Dialog open={issueOpen} onOpenChange={setIssueOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Issue Device Token</DialogTitle>
            <DialogDescription>
              Register a new Pi device for this tenant. The API token will be shown
              once after creation.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-1.5">
              <Label htmlFor="mac">MAC Address</Label>
              <Input
                id="mac"
                placeholder="aa:bb:cc:dd:ee:ff"
                value={mac}
                onChange={(e) => setMac(e.target.value)}
                autoFocus
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="role">Role</Label>
              <Select value={role} onValueChange={(v) => setRole(v as "scanner" | "master")}>
                <SelectTrigger id="role">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="scanner">Scanner</SelectItem>
                  <SelectItem value="master">Master</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIssueOpen(false)} disabled={issuing}>
              Cancel
            </Button>
            <Button onClick={handleIssue} disabled={issuing}>
              {issuing ? "Issuing…" : "Issue Token"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* One-time token reveal dialog */}
      <Dialog open={!!issued} onOpenChange={(o) => !o && setIssued(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Device Token Issued</DialogTitle>
            <DialogDescription>
              Copy this token now — it will not be shown again.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="flex items-start gap-2 p-3 rounded-md bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-900">
              <AlertTriangle className="h-4 w-4 text-amber-600 flex-shrink-0 mt-0.5" />
              <p className="text-xs text-amber-900 dark:text-amber-200">
                This token will not be shown again. Store it on the Pi now.
              </p>
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">Device ID</Label>
              <code className="block w-full px-3 py-2 rounded-md bg-muted font-mono text-xs">
                {issued?.device_id}
              </code>
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">API Token</Label>
              <div className="flex gap-2">
                <code className="flex-1 px-3 py-2 rounded-md bg-muted font-mono text-xs break-all">
                  {issued?.api_token}
                </code>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={copyToken}
                  className="flex-shrink-0"
                >
                  {copied ? (
                    <>
                      <Check className="h-3.5 w-3.5 mr-1" />
                      Copied
                    </>
                  ) : (
                    <>
                      <Copy className="h-3.5 w-3.5 mr-1" />
                      Copy
                    </>
                  )}
                </Button>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button onClick={() => setIssued(null)}>Done</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
