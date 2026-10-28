"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Activity, BarChart3, Building2, CalendarDays, ChevronRight, ClipboardCheck, CreditCard, FileWarning, LayoutDashboard, LogOut, Menu, RotateCcw, ShieldCheck, Users, Wallet, X } from "lucide-react";
import { adminApi, AdminUser } from "@/lib/admin-api";

export const ADMIN_NAV = [
  { href: "/admin", label: "Overview", icon: LayoutDashboard },
  { href: "/admin/applications", label: "Applications", icon: ClipboardCheck },
  { href: "/admin/hotels", label: "Hotels", icon: Building2 },
  { href: "/admin/bookings", label: "Bookings", icon: CalendarDays },
  { href: "/admin/users", label: "Users", icon: Users },
  { href: "/admin/payments", label: "Payments", icon: CreditCard },
  { href: "/admin/refunds", label: "Refunds", icon: Wallet },
  { href: "/admin/reconciliation", label: "Reconciliation", icon: FileWarning },
  { href: "/admin/analytics", label: "Analytics", icon: BarChart3 },
  { href: "/admin/audit", label: "Audit history", icon: Activity },
] as const;

export function AdminShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [operator, setOperator] = useState<AdminUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [mobile, setMobile] = useState(false);
  const [logoutError, setLogoutError] = useState("");
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const check = useCallback(async () => {
    try {
      const user = await adminApi.me();
      if (user.role !== "ADMIN") throw new Error("Admin access required.");
      setOperator(user);
    } catch {
      setOperator(null);
      router.replace("/admin/login");
    } finally { setLoading(false); }
  }, [router]);
  useEffect(() => { if (pathname !== "/admin/login") void check(); else setLoading(false); }, [pathname, check]);
  async function signOut() {
    if (signingOut) return;
    setLogoutError("");
    setSigningOut(true);
    try {
      await adminApi.logout();
      setOperator(null);
      setLogoutConfirmOpen(false);
      router.replace("/admin/login");
      router.refresh();
    } catch (error) {
      setLogoutError(error instanceof Error ? error.message : "Sign-out failed.");
    } finally {
      setSigningOut(false);
    }
  }
  if (pathname === "/admin/login") return <>{children}</>;
  if (loading || !operator) return <div className="sa-loading">{loading ? "Verifying administrator access…" : "Redirecting to sign in…"}</div>;
  const current = ADMIN_NAV.find((item) => item.href === pathname);
  return (
    <div className="sa-root">
      <aside className={`sa-sidebar ${mobile ? "sa-sidebar-open" : ""}`}>
        <div className="sa-brand"><div className="sa-brand-icon"><Building2 size={20} /></div><div><strong>SpatialStay</strong><span>Platform administration</span></div><button className="sa-mobile-close" onClick={() => setMobile(false)} aria-label="Close navigation"><X size={20} /></button></div>
        <div className="sa-nav-label">WORKSPACE</div>
        <nav className="sa-navigation" aria-label="Admin navigation">{ADMIN_NAV.map(({ href, label, icon: Icon }) => <Link href={href} key={href} className={pathname === href ? "sa-active" : ""} onClick={() => setMobile(false)}><Icon size={18} /><span>{label}</span>{pathname === href && <ChevronRight size={15} className="sa-nav-arrow" />}</Link>)}</nav>
        <div className="sa-side-bottom"><div className="sa-security"><ShieldCheck size={17} /><span>Administrator-only workspace</span></div><button className="sa-signout" onClick={() => setLogoutConfirmOpen(true)}><LogOut size={17} /> Sign out</button>{logoutError && <p className="sa-inline-error">{logoutError}</p>}</div>
      </aside>
      {mobile && <button className="sa-backdrop" aria-label="Close navigation" onClick={() => setMobile(false)} />}
      <div className="sa-workspace"><header className="sa-topbar"><button className="sa-menu" aria-label="Open navigation" onClick={() => setMobile(true)}><Menu size={22} /></button><div className="sa-breadcrumb">Platform <ChevronRight size={15} /> <strong>{current?.label ?? "Overview"}</strong></div><div className="sa-account"><span className="sa-avatar">{operator.firstName?.[0]}{operator.lastName?.[0]}</span><div><strong>{operator.firstName} {operator.lastName}</strong><small>Administrator</small></div></div></header><main className="sa-content">{children}</main></div>
      {logoutConfirmOpen && (
        <div className="sa-modal-backdrop sa-confirm-backdrop" role="presentation" onMouseDown={() => !signingOut && setLogoutConfirmOpen(false)}>
          <div className="sa-dialog sa-logout-dialog" role="dialog" aria-modal="true" aria-labelledby="admin-signout-title" onMouseDown={(event) => event.stopPropagation()}>
            <div className="sa-logout-icon"><LogOut size={22} /></div>
            <h2 id="admin-signout-title">Sign out of SpatialStay Admin?</h2>
            <p>Your administrator session will end. You’ll need to sign in again to access the platform administration workspace.</p>
            {logoutError && <div className="sa-error">{logoutError}</div>}
            <div className="sa-dialog-actions">
              <button className="sa-button sa-button-secondary" disabled={signingOut} onClick={() => setLogoutConfirmOpen(false)}>Cancel</button>
              <button className="sa-button sa-button-danger" disabled={signingOut} onClick={() => void signOut()}><LogOut size={16} /> {signingOut ? "Signing out…" : "Sign out"}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
export function PageHeading({ title, description, refresh, refreshing }: { title: string; description: string; refresh?: () => void; refreshing?: boolean }) {
  return <div className="sa-page-heading"><div><h1>{title}</h1><p>{description}</p></div>{refresh && <button className="sa-button sa-button-secondary" disabled={refreshing} onClick={refresh}><RotateCcw size={16} className={refreshing ? "sa-spinning" : ""} /> Refresh</button>}</div>;
}
