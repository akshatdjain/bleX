# TC08: Multi-Identity Conflict (MAC Spoofing Check)

## 📋 Scenario
- Action: Try to register a scanner with a name, then try to register another scanner using the SAME MAC address but a different name.

## ⌛ Status
**PENDING (API Validation)**

## 🔍 Findings
- Backend API should ideally return a 409 Conflict.
- Current app behavior replaces local registration status.

## 📝 Comments
Need to confirm API layer behavior for ID conflicts.
