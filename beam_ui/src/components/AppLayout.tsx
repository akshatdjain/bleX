import { Link, useLocation, useNavigate } from "react-router-dom";
import logo from "@/assets/sigmatic_logo_teal.png";
import { cn } from "@/lib/utils";
import { LayoutDashboard, ScrollText, Radio, LogOut, ChevronDown, Menu } from "lucide-react";
import { NotificationDropdown } from "@/components/NotificationDropdown";
import { HealthBar } from "@/components/HealthBar";
import { GlobalSearch } from "@/components/GlobalSearch";
import { useEffect, useState, useRef } from "react";
import { getMe, logout, AuthUser } from "@/lib/auth";
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
  const [user, setUser] = useState<AuthUser | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    getMe().then((u) => {
      if (u) setUser(u);
    });
  }, []);

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
                <button
                  onClick={handleLogout}
                  className="flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm text-muted-foreground hover:text-foreground hover:bg-muted transition-colors cursor-pointer"
                  title="Sign out"
                >
                  <LogOut className="h-4 w-4" />
                </button>
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
    </div>
  );
}
