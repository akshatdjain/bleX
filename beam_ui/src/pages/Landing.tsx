import { Link } from "react-router-dom";

// ── Design tokens extracted from sigmatic.ai ──────────────────
const T = {
  tealDark:   "#005F67",   // h1, h3, nav links, borders
  tealMid:    "#028994",   // paragraphs, section labels
  tealLight:  "#D5F5F4",   // h2 on dark backgrounds
  heroBg:     "linear-gradient(135deg, #e8f8f7 0%, #d5f2f0 40%, #eafaf9 100%)",
  darkBg:     "#005F67",   // CTA section bg
  cardBorder: "rgba(0,95,103,0.12)",
  sectionBg:  "#f7fdfc",
  white:      "#fff",
  bodyText:   "#4a6e72",
  fontHead:   "'Sora', sans-serif",
  fontBody:   "'Inter', sans-serif",
};

// Sunburst SVG matching sigmatic.ai logo style
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

// Section label — matches sigmatic.ai small teal labels above headings
function Label({ children }: { children: React.ReactNode }) {
  return (
    <p style={{
      fontFamily: T.fontBody, fontSize: "14px", fontWeight: 500,
      color: T.tealMid, marginBottom: "12px", letterSpacing: "0.2px",
    }}>{children}</p>
  );
}

// Pill chip — matches the hero category badges
function Chip({ children }: { children: React.ReactNode }) {
  return (
    <span style={{
      display: "inline-block", padding: "4px 14px", borderRadius: "20px",
      border: `1px solid ${T.cardBorder}`, background: "rgba(0,95,103,0.06)",
      color: T.tealMid, fontFamily: T.fontBody, fontSize: "13px",
      fontWeight: 500, marginBottom: "20px",
    }}>{children}</span>
  );
}

export default function Landing() {
  return (
    <div style={{ fontFamily: T.fontHead, background: T.white, color: T.tealDark, overflowX: "hidden" }}>

      {/* ── IMPORT FONTS ────────────────────────────────────────── */}
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Sora:wght@400;500;600;700&family=Inter:wght@400;500;600&display=swap');
        * { box-sizing: border-box; margin: 0; padding: 0; }
        a { text-decoration: none; }
        .nav-link:hover { color: ${T.tealDark} !important; opacity: 0.75; }
        .hover-card:hover { box-shadow: 0 8px 32px rgba(0,95,103,0.12); transform: translateY(-2px); }
        .hover-card { transition: box-shadow 0.25s, transform 0.25s; }
        @media (max-width: 768px) {
          .hero-btns { flex-direction: column !important; align-items: stretch !important; }
          .stats-row { flex-direction: column !important; }
          .grid-3 { grid-template-columns: 1fr !important; }
          .grid-2 { grid-template-columns: 1fr !important; }
          .grid-4 { grid-template-columns: 1fr 1fr !important; }
          .split { flex-direction: column !important; }
        }
      `}</style>

      {/* ── NAV ─────────────────────────────────────────────────── */}
      <nav style={{
        display: "flex", alignItems: "center", justifyContent: "space-between",
        padding: "0 48px", height: "68px", borderBottom: `1px solid ${T.cardBorder}`,
        background: T.white, position: "sticky", top: 0, zIndex: 100,
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <Sunburst size={34} />
          <span style={{ fontFamily: T.fontHead, fontWeight: 600, fontSize: "18px", color: T.tealDark, letterSpacing: "-0.4px" }}>BleX</span>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
          <Link to="/login" className="nav-link" style={{
            padding: "8px 20px", fontSize: "14px", fontWeight: 500,
            color: T.tealDark, fontFamily: T.fontBody, borderRadius: "6px",
            transition: "opacity 0.2s",
          }}>Log in</Link>
          <Link to="/register" style={{
            padding: "9px 22px", borderRadius: "8px", fontSize: "14px",
            fontWeight: 600, color: T.white, fontFamily: T.fontBody,
            background: T.tealDark, transition: "opacity 0.2s",
          }}
            onMouseEnter={e => (e.currentTarget.style.opacity = "0.85")}
            onMouseLeave={e => (e.currentTarget.style.opacity = "1")}
          >Get Started</Link>
        </div>
      </nav>

      {/* ── HERO ─────────────────────────────────────────────────── */}
      {/* Matches sigmatic.ai: rounded card with mint gradient, scattered dots, centered */}
      <section style={{ padding: "40px 48px 60px", background: T.white }}>
        <div style={{
          background: T.heroBg,
          borderRadius: "24px", padding: "80px 40px",
          textAlign: "center", position: "relative", overflow: "hidden",
        }}>
          {/* Scattered dots — matches sigmatic.ai hero */}
          {[
            [8,15],[92,20],[15,75],[85,65],[50,5],[20,50],[75,85],[60,40],[35,90],[88,45]
          ].map(([x, y], i) => (
            <div key={i} style={{
              position: "absolute", left: `${x}%`, top: `${y}%`,
              width: i % 3 === 0 ? "8px" : "5px", height: i % 3 === 0 ? "8px" : "5px",
              borderRadius: "50%", background: "rgba(0,95,103,0.18)",
              transform: "translate(-50%,-50%)",
            }} />
          ))}

          <div style={{ position: "relative" }}>
            <Chip>Real-Time BLE Asset Tracking</Chip>

            <h1 style={{
              fontFamily: T.fontHead, fontSize: "clamp(34px, 5vw, 52px)",
              fontWeight: 500, color: T.tealDark, lineHeight: 1.15,
              letterSpacing: "-1.34px", maxWidth: "760px", margin: "0 auto 20px",
            }}>
              Know where everything is.<br />
              <strong style={{ fontWeight: 700 }}>In seconds.</strong>
            </h1>

            <p style={{
              fontFamily: T.fontBody, fontSize: "17px", color: T.tealMid,
              maxWidth: "560px", margin: "0 auto 36px", lineHeight: 1.7,
            }}>
              BleX turns an Android tablet into an enterprise-grade asset tracking hub.
              Attach a beacon, drop a scanner, and see every asset move in real time — no cloud required.
            </p>

            <div className="hero-btns" style={{ display: "flex", gap: "12px", justifyContent: "center", flexWrap: "wrap" }}>
              <Link to="/register" style={{
                display: "inline-flex", alignItems: "center", gap: "8px",
                padding: "12px 26px", borderRadius: "8px", fontSize: "15px",
                fontWeight: 600, color: T.white, fontFamily: T.fontBody,
                background: T.tealDark, transition: "opacity 0.2s",
              }}
                onMouseEnter={e => (e.currentTarget.style.opacity = "0.85")}
                onMouseLeave={e => (e.currentTarget.style.opacity = "1")}
              >
                Get Started Free
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
              </Link>
              <Link to="/login" style={{
                display: "inline-flex", alignItems: "center", gap: "8px",
                padding: "12px 26px", borderRadius: "8px", fontSize: "15px",
                fontWeight: 600, color: T.tealDark, fontFamily: T.fontBody,
                border: `1.5px solid ${T.tealDark}`, background: "transparent",
                transition: "background 0.2s",
              }}
                onMouseEnter={e => (e.currentTarget.style.background = "rgba(0,95,103,0.06)")}
                onMouseLeave={e => (e.currentTarget.style.background = "transparent")}
              >Sign In to Dashboard</Link>
            </div>
          </div>
        </div>

        {/* Stats row — below the card, matches sigmatic.ai bottom bar */}
        <div className="stats-row" style={{
          display: "flex", marginTop: "0", borderTop: "none",
          border: `1px solid ${T.cardBorder}`, borderRadius: "12px",
          overflow: "hidden", maxWidth: "640px", margin: "32px auto 0",
        }}>
          {[
            { val: "< 1s",  label: "Latency" },
            { val: "50+",   label: "Scanners per hub" },
            { val: "100%",  label: "Offline-capable" },
          ].map((s, i) => (
            <div key={i} style={{
              flex: 1, padding: "22px 16px", textAlign: "center",
              borderRight: i < 2 ? `1px solid ${T.cardBorder}` : "none",
            }}>
              <div style={{ fontFamily: T.fontHead, fontSize: "26px", fontWeight: 700, color: T.tealDark, letterSpacing: "-1px" }}>{s.val}</div>
              <div style={{ fontFamily: T.fontBody, fontSize: "13px", color: T.tealMid, marginTop: "4px" }}>{s.label}</div>
            </div>
          ))}
        </div>
      </section>

      {/* ── PROBLEM SECTION ─────────────────────────────────────── */}
      {/* Matches sigmatic.ai: split layout — text left, 2×2 problem grid right */}
      <section style={{ padding: "80px 48px", background: T.sectionBg }}>
        <div className="split" style={{ display: "flex", gap: "60px", maxWidth: "1100px", margin: "0 auto", alignItems: "flex-start" }}>
          <div style={{ flex: "0 0 380px" }}>
            <Label>The Problem We Solve</Label>
            <h3 style={{
              fontFamily: T.fontHead, fontSize: "clamp(24px, 3vw, 32px)",
              fontWeight: 500, color: T.tealDark, letterSpacing: "-0.32px", lineHeight: 1.2,
            }}>
              Asset tracking shouldn't require an IT team.
            </h3>
          </div>
          <div className="grid-2" style={{ flex: 1, display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px" }}>
            {[
              "Manual scanner setup via SSH",
              "Cloud MQTT fees & latency",
              "No offline fallback",
              "Data locked in single schema",
            ].map(problem => (
              <div key={problem} style={{
                padding: "20px 24px", borderRadius: "12px",
                border: `1px solid ${T.cardBorder}`, background: T.white,
              }}>
                <h6 style={{ fontFamily: T.fontHead, fontSize: "15px", fontWeight: 600, color: T.tealDark }}>{problem}</h6>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── SEE / DEPLOY / TRACK ─────────────────────────────────── */}
      {/* Matches sigmatic.ai "See / Sense / Operate" 3-column with micro-labels */}
      <section style={{ padding: "80px 48px" }}>
        <div style={{ maxWidth: "1100px", margin: "0 auto" }}>
          <h2 style={{
            fontFamily: T.fontHead, fontSize: "clamp(26px, 3.5vw, 38px)",
            fontWeight: 500, color: T.tealDark, letterSpacing: "-0.4px",
            textAlign: "center", marginBottom: "56px",
          }}>A Complete Picture, Not Partial Answers.</h2>

          <div className="grid-3" style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "32px" }}>
            {[
              {
                micro: "Attach",
                title: "Beacons",
                desc: "Stick iBeacon or Eddystone tags to any asset — equipment, tools, containers. They broadcast BLE every 100ms with RSSI and battery level.",
              },
              {
                micro: "Deploy",
                title: "Scanners",
                desc: "Mount Android tablets or Raspberry Pi nodes at zone boundaries. They auto-discover each other over UDP 9000 — no IP entry, no configuration.",
              },
              {
                micro: "Track",
                title: "In Real-Time",
                desc: "The hub applies Kalman-filtered RSSI and 10-second dwell logic. Zone transitions appear on the dashboard within 1 second.",
              },
            ].map(col => (
              <div key={col.title} className="hover-card" style={{
                padding: "32px 28px", border: `1px solid ${T.cardBorder}`,
                borderRadius: "16px", background: T.white,
              }}>
                <p style={{ fontFamily: T.fontBody, fontSize: "13px", fontWeight: 600, color: T.tealMid, marginBottom: "8px", letterSpacing: "0.3px" }}>{col.micro}</p>
                <h5 style={{ fontFamily: T.fontHead, fontSize: "20px", fontWeight: 600, color: T.tealDark, marginBottom: "14px" }}>{col.title}</h5>
                <p style={{ fontFamily: T.fontBody, fontSize: "14px", color: T.bodyText, lineHeight: 1.7 }}>{col.desc}</p>
              </div>
            ))}
          </div>

          {/* "What you get" bullet list — matches sigmatic.ai */}
          <div style={{
            marginTop: "48px", padding: "32px 40px", borderRadius: "16px",
            border: `1px solid ${T.cardBorder}`, background: T.sectionBg,
            display: "flex", gap: "40px", flexWrap: "wrap",
          }}>
            <p style={{ fontFamily: T.fontBody, fontSize: "13px", fontWeight: 600, color: T.tealMid, minWidth: "120px", paddingTop: "2px" }}>What you get</p>
            <div className="grid-2" style={{ flex: 1, display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
              {[
                "Multi-tenant schema isolation",
                "Auto-provisioning via UDP + HTTP",
                "Embedded MQTT broker (Moquette)",
                "Offline-first edge processing",
                "Battery monitoring via BLE",
                "JWT + httpOnly cookie auth",
                "Cloud bridge via WSS/TLS",
                "Configurable dwell-time logic",
              ].map(item => (
                <div key={item} style={{ display: "flex", alignItems: "flex-start", gap: "10px" }}>
                  <span style={{ color: T.tealDark, fontWeight: 700, marginTop: "1px" }}>•</span>
                  <h6 style={{ fontFamily: T.fontBody, fontSize: "14px", fontWeight: 500, color: T.tealDark }}>{item}</h6>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* ── DESIGNED TO REMOVE BURDEN ──────────────────────────── */}
      {/* Matches sigmatic.ai split section with tab switcher */}
      <section style={{ padding: "80px 48px", background: T.sectionBg }}>
        <div style={{ maxWidth: "1100px", margin: "0 auto" }}>
          <div className="split" style={{ display: "flex", gap: "64px", alignItems: "flex-start" }}>
            <div style={{ flex: "0 0 360px" }}>
              <Label>Validated insights for confident, real-time action.</Label>
              <h2 style={{
                fontFamily: T.fontHead, fontSize: "clamp(24px, 3vw, 34px)",
                fontWeight: 500, color: T.tealDark, letterSpacing: "-0.4px", lineHeight: 1.25,
                marginBottom: "24px",
              }}>Designed to Remove Setup Burden.</h2>
              <Link to="/register" style={{
                display: "inline-flex", alignItems: "center", gap: "8px",
                padding: "11px 24px", borderRadius: "8px", fontSize: "14px",
                fontWeight: 600, color: T.white, fontFamily: T.fontBody,
                background: T.tealDark, transition: "opacity 0.2s",
              }}
                onMouseEnter={e => (e.currentTarget.style.opacity = "0.85")}
                onMouseLeave={e => (e.currentTarget.style.opacity = "1")}
              >Create Free Account</Link>
            </div>
            <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: "20px" }}>
              {[
                {
                  tab: "Provision Scanners",
                  body: "BleX auto-discovers Pi and ESP32 scanners via UDP broadcast. One tap provisions WiFi + MQTT config over HTTP — no SSH, no terminal, no manual IP entry.",
                },
                {
                  tab: "Switch Local ↔ Cloud",
                  body: "Choose local mode (Pi → Tablet MQTT broker) or cloud mode (Pi → DGX over TLS). The tablet auto-fetches the Pi's registered IP from the cloud and reconfigures the bridge instantly.",
                },
              ].map((item, i) => (
                <div key={i} style={{
                  padding: "24px 28px", borderRadius: "14px",
                  border: `1px solid ${T.cardBorder}`, background: T.white,
                }}>
                  <p style={{ fontFamily: T.fontBody, fontSize: "13px", fontWeight: 600, color: T.tealMid, marginBottom: "8px" }}>{item.tab}</p>
                  <p style={{ fontFamily: T.fontBody, fontSize: "14px", color: T.bodyText, lineHeight: 1.7 }}>{item.body}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* ── THE PAYOFF ──────────────────────────────────────────── */}
      {/* Matches sigmatic.ai 2×2 outcome grid */}
      <section style={{ padding: "80px 48px" }}>
        <div style={{ maxWidth: "1100px", margin: "0 auto" }}>
          <div className="split" style={{ display: "flex", gap: "60px", alignItems: "flex-start" }}>
            <div style={{ flex: "0 0 300px" }}>
              <Label>The Payoff</Label>
              <h2 style={{
                fontFamily: T.fontHead, fontSize: "clamp(24px, 3vw, 34px)",
                fontWeight: 500, color: T.tealDark, letterSpacing: "-0.4px", lineHeight: 1.25,
              }}>What This Means for Your Operation</h2>
            </div>
            <div className="grid-2" style={{ flex: 1, display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
              {[
                { title: "Find Assets Instantly", body: "Stop wasting time searching. Every asset shows its last-known zone within 1 second of a scan." },
                { title: "Deploy in Minutes", body: "From unboxing a Pi scanner to live tracking takes under 5 minutes. No IT department required." },
                { title: "Know Battery Status", body: "Read BLE beacon battery over Service UUID 0xFFF0. Get alerts before beacons go dark." },
                { title: "Scale Without Pain", body: "50+ scanners per tablet hub. Add zones, assets, and tenants without changing infrastructure." },
              ].map(card => (
                <div key={card.title} className="hover-card" style={{
                  padding: "28px 24px", borderRadius: "14px",
                  border: `1px solid ${T.cardBorder}`, background: T.white,
                }}>
                  <h5 style={{ fontFamily: T.fontHead, fontSize: "16px", fontWeight: 600, color: T.tealDark, marginBottom: "10px" }}>{card.title}</h5>
                  <p style={{ fontFamily: T.fontBody, fontSize: "14px", color: T.bodyText, lineHeight: 1.65 }}>{card.body}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* ── TENANT ID ────────────────────────────────────────────── */}
      <section style={{ padding: "80px 48px", background: T.sectionBg }}>
        <div style={{ maxWidth: "800px", margin: "0 auto", textAlign: "center" }}>
          <div style={{
            display: "inline-block", padding: "8px 20px", borderRadius: "10px",
            background: "rgba(0,95,103,0.08)", color: T.tealDark,
            fontFamily: "'IBM Plex Mono', 'Courier New', monospace",
            fontWeight: 700, fontSize: "20px", letterSpacing: "4px", marginBottom: "28px",
            border: `1px solid ${T.cardBorder}`,
          }}>HQTJAC</div>
          <h2 style={{
            fontFamily: T.fontHead, fontSize: "clamp(26px, 3.5vw, 38px)",
            fontWeight: 500, color: T.tealDark, letterSpacing: "-0.8px", marginBottom: "16px",
          }}>One Tenant ID. Your entire fleet.</h2>
          <p style={{ fontFamily: T.fontBody, fontSize: "16px", color: T.tealMid, lineHeight: 1.75, marginBottom: "36px" }}>
            When you register, BleX assigns a unique 6-character Tenant ID. Every scanner, beacon, and zone belongs to it. Configure your Pi scanners and Android hubs once — they sync automatically.
          </p>
          <div style={{ display: "flex", gap: "32px", justifyContent: "center", flexWrap: "wrap" }}>
            {["One ID, all devices", "No DNS or IP config", "Multi-tenant isolated"].map(t => (
              <div key={t} style={{ display: "flex", alignItems: "center", gap: "8px", fontFamily: T.fontBody, fontWeight: 500, fontSize: "14px", color: T.tealDark }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={T.tealDark} strokeWidth="2.5"><polyline points="20,6 9,17 4,12"/></svg>
                {t}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── CTA BANNER ───────────────────────────────────────────── */}
      {/* Matches sigmatic.ai dark teal CTA section */}
      <section style={{ background: T.tealDark, padding: "80px 48px", textAlign: "center" }}>
        <h1 style={{
          fontFamily: T.fontHead, fontSize: "clamp(26px, 4vw, 44px)",
          fontWeight: 500, color: T.tealLight, letterSpacing: "-1px", marginBottom: "16px",
        }}>Ready to track your assets?</h1>
        <p style={{ fontFamily: T.fontBody, fontSize: "16px", color: "rgba(213,245,244,0.75)", marginBottom: "36px" }}>
          Get started in minutes. No credit card required.
        </p>
        <Link to="/register" style={{
          display: "inline-flex", alignItems: "center", gap: "8px",
          padding: "14px 32px", borderRadius: "8px", fontSize: "15px",
          fontWeight: 600, color: T.tealDark, fontFamily: T.fontBody,
          background: T.white, transition: "background 0.2s",
        }}
          onMouseEnter={e => (e.currentTarget.style.background = T.tealLight)}
          onMouseLeave={e => (e.currentTarget.style.background = T.white)}
        >
          Create Free Account
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
        </Link>
      </section>

      {/* ── FOOTER ───────────────────────────────────────────────── */}
      {/* Matches sigmatic.ai: logo + links left, copyright + social right */}
      <footer style={{
        padding: "28px 48px", borderTop: `1px solid ${T.cardBorder}`,
        display: "flex", alignItems: "center", justifyContent: "space-between",
        flexWrap: "wrap", gap: "16px", background: T.white,
      }}>
        <div style={{ display: "flex", gap: "32px", alignItems: "center", flexWrap: "wrap" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <Sunburst size={26} />
            <span style={{ fontFamily: T.fontHead, fontWeight: 600, fontSize: "15px", color: T.tealDark }}>BleX</span>
          </div>
          {["Technology", "Sign In", "Register"].map((l, i) => (
            <Link key={l} to={i === 0 ? "/login" : i === 1 ? "/login" : "/register"}
              style={{ fontFamily: T.fontBody, fontSize: "13px", color: T.tealMid, fontWeight: 500 }}
            >{l}</Link>
          ))}
        </div>
        <div style={{ display: "flex", gap: "20px", alignItems: "center" }}>
          <span style={{ fontFamily: T.fontBody, fontSize: "13px", color: T.tealMid }}>
            2026 BleX by Sigmatic AI. All Rights Reserved
          </span>
          <span style={{ fontFamily: T.fontBody, fontSize: "12px", color: "rgba(0,95,103,0.4)" }}>v3.0.6</span>
        </div>
      </footer>
    </div>
  );
}
