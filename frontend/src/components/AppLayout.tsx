import { Link, useLocation, useNavigate } from "react-router-dom";
import logo from "@/assets/sigmatic_logo_teal.png";
import { cn } from "@/lib/utils";
import { LayoutDashboard, ScrollText, Radio, LogOut, ChevronDown, Menu, Shield } from "lucide-react";
import { NotificationDropdown } from "@/components/NotificationDropdown";
import { HealthBar } from "@/components/HealthBar";
import { GlobalSearch } from "@/components/GlobalSearch";
import { useEffect, useState, useRef } from "react";
import { useAuth } from "@/lib/auth-context";
import { useIsMobile } from "@/hooks/use-mobile";
import { Sheet, SheetContent, SheetDescription, SheetTitle } from "@/components/ui/sheet";

const navItems = [
  { to: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { to: "/logs", label: "Logs", icon: ScrollText },
  { to: "/assets", label: "Assets", icon: Radio },
];

function MobileNav() {
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  return (
    <>
      <button
        onClick={() => setDrawerOpen(true)}
        className="flex items-center rounded-md px-2 py-1.5 text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
        aria-label="Open menu"
      >
        <Menu className="h-4 w-4" />
      </button>
      <Sheet open={drawerOpen} onOpenChange={setDrawerOpen}>
        <SheetContent side="left" className="w-52 pt-10">
          <SheetTitle className="sr-only">Navigation</SheetTitle>
          <SheetDescription className="sr-only">App navigation links</SheetDescription>
          <nav className="flex flex-col gap-1">
            {navItems.map(({ to, label, icon: Icon }) => {
              const active = location.pathname === to || (to !== "/dashboard" && location.pathname.startsWith(to));
              return (
                <Link key={to} to={to} onClick={() => setDrawerOpen(false)}
                  className={cn(
                    "flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                    active ? "bg-accent text-accent-foreground" : "text-muted-foreground hover:text-foreground hover:bg-muted"
                  )}>
                  <Icon className="h-4 w-4" />
                  {label}
                </Link>
              );
            })}
          </nav>
        </SheetContent>
      </Sheet>
    </>
  );
}

export function AppLayout({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();
  const isMobile = useIsMobile();
  const { user, logout } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const [showLogoutDialog, setShowLogoutDialog] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  // Close dropdown on outside click
  useEffect(() => {
    function handler(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  async function handleLogout() {
    await logout();
    navigate("/login", { replace: true });
  }

  // Intercept browser back button — show logout confirmation if navigating away from app
  useEffect(() => {
    // Push a sentinel entry so we can detect back navigation
    window.history.pushState({ blex: true }, "");
    function onPopState(e: PopStateEvent) {
      if (!e.state?.blex) {
        // User pressed back past the app — show logout dialog
        window.history.pushState({ blex: true }, ""); // re-push so dialog can cancel
        setShowLogoutDialog(true);
      }
    }
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  return (
    <div className="min-h-screen flex flex-col">
      {/* Header */}
      <header className="sticky top-0 z-40 border-b bg-card/80 backdrop-blur-md">
        <div className="container flex h-14 items-center justify-between">
          <Link to="/dashboard" className="flex items-center gap-1.5 group">
            <img
              src={logo}
              alt="Sigmatic"
              className="h-7 w-auto"
            />
            <span className="text-base font-semibold tracking-tight text-[#1E293B] dark:text-white mt-0.5">
              - bleX
            </span>
          </Link>

          <div className="flex items-center gap-1">
            {isMobile ? (
              <MobileNav />
            ) : (
              <nav className="flex items-center gap-1">
                {navItems.map(({ to, label, icon: Icon }) => {
                  const active =
                    location.pathname === to ||
                    (to !== "/dashboard" && location.pathname.startsWith(to));
                  return (
                    <Link
                      key={to}
                      to={to}
                      className={cn(
                        "flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                        active
                          ? "bg-accent text-accent-foreground"
                          : "text-muted-foreground hover:text-foreground hover:bg-muted"
                      )}
                    >
                      <Icon className="h-4 w-4" />
                      <span className="hidden sm:inline">{label}</span>
                    </Link>
                  );
                })}
              </nav>
            )}

            <div className="ml-2 border-l pl-2 flex items-center gap-1">
              <GlobalSearch />
              <NotificationDropdown />

              {/* User menu */}
              {user && (
                <div className="relative" ref={menuRef}>
                  <button
                    onClick={() => setMenuOpen(!menuOpen)}
                    className="flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm font-medium text-muted-foreground hover:text-foreground hover:bg-muted transition-colors cursor-pointer"
                  >
                    <div className="w-6 h-6 rounded-full bg-primary/15 flex items-center justify-center text-primary text-xs font-bold">
                      {user.name.charAt(0).toUpperCase()}
                    </div>
                    <span className="hidden md:inline max-w-[100px] truncate">{user.name}</span>
                    <ChevronDown className="h-3.5 w-3.5 opacity-50" />
                  </button>

                  {menuOpen && (
                    <div className="absolute right-0 mt-1 w-56 bg-card border rounded-xl shadow-xl z-50 overflow-hidden py-1">
                      <div className="px-3 py-2 border-b mb-1">
                        <p className="text-xs font-semibold text-foreground truncate">{user.name}</p>
                        <p className="text-xs text-muted-foreground truncate">{user.email}</p>
                        <p
                          className="text-xs text-primary/70 mt-0.5"
                          style={{ fontFamily: "'IBM Plex Mono', monospace" }}
                        >
                          {user.org_name} · {user.tenant_id}
                        </p>
                      </div>
                      <Link
                        to="/admin"
                        onClick={() => setMenuOpen(false)}
                        className="w-full flex items-center gap-2 px-3 py-2 text-sm text-foreground hover:bg-muted transition-colors cursor-pointer"
                      >
                        <Shield className="h-4 w-4" />
                        Admin Panel
                      </Link>
                      <button
                        onClick={handleLogout}
                        className="w-full flex items-center gap-2 px-3 py-2 text-sm text-destructive hover:bg-destructive/10 transition-colors cursor-pointer"
                      >
                        <LogOut className="h-4 w-4" />
                        Sign out
                      </button>
                    </div>
                  )}
                </div>
              )}

              {/* Fallback if no user loaded yet — show logout icon directly */}
              {!user && (
                <div className="w-6 h-6 rounded-full bg-muted animate-pulse" />
              )}
            </div>
          </div>
        </div>
      </header>

      {/* Health bar */}
      <div className="border-b bg-muted/20 backdrop-blur-sm">
        <div className="container flex h-9 items-center">
          <HealthBar />
        </div>
      </div>

      {/* Content */}
      <main className="flex-1">
        <div className="container py-6">{children}</div>
      </main>

      {/* Back-button logout confirmation dialog */}
      {showLogoutDialog && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
          onClick={() => setShowLogoutDialog(false)}
        >
          <div
            style={{ border: "1px solid rgba(0,95,103,0.12)", borderRadius: "20px", overflow: "hidden" }}
            className="w-full max-w-sm bg-white shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Header card — matching login mint gradient */}
            <div style={{ background: "linear-gradient(135deg, #e8f8f7 0%, #d5f2f0 40%, #eafaf9 100%)", padding: "28px 32px 20px" }}>
              <p className="text-sm font-medium text-[#028994] mb-1">Just checking</p>
              <h2 className="text-xl font-semibold text-[#005F67] tracking-tight">Sign out of BleX?</h2>
              <p className="text-sm text-[#4a6e72] mt-1">Your session will end and you'll need to sign back in.</p>
            </div>
            {/* Buttons */}
            <div className="px-8 py-6 flex flex-col gap-3 bg-white">
              <button
                onClick={handleLogout}
                className="w-full py-2.5 rounded-lg text-sm font-semibold text-white transition-opacity hover:opacity-85"
                style={{ background: "#005F67" }}
              >
                Yes, sign out
              </button>
              <button
                onClick={() => setShowLogoutDialog(false)}
                className="w-full py-2.5 rounded-lg text-sm font-semibold border transition-colors hover:bg-muted"
                style={{ borderColor: "rgba(0,95,103,0.2)", color: "#005F67" }}
              >
                Stay signed in
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
