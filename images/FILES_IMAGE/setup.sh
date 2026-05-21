#!/bin/bash
# =============================================================
# BleX Pi Setup Script
# Run this on a fresh Pi OS (Bookworm 64-bit) as the blex user.
# =============================================================
set -e

BLEX_USER="blex"
BLEX_DIR="/home/$BLEX_USER/Desktop/blex"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "[SETUP] BleX Pi setup starting..."

# -------------------------------------------------
# 1. System packages
# -------------------------------------------------
echo "[SETUP] Installing system packages..."
sudo apt-get update -qq
sudo apt-get install -y -qq \
    mosquitto \
    mosquitto-clients \
    redis-server \
    python3-pip \
    bluetooth \
    bluez \
    rfkill \
    network-manager

# -------------------------------------------------
# 2. Python dependencies
# -------------------------------------------------
echo "[SETUP] Installing Python packages..."
pip3 install -r "$SCRIPT_DIR/requirements.txt" --break-system-packages

# -------------------------------------------------
# 3. Create blex user (if not exists)
# -------------------------------------------------
if ! id "$BLEX_USER" &>/dev/null; then
    echo "[SETUP] Creating user $BLEX_USER..."
    sudo useradd -m -s /bin/bash "$BLEX_USER"
    sudo usermod -aG sudo,bluetooth "$BLEX_USER"
fi

# -------------------------------------------------
# 4. Copy code to Pi directory
# -------------------------------------------------
echo "[SETUP] Copying BleX code..."
sudo mkdir -p "$BLEX_DIR/master" "$BLEX_DIR/scanner"
sudo cp "$SCRIPT_DIR/master/"*.py "$BLEX_DIR/master/"
sudo cp "$SCRIPT_DIR/master/"*.sh "$BLEX_DIR/master/"
sudo cp "$SCRIPT_DIR/scanner/"*.py "$BLEX_DIR/scanner/"
sudo chown -R "$BLEX_USER:$BLEX_USER" "$BLEX_DIR"
sudo chmod +x "$BLEX_DIR/master/master_stack.sh"

# -------------------------------------------------
# 5. Install mode-check script
# -------------------------------------------------
echo "[SETUP] Installing blex-mode-check.py..."
sudo cp "$SCRIPT_DIR/scripts/blex-mode-check.py" /usr/local/bin/blex-mode-check.py
sudo chmod +x /usr/local/bin/blex-mode-check.py

# -------------------------------------------------
# 6. Create /etc/blex directory
# -------------------------------------------------
echo "[SETUP] Creating /etc/blex..."
sudo mkdir -p /etc/blex

# -------------------------------------------------
# 7. Configure Redis
# -------------------------------------------------
echo "[SETUP] Configuring Redis..."
sudo sed -i 's/^# requirepass.*/requirepass 1234/' /etc/redis/redis.conf || true
sudo systemctl enable redis-server
sudo systemctl restart redis-server

# -------------------------------------------------
# 8. Configure Mosquitto
# -------------------------------------------------
echo "[SETUP] Configuring Mosquitto..."
sudo cp "$SCRIPT_DIR/mqtt/mosquitto.conf" /etc/mosquitto/mosquitto.conf
sudo cp "$SCRIPT_DIR/mqtt/conf.d/listener.conf" /etc/mosquitto/conf.d/listener.conf
sudo systemctl enable mosquitto
sudo systemctl restart mosquitto

# -------------------------------------------------
# 9. Install systemd services
# -------------------------------------------------
echo "[SETUP] Installing systemd services..."
for svc in blex-mode blex-provisioner blex-scanner blex-master; do
    sudo cp "$SCRIPT_DIR/systemd/$svc.service" /etc/systemd/system/
done

sudo systemctl daemon-reload
sudo systemctl enable blex-mode.service
sudo systemctl enable blex-provisioner.service
sudo systemctl enable blex-scanner.service
sudo systemctl enable blex-master.service

# -------------------------------------------------
# 10. Add blex user to sudoers for service restarts
#     (provisioner needs to restart services without password)
# -------------------------------------------------
SUDOERS_LINE="$BLEX_USER ALL=(ALL) NOPASSWD: /usr/bin/systemctl restart blex-*.service, /usr/bin/systemctl stop blex-*.service, /usr/bin/tee /etc/blex/*, /usr/bin/mkdir -p /etc/blex, /usr/bin/nmcli *"
if ! sudo grep -qF "$BLEX_USER ALL=(ALL) NOPASSWD" /etc/sudoers.d/blex 2>/dev/null; then
    echo "$SUDOERS_LINE" | sudo tee /etc/sudoers.d/blex > /dev/null
    sudo chmod 440 /etc/sudoers.d/blex
fi

echo ""
echo "[SETUP] Done! Reboot to start BleX services."
echo "        After reboot, use the Android app to provision this Pi."
echo ""
echo "  Logs:  journalctl -u blex-scanner -f"
echo "         journalctl -u blex-master -f"
echo "         journalctl -u blex-provisioner -f"
