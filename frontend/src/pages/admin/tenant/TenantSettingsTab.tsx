import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { getTenant, updateTenant, TenantUpdate } from "@/lib/tenants";
import { useToast } from "@/hooks/use-toast";

export default function TenantSettingsTab() {
  const { tenantId = "" } = useParams<{ tenantId: string }>();
  const qc = useQueryClient();
  const { toast } = useToast();

  const { data: tenant } = useQuery({
    queryKey: ["tenant", tenantId],
    queryFn: () => getTenant(tenantId),
    enabled: !!tenantId,
  });

  const [form, setForm] = useState<TenantUpdate>({});
  const [saving, setSaving] = useState(false);

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
    }
  }, [tenant?.tenant_id]);

  async function save() {
    if (!tenant) return;
    setSaving(true);
    try {
      const patch: TenantUpdate = {};
      for (const key of Object.keys(form) as (keyof TenantUpdate)[]) {
        const val = form[key];
        const orig = (tenant as any)[key];
        if (val !== orig && val !== undefined) (patch as any)[key] = val;
      }
      if (Object.keys(patch).length === 0) {
        toast({ title: "No changes to save" });
        setSaving(false);
        return;
      }
      const res = await updateTenant(tenant.tenant_id, patch);
      toast({
        title: "Saved",
        description: `Updated: ${res.updated.filter((f) => f !== "tid").join(", ") || "—"}`,
      });
      qc.invalidateQueries({ queryKey: ["tenant", tenantId] });
      qc.invalidateQueries({ queryKey: ["admin-tenants"] });
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

  if (!tenant) {
    return <p className="text-sm text-muted-foreground">Loading…</p>;
  }

  return (
    <div className="space-y-5 max-w-3xl">
      <div>
        <h2 className="text-lg font-semibold">Settings</h2>
        <p className="text-xs text-muted-foreground">
          Tenant deployment configuration. Pi devices read these values at provisioning.
        </p>
      </div>

      <Card>
        <CardContent className="p-5 space-y-6">
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
                onChange={(e) => setForm({ ...form, contact_email: e.target.value })}
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
                onChange={(e) => setForm({ ...form, mqtt_username: e.target.value })}
              />
            </Field>
            <Field label="MQTT Password">
              <Input
                type="password"
                value={form.mqtt_password ?? ""}
                onChange={(e) => setForm({ ...form, mqtt_password: e.target.value })}
              />
            </Field>
            <Field label="Tablet Host (fallback)">
              <Input
                placeholder="e.g. 192.168.29.42"
                value={form.tablet_host ?? ""}
                onChange={(e) => setForm({ ...form, tablet_host: e.target.value })}
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

          <div className="flex justify-end gap-2 pt-4 border-t">
            <Button onClick={save} disabled={saving}>
              {saving ? "Saving…" : "Save Changes"}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
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
