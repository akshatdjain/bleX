import { useState, FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { register } from "@/lib/auth";

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

function Field({ label, type="text", value, onChange, placeholder, required=true, minLength, showToggle, showPw, onToggle }: {
  label:string; type?:string; value:string; onChange:(v:string)=>void;
  placeholder:string; required?:boolean; minLength?:number;
  showToggle?:boolean; showPw?:boolean; onToggle?:()=>void;
}) {
  return (
    <div>
      <label style={{ display:"block", fontFamily:T.fontBody, fontSize:"13px", fontWeight:500, color:T.tealDark, marginBottom:"6px" }}>{label}</label>
      <div style={{ position:"relative" }}>
        <input
          type={showToggle ? (showPw ? "text" : "password") : type}
          required={required} value={value} minLength={minLength}
          onChange={e => onChange(e.target.value)} placeholder={placeholder}
          style={{ width:"100%", padding:`10px ${showToggle?"42px":"14px"} 10px 14px`, borderRadius:"8px", border:`1px solid ${T.cardBorder}`, fontFamily:T.fontBody, fontSize:"14px", color:T.tealDark, outline:"none", transition:"border-color 0.2s", background:"#fafefe" }}
          onFocus={e => (e.target.style.borderColor = T.tealDark)}
          onBlur={e => (e.target.style.borderColor = T.cardBorder)}
        />
        {showToggle && (
          <button type="button" onClick={onToggle} style={{ position:"absolute", right:"12px", top:"50%", transform:"translateY(-50%)", background:"none", border:"none", cursor:"pointer", color:T.tealMid, padding:"0", display:"flex" }}>
            {showPw
              ? <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24M1 1l22 22"/></svg>
              : <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
            }
          </button>
        )}
      </div>
    </div>
  );
}

export default function Register() {
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [orgName, setOrgName] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [tenantId, setTenantId] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    const result = await register(name, email, password, orgName);
    setLoading(false);
    if ("error" in result) {
      setError(result.error);
    } else {
      setTenantId(result.user.tenant_id);
    }
  }

  function copyId() {
    if (tenantId) { navigator.clipboard.writeText(tenantId); setCopied(true); setTimeout(()=>setCopied(false),2000); }
  }

  // ── Success state ───────────────────────────────────────────────────
  if (tenantId) {
    return (
      <div style={{ minHeight:"100vh", background:"#fff", fontFamily:T.fontHead, display:"flex", flexDirection:"column" }}>
        <style>{`@import url('https://fonts.googleapis.com/css2?family=Sora:wght@400;500;600;700&family=Inter:wght@400;500;600&display=swap'); *{box-sizing:border-box;margin:0;padding:0;}`}</style>
        <nav style={{ display:"flex", alignItems:"center", padding:"0 48px", height:"68px", borderBottom:`1px solid ${T.cardBorder}`, background:"#fff" }}>
          <Link to="/" style={{ display:"flex", alignItems:"center", gap:"10px", textDecoration:"none" }}>
            <Sunburst/><span style={{ fontFamily:T.fontHead, fontWeight:600, fontSize:"18px", color:T.tealDark }}>BleX</span>
          </Link>
        </nav>
        <div style={{ flex:1, display:"flex", alignItems:"center", justifyContent:"center", padding:"40px 24px" }}>
          <div style={{ width:"100%", maxWidth:"440px" }}>
            <div style={{ background:T.heroBg, borderRadius:"20px 20px 0 0", padding:"40px 40px 32px", textAlign:"center", position:"relative", overflow:"hidden" }}>
              {[[10,20],[85,15],[20,80],[80,75]].map(([x,y],i)=>(
                <div key={i} style={{ position:"absolute", left:`${x}%`, top:`${y}%`, width:"6px", height:"6px", borderRadius:"50%", background:"rgba(0,95,103,0.18)", transform:"translate(-50%,-50%)" }}/>
              ))}
              <div style={{ position:"relative" }}>
                <div style={{ width:"52px", height:"52px", borderRadius:"50%", background:"rgba(0,95,103,0.12)", border:`1px solid ${T.cardBorder}`, display:"flex", alignItems:"center", justifyContent:"center", margin:"0 auto 16px" }}>
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke={T.tealDark} strokeWidth="2.5"><polyline points="20,6 9,17 4,12"/></svg>
                </div>
                <h1 style={{ fontFamily:T.fontHead, fontSize:"24px", fontWeight:500, color:T.tealDark, letterSpacing:"-0.6px", marginBottom:"8px" }}>Account created!</h1>
                <p style={{ fontFamily:T.fontBody, fontSize:"14px", color:T.tealMid }}>Save your Tenant ID — you'll need it to configure your scanners.</p>
              </div>
            </div>
            <div style={{ border:`1px solid ${T.cardBorder}`, borderTop:"none", borderRadius:"0 0 20px 20px", padding:"28px 40px 36px", background:"#fff" }}>
              <div style={{ border:`1px solid ${T.cardBorder}`, borderRadius:"12px", padding:"20px 24px", marginBottom:"16px", background:"#fafefe" }}>
                <p style={{ fontFamily:T.fontBody, fontSize:"12px", fontWeight:500, color:T.tealMid, marginBottom:"8px", textTransform:"uppercase", letterSpacing:"0.5px" }}>Your Tenant ID</p>
                <div style={{ display:"flex", alignItems:"center", justifyContent:"space-between", gap:"12px" }}>
                  <code style={{ fontFamily:"'IBM Plex Mono','Courier New',monospace", fontSize:"22px", fontWeight:700, color:T.tealDark, letterSpacing:"4px" }}>{tenantId}</code>
                  <button onClick={copyId} style={{ background:"none", border:"none", cursor:"pointer", color:T.tealMid, padding:"4px", display:"flex" }} title="Copy">
                    {copied
                      ? <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={T.tealDark} strokeWidth="2.5"><polyline points="20,6 9,17 4,12"/></svg>
                      : <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
                    }
                  </button>
                </div>
              </div>
              <div style={{ padding:"12px 16px", borderRadius:"8px", background:"rgba(245,158,11,0.06)", border:"1px solid rgba(245,158,11,0.2)", marginBottom:"20px" }}>
                <p style={{ fontFamily:T.fontBody, fontSize:"13px", color:"#92400e" }}>Keep this ID safe — it links all your scanners and assets.</p>
              </div>
              <button onClick={() => navigate("/dashboard",{replace:true})} style={{ width:"100%", padding:"12px", borderRadius:"8px", border:"none", background:T.tealDark, color:"#fff", fontFamily:T.fontBody, fontSize:"15px", fontWeight:600, cursor:"pointer", transition:"opacity 0.2s" }}
                onMouseEnter={e=>(e.currentTarget.style.opacity="0.85")} onMouseLeave={e=>(e.currentTarget.style.opacity="1")}
              >Go to Dashboard →</button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // ── Registration form ────────────────────────────────────────────────
  return (
    <div style={{ minHeight:"100vh", background:"#fff", fontFamily:T.fontHead, display:"flex", flexDirection:"column" }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=Sora:wght@400;500;600;700&family=Inter:wght@400;500;600&display=swap'); *{box-sizing:border-box;margin:0;padding:0;} @keyframes spin{to{transform:rotate(360deg)}}`}</style>

      <nav style={{ display:"flex", alignItems:"center", justifyContent:"space-between", padding:"0 48px", height:"68px", borderBottom:`1px solid ${T.cardBorder}`, background:"#fff", flexShrink:0 }}>
        <Link to="/" style={{ display:"flex", alignItems:"center", gap:"10px", textDecoration:"none" }}>
          <Sunburst/><span style={{ fontFamily:T.fontHead, fontWeight:600, fontSize:"18px", color:T.tealDark, letterSpacing:"-0.4px" }}>BleX</span>
        </Link>
        <Link to="/login" style={{ padding:"9px 22px", borderRadius:"8px", fontSize:"14px", fontWeight:600, color:T.tealDark, fontFamily:T.fontBody, border:`1.5px solid ${T.tealDark}`, textDecoration:"none" }}>
          Sign In
        </Link>
      </nav>

      <div style={{ flex:1, display:"flex", alignItems:"center", justifyContent:"center", padding:"40px 24px" }}>
        <div style={{ width:"100%", maxWidth:"460px" }}>

          <div style={{ background:T.heroBg, borderRadius:"20px 20px 0 0", padding:"36px 40px 28px", position:"relative", overflow:"hidden" }}>
            {[[8,15],[92,20],[15,75],[85,65],[60,10],[80,85]].map(([x,y],i)=>(
              <div key={i} style={{ position:"absolute", left:`${x}%`, top:`${y}%`, width:"6px", height:"6px", borderRadius:"50%", background:"rgba(0,95,103,0.18)", transform:"translate(-50%,-50%)" }}/>
            ))}
            <div style={{ position:"relative" }}>
              <p style={{ fontFamily:T.fontBody, fontSize:"13px", fontWeight:500, color:T.tealMid, marginBottom:"10px" }}>Free forever</p>
              <h1 style={{ fontFamily:T.fontHead, fontSize:"28px", fontWeight:500, color:T.tealDark, letterSpacing:"-0.8px", marginBottom:"6px" }}>Create your account</h1>
              <p style={{ fontFamily:T.fontBody, fontSize:"14px", color:T.tealMid }}>Get started with BleX asset tracking</p>
            </div>
          </div>

          <div style={{ border:`1px solid ${T.cardBorder}`, borderTop:"none", borderRadius:"0 0 20px 20px", padding:"28px 40px 36px", background:"#fff" }}>
            <form onSubmit={handleSubmit} style={{ display:"flex", flexDirection:"column", gap:"16px" }}>

              <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:"14px" }}>
                <Field label="Full Name" value={name} onChange={setName} placeholder="Alex Chen"/>
                <Field label="Organization" value={orgName} onChange={setOrgName} placeholder="Acme Corp"/>
              </div>
              <Field label="Email" type="email" value={email} onChange={setEmail} placeholder="you@company.com"/>
              <Field label="Password" value={password} onChange={setPassword} placeholder="Min. 8 characters" minLength={8} showToggle showPw={showPw} onToggle={()=>setShowPw(p=>!p)}/>

              {error && (
                <div style={{ padding:"10px 14px", borderRadius:"8px", background:"rgba(220,38,38,0.06)", border:"1px solid rgba(220,38,38,0.2)", fontFamily:T.fontBody, fontSize:"13px", color:"#dc2626" }}>{error}</div>
              )}

              <button type="submit" disabled={loading}
                style={{ width:"100%", padding:"12px", borderRadius:"8px", border:"none", background:loading?"rgba(0,95,103,0.5)":T.tealDark, color:"#fff", fontFamily:T.fontBody, fontSize:"15px", fontWeight:600, cursor:loading?"not-allowed":"pointer", transition:"opacity 0.2s", display:"flex", alignItems:"center", justifyContent:"center", gap:"8px" }}
                onMouseEnter={e=>{if(!loading)(e.currentTarget.style.opacity="0.85");}} onMouseLeave={e=>(e.currentTarget.style.opacity="1")}
              >
                {loading ? <><div style={{ width:"16px", height:"16px", border:"2px solid rgba(255,255,255,0.3)", borderTopColor:"#fff", borderRadius:"50%", animation:"spin 0.7s linear infinite" }}/>Creating account…</> : "Create Account"}
              </button>
            </form>

            <p style={{ marginTop:"20px", textAlign:"center", fontFamily:T.fontBody, fontSize:"13px", color:T.bodyText }}>
              Already have an account?{" "}
              <Link to="/login" style={{ color:T.tealDark, fontWeight:600, textDecoration:"none" }}>Sign in</Link>
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
