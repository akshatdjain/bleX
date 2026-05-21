import { useRef, useEffect, useState } from "react";
import gsap from "gsap";

const C = {
  bg:         "#FAFBFC",
  card:       "#FFFFFF",
  border:     "rgba(15,23,42,0.08)",
  borderMid:  "rgba(15,23,42,0.12)",
  text:       "#0F172A",
  textMuted:  "#64748B",
  teal:       "#005F67",
  tealLight:  "#028994",
  mono:       "'Fira Code', 'JetBrains Mono', monospace",
  sans:       "'Inter', sans-serif",
};

const ROOMS = [
  { id: "emergency", label: "Emergency",  sub: "201", x: 18,  y: 18,  w: 132, h: 116, accent: "#EF4444" },
  { id: "icu",       label: "ICU",         sub: "202", x: 184, y: 18,  w: 132, h: 116, accent: "#F59E0B" },
  { id: "surgery",   label: "Surgery",     sub: "203", x: 350, y: 18,  w: 132, h: 116, accent: "#005F67" },
  { id: "recovery",  label: "Recovery",    sub: "204", x: 18,  y: 206, w: 132, h: 116, accent: "#22C55E" },
  { id: "storage",   label: "Supply Rm",   sub: "205", x: 184, y: 206, w: 132, h: 116, accent: "#94A3B8" },
  { id: "nurses",    label: "Nurses Stn",  sub: "206", x: 350, y: 206, w: 132, h: 116, accent: "#6366F1" },
];

const rc = (id: string) => {
  const r = ROOMS.find(x => x.id === id)!;
  return { x: r.x + r.w / 2, y: r.y + r.h / 2 };
};

const ASSETS = [
  { id: "wc",    label: "W",  color: "#EF4444", name: "Wheelchair #3", route: ["emergency", "recovery", "icu",     "emergency"] },
  { id: "iv",    label: "IV", color: "#F59E0B", name: "IV Pump #7",    route: ["icu",       "surgery",  "nurses",   "icu"] },
  { id: "def",   label: "D",  color: "#6366F1", name: "Defib Unit",    route: ["recovery",  "nurses",   "surgery",  "recovery"] },
];

interface LogEntry { id: number; asset: string; from: string; to: string; color: string; ts: number; }
let _id = 0;

export function HospitalDemo() {
  const assetRefs  = useRef<Record<string, SVGGElement | null>>({});
  const [logs, setLogs]     = useState<LogEntry[]>([]);
  const [zones, setZones]   = useState<Record<string, string>>({
    wc:  "Emergency", iv: "ICU", def: "Recovery",
  });
  const [, tick] = useState(0);

  // Live seconds ticker
  useEffect(() => {
    const t = setInterval(() => tick(n => n + 1), 1000);
    return () => clearInterval(t);
  }, []);

  function ago(ts: number) {
    const s = Math.floor((Date.now() - ts) / 1000);
    if (s < 5)  return "just now";
    if (s < 60) return `${s}s ago`;
    return `${Math.floor(s / 60)}m ago`;
  }

  useEffect(() => {
    const mm = gsap.matchMedia();

    mm.add("(prefers-reduced-motion: no-preference)", () => {
      const tls: gsap.core.Timeline[] = [];

      ASSETS.forEach((asset, ai) => {
        const el = assetRefs.current[asset.id];
        if (!el) return;

        const start = rc(asset.route[0]);
        gsap.set(el, { x: start.x, y: start.y });

        const tl = gsap.timeline({ repeat: -1, delay: ai * 2.2 });

        for (let i = 0; i < asset.route.length - 1; i++) {
          const fromId = asset.route[i];
          const toId   = asset.route[i + 1];
          const dest   = rc(toId);
          const fromRoom = ROOMS.find(r => r.id === fromId)!;
          const toRoom   = ROOMS.find(r => r.id === toId)!;

          const dur   = 3.8 + Math.random() * 2.4; // 3.8–6.2s move
          const dwell = 2.5 + Math.random() * 3.5;  // 2.5–6s dwell

          tl.to(el, {
            x: dest.x,
            y: dest.y,
            duration: dur,
            ease: "power1.inOut",
            onStart: () => {
              // Log entry fires when movement begins
              const entry: LogEntry = {
                id: ++_id,
                asset: asset.name,
                from: fromRoom.label,
                to:   toRoom.label,
                color: asset.color,
                ts: Date.now(),
              };
              setLogs(prev => [entry, ...prev].slice(0, 5));
            },
            onComplete: () => {
              // Location updates only when asset ARRIVES
              setZones(prev => ({ ...prev, [asset.id]: toRoom.label }));
            },
          }).to(el, { duration: dwell });
        }

        tls.push(tl);
      });

      return () => tls.forEach(t => t.kill());
    });

    mm.add("(prefers-reduced-motion: reduce)", () => {
      ASSETS.forEach(a => {
        const el = assetRefs.current[a.id];
        if (el) gsap.set(el, { x: rc(a.route[0]).x, y: rc(a.route[0]).y });
      });
    });

    return () => mm.revert();
  }, []);

  return (
    <div style={{ display: "flex", gap: "18px", alignItems: "flex-start", flexWrap: "wrap" }}>

      {/* ── Floor plan ── */}
      <div style={{
        flex: "1 1 0",
        borderRadius: "16px",
        border: `1px solid ${C.border}`,
        background: C.card,
        overflow: "hidden",
        boxShadow: "0 1px 4px rgba(0,0,0,0.04), 0 4px 16px rgba(0,0,0,0.04)",
      }}>
        {/* Header bar */}
        <div style={{
          padding: "13px 18px",
          borderBottom: `1px solid ${C.border}`,
          display: "flex", alignItems: "center", gap: "10px",
          background: "#FAFBFC",
        }}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={C.teal} strokeWidth="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z"/><polyline points="9,22 9,12 15,12 15,22"/>
          </svg>
          <span style={{ fontFamily: C.sans, fontSize: "12.5px", fontWeight: 600, color: C.text }}>
            City General — Floor 2
          </span>
          <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: "6px" }}>
            <span style={{ width: "6px", height: "6px", borderRadius: "50%", background: "#22C55E", display: "inline-block", animation: "blink 2s ease-in-out infinite" }} />
            <span style={{ fontFamily: C.mono, fontSize: "10px", color: "#22C55E", fontWeight: 600, letterSpacing: "1px" }}>LIVE</span>
          </div>
        </div>

        {/* SVG map */}
        <div style={{ padding: "18px 18px 14px" }}>
          <svg viewBox="0 0 500 340" style={{ width: "100%", height: "auto", overflow: "visible" }}>
            {/* Background grid */}
            <defs>
              <pattern id="grid" width="20" height="20" patternUnits="userSpaceOnUse">
                <path d="M 20 0 L 0 0 0 20" fill="none" stroke="rgba(15,23,42,0.04)" strokeWidth="0.5"/>
              </pattern>
            </defs>
            <rect width="500" height="340" fill="url(#grid)" />

            {/* Corridor */}
            <rect x="0" y="148" width="500" height="44" fill="rgba(15,23,42,0.025)" stroke="rgba(15,23,42,0.06)" strokeWidth="0.5" />
            <text x="250" y="173" textAnchor="middle" fill="rgba(15,23,42,0.25)" fontSize="8.5" fontFamily="monospace" letterSpacing="2">CORRIDOR</text>

            {/* Rooms */}
            {ROOMS.map((r) => (
              <g key={r.id}>
                {/* Room fill */}
                <rect x={r.x} y={r.y} width={r.w} height={r.h} rx="6"
                  fill={`${r.accent}08`}
                  stroke={`${r.accent}35`}
                  strokeWidth="1.5"
                />
                {/* Top accent bar */}
                <rect x={r.x} y={r.y} width={r.w} height="3" rx="6"
                  fill={`${r.accent}60`}
                />

                {/* Room label */}
                <text x={r.x + r.w / 2} y={r.y + 24} textAnchor="middle"
                  fill={r.accent} fontSize="9" fontFamily="monospace" fontWeight="700" letterSpacing="0.3">
                  {r.label.toUpperCase()}
                </text>
                <text x={r.x + r.w / 2} y={r.y + 37} textAnchor="middle"
                  fill={r.accent} fontSize="7.5" fontFamily="monospace" opacity="0.55">
                  RM {r.sub}
                </text>

                {/* Scanner chip */}
                <rect x={r.x + r.w / 2 - 18} y={r.y + r.h - 24} width="36" height="14" rx="3"
                  fill={`${r.accent}15`} stroke={`${r.accent}30`} strokeWidth="0.8" />
                <text x={r.x + r.w / 2} y={r.y + r.h - 14} textAnchor="middle"
                  fill={r.accent} fontSize="6.5" fontFamily="monospace" opacity="0.65">SCANNER</text>
              </g>
            ))}

            {/* Asset dots */}
            {ASSETS.map((asset) => (
              <g key={asset.id} ref={(el) => { assetRefs.current[asset.id] = el; }} style={{ transformOrigin: "0 0" }}>
                {/* Outer glow ring */}
                <circle r="14" fill={asset.color} opacity="0.08" />
                {/* Shadow ring */}
                <circle r="9" fill="rgba(0,0,0,0.08)" cy="1" />
                {/* Main dot */}
                <circle r="9" fill={asset.color} />
                <circle r="9" fill="none" stroke="white" strokeWidth="1.5" opacity="0.5" />
                {/* Label */}
                <text textAnchor="middle" y="4" fill="white"
                  fontSize={asset.label.length > 1 ? "5.5" : "7"}
                  fontFamily="monospace" fontWeight="800">{asset.label}</text>
              </g>
            ))}
          </svg>

          {/* Legend */}
          <div style={{ display: "flex", gap: "14px", paddingTop: "10px", flexWrap: "wrap" }}>
            {ASSETS.map((a) => (
              <div key={a.id} style={{ display: "flex", alignItems: "center", gap: "5px" }}>
                <div style={{ width: "8px", height: "8px", borderRadius: "50%", background: a.color }} />
                <span style={{ fontFamily: C.mono, fontSize: "10px", color: C.textMuted }}>{a.name}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* ── Right panel ── */}
      <div style={{ flex: "1 1 220px", maxWidth: "260px", display: "flex", flexDirection: "column", gap: "12px" }}>

        {/* Live Locations */}
        <div style={{
          borderRadius: "14px", border: `1px solid ${C.border}`, background: C.card, overflow: "hidden",
          boxShadow: "0 1px 3px rgba(0,0,0,0.04)",
        }}>
          <div style={{ padding: "11px 15px", borderBottom: `1px solid ${C.border}`, display: "flex", alignItems: "center", gap: "7px" }}>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke={C.tealLight} strokeWidth="2.5">
              <circle cx="12" cy="12" r="3"/><path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
            </svg>
            <span style={{ fontFamily: C.sans, fontSize: "11px", fontWeight: 600, color: C.tealLight, letterSpacing: "0.4px", textTransform: "uppercase" as const }}>
              Live Locations
            </span>
          </div>
          <div style={{ padding: "6px 0" }}>
            {ASSETS.map((asset) => (
              <div key={asset.id} style={{
                padding: "7px 15px",
                display: "flex", alignItems: "center", justifyContent: "space-between",
                gap: "8px",
              }}>
                <div style={{ display: "flex", alignItems: "center", gap: "7px", minWidth: 0 }}>
                  <div style={{ width: "7px", height: "7px", borderRadius: "50%", background: asset.color, flexShrink: 0 }} />
                  <span style={{ fontFamily: C.sans, fontSize: "11.5px", fontWeight: 500, color: C.text, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" as const }}>
                    {asset.name}
                  </span>
                </div>
                <span style={{
                  fontFamily: C.mono, fontSize: "9.5px", fontWeight: 600,
                  color: asset.color, whiteSpace: "nowrap" as const,
                  background: `${asset.color}12`, padding: "2px 6px", borderRadius: "4px",
                }}>
                  {zones[asset.id]}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* History */}
        <div style={{
          borderRadius: "14px", border: `1px solid ${C.border}`, background: C.card, overflow: "hidden",
          boxShadow: "0 1px 3px rgba(0,0,0,0.04)",
        }}>
          <div style={{ padding: "11px 15px", borderBottom: `1px solid ${C.border}`, display: "flex", alignItems: "center", gap: "7px" }}>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke={C.tealLight} strokeWidth="2.5">
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
            </svg>
            <span style={{ fontFamily: C.sans, fontSize: "11px", fontWeight: 600, color: C.tealLight, letterSpacing: "0.4px", textTransform: "uppercase" as const }}>
              History
            </span>
          </div>
          <div style={{ padding: "4px 0", maxHeight: "260px", overflow: "hidden" }}>
            {logs.length === 0 ? (
              <div style={{ padding: "28px 15px", textAlign: "center" }}>
                <div style={{ width: "7px", height: "7px", borderRadius: "50%", background: C.tealLight, margin: "0 auto 10px", animation: "blink 1.5s ease-in-out infinite" }} />
                <p style={{ fontFamily: C.sans, fontSize: "11px", color: C.textMuted }}>Waiting for movement...</p>
              </div>
            ) : (
              logs.map((log, i) => (
                <div key={log.id} style={{
                  padding: "7px 15px",
                  borderBottom: i < logs.length - 1 ? `1px solid rgba(15,23,42,0.05)` : "none",
                  animation: i === 0 ? "slideDown 0.22s ease" : "none",
                }}>
                  <div style={{ display: "flex", alignItems: "center", gap: "6px", marginBottom: "3px" }}>
                    <div style={{ width: "5px", height: "5px", borderRadius: "50%", background: log.color, flexShrink: 0 }} />
                    <span style={{ fontFamily: C.sans, fontSize: "11px", fontWeight: 600, color: C.text, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" as const }}>
                      {log.asset}
                    </span>
                    <span style={{ marginLeft: "auto", fontFamily: C.mono, fontSize: "9px", color: C.textMuted, flexShrink: 0, whiteSpace: "nowrap" as const }}>
                      {ago(log.ts)}
                    </span>
                  </div>
                  <div style={{ paddingLeft: "11px", display: "flex", alignItems: "center", gap: "4px" }}>
                    <span style={{ fontFamily: C.mono, fontSize: "9px", color: C.textMuted }}>{log.from}</span>
                    <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke={C.tealLight} strokeWidth="2.5" style={{ flexShrink: 0 }}>
                      <path d="M5 12h14M12 5l7 7-7 7"/>
                    </svg>
                    <span style={{ fontFamily: C.mono, fontSize: "9px", color: C.teal, fontWeight: 700 }}>{log.to}</span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      <style>{`
        @keyframes blink {
          0%, 100% { opacity: 1; }
          50%       { opacity: 0.3; }
        }
        @keyframes slideDown {
          from { opacity: 0; transform: translateY(-8px); }
          to   { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </div>
  );
}
