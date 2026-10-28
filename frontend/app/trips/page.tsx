"use client";

import {
  AlertCircle,
  CalendarDays,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Compass,
  History,
  Hotel,
  Luggage,
  RefreshCw,
  Sparkles,
  Users,
  XCircle,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";

import {
  BookingApiError,
  bookingApi,
} from "@/lib/api/bookings";
import type {
  BookingResponse,
  BookingStatus,
} from "@/types/api-booking";

type TripTab = "upcoming" | "past" | "cancelled";

const tabs: {
  id: TripTab;
  label: string;
  icon: typeof CalendarDays;
}[] = [
  { id: "upcoming", label: "Upcoming", icon: CalendarDays },
  { id: "past", label: "Past", icon: History },
  { id: "cancelled", label: "Cancelled", icon: XCircle },
];

function dateOnly(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day);
}

function todayOnly() {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(dateOnly(value));
}

function formatMoney(amount: number, currency: string) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}

function statusLabel(status: BookingStatus) {
  switch (status) {
    case "CONFIRMED":
      return "Confirmed";
    case "PENDING_PAYMENT":
      return "Payment pending";
    case "CANCELLED":
      return "Cancelled";
    case "EXPIRED":
      return "Expired";
  }
}

function statusClasses(status: BookingStatus) {
  switch (status) {
    case "CONFIRMED":
      return "border-emerald-200 bg-emerald-50 text-emerald-700";
    case "PENDING_PAYMENT":
      return "border-amber-200 bg-amber-50 text-amber-700";
    case "CANCELLED":
      return "border-rose-200 bg-rose-50 text-rose-700";
    case "EXPIRED":
      return "border-slate-200 bg-slate-50 text-slate-600";
  }
}

function TripCard({ booking }: { booking: BookingResponse }) {
  return (
    <article className="group overflow-hidden rounded-[24px] border border-[#dce8f6] bg-white/90 shadow-[0_14px_42px_rgba(39,76,122,.08)] transition hover:-translate-y-0.5 hover:shadow-[0_18px_50px_rgba(39,76,122,.13)]">
      <div className="p-5 sm:p-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex min-w-0 gap-3">
            <div className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-[linear-gradient(145deg,#eaf3ff,#f7fbff)] text-[#3475f6] shadow-sm">
              <Hotel size={22} />
            </div>
            <div className="min-w-0">
              <p className="text-xs font-extrabold uppercase tracking-[.13em] text-[#7d93ae]">
                {booking.bookingReference}
              </p>
              <h2 className="mt-1 truncate text-xl font-extrabold text-[#17385f]">
                {booking.hotelName}
              </h2>
              <p className="mt-1 text-sm font-semibold text-[#68809d]">
                {booking.roomName}
              </p>
            </div>
          </div>

          <span className={`w-fit rounded-full border px-3 py-1.5 text-xs font-extrabold ${statusClasses(booking.status)}`}>
            {statusLabel(booking.status)}
          </span>
        </div>

        <div className="mt-5 grid gap-3 rounded-[20px] bg-[#f6f9fd] p-4 sm:grid-cols-3">
          <div className="flex items-start gap-2">
            <CalendarDays size={16} className="mt-0.5 text-[#3975d6]" />
            <div>
              <p className="text-[11px] font-bold uppercase tracking-wider text-[#8aa0b8]">Stay</p>
              <p className="mt-1 text-sm font-bold text-[#31567f]">
                {formatDate(booking.checkIn)} – {formatDate(booking.checkOut)}
              </p>
            </div>
          </div>
          <div className="flex items-start gap-2">
            <Users size={16} className="mt-0.5 text-[#3975d6]" />
            <div>
              <p className="text-[11px] font-bold uppercase tracking-wider text-[#8aa0b8]">Guests</p>
              <p className="mt-1 text-sm font-bold text-[#31567f]">
                {booking.guests} guest{booking.guests === 1 ? "" : "s"} · {booking.units} room{booking.units === 1 ? "" : "s"}
              </p>
            </div>
          </div>
          <div>
            <p className="text-[11px] font-bold uppercase tracking-wider text-[#8aa0b8]">Total</p>
            <p className="mt-1 text-sm font-extrabold text-[#17385f]">
              {formatMoney(booking.totalAmount, booking.currency)}
            </p>
          </div>
        </div>

        <div className="mt-5 flex items-center justify-between gap-3">
          <p className="text-xs font-semibold text-[#8296ad]">
            {booking.nights} night{booking.nights === 1 ? "" : "s"}
          </p>
          <Link
            href={`/trips/${encodeURIComponent(booking.bookingId)}`}
            className="inline-flex items-center gap-1.5 rounded-full bg-[#173f76] px-4 py-2.5 text-sm font-extrabold text-white transition hover:bg-[#1d4d8d]"
          >
            View booking
            <ChevronRight size={15} />
          </Link>
        </div>
      </div>
    </article>
  );
}

export default function TripsPage() {
  const [activeTab, setActiveTab] = useState<TripTab>("upcoming");
  const [bookings, setBookings] = useState<BookingResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [unauthorized, setUnauthorized] = useState(false);

  const loadBookings = useCallback(async () => {
    setLoading(true);
    setError("");
    setUnauthorized(false);

    try {
      setBookings(await bookingApi.getMyBookings());
    } catch (loadError) {
      if (loadError instanceof BookingApiError && loadError.status === 401) {
        setUnauthorized(true);
        setError("Please sign in to view your trips.");
      } else {
        setError(
          loadError instanceof Error
            ? loadError.message
            : "We could not load your trips.",
        );
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadBookings();
  }, [loadBookings]);

  const grouped = useMemo(() => {
    const today = todayOnly();

    return {
      upcoming: bookings.filter(
        (booking) =>
          (booking.status === "CONFIRMED" ||
            booking.status === "PENDING_PAYMENT") &&
          dateOnly(booking.checkOut) >= today,
      ),
      past: bookings.filter(
        (booking) =>
          booking.status === "CONFIRMED" &&
          dateOnly(booking.checkOut) < today,
      ),
      cancelled: bookings.filter(
        (booking) =>
          booking.status === "CANCELLED" ||
          booking.status === "EXPIRED",
      ),
    };
  }, [bookings]);

  const visibleBookings = grouped[activeTab];

  return (
    <main className="travel-shell min-h-screen pb-24 pt-[105px]">
      <section className="mx-auto max-w-6xl px-4 sm:px-6">
        <div className="glass-panel overflow-hidden rounded-[28px]">
          <div className="border-b border-[#dce8f6]/80 p-5 sm:p-8">
            <div className="flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <p className="text-xs font-bold uppercase tracking-[.16em] text-[#6c86a8]">
                  Your travel space
                </p>
                <h1 className="mt-2 text-3xl font-extrabold tracking-tight text-[#17385f] sm:text-5xl">
                  My Trips
                </h1>
                <p className="mt-3 max-w-2xl text-sm leading-6 text-[#6e85a3]">
                  View your real SpatialStay reservations, booking references and stay details in one place.
                </p>
              </div>

              <Link
                href="/hotels"
                className="inline-flex w-fit items-center justify-center gap-2 rounded-full bg-[#173f76] px-5 py-3 text-sm font-extrabold text-white shadow-[0_10px_28px_rgba(23,63,118,.18)] transition hover:-translate-y-0.5 hover:bg-[#1d4d8d]"
              >
                <Compass size={16} />
                Explore stays
              </Link>
            </div>
          </div>

          <div className="p-5 sm:p-8">
            <div className="flex gap-2 overflow-x-auto pb-2">
              {tabs.map((tab) => {
                const Icon = tab.icon;
                const count = grouped[tab.id].length;

                return (
                  <button
                    key={tab.id}
                    type="button"
                    onClick={() => setActiveTab(tab.id)}
                    className={`flex shrink-0 items-center gap-2 rounded-full px-5 py-2.5 text-sm font-bold transition ${
                      activeTab === tab.id
                        ? "blue-button"
                        : "bg-[#edf4ff] text-[#6480a2] hover:bg-[#e4efff]"
                    }`}
                  >
                    <Icon size={15} />
                    {tab.label}
                    {!loading && (
                      <span className={`grid min-w-5 place-items-center rounded-full px-1.5 text-[10px] ${
                        activeTab === tab.id
                          ? "bg-white/20 text-white"
                          : "bg-white text-[#52739b]"
                      }`}>
                        {count}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>

            {loading && (
              <div className="mt-6 grid gap-4">
                {[1, 2].map((item) => (
                  <div
                    key={item}
                    className="h-56 animate-pulse rounded-[24px] border border-[#dce8f6] bg-white/70"
                  />
                ))}
              </div>
            )}

            {!loading && error && (
              <div className="mt-6 rounded-[24px] border border-rose-200 bg-rose-50/80 p-7 text-center">
                <AlertCircle className="mx-auto text-rose-500" size={28} />
                <h2 className="mt-3 text-lg font-extrabold text-[#17385f]">
                  {unauthorized ? "Sign in to see My Trips" : "Trips unavailable"}
                </h2>
                <p className="mx-auto mt-2 max-w-lg text-sm leading-6 text-[#6f86a1]">
                  {error}
                  {unauthorized && " Use Login / Sign Up in the navigation bar, then return here."}
                </p>
                {!unauthorized && (
                  <button
                    type="button"
                    onClick={() => void loadBookings()}
                    className="mt-5 inline-flex items-center gap-2 rounded-full bg-[#173f76] px-5 py-2.5 text-sm font-extrabold text-white"
                  >
                    <RefreshCw size={15} />
                    Try again
                  </button>
                )}
              </div>
            )}

            {!loading && !error && visibleBookings.length > 0 && (
              <div className="mt-6 grid gap-4">
                {visibleBookings.map((booking) => (
                  <TripCard key={booking.bookingId} booking={booking} />
                ))}
              </div>
            )}

            {!loading && !error && visibleBookings.length === 0 && (
              <div className="mt-6 overflow-hidden rounded-[26px] border border-[#dce8f6] bg-[linear-gradient(145deg,rgba(255,255,255,.95),rgba(239,246,255,.82))]">
                <div className="relative flex min-h-[360px] flex-col items-center justify-center overflow-hidden px-5 py-12 text-center">
                  <div className="pointer-events-none absolute left-1/2 top-1/2 h-[300px] w-[300px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#dceaff]/50 blur-3xl" />
                  <div className="relative">
                    <div className="relative mx-auto grid h-20 w-20 place-items-center rounded-[26px] border border-[#cfe0f5] bg-white shadow-[0_20px_50px_rgba(46,89,145,.13)]">
                      {activeTab === "upcoming" ? (
                        <Luggage size={32} className="text-[#3475f6]" />
                      ) : activeTab === "past" ? (
                        <CheckCircle2 size={32} className="text-[#3475f6]" />
                      ) : (
                        <Clock3 size={32} className="text-[#3475f6]" />
                      )}
                      <span className="absolute -right-2 -top-2 grid h-7 w-7 place-items-center rounded-full bg-gradient-to-br from-[#397bff] to-[#766af4] text-white">
                        <Sparkles size={12} />
                      </span>
                    </div>
                    <h2 className="mt-6 text-2xl font-extrabold tracking-tight text-[#17385f]">
                      No {activeTab} trips
                    </h2>
                    <p className="mx-auto mt-3 max-w-lg text-sm leading-6 text-[#6f86a1]">
                      {activeTab === "upcoming"
                        ? "Your confirmed upcoming stays and active payment bookings will appear here."
                        : activeTab === "past"
                          ? "Your completed confirmed stays will appear here after checkout."
                          : "Cancelled and expired reservations will appear here."}
                    </p>
                    {activeTab === "upcoming" && (
                      <Link
                        href="/hotels"
                        className="mt-6 inline-flex items-center gap-2 rounded-full bg-[#173f76] px-5 py-3 text-sm font-extrabold text-white"
                      >
                        <Compass size={16} />
                        Browse hotels
                      </Link>
                    )}
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </section>
    </main>
  );
}
