# TC14: Wi-Fi "Zombie" Recovery (Safety Fallback)

## 📋 Scenario
- Action: Provision a scanner with WRONG Wi-Fi credentials.

## ⌛ Status
**PENDING (Field Soak Test)**

## 🔍 Findings
- Recovery loop exists in `provisioner_service.py`.
- Threshold is currently set to 120 seconds.

## 📝 Comments
Must ensure the 'setup' hotspot is available during recovery.
