#!/bin/bash

# setup_pi_provisioning.sh
# Run this once on your Pi images to prepare them for Zero-Touch Provisioning.

echo "--- Configuring AsseTrack Provisioning Fallback ---"

# 1. Add the 'setup' hotspot as a known connection
# Priority is set to 1 (default site WiFi usually has higher priority)
nmcli con add type wifi ifname wlan0 con-name "AsseTrack-Setup" ssid "setup" -- \
    wifi-sec.key-mgmt wpa-psk wifi-sec.psk "setup@1234" 

# 2. Set autoconnect-priority
# Higher numbers mean try this first. 
# We set 'setup' to 1. Your site WiFi will stay at the default (usually 0 or higher).
# This ensures that if it can't find its main network, it tries 'setup'.
nmcli con mod "AsseTrack-Setup" connection.autoconnect-priority 1
nmcli con mod "AsseTrack-Setup" connection.autoconnect-retries 3

# 3. Enable NetworkManager Wait Online
# Ensures discovery scripts don't fail immediately on boot
systemctl enable NetworkManager-wait-online.service

echo "Done. Please copy 'discovery_broadcast.py' and 'provisioner_service.py' to /home/pi/scanner"
echo "and add them to your /etc/rc.local or create a systemd service for them."
