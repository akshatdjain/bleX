import { useState, FormEvent, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "@/lib/auth-context";

const T = {
  tealDark:  "#005F67",
  tealMid:   "#028994",
  heroBg:    "linear-gradient(135deg, #e8f8f7 0%, #d5f2f0 40%, #eafaf9 100%)",
  cardBorder:"rgba(0,95,103,0.12)",
  bodyText:  "#4a6e72",
  fontHead:  "'Sora', sans-serif",
  fontBody:  "'Inter', sans-serif",
};

function Sunburst() {
  return (
    <svg width="36" height="36" viewBox="0 0 40 40" fill="none">
      <circle cx="20" cy="20" r="5.5" fill={T.tealDark}/>
      {Array.from({length:12}).map((_,i)=>{
        const r=(i*30*Math.PI)/180;
        return <line key={i} x1={20+8*Math.cos(r)} y1={20+8*Math.sin(r)} x2={20+17*Math.cos(r)} y2={20+17*Math.sin(r)} stroke={T.tealDark} strokeWidth="2" strokeLinecap="round"/>;
      })}
    </svg>
  );
}

export default function Login() {
  const navigate = useNavigate();
  const { login, user } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // Role-aware redirect after login (user state populates inside useAuth().login)
  useEffect(() => {
    if (user) {
      navigate(user.role === "admin" ? "/admin" : "/dashboard", { replace: true });
    }
  }, [user, navigate]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    const result = await login(email, password);
    setLoading(false);
    if (!result.ok) setError(result.error);
    // Redirect handled by the useEffect above once user state is set
  }

  return (
    <div style={{ minHeight:"100vh", background:"#fff", fontFamily:T.fontHead, display:"flex", flexDirection:"column" }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=Sora:wght@400;500;600;700&family=Inter:wght@400;500;600&display=swap'); *{box-sizing:border-box;margin:0;padding:0;}`}</style>

      {/* Nav */}
      <nav style={{ display:"flex", alignItems:"center", justifyContent:"space-between", padding:"0 48px", height:"68px", borderBottom:`1px solid ${T.cardBorder}`, background:"#fff", flexShrink:0 }}>
        <Link to="/" style={{ display:"flex", alignItems:"center", gap:"10px", textDecoration:"none" }}>
          <Sunburst/>
          <span style={{ fontFamily:T.fontHead, fontWeight:600, fontSize:"18px", color:T.tealDark, letterSpacing:"-0.4px" }}>BleX</span>
        </Link>
        <Link to="/register" style={{ padding:"9px 22px", borderRadius:"8px", fontSize:"14px", fontWeight:600, color:"#fff", fontFamily:T.fontBody, background:T.tealDark, textDecoration:"none" }}>
          Get Started
        </Link>
      </nav>

      {/* Body */}
      <div style={{ flex:1, display:"flex", alignItems:"center", justifyContent:"center", padding:"40px 24px", background:"#fff" }}>
        <div style={{ width:"100%", maxWidth:"440px" }}>

          {/* Hero card — mint gradient matching landing */}
          <div style={{ background:T.heroBg, borderRadius:"20px", padding:"40px 40px 32px", marginBottom:"0", position:"relative", overflow:"hidden" }}>
            {/* Dots */}
            {[[8,15],[92,20],[15,75],[85,65],[60,10],[80,85]].map(([x,y],i)=>(
              <div key={i} style={{ position:"absolute", left:`${x}%`, top:`${y}%`, width:"6px", height:"6px", borderRadius:"50%", background:"rgba(0,95,103,0.18)", transform:"translate(-50%,-50%)" }}/>
            ))}
            <div style={{ position:"relative" }}>
              <p style={{ fontFamily:T.fontBody, fontSize:"13px", fontWeight:500, color:T.tealMid, marginBottom:"10px" }}>Welcome back</p>
              <h1 style={{ fontFamily:T.fontHead, fontSize:"28px", fontWeight:500, color:T.tealDark, letterSpacing:"-0.8px", marginBottom:"6px" }}>Sign in to BleX</h1>
              <p style={{ fontFamily:T.fontBody, fontSize:"14px", color:T.tealMid }}>Track your assets in real-time</p>
            </div>
          </div>

          {/* Form card */}
          <div style={{ border:`1px solid ${T.cardBorder}`, borderTop:"none", borderRadius:"0 0 20px 20px", padding:"32px 40px 36px", background:"#fff" }}>
            <form onSubmit={handleSubmit} style={{ display:"flex", flexDirection:"column", gap:"18px" }}>

              <div>
                <label style={{ display:"block", fontFamily:T.fontBody, fontSize:"13px", fontWeight:500, color:T.tealDark, marginBottom:"6px" }}>Email</label>
                <input
                  type="email" required value={email}
                  onChange={e => setEmail(e.target.value)}
                  placeholder="you@company.com"
                  style={{ width:"100%", padding:"10px 14px", borderRadius:"8px", border:`1px solid ${T.cardBorder}`, fontFamily:T.fontBody, fontSize:"14px", color:T.tealDark, outline:"none", transition:"border-color 0.2s", background:"#fafefe" }}
                  onFocus={e => (e.target.style.borderColor = T.tealDark)}
                  onBlur={e => (e.target.style.borderColor = T.cardBorder)}
                />
              </div>

              <div>
                <label style={{ display:"block", fontFamily:T.fontBody, fontSize:"13px", fontWeight:500, color:T.tealDark, marginBottom:"6px" }}>Password</label>
                <div style={{ position:"relative" }}>
                  <input
                    type={showPw ? "text" : "password"} required value={password}
                    onChange={e => setPassword(e.target.value)}
                    placeholder="••••••••"
                    style={{ width:"100%", padding:"10px 42px 10px 14px", borderRadius:"8px", border:`1px solid ${T.cardBorder}`, fontFamily:T.fontBody, fontSize:"14px", color:T.tealDark, outline:"none", transition:"border-color 0.2s", background:"#fafefe" }}
                    onFocus={e => (e.target.style.borderColor = T.tealDark)}
                    onBlur={e => (e.target.style.borderColor = T.cardBorder)}
                  />
                  <button type="button" onClick={() => setShowPw(!showPw)} style={{ position:"absolute", right:"12px", top:"50%", transform:"translateY(-50%)", background:"none", border:"none", cursor:"pointer", color:T.tealMid, padding:"0", display:"flex" }}>
                    {showPw
                      ? <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24M1 1l22 22"/></svg>
                      : <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                    }
                  </button>
                </div>
              </div>

              {error && (
                <div style={{ padding:"10px 14px", borderRadius:"8px", background:"rgba(220,38,38,0.06)", border:"1px solid rgba(220,38,38,0.2)", fontFamily:T.fontBody, fontSize:"13px", color:"#dc2626" }}>
                  {error}
                </div>
              )}

              <button
                type="submit" disabled={loading}
                style={{ width:"100%", padding:"12px", borderRadius:"8px", border:"none", background:loading ? "rgba(0,95,103,0.5)" : T.tealDark, color:"#fff", fontFamily:T.fontBody, fontSize:"15px", fontWeight:600, cursor:loading ? "not-allowed" : "pointer", transition:"opacity 0.2s", display:"flex", alignItems:"center", justifyContent:"center", gap:"8px" }}
                onMouseEnter={e => { if (!loading) (e.currentTarget.style.opacity = "0.85"); }}
                onMouseLeave={e => (e.currentTarget.style.opacity = "1")}
              >
                {loading ? <><div style={{ width:"16px", height:"16px", border:"2px solid rgba(255,255,255,0.3)", borderTopColor:"#fff", borderRadius:"50%", animation:"spin 0.7s linear infinite" }}/>Signing in…</> : "Sign In"}
              </button>

              <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
            </form>

            <p style={{ marginTop:"20px", textAlign:"center", fontFamily:T.fontBody, fontSize:"13px", color:T.bodyText }}>
              No account?{" "}
              <Link to="/register" style={{ color:T.tealDark, fontWeight:600, textDecoration:"none" }}>Create one</Link>
            </p>
          </div>

          <p style={{ marginTop:"24px", textAlign:"center", fontFamily:T.fontBody, fontSize:"12px", color:"rgba(0,95,103,0.4)" }}>
            2026 BleX by Sigmatic AI
          </p>
        </div>
      </div>
    </div>
  );
}
