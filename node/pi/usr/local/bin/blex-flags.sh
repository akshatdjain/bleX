#!/bin/bash
# blex-flags.sh — translates /etc/blex/blex.env into systemd flag files in /run/blex/.
# Run by blex-mode.service at boot, and by provisioner_service.py after writing blex.env.
# The flag files exist solely so systemd ConditionPathExists= can gate other services.

set -e
mkdir -p /run/blex
rm -f /run/blex/mode-* /run/blex/role-* /run/blex/unprovisioned

if [ ! -f /etc/blex/blex.env ]; then
    touch /run/blex/unprovisioned
    echo "[BLEX-FLAGS] /etc/blex/blex.env not present — wrote unprovisioned flag" >&2
    exit 0
fi

# Source blex.env into this shell (set -a auto-exports each var).
set -a
. /etc/blex/blex.env
set +a

touch "/run/blex/mode-${MODE:-cloud}"
touch "/run/blex/role-${ROLE:-master}"

echo "[BLEX-FLAGS] mode=${MODE:-cloud} role=${ROLE:-master} tenant=${TENANT_ID:-?}" >&2
