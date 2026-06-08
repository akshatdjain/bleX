import { useEffect, useState } from "react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useQuery } from "@tanstack/react-query";
import { Tenant, updateTenant, getTenantEvents, TenantUpdate } from "@/lib/tenants";
import { useToast } from "@/hooks/use-toast";

interface Props {
  tenant: Tenant | null;
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}

export function TenantEditDrawer({ tenant, open, onClose, onSaved }: Props) {
  const { toast } = useToast();
  const [form, setForm] = useState<TenantUpdate>({});
  const [saving, setSaving] = useState(false);

  // Reset form when tenant changes
  useEffect(() => {
    if (tenant) {
      setForm({
        name: tenant.name,
        status: tenant.status,
        plan: tenant.plan,
        scanner_limit: tenant.scanner_limit,
        asset_limit: tenant.asset_limit,
        contact_email: tenant.contact_email ?? "",
        mode: (tenant.mode ?? "cloud") as "local" | "cloud",
        tablet_host: tenant.tablet_host ?? "",
        tablet_port: tenant.tablet_port ?? 1883,
        mqtt_username: tenant.mqtt_username ?? "",
        mqtt_password: tenant.mqtt_password ?? "",
      });
    } else {
      setForm({});
    }
  }, [tenant?.tenant_id]);

  const events = useQuery({
    queryKey: ["tenant-events", tenant?.tenant_id],
    queryFn: () => getTenantEvents(tenant!.tenant_id, 20),
    enabled: !!tenant && open,
  });

  if (!tenant) return null;

  async function save() {
    if (!tenant) return;
    setSaving(true);
    try {
      // Compute diff: only include fields that changed
      const patch: TenantUpdate = {};
      for (const key of Object.keys(form) as (keyof TenantUpdate)[]) {
        const val = form[key];
        const orig = (tenant as any)[key];
        if (val !== orig && val !== "" && val !== undefined) patch[key] = val as any;
        if (val === "" && orig) (patch as any)[key] = "";
      }
      if (Object.keys(patch).length === 0) {
        toast({ title: "No changes to save" });
        setSaving(false);
        return;
      }
      const res = await updateTenant(tenant.tenant_id, patch);
      toast({
        title: "Saved",
        description: `Updated: ${res.updated.filter((f) => f !== "tid").join(", ")}`,
      });
      onSaved();
    } catch (err) {
      toast({
        title: "Save failed",
        description: (err as Error).message,
        variant: "destructive",
      });
    } finally {
      setSaving(false);
    }
  }

  return (
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="sm:max-w-2xl w-full overflow-y-auto">
        <SheetHeader>
          <SheetTitle>{tenant.name}</SheetTitle>
          <SheetDescription className="font-mono text-xs">
            {tenant.tenant_id} · created{" "}
            {tenant.created_at
              ? new Date(tenant.created_at).toLocaleString()
              : "—"}
          </SheetDescription>
        </SheetHeader>

        <div className="space-y-6 mt-6">
          {/* Basic */}
          <Section title="Basic">
            <Field label="Name">
              <Input
                value={form.name ?? ""}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
            </Field>
            <Field label="Contact Email">
              <Input
                type="email"
                value={form.contact_email ?? ""}
                onChange={(e) =>
                  setForm({ ...form, contact_email: e.target.value })
                }
              />
            </Field>
            <Field label="Status">
              <Select
                value={form.status ?? "active"}
                onValueChange={(v) => setForm({ ...form, status: v as any })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="active">Active</SelectItem>
                  <SelectItem value="suspended">Suspended</SelectItem>
                  <SelectItem value="churned">Churned</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field label="Plan">
              <Input
                value={form.plan ?? ""}
                onChange={(e) => setForm({ ...form, plan: e.target.value })}
              />
            </Field>
          </Section>

          {/* Limits */}
          <Section title="Limits">
            <Field label="Scanner Limit">
              <Input
                type="number"
                value={form.scanner_limit ?? 0}
                onChange={(e) =>
                  setForm({ ...form, scanner_limit: parseInt(e.target.value) || 0 })
                }
              />
            </Field>
            <Field label="Asset Limit">
              <Input
                type="number"
                value={form.asset_limit ?? 0}
                onChange={(e) =>
                  setForm({ ...form, asset_limit: parseInt(e.target.value) || 0 })
                }
              />
            </Field>
          </Section>

          {/* Deployment */}
          <Section title="Deployment (Pi config)">
            <Field label="Mode">
              <Select
                value={form.mode ?? "cloud"}
                onValueChange={(v) => setForm({ ...form, mode: v as any })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="cloud">Cloud</SelectItem>
                  <SelectItem value="local">Local</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field label="MQTT Username">
              <Input
                value={form.mqtt_username ?? ""}
                onChange={(e) =>
                  setForm({ ...form, mqtt_username: e.target.value })
                }
              />
            </Field>
            <Field label="MQTT Password">
              <Input
                type="password"
                value={form.mqtt_password ?? ""}
                onChange={(e) =>
                  setForm({ ...form, mqtt_password: e.target.value })
                }
              />
            </Field>
            <Field label="Tablet Host (fallback)">
              <Input
                placeholder="e.g. 192.168.29.42"
                value={form.tablet_host ?? ""}
                onChange={(e) =>
                  setForm({ ...form, tablet_host: e.target.value })
                }
              />
            </Field>
            <Field label="Tablet Port">
              <Input
                type="number"
                value={form.tablet_port ?? 1883}
                onChange={(e) =>
                  setForm({ ...form, tablet_port: parseInt(e.target.value) || 1883 })
                }
              />
            </Field>
          </Section>

          {/* Audit Log */}
          <Section title="Audit Log">
            {events.isLoading ? (
              <p className="text-xs text-muted-foreground">Loading events…</p>
            ) : events.data && events.data.length > 0 ? (
              <ul className="divide-y border rounded-md max-h-64 overflow-y-auto">
                {events.data.map((e) => (
                  <li key={e.id} className="px-3 py-2 text-xs">
                    <div className="flex items-center justify-between">
                      <span className="font-semibold">{e.event_type}</span>
                      <span className="text-muted-foreground">
                        {new Date(e.created_at).toLocaleString()}
                      </span>
                    </div>
                    <p className="text-muted-foreground mt-0.5">
                      by {e.actor}
                      {Object.keys(e.payload || {}).length > 0 && (
                        <span className="ml-2 font-mono">
                          {JSON.stringify(e.payload)}
                        </span>
                      )}
                    </p>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-xs text-muted-foreground">No events yet.</p>
            )}
          </Section>

          {/* Actions */}
          <div className="flex justify-end gap-2 pt-4 border-t sticky bottom-0 bg-background pb-2">
            <Button variant="outline" onClick={onClose} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={save} disabled={saving}>
              {saving ? "Saving…" : "Save Changes"}
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-3">
        {title}
      </h3>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">{children}</div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label className="text-xs">{label}</Label>
      {children}
    </div>
  );
}
