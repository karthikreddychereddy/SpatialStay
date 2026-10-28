"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  Activity,
  BarChart3,
  BedDouble,
  Building2,
  CalendarDays,
  ChevronDown,
  ClipboardList,
  DoorOpen,
  LogOut,
  Menu,
  ShieldCheck,
  Users,
  X,
} from "lucide-react";

import { useHotelOps } from "./HotelOpsProvider";

const nav = [
  ["/hotel", "Dashboard", BarChart3, "all"],
  ["/hotel/reservations", "Reservations", CalendarDays, "all"],
  ["/hotel/rooms", "Rooms & Inventory", BedDouble, "all"],
  ["/hotel/profile", "Hotel Profile", Building2, "head"],
  ["/hotel/staff", "Staff", Users, "head"],
  ["/hotel/finance", "Financial Overview", ClipboardList, "head"],
  ["/hotel/activity", "Activity History", Activity, "all"],
] as const;

const HOTEL_HEAD_ONLY_ROUTES = [
  "/hotel/profile",
  "/hotel/staff",
  "/hotel/finance",
] as const;

function isHotelHeadOnlyRoute(pathname: string): boolean {
  return HOTEL_HEAD_ONLY_ROUTES.some(
    (route) => pathname === route || pathname.startsWith(`${route}/`)
  );
}

export function HotelOpsShell({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const router = useRouter();

  const {
    operator,
    hotelId,
    setHotelId,
    loading,
    logout,
  } = useHotelOps();

  const [mobile, setMobile] = useState(false);

  const isHotelHead = operator?.role === "HOTEL_HEAD";

  const unauthorizedHeadRoute =
    Boolean(operator) &&
    !isHotelHead &&
    isHotelHeadOnlyRoute(pathname);

  useEffect(() => {
    if (unauthorizedHeadRoute) {
      router.replace("/hotel");
    }
  }, [unauthorizedHeadRoute, router]);

  if (loading) {
    return (
      <div className="hotel-loading">
        <div className="hotel-spinner" />
        <p>Opening Hotel Operations…</p>
      </div>
    );
  }

  if (!operator) {
    return null;
  }

  /*
   * Do not render privileged page children while redirecting.
   *
   * This prevents HOTEL_STAFF from briefly seeing HOTEL_HEAD-only
   * controls such as Add Staff Member, profile editing, or financial
   * information when a protected URL is entered manually.
   */
  if (unauthorizedHeadRoute) {
    return (
      <div className="hotel-loading">
        <div className="hotel-spinner" />
        <p>Returning to Hotel Operations…</p>
      </div>
    );
  }

  const hotel = operator.hotels.find(
    (item) => item.id === hotelId
  );

  return (
    <div className="hotel-app">
      <aside
        className={`hotel-sidebar ${mobile ? "open" : ""}`}
      >
        <div className="hotel-brand">
          <div className="hotel-brand-mark">
            <DoorOpen size={22} />
          </div>

          <div>
            <b>SpatialStay</b>
            <span>Hotel Operations</span>
          </div>

          <button
            className="hotel-close"
            onClick={() => setMobile(false)}
            aria-label="Close menu"
          >
            <X />
          </button>
        </div>

        <div className="hotel-property">
          <small>PROPERTY</small>

          <div className="hotel-select-wrap">
            <Building2 size={16} />

            <select
              value={hotelId ?? ""}
              onChange={(event) =>
                setHotelId(Number(event.target.value))
              }
            >
              {operator.hotels.map((item) => (
                <option
                  key={item.id}
                  value={item.id}
                >
                  {item.name}
                </option>
              ))}
            </select>

            <ChevronDown size={15} />
          </div>

          {hotel && (
            <span>
              {hotel.city}, {hotel.country}
            </span>
          )}
        </div>

        <nav className="hotel-nav">
          {nav
            .filter(
              (item) =>
                item[3] === "all" || isHotelHead
            )
            .map(([href, label, Icon]) => {
              const active =
                href === "/hotel"
                  ? pathname === href
                  : pathname.startsWith(href);

              return (
                <Link
                  key={href}
                  href={href}
                  onClick={() => setMobile(false)}
                  className={active ? "active" : ""}
                >
                  <Icon size={18} />
                  <span>{label}</span>
                </Link>
              );
            })}
        </nav>

        <div className="hotel-side-footer">
          <div className="hotel-role">
            <ShieldCheck size={17} />

            <div>
              <b>
                {operator.firstName} {operator.lastName}
              </b>

              <span>
                {operator.role.replace("HOTEL_", "")}
              </span>
            </div>
          </div>

          <button onClick={() => void logout()}>
            <LogOut size={17} />
            Sign out
          </button>
        </div>
      </aside>

      {mobile && (
        <button
          aria-label="Close menu"
          className="hotel-overlay"
          onClick={() => setMobile(false)}
        />
      )}

      <main className="hotel-main">
        <header className="hotel-topbar">
          <button
            className="hotel-menu"
            onClick={() => setMobile(true)}
            aria-label="Open menu"
          >
            <Menu />
          </button>

          <div>
            <small>HOTEL OPERATIONS</small>
            <b>{hotel?.name ?? "Your property"}</b>
          </div>

          <div className="hotel-user-chip">
            <span>
              {operator.firstName?.[0]}
              {operator.lastName?.[0]}
            </span>

            <div>
              <b>{operator.firstName}</b>
              <small>
                {isHotelHead
                  ? "Hotel Head"
                  : "Hotel Staff"}
              </small>
            </div>
          </div>
        </header>

        <div className="hotel-content">
          {children}
        </div>
      </main>
    </div>
  );
}