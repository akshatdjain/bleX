import { Link } from "react-router-dom";
import { useEffect, useRef } from "react";
import { HospitalDemo } from "@/components/HospitalDemo";

const T = {
  tealDark:   "#005F67",
  tealMid:    "#028994",
  tealLight:  "#D5F5F4",
  heroBg:     "linear-gradient(135deg, #e8f8f7 0%, #d5f2f0 40%, #eafaf9 100%)",
  darkBg:     "#005F67",
  cardBorder: "rgba(0,95,103,0.12)",
  sectionBg:  "#f7fdfc",
  white:      "#fff",
  bodyText:   "#4a6e72",
  fontHead:   "'Sora', sans-serif",
  fontBody:   "'Inter', sans-serif",
  fontMono:   "'Fira Code', monospace",
};

function Sunburst({ size = 36, color = T.tealDark }: { size?: number; color?: string }) {
  const rays = 12;
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none">
      <circle cx="20" cy="20" r="5.5" fill={color} />
      {Array.from({ length: rays }).map((_, i) => {
        const angle = (i * 360) / rays;
        const rad = (angle * Math.PI) / 180;
        return (
          <line key={i}
            x1={20 + 8 * Math.cos(rad)} y1={20 + 8 * Math.sin(rad)}
            x2={20 + 17 * Math.cos(rad)} y2={20 + 17 * Math.sin(rad)}
            stroke={color} strokeWidth="2" strokeLinecap="round"
          />
        );
      })}
    </svg>
  );
}

function Label({ children }: { children: React.ReactNode }) {
  return (
    <p style={{
      fontFamily: T.fontBody, fontSize: "13px", fontWeight: 600,
      color: T.tealMid, marginBottom: "12px", letterSpacing: "0.6px",
      textTransform: "uppercase",
    }}>{children}</p>
  );
}

// Animated counter hook
function useCounter(target: number, duration = 1200) {
  const ref = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    let start = 0;
    const step = target / (duration / 16);
    const timer = setInterval(() => {
      start = Math.min(start + step, target);
      el.textContent = Math.round(start).toString();
      if (start >= target) clearInterval(timer);
    }, 16);
    return () => clearInterval(timer);
  }, [target, duration]);
  return ref;
}

export default function Landing() {
  const c1 = useCounter(1);
  const c2 = useCounter(50);
  const c3 = useCounter(100);

  return (
    <div className="landing-scroll" style={{ fontFamily: T.fontHead, background: T.white, color: T.tealDark }}>

      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Sora:wght@400;500;600;700;800&family=Inter:wght@400;500;600&family=Fira+Code:wght@400;500&display=swap');
        * { box-sizing: border-box; margin: 0; padding: 0; }
        a { text-decoration: none; }

        .nav-link:hover { opacity: 0.7; }
        .nav-link { transition: opacity 0.2s; }

        .hover-lift { transition: transform 0.22s cubic-bezier(.34,1.56,.64,1), box-shadow 0.22s ease; cursor: default; }
        .hover-lift:hover { transform: translateY(-3px); box-shadow: 0 12px 40px rgba(0,95,103,0.13); }

        .btn-primary { transition: opacity 0.18s, transform 0.18s; }
        .btn-primary:hover { opacity: 0.88; transform: translateY(-1px); }

        .btn-outline { transition: background 0.18s, transform 0.18s; }
        .btn-outline:hover { background: rgba(0,95,103,0.07) !important; transform: translateY(-1px); }

        .tag-pulse {
          animation: pulse-ring 2.4s cubic-bezier(.4,0,.6,1) infinite;
        }
        @keyframes pulse-ring {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.55; }
        }

        @media (max-width: 768px) {
          .hero-btns { flex-direction: column !important; align-items: stretch !important; }
          .stats-row { flex-direction: column !important; }
          .grid-3 { grid-template-columns: 1fr !important; }
          .grid-2 { grid-template-columns: 1fr !important; }
          .split { flex-direction: column !important; }
          .nav-links { display: none !important; }
        }

        @media (prefers-reduced-motion: reduce) {
          .tag-pulse, .hover-lift, .btn-primary, .btn-outline { animation: none !important; transition: none !important; }
        }

        /* Scroll snap — gentle, section by section */
        .landing-scroll { scroll-snap-type: y proximity; overflow-y: scroll; height: 100vh; scroll-behavior: smooth; }
        .snap-section { scroll-snap-align: start; scroll-snap-stop: normal; }
        .snap-hero { scroll-snap-align: start; }
      `}</style>

      {/* ── NAV ── */}
      <nav style={{
        display: "flex", alignItems: "center", justifyContent: "space-between",
        padding: "0 clamp(20px, 5vw, 72px)", height: "68px", borderBottom: `1px solid ${T.cardBorder}`,
        background: "rgba(255,255,255,0.95)", backdropFilter: "blur(12px)",
        position: "sticky", top: 0, zIndex: 100,
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <Sunburst size={32} />
          <span style={{ fontFamily: T.fontHead, fontWeight: 700, fontSize: "17px", color: T.tealDark, letterSpacing: "-0.5px" }}>BleX</span>
        </div>
        <div className="nav-links" style={{ display: "flex", alignItems: "center", gap: "28px" }}>
          <a href="#how" className="nav-link" style={{ fontFamily: T.fontBody, fontSize: "14px", fontWeight: 500, color: T.bodyText }}>How it works</a>
          <a href="#why" className="nav-link" style={{ fontFamily: T.fontBody, fontSize: "14px", fontWeight: 500, color: T.bodyText }}>Why BleX</a>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <Link to="/login" className="nav-link" style={{
            padding: "8px 18px", fontSize: "14px", fontWeight: 500,
            color: T.tealDark, fontFamily: T.fontBody, borderRadius: "6px",
          }}>Sign in</Link>
          <Link to="/register" className="btn-primary" style={{
            padding: "9px 20px", borderRadius: "8px", fontSize: "14px",
            fontWeight: 600, color: T.white, fontFamily: T.fontBody,
            background: T.tealDark, display: "inline-block",
          }}>Get started</Link>
        </div>
      </nav>

      {/* ── HERO ── */}
      <section style={{ padding: "clamp(24px, 3vh, 48px) clamp(20px, 5vw, 72px)", background: T.white, minHeight: "calc(100vh - 68px)", display: "flex", flexDirection: "column" as const, justifyContent: "center" }}>
        <div style={{
          background: T.heroBg, borderRadius: "24px", padding: "88px 40px 72px",
          textAlign: "center", position: "relative", overflow: "hidden",
        }}>
          {/* Ambient dots */}
          {[[8,15],[92,20],[15,75],[85,65],[50,5],[20,50],[75,85],[60,40],[35,90],[88,45]].map(([x, y], i) => (
            <div key={i} style={{
              position: "absolute", left: `${x}%`, top: `${y}%`,
              width: i % 3 === 0 ? "7px" : "4px", height: i % 3 === 0 ? "7px" : "4px",
              borderRadius: "50%", background: "rgba(0,95,103,0.15)",
              transform: "translate(-50%,-50%)",
            }} />
          ))}

          {/* Live indicator pill */}
          <div style={{
            display: "inline-flex", alignItems: "center", gap: "7px",
            padding: "5px 14px 5px 10px", borderRadius: "20px",
            border: `1px solid rgba(0,95,103,0.18)`, background: "rgba(0,95,103,0.05)",
            marginBottom: "28px",
          }}>
            <span className="tag-pulse" style={{
              display: "inline-block", width: "7px", height: "7px",
              borderRadius: "50%", background: "#00b37e",
            }} />
            <span style={{ fontFamily: T.fontBody, fontSize: "13px", fontWeight: 500, color: T.tealMid }}>
              Live zone tracking, no cloud required
            </span>
          </div>

          <h1 style={{
            fontFamily: T.fontHead, fontSize: "clamp(36px, 5.5vw, 58px)",
            fontWeight: 800, color: T.tealDark, lineHeight: 1.1,
            letterSpacing: "-1.8px", maxWidth: "780px", margin: "0 auto 22px",
          }}>
            Every asset, every zone.<br />
            <span style={{ fontWeight: 400, opacity: 0.7 }}>Always current.</span>
          </h1>

          <p style={{
            fontFamily: T.fontBody, fontSize: "17px", color: T.bodyText,
            maxWidth: "520px", margin: "0 auto 40px", lineHeight: 1.75,
            fontWeight: 400,
          }}>
            Plug in a beacon. Mount a scanner. Your tablet becomes a real-time tracking hub,
            completely self-contained. Zone logic runs on the tablet — no cloud needed to track assets.
          </p>

          <div className="hero-btns" style={{ display: "flex", gap: "12px", justifyContent: "center", flexWrap: "wrap" }}>
            <Link to="/register" className="btn-primary" style={{
              display: "inline-flex", alignItems: "center", gap: "8px",
              padding: "13px 28px", borderRadius: "9px", fontSize: "15px",
              fontWeight: 600, color: T.white, fontFamily: T.fontBody,
              background: T.tealDark,
            }}>
              Start free
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
            </Link>
            <Link to="/login" className="btn-outline" style={{
              display: "inline-flex", alignItems: "center", gap: "8px",
              padding: "13px 28px", borderRadius: "9px", fontSize: "15px",
              fontWeight: 600, color: T.tealDark, fontFamily: T.fontBody,
              border: `1.5px solid rgba(0,95,103,0.3)`, background: "transparent",
            }}>Open dashboard</Link>
          </div>
        </div>

        {/* Stats bar */}
        <div className="stats-row" style={{
          display: "flex", border: `1px solid ${T.cardBorder}`, borderRadius: "14px",
          overflow: "hidden", maxWidth: "860px", marginTop: "32px", alignSelf: "center",
          width: "100%",
          boxSizing: "border-box",
        }}>
          {[
            { ref: c1,  suffix: "s",  label: "Detection latency" },
            { ref: c2,  suffix: "+",  label: "Scanners per hub" },
            { ref: c3,  suffix: "%",  label: "Offline capable" },
          ].map((s, i) => (
            <div key={i} style={{
              flex: 1, padding: "22px 16px", textAlign: "center",
              borderRight: i < 2 ? `1px solid ${T.cardBorder}` : "none",
            }}>
              <div style={{ fontFamily: T.fontHead, fontSize: "28px", fontWeight: 800, color: T.tealDark, letterSpacing: "-1.2px" }}>
                <span ref={s.ref}>0</span>{s.suffix}
              </div>
              <div style={{ fontFamily: T.fontBody, fontSize: "12px", color: T.tealMid, marginTop: "4px", fontWeight: 500 }}>{s.label}</div>
            </div>
          ))}
        </div>
      </section>

      {/* ── GLOSSARY: Node / Zone / Asset ── */}
      <section className="snap-section" style={{ padding: "72px clamp(20px, 5vw, 72px)", background: T.white, minHeight: "100vh", display: "flex", flexDirection: "column" as const, justifyContent: "center", borderTop: "1px solid rgba(0,95,103,0.08)" }}>
        <div style={{ maxWidth: "1400px", margin: "0 auto" }}>
          <Label>The BleX vocabulary</Label>
          <h2 style={{
            fontFamily: T.fontHead, fontSize: "clamp(24px, 3vw, 36px)",
            fontWeight: 700, color: T.tealDark, letterSpacing: "-0.6px",
            marginBottom: "48px", lineHeight: 1.15, maxWidth: "520px",
          }}>Three concepts. That's the whole system.</h2>

          <div className="grid-3" style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "20px", alignItems: "stretch" }}>

            {/* 1 — Asset */}
            <div className="hover-lift" style={{ padding: "28px 24px", border: `1px solid ${T.cardBorder}`, borderRadius: "16px", background: T.sectionBg, display: "flex", flexDirection: "column" as const }}>
              <div style={{ width: "44px", height: "44px", borderRadius: "12px", background: "rgba(0,95,103,0.1)", display: "flex", alignItems: "center", justifyContent: "center", marginBottom: "20px" }}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={T.tealDark} strokeWidth="2" strokeLinecap="round">
                  <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z"/><circle cx="12" cy="9" r="2.5"/>
                </svg>
              </div>
              <div style={{ fontFamily: T.fontMono, fontSize: "11px", fontWeight: 600, color: T.tealMid, marginBottom: "8px", letterSpacing: "0.5px", textTransform: "uppercase" as const }}>Asset</div>
              <h4 style={{ fontFamily: T.fontHead, fontSize: "19px", fontWeight: 700, color: T.tealDark, marginBottom: "12px", letterSpacing: "-0.3px" }}>A BLE Beacon</h4>
              <p style={{ fontFamily: T.fontBody, fontSize: "14px", color: T.bodyText, lineHeight: 1.75, marginBottom: "20px", flex: 1 }}>
                A small wireless tag you attach to anything — equipment, vehicles, people. It broadcasts a Bluetooth signal every 100ms. No app, no battery management, no configuration required.
              </p>
              <div style={{ display: "flex", justifyContent: "center", padding: "8px 0" }}>
                <div style={{ width: "86px", height: "86px", borderRadius: "50%", background: "radial-gradient(circle at 35% 35%, rgba(0,137,148,0.15), rgba(0,95,103,0.08))", border: `1.5px solid rgba(0,95,103,0.2)`, display: "flex", alignItems: "center", justifyContent: "center", position: "relative" as const }}>
                  <div style={{ animation: "spinBeacon 8s linear infinite" }}>
                    <svg width="42" height="42" viewBox="0 0 44 44" fill="none">
                      <rect x="8" y="12" width="28" height="20" rx="4" fill={T.tealDark} opacity="0.85"/>
                      <circle cx="22" cy="10" r="3" fill="#00b37e" opacity="0.9" />
                      <path d="M15 34 Q22 38 29 34" stroke={T.tealMid} strokeWidth="1.5" strokeLinecap="round" opacity="0.6"/>
                      <circle cx="22" cy="22" r="4" fill={T.tealLight} opacity="0.7"/>
                    </svg>
                  </div>
                  <div style={{ position: "absolute", inset: "-8px", borderRadius: "50%", border: `1px solid rgba(0,95,103,0.12)`, animation: "pulseRing 2.4s ease-in-out infinite" }} />
                </div>
              </div>
              <style>{`
                @keyframes spinBeacon { from { transform: rotateY(0deg); } to { transform: rotateY(360deg); } }
                @keyframes pulseRing { 0%,100% { transform:scale(1);opacity:1; } 50% { transform:scale(1.08);opacity:0.4; } }
              `}</style>
            </div>

            {/* 2 — Node */}
            <div className="hover-lift" style={{ padding: "28px 24px", border: `1px solid ${T.cardBorder}`, borderRadius: "16px", background: T.sectionBg, display: "flex", flexDirection: "column" as const }}>
              <div style={{ width: "44px", height: "44px", borderRadius: "12px", background: "rgba(0,95,103,0.1)", display: "flex", alignItems: "center", justifyContent: "center", marginBottom: "20px" }}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={T.tealDark} strokeWidth="2" strokeLinecap="round">
                  <circle cx="12" cy="5" r="2"/><circle cx="5" cy="19" r="2"/><circle cx="19" cy="19" r="2"/>
                  <line x1="12" y1="7" x2="5" y2="17"/><line x1="12" y1="7" x2="19" y2="17"/>
                </svg>
              </div>
              <div style={{ fontFamily: T.fontMono, fontSize: "11px", fontWeight: 600, color: T.tealMid, marginBottom: "8px", letterSpacing: "0.5px", textTransform: "uppercase" as const }}>Node</div>
              <h4 style={{ fontFamily: T.fontHead, fontSize: "19px", fontWeight: 700, color: T.tealDark, marginBottom: "12px", letterSpacing: "-0.3px" }}>A BleX Scanner</h4>
              <p style={{ fontFamily: T.fontBody, fontSize: "14px", color: T.bodyText, lineHeight: 1.75, marginBottom: "20px", flex: 1 }}>
                A device that actively listens for BLE beacons using Bluetooth. When a beacon comes in range, the node picks it up, measures signal strength, and reports it continuously.
              </p>
              <div style={{ display: "flex", flexDirection: "column" as const, gap: "8px" }}>
                {[
                  { label: "Raspberry Pi", sub: "Edge device, mounts at zone boundaries" },
                  { label: "Android Tablet", sub: "All-in-one hub: scanner, broker, dashboard" },
                ].map((item) => (
                  <div key={item.label} style={{ display: "flex", alignItems: "flex-start", gap: "10px", padding: "10px 12px", borderRadius: "10px", background: T.white, border: `1px solid ${T.cardBorder}` }}>
                    <div style={{ width: "6px", height: "6px", borderRadius: "50%", background: T.tealMid, flexShrink: 0, marginTop: "5px" }} />
                    <div>
                      <div style={{ fontFamily: T.fontBody, fontSize: "13px", fontWeight: 600, color: T.tealDark }}>{item.label}</div>
                      <div style={{ fontFamily: T.fontBody, fontSize: "12px", color: T.bodyText, lineHeight: 1.5 }}>{item.sub}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* 3 — Zone */}
            <div className="hover-lift" style={{ padding: "28px 24px", border: `1px solid ${T.cardBorder}`, borderRadius: "16px", background: T.sectionBg, display: "flex", flexDirection: "column" as const }}>
              <div style={{ width: "44px", height: "44px", borderRadius: "12px", background: "rgba(0,95,103,0.1)", display: "flex", alignItems: "center", justifyContent: "center", marginBottom: "20px" }}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={T.tealDark} strokeWidth="2" strokeLinecap="round">
                  <rect x="3" y="3" width="18" height="18" rx="3"/><path d="M9 3v18M15 3v18M3 9h18M3 15h18"/>
                </svg>
              </div>
              <div style={{ fontFamily: T.fontMono, fontSize: "11px", fontWeight: 600, color: T.tealMid, marginBottom: "8px", letterSpacing: "0.5px", textTransform: "uppercase" as const }}>Zone</div>
              <h4 style={{ fontFamily: T.fontHead, fontSize: "19px", fontWeight: 700, color: T.tealDark, marginBottom: "12px", letterSpacing: "-0.3px" }}>A Virtual Space</h4>
              <p style={{ fontFamily: T.fontBody, fontSize: "14px", color: T.bodyText, lineHeight: 1.75, marginBottom: "20px", flex: 1 }}>
                A zone is a named location you define — a room, a floor, a warehouse aisle. Add a BleX node to it and it becomes part of your tracking network. Assets that enter are automatically logged.
              </p>
              <div style={{ padding: "16px", borderRadius: "12px", background: T.white, border: `1px solid ${T.cardBorder}` }}>
                <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" as const }}>
                  {["Ward A", "ICU", "Storage", "Corridor"].map((z) => (
                    <div key={z} style={{ padding: "4px 12px", borderRadius: "20px", border: `1px solid rgba(0,95,103,0.2)`, background: "rgba(0,95,103,0.05)", fontFamily: T.fontMono, fontSize: "11px", color: T.tealDark }}>{z}</div>
                  ))}
                </div>
                <p style={{ fontFamily: T.fontBody, fontSize: "11px", color: T.bodyText, marginTop: "10px", lineHeight: 1.5 }}>
                  You name them. BleX tracks movement between them.
                </p>
              </div>
            </div>

          </div>
        </div>
      </section>

      {/* ── HOW IT WORKS + LIVE DEMO (merged) ── */}
      <section id="how" className="snap-section" style={{
        padding: "60px clamp(20px, 5vw, 72px)",
        background: "linear-gradient(135deg, #ffffff 0%, #f7fdfc 100%)",
        borderTop: "1px solid rgba(0,95,103,0.08)",
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column" as const,
        justifyContent: "center",
      }}>
        <div style={{ maxWidth: "1400px", margin: "0 auto", width: "100%" }}>

          {/* Two-column layout: timeline left | HospitalDemo right */}
          <div style={{ display: "flex", gap: "48px", alignItems: "flex-start" }} className="split">

            {/* Left column: narrower, ~280px with vertical timeline */}
            <div style={{ flex: "0 0 280px", display: "flex", flexDirection: "column" as const, alignSelf: "stretch" }}>
              <Label>How it works</Label>
              <h2 style={{
                fontFamily: T.fontHead, fontSize: "clamp(24px, 2.8vw, 32px)",
                fontWeight: 800, color: T.tealDark, letterSpacing: "-0.6px",
                lineHeight: 1.1, marginBottom: "12px",
              }}>Up and running before lunch.</h2>
              <p style={{ fontFamily: T.fontBody, fontSize: "15px", color: T.bodyText, lineHeight: 1.7, marginBottom: "28px" }}>
                No vendor onboarding. No IT team. Three steps and assets are live.
              </p>

              {/* Vertical timeline with dashed connector — flex: 1 so it fills remaining height */}
              <div style={{ position: "relative", paddingLeft: "20px", flex: 1, display: "flex", flexDirection: "column" as const, justifyContent: "space-between" }}>
                {/* Dashed line behind steps */}
                <div style={{
                  position: "absolute",
                  left: "9px",
                  top: "12px",
                  bottom: "12px",
                  width: "2px",
                  borderLeft: "2px dashed rgba(0,95,103,0.2)",
                }} />

                {[
                  { num: "01", title: "Tag your assets", time: "2 min", color: T.tealDark,
                    desc: "Stick a BLE beacon on anything. It broadcasts every 100ms automatically." },
                  { num: "02", title: "Place scanners", time: "5 min", color: T.tealMid,
                    desc: "Set a tablet or Pi at each zone boundary. Auto-discovers over UDP." },
                  { num: "03", title: "Watch it live", time: "Instant", color: "#00b37e",
                    desc: "Zone changes appear in one second. Every move logged with signal and time." },
                ].map((s) => (
                  <div key={s.num} style={{
                    display: "flex",
                    gap: "12px",
                    position: "relative",
                  }}>
                    {/* Small filled circle number badge */}
                    <div style={{
                      width: "20px",
                      height: "20px",
                      borderRadius: "50%",
                      background: s.color,
                      color: T.white,
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      fontFamily: T.fontMono,
                      fontSize: "9px",
                      fontWeight: 700,
                      flexShrink: 0,
                      position: "absolute",
                      left: "-30px",
                      top: "1px",
                    }}>
                      {s.num}
                    </div>

                    {/* Content area */}
                    <div style={{ flex: 1, paddingTop: "2px" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: "6px", marginBottom: "4px", flexWrap: "wrap" }}>
                        <h5 style={{
                          fontFamily: T.fontHead,
                          fontSize: "15px",
                          fontWeight: 700,
                          color: T.tealDark,
                        }}>
                          {s.title}
                        </h5>
                        <span style={{
                          fontFamily: T.fontMono,
                          fontSize: "11px",
                          fontWeight: 600,
                          color: s.color,
                          background: `${s.color}14`,
                          padding: "2px 7px",
                          borderRadius: "4px",
                          border: `1px solid ${s.color}28`,
                        }}>
                          {s.time}
                        </span>
                      </div>
                      <p style={{
                        fontFamily: T.fontBody,
                        fontSize: "13px",
                        color: T.bodyText,
                        lineHeight: 1.65,
                      }}>
                        {s.desc}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Right column: HospitalDemo with enhanced styling */}
            <div style={{
              flex: 1,
              minWidth: 0,
              border: "1px solid rgba(0,95,103,0.15)",
              borderRadius: "16px",
              boxShadow: "0 0 40px rgba(0,95,103,0.06)",
              padding: "20px",
              background: T.white,
            }}>
              <HospitalDemo />
            </div>

          </div>
        </div>
      </section>

      {/* ── WHY BLEX ── */}
      <section id="why" className="snap-section" style={{ padding: "80px clamp(20px, 5vw, 72px)", background: "linear-gradient(135deg, #004f57 0%, #005F67 40%, #006e79 70%, #004a52 100%)", minHeight: "100vh", display: "flex", flexDirection: "column" as const, justifyContent: "center", borderTop: "1px solid rgba(0,95,103,0.08)", position: "relative", overflow: "hidden" }}>
        {/* Radial glow accents */}
        <div style={{ position: "absolute", top: "-10%", right: "-5%", width: "520px", height: "520px", borderRadius: "50%", background: "radial-gradient(circle, rgba(0,137,148,0.18) 0%, transparent 70%)", pointerEvents: "none" }} />
        <div style={{ position: "absolute", bottom: "-8%", left: "10%", width: "380px", height: "380px", borderRadius: "50%", background: "radial-gradient(circle, rgba(213,245,244,0.06) 0%, transparent 70%)", pointerEvents: "none" }} />
        <div style={{ maxWidth: "1400px", margin: "0 auto", position: "relative" }}>
          <div style={{ display: "flex", gap: "56px", alignItems: "flex-end", marginBottom: "48px" }} className="split">
            <div style={{ flex: "0 0 400px" }}>
              <p style={{ fontFamily: T.fontBody, fontSize: "13px", fontWeight: 600, color: "rgba(213,245,244,0.5)", marginBottom: "12px", letterSpacing: "0.6px", textTransform: "uppercase" as const }}>Why BleX</p>
              <h2 style={{
                fontFamily: T.fontHead, fontSize: "clamp(26px, 3.5vw, 42px)",
                fontWeight: 800, color: T.tealLight, letterSpacing: "-1px",
                lineHeight: 1.1,
              }}>Other systems need IT. BleX needs a power outlet.</h2>
            </div>
            <p style={{ fontFamily: T.fontBody, fontSize: "16px", color: "rgba(213,245,244,0.65)", lineHeight: 1.8, flex: 1 }}>
              Enterprise asset tracking has always been an IT project. Weeks of setup, vendor contracts, cloud lock-in. BleX flips this. The hardware is commodity. The software is yours. The setup is a morning.
            </p>
          </div>

          <div className="grid-2" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "14px" }}>
            {[
              {
                icon: "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z",
                title: "Zero network configuration",
                body: "Scanners broadcast their presence over UDP. The tablet finds them. You tap once to provision. No static IPs, no DHCP reservations, no firewall rules to touch.",
              },
              {
                icon: "M1 6s1-1 4-1 5 2 8 2 5-2 8-2v14s-1 1-4 1-5-2-8-2-5 2-8 2z M1 6v14",
                title: "Your data, your schema",
                body: "Every customer gets a fully isolated PostgreSQL schema. No shared tables. Your beacons, zones, and movement logs are yours — exportable, queryable, completely private.",
              },
              {
                icon: "M18.36 6.64A9 9 0 1 1 5.64 6.64 M12 2v10",
                title: "Works when the internet goes down",
                body: "The tablet runs a full MQTT broker locally. Zone logic processes at the edge. A dead WAN connection doesn't stop a single zone update from firing.",
              },
              {
                icon: "M22 12h-4l-3 9L9 3l-3 9H2",
                title: "From one room to fifty zones",
                body: "Add scanners, zones, and assets from the app. No infrastructure changes needed. One tablet hub handles 50+ simultaneous scanners. Switch to cloud mode for unlimited scale.",
              },
            ].map(card => (
              <div key={card.title} style={{
                padding: "28px 26px", borderRadius: "14px",
                border: "1px solid rgba(213,245,244,0.1)",
                background: "rgba(255,255,255,0.05)",
              }}>
                <div style={{ marginBottom: "14px" }}>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={T.tealLight} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d={card.icon}/>
                  </svg>
                </div>
                <h5 style={{ fontFamily: T.fontHead, fontSize: "15px", fontWeight: 700, color: T.tealLight, marginBottom: "10px", letterSpacing: "-0.2px" }}>{card.title}</h5>
                <p style={{ fontFamily: T.fontBody, fontSize: "14px", color: "rgba(213,245,244,0.6)", lineHeight: 1.75 }}>{card.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── TENANT ID + CTA ── */}
      <section className="snap-section" style={{ display: "flex", flexDirection: "column" as const, justifyContent: "flex-start", borderTop: "1px solid rgba(0,95,103,0.08)" }}>
        {/* Tenant ID Section */}
        <section style={{ padding: "72px clamp(20px, 5vw, 72px)", background: T.sectionBg }}>
          <div style={{ maxWidth: "700px", margin: "0 auto", textAlign: "center" }}>
            <div style={{
              display: "inline-block", padding: "8px 22px", borderRadius: "10px",
              background: "rgba(0,95,103,0.07)", color: T.tealDark,
              fontFamily: T.fontMono, fontWeight: 500, fontSize: "18px",
              letterSpacing: "5px", marginBottom: "28px",
              border: `1px solid ${T.cardBorder}`,
            }}>HQTJAC</div>
            <h2 style={{
              fontFamily: T.fontHead, fontSize: "clamp(26px, 3.5vw, 38px)",
              fontWeight: 700, color: T.tealDark, letterSpacing: "-0.8px", marginBottom: "16px", lineHeight: 1.15,
            }}>One ID ties your whole operation together.</h2>
            <p style={{ fontFamily: T.fontBody, fontSize: "16px", color: T.bodyText, lineHeight: 1.8, marginBottom: "40px" }}>
              When you sign up, you get a six-character ID. Your scanners, tablets, beacons, and zones
              all connect to it. Add a new scanner and it shows up automatically, no re-configuration anywhere.
            </p>
            <div style={{ display: "flex", gap: "28px", justifyContent: "center", flexWrap: "wrap" }}>
              {["One ID, every device", "No IP addresses to manage", "Data fully isolated per tenant"].map(t => (
                <div key={t} style={{ display: "flex", alignItems: "center", gap: "8px", fontFamily: T.fontBody, fontWeight: 500, fontSize: "14px", color: T.tealDark }}>
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke={T.tealDark} strokeWidth="2.5"><polyline points="20,6 9,17 4,12"/></svg>
                  {t}
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* CTA Section */}
        <section style={{ background: T.tealDark, padding: "72px clamp(20px, 5vw, 72px)", textAlign: "center", position: "relative", overflow: "hidden", flex: 1 }}>
        {/* Subtle background grid */}
        <div style={{
          position: "absolute", inset: 0, opacity: 0.04,
          backgroundImage: "repeating-linear-gradient(0deg, #fff 0px, #fff 1px, transparent 1px, transparent 40px), repeating-linear-gradient(90deg, #fff 0px, #fff 1px, transparent 1px, transparent 40px)",
        }} />
        <div style={{ position: "relative" }}>
          <h1 style={{
            fontFamily: T.fontHead, fontSize: "clamp(28px, 4.5vw, 48px)",
            fontWeight: 800, color: T.tealLight, letterSpacing: "-1.2px",
            marginBottom: "16px", lineHeight: 1.1,
          }}>Set up in an afternoon.</h1>
          <p style={{ fontFamily: T.fontBody, fontSize: "16px", color: "rgba(213,245,244,0.7)", marginBottom: "40px", lineHeight: 1.7 }}>
            No staging environment. No vendor onboarding call. Just sign up and start tracking.
          </p>
          <Link to="/register" className="btn-primary" style={{
            display: "inline-flex", alignItems: "center", gap: "8px",
            padding: "14px 34px", borderRadius: "9px", fontSize: "15px",
            fontWeight: 700, color: T.tealDark, fontFamily: T.fontBody,
            background: T.white,
          }}>
            Create your account
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
          </Link>
        </div>
        </section>

        {/* Footer moved inside the section */}
        <footer style={{
          padding: "24px clamp(20px, 5vw, 72px)", borderTop: `1px solid ${T.cardBorder}`,
          display: "flex", alignItems: "center", justifyContent: "space-between",
          flexWrap: "wrap", gap: "16px", background: T.white,
        }}>
          <div style={{ display: "flex", gap: "28px", alignItems: "center", flexWrap: "wrap" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <Sunburst size={24} />
              <span style={{ fontFamily: T.fontHead, fontWeight: 700, fontSize: "14px", color: T.tealDark }}>BleX</span>
            </div>
            <Link to="/login" style={{ fontFamily: T.fontBody, fontSize: "13px", color: T.bodyText, fontWeight: 500 }}>Sign in</Link>
            <Link to="/register" style={{ fontFamily: T.fontBody, fontSize: "13px", color: T.bodyText, fontWeight: 500 }}>Register</Link>
          </div>
          <div style={{ display: "flex", gap: "16px", alignItems: "center" }}>
            <span style={{ fontFamily: T.fontBody, fontSize: "13px", color: T.bodyText }}>© 2026 Sigmatic AI</span>
            <span style={{ fontFamily: T.fontMono, fontSize: "11px", color: "rgba(0,95,103,0.35)" }}>v3.1.0</span>
          </div>
        </footer>
      </section>
    </div>
  );
}
