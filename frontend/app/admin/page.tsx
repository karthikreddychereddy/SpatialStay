"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, ArrowUpRight, Building2, CalendarDays, ClipboardCheck, CreditCard, FileWarning, Users } from "lucide-react";
import { adminApi, AdminDashboard, AdminAnalytics, HotelApplication } from "@/lib/admin-api";
import { PageHeading } from "@/components/admin/AdminShell";

export default function AdminOverview() {
  const [dashboard, setDashboard] = useState<AdminDashboard | null>(null);
  const [analytics, setAnalytics] = useState<AdminAnalytics | null>(null);
  const [pending, setPending] = useState<HotelApplication[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(true);
  const load = useCallback(async () => {
    setBusy(true); setError("");
    const [d, a, p] = await Promise.allSettled([adminApi.dashboard(), adminApi.analytics(), adminApi.applications()]);
    if (d.status === "fulfilled") setDashboard(d.value);
    if (a.status === "fulfilled") setAnalytics(a.value);
    if (p.status === "fulfilled") setPending(p.value.filter((item) => item.status === "PENDING_APPROVAL").slice(0, 5));
    if (d.status === "rejected") setError(d.reason instanceof Error ? d.reason.message : "Dashboard unavailable.");
    else if (a.status === "rejected" || p.status === "rejected") setError("Some dashboard details are temporarily unavailable.");
    setBusy(false);
  }, []);
  useEffect(() => { void load(); }, [load]);
  const cards = [
    { label: "Active & listed hotels", value: dashboard?.hotels, icon: Building2, href: "/admin/hotels", note: "All hotel records" },
    { label: "Pending applications", value: dashboard?.pendingApplications, icon: ClipboardCheck, href: "/admin/applications", note: "Requires review" },
    { label: "Total bookings", value: dashboard?.bookings, icon: CalendarDays, href: "/admin/bookings", note: "Across the platform" },
    { label: "Registered users", value: dashboard?.users, icon: Users, href: "/admin/users", note: "All account roles" },
    { label: "Payment attempts", value: dashboard?.payments, icon: CreditCard, href: "/admin/payments", note: "Provider-backed records" },
    { label: "Reconciliation cases", value: dashboard?.reconciliationCases, icon: FileWarning, href: "/admin/reconciliation", note: "Manual review required" },
  ];
  return <><PageHeading title="Platform overview" description="Your operational snapshot across hotels, guests, bookings and payments." refresh={() => void load()} refreshing={busy} />{error && <div className="sa-error" role="alert">{error}</div>}{busy && !dashboard ? <div className="sa-panel sa-state">Loading platform overview…</div> : <><div className="sa-metrics">{cards.map(({ label, value, icon: Icon, href, note }) => <Link className="sa-metric" href={href} key={label}><div className="sa-metric-top"><span>{label}</span><Icon size={19} /></div><strong>{(value ?? 0).toLocaleString("en-IN")}</strong><div className="sa-metric-bottom">{note}<ArrowUpRight size={16} /></div></Link>)}</div><div className="sa-overview-grid"><section className="sa-panel"><div className="sa-panel-heading"><div><h2>Pending hotel applications</h2><p>Review onboarding requests before hotels go live.</p></div><Link href="/admin/applications">View all <ArrowUpRight size={15} /></Link></div>{pending.length ? <div className="sa-list">{pending.map((item) => <Link className="sa-list-row" key={item.id} href="/admin/applications"><div><strong>{item.hotelName}</strong><span>{item.applicantName} · {item.city}, {item.country}</span></div><span className="sa-badge sa-badge-warning">Pending</span></Link>)}</div> : <div className="sa-empty">No pending applications right now.</div>}</section><section className="sa-panel"><div className="sa-panel-heading"><div><h2>Financial visibility</h2><p>Based on recorded payment and refund outcomes.</p></div><Link href="/admin/analytics">Analytics <ArrowUpRight size={15} /></Link></div><div className="sa-finance-stats"><div><span>Successful payments</span><strong>{(analytics?.successfulPayments ?? 0).toLocaleString("en-IN")}</strong></div><div><span>Successful refunds</span><strong>{(analytics?.successfulRefunds ?? 0).toLocaleString("en-IN")}</strong></div><div><span>Confirmed bookings</span><strong>{(analytics?.confirmedBookings ?? 0).toLocaleString("en-IN")}</strong></div></div>{(dashboard?.reconciliationCases ?? 0) > 0 && <Link className="sa-attention" href="/admin/reconciliation"><AlertTriangle size={18} /> Review {dashboard?.reconciliationCases} reconciliation case(s) <ArrowUpRight size={15} /></Link>}</section></div></>}</>;
}
