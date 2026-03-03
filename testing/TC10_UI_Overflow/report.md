# TC10: Scanner Name Overflow & UI Clipping

## 📋 Scenario
- Action: Register a scanner with an extremely long name (50+ characters).
- Heavy Testing Aspect: Check the ScannerCard layout.

## ✅ Status
**DONE**

## 🔍 Findings
- `TextOverflow.Ellipsis` correctly truncates names.
- 3-button layout remains vertically aligned and usable.

## 📝 Comments
Material 3 cards auto-expand slightly but stay within list bounds.
