#!/usr/bin/env python3
# blex-mode-check.py — runs on every boot, decides local vs cloud vs unprovisioned
import json, os, sys

MODE_JSON   = "/etc/blex/mode.json"
RUN_DIR     = "/run/blex"
ENV_FILE    = "/etc/blex/env"

os.makedirs(RUN_DIR, exist_ok=True)

# Clean up old runtime flags
for f in ["mode-local", "mode-cloud", "unprovisioned"]:
    try: os.remove(f"{RUN_DIR}/{f}")
    except FileNotFoundError: pass

if not os.path.exists(MODE_JSON):
    print("[BLEX-MODE] Not provisioned — starting provisioner only", flush=True)
    open(f"{RUN_DIR}/unprovisioned", "w").close()
    sys.exit(0)

with open(MODE_JSON) as f:
    cfg = json.load(f)

mode      = cfg.get("mode", "cloud")
tenant_id = cfg.get("tenant_id", "default")

print(f"[BLEX-MODE] mode={mode} tenant={tenant_id}", flush=True)

# Write runtime flag for systemd ConditionPathExists
flag = "mode-local" if mode == "local" else "mode-cloud"
open(f"{RUN_DIR}/{flag}", "w").close()

# Write /etc/blex/env for other services to read
env_content = f"TENANT_ID={tenant_id}\nMODE={mode}\n"
with open(ENV_FILE, "w") as f:
    f.write(env_content)

print(f"[BLEX-MODE] Runtime flag: /run/blex/{flag}", flush=True)
print(f"[BLEX-MODE] Env written: {ENV_FILE}", flush=True)
