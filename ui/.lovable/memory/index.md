BLE asset tracking dashboard "bleX" for hospital sites — cool slate + teal palette, Inter + JetBrains Mono fonts

- Brand: bleX
- Context: Hospital asset tracking (not warehouse)
- Palette: slate foreground (215 25% 17%), teal primary (173 58% 39%), status colors for active/idle/offline
- Pages: Dashboard, Zone Detail (/zones/:id), Logs (with date filter), Assets, Asset Detail (/assets/:id)
- Beacon shapes: triangle, card, pebble — rendered as SVG BeaconIcon component
- Mock data in src/lib/data.ts — hospital zones (Emergency Ward, ICU, Radiology, Pharmacy, etc.)
- Movement toast: floating notification showing live asset movements (bottom-right)
- Notification bell: header dropdown for alerts (scanner offline, low battery, system updates)
- Visualize: 3D mockup feature planned — "Coming soon" CTA on dashboard
- Subtle page gradient background on #root
- No dark mode toggle needed
- Polling 1s for live movements — to be improved later
