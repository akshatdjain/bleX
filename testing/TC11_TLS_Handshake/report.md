# TC11: TLS 1.3 Handshake Resilience

## 📋 Scenario
- Action: Enable TLS on the broker, upload a custom `ca.crt` to the app. Point a scanner to the TLS port.

## ⌛ Status
**PENDING**

## 🔍 Findings
- Handshake logic is complete in `MqttManager`.
- TrustManager bypass for IP mismatches verified during previous build session.

## 📝 Comments
Need to confirm certificate pinning stability.
