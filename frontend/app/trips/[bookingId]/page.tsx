"use client";

import {
  AlertCircle,
  ArrowLeft,
  CalendarDays,
  CheckCircle2,
  Clock3,
  Hotel,
  RefreshCw,
  ShieldCheck,
  TriangleAlert,
  Users,
  X,
} from "lucide-react";
import Link from "next/link";
import ModifyBooking from "../../../components/trips/ModifyBooking";
import { useParams } from "next/navigation";
import {
  useCallback,
  useEffect,
  useState,
} from "react";

import {
  BookingApiError,
  bookingApi,
} from "@/lib/api/bookings";
import type {
  BookingResponse,
  BookingStatus,
  CancelBookingResponse,
  CancellationPreviewResponse,
  RefundStatus,
} from "@/types/api-booking";

function dateOnly(value: string) {
  const [year, month, day] = value
    .split("-")
    .map(Number);

  return new Date(
    year,
    month - 1,
    day,
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(
    "en-IN",
    {
      weekday: "short",
      day: "numeric",
      month: "short",
      year: "numeric",
    },
  ).format(dateOnly(value));
}

function formatMoney(
  amount: number,
  currency: string,
) {
  return new Intl.NumberFormat(
    "en-IN",
    {
      style: "currency",
      currency,
      maximumFractionDigits: 0,
    },
  ).format(amount);
}

function formatInstant(
  value: string | null,
) {
  if (!value) {
    return "—";
  }

  return new Intl.DateTimeFormat(
    "en-IN",
    {
      day: "numeric",
      month: "short",
      year: "numeric",
      hour: "numeric",
      minute: "2-digit",
    },
  ).format(new Date(value));
}

function statusMeta(
  status: BookingStatus,
) {
  switch (status) {
    case "CONFIRMED":
      return {
        label: "Confirmed",
        text:
          "Your reservation is confirmed and secured.",
        classes:
          "border-emerald-200 bg-emerald-50 text-emerald-700",
      };

    case "PENDING_PAYMENT":
      return {
        label: "Payment pending",
        text:
          "This booking is waiting for authoritative payment verification.",
        classes:
          "border-amber-200 bg-amber-50 text-amber-700",
      };

    case "CANCELLED":
      return {
        label: "Cancelled",
        text:
          "This reservation has been cancelled.",
        classes:
          "border-rose-200 bg-rose-50 text-rose-700",
      };

    case "EXPIRED":
      return {
        label: "Expired",
        text:
          "The payment window for this reservation expired.",
        classes:
          "border-slate-200 bg-slate-50 text-slate-600",
      };
  }
}

function refundStatusMeta(
  status: RefundStatus | null,
) {
  switch (status) {
    case "PENDING":
      return {
        title: "Refund queued",
        description:
          "Your refund has been queued for secure processing.",
        classes:
          "border-blue-200 bg-blue-50 text-blue-700",
      };

    case "PROCESSING":
      return {
        title: "Refund processing",
        description:
          "The payment provider is processing your refund.",
        classes:
          "border-blue-200 bg-blue-50 text-blue-700",
      };

    case "SUCCEEDED":
      return {
        title: "Refund completed",
        description:
          "The payment provider has confirmed the refund.",
        classes:
          "border-emerald-200 bg-emerald-50 text-emerald-700",
      };

    case "FAILED":
      return {
        title: "Refund needs attention",
        description:
          "The refund could not be completed automatically. Do not submit another cancellation request.",
        classes:
          "border-rose-200 bg-rose-50 text-rose-700",
      };

    case "REQUIRES_RECONCILIATION":
      return {
        title:
          "Refund verification in progress",
        description:
          "SpatialStay is verifying the refund with the payment provider. No action is required from you.",
        classes:
          "border-violet-200 bg-violet-50 text-violet-700",
      };

    default:
      return {
        title: "No refund required",
        description:
          "No refund processing is required for this cancellation.",
        classes:
          "border-slate-200 bg-slate-50 text-slate-600",
      };
  }
}

function apiErrorMessage(
  error: unknown,
  fallback: string,
) {
  if (
    error instanceof BookingApiError
  ) {
    return error.message;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return fallback;
}

export default function TripDetailsPage() {
  const params =
    useParams<{
      bookingId: string;
    }>();

  const bookingId =
    params.bookingId;

  const [
    booking,
    setBooking,
  ] =
    useState<BookingResponse | null>(
      null,
    );

  const [
    loading,
    setLoading,
  ] = useState(true);

  const [
    error,
    setError,
  ] = useState("");

  const [
    unauthorized,
    setUnauthorized,
  ] = useState(false);

  const [
    cancellationPreview,
    setCancellationPreview,
  ] =
    useState<CancellationPreviewResponse | null>(
      null,
    );

  const [
    cancelResult,
    setCancelResult,
  ] =
    useState<CancelBookingResponse | null>(
      null,
    );

  const [
    manageOpen,
    setManageOpen,
  ] = useState(false);

  const [
    previewLoading,
    setPreviewLoading,
  ] = useState(false);

  const [
    cancelling,
    setCancelling,
  ] = useState(false);

  const [
    manageError,
    setManageError,
  ] = useState("");

  const loadBooking =
    useCallback(async () => {
      if (!bookingId) {
        return;
      }

      setLoading(true);
      setError("");
      setUnauthorized(false);

      try {
        setBooking(
          await bookingApi.getBooking(
            bookingId,
          ),
        );
      } catch (loadError) {
        if (
          loadError instanceof
            BookingApiError &&
          loadError.status === 401
        ) {
          setUnauthorized(true);

          setError(
            "Please sign in to view this booking.",
          );
        } else {
          setError(
            loadError instanceof Error
              ? loadError.message
              : "We could not load this booking.",
          );
        }
      } finally {
        setLoading(false);
      }
    }, [bookingId]);

  useEffect(() => {
    void loadBooking();
  }, [loadBooking]);

  async function openManageBooking() {
    if (
      !booking ||
      booking.status !==
        "CONFIRMED" ||
      previewLoading
    ) {
      return;
    }

    setPreviewLoading(true);
    setManageError("");
    setCancelResult(null);
    setCancellationPreview(null);

    try {
      /*
       * Cancellation eligibility,
       * fee and refund are always
       * calculated by the backend.
       */
      const preview =
        await bookingApi.getCancellationPreview(
          booking.bookingId,
        );

      setCancellationPreview(
        preview,
      );

      setManageOpen(true);
    } catch (previewError) {
      setManageError(
        apiErrorMessage(
          previewError,
          "We could not load the cancellation policy.",
        ),
      );

      setManageOpen(true);
    } finally {
      setPreviewLoading(false);
    }
  }

  function closeManageBooking() {
    if (cancelling) {
      return;
    }

    setManageOpen(false);
    setCancellationPreview(null);
    setCancelResult(null);
    setManageError("");
  }

  async function confirmCancellation() {
    if (
      !booking ||
      !cancellationPreview?.cancellable ||
      cancelling
    ) {
      return;
    }

    setCancelling(true);
    setManageError("");

    try {
      /*
       * No amount, fee, payment ID,
       * refund ID or provider data
       * is supplied by the browser.
       *
       * The backend re-validates the
       * booking and calculates all
       * trusted financial values.
       */
      const result =
        await bookingApi.cancelBooking(
          booking.bookingId,
        );

      setCancelResult(result);

      /*
       * Re-read the authoritative
       * booking after cancellation.
       */
      const refreshed =
        await bookingApi.getBooking(
          booking.bookingId,
        );

      setBooking(refreshed);
    } catch (cancelError) {
      setManageError(
        apiErrorMessage(
          cancelError,
          "We could not complete the cancellation.",
        ),
      );

      /*
       * The request may have committed
       * server-side even if the browser
       * lost the response.
       *
       * Read the booking again instead
       * of blindly retrying cancellation.
       */
      try {
        const refreshed =
          await bookingApi.getBooking(
            booking.bookingId,
          );

        setBooking(refreshed);

        if (
          refreshed.status ===
          "CANCELLED"
        ) {
          setManageError(
            "The booking is cancelled. Do not submit another cancellation request.",
          );
        }
      } catch {
        /*
         * Keep the original error.
         */
      }
    } finally {
      setCancelling(false);
    }
  }

  return (
    <main className="travel-shell min-h-screen pb-24 pt-[105px]">
      <section className="mx-auto max-w-5xl px-4 sm:px-6">
        <Link
          href="/trips"
          className="mb-4 inline-flex items-center gap-2 text-sm font-extrabold text-[#52739b] transition hover:text-[#17385f]"
        >
          <ArrowLeft size={16} />
          Back to My Trips
        </Link>

        {loading && (
          <div className="glass-panel h-[520px] animate-pulse rounded-[28px]" />
        )}

        {!loading &&
          error && (
            <div className="glass-panel rounded-[28px] p-8 text-center">
              <AlertCircle
                className="mx-auto text-rose-500"
                size={30}
              />

              <h1 className="mt-3 text-2xl font-extrabold text-[#17385f]">
                {unauthorized
                  ? "Sign in required"
                  : "Booking unavailable"}
              </h1>

              <p className="mx-auto mt-2 max-w-lg text-sm leading-6 text-[#6f86a1]">
                {error}
              </p>

              {!unauthorized && (
                <button
                  type="button"
                  onClick={() =>
                    void loadBooking()
                  }
                  className="mt-5 inline-flex items-center gap-2 rounded-full bg-[#173f76] px-5 py-2.5 text-sm font-extrabold text-white"
                >
                  <RefreshCw
                    size={15}
                  />
                  Try again
                </button>
              )}
            </div>
          )}

        {!loading &&
          !error &&
          booking &&
          (() => {
            const meta =
              statusMeta(
                booking.status,
              );

            return (
              <div className="glass-panel overflow-hidden rounded-[28px]">
                <div className="border-b border-[#dce8f6]/80 p-5 sm:p-8">
                  <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <p className="text-xs font-extrabold uppercase tracking-[.15em] text-[#7890ad]">
                        Booking{" "}
                        {
                          booking.bookingReference
                        }
                      </p>

                      <h1 className="mt-2 text-3xl font-extrabold tracking-tight text-[#17385f] sm:text-4xl">
                        {
                          booking.hotelName
                        }
                      </h1>

                      <p className="mt-2 text-base font-semibold text-[#68809d]">
                        {
                          booking.roomName
                        }
                      </p>
                    </div>

                    <span
                      className={`w-fit rounded-full border px-4 py-2 text-sm font-extrabold ${meta.classes}`}
                    >
                      {meta.label}
                    </span>
                  </div>

                  <div
                    className={`mt-6 flex items-start gap-3 rounded-[20px] border p-4 ${meta.classes}`}
                  >
                    {booking.status ===
                    "CONFIRMED" ? (
                      <CheckCircle2
                        size={20}
                        className="mt-0.5 shrink-0"
                      />
                    ) : (
                      <Clock3
                        size={20}
                        className="mt-0.5 shrink-0"
                      />
                    )}

                    <p className="text-sm font-semibold">
                      {meta.text}
                    </p>
                  </div>
                </div>

                <div className="p-5 sm:p-8">
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="rounded-[22px] border border-[#e0eaf5] bg-white/75 p-5">
                      <div className="flex items-center gap-2 text-[#3975d6]">
                        <CalendarDays
                          size={18}
                        />

                        <p className="text-xs font-extrabold uppercase tracking-wider">
                          Stay
                        </p>
                      </div>

                      <div className="mt-4 grid grid-cols-2 gap-4">
                        <div>
                          <p className="text-xs font-semibold text-[#8aa0b8]">
                            Check-in
                          </p>

                          <p className="mt-1 text-sm font-extrabold text-[#31567f]">
                            {formatDate(
                              booking.checkIn,
                            )}
                          </p>
                        </div>

                        <div>
                          <p className="text-xs font-semibold text-[#8aa0b8]">
                            Check-out
                          </p>

                          <p className="mt-1 text-sm font-extrabold text-[#31567f]">
                            {formatDate(
                              booking.checkOut,
                            )}
                          </p>
                        </div>
                      </div>

                      <p className="mt-4 text-sm font-semibold text-[#7187a2]">
                        {
                          booking.nights
                        }{" "}
                        night
                        {booking.nights ===
                        1
                          ? ""
                          : "s"}
                      </p>
                    </div>

                    <div className="rounded-[22px] border border-[#e0eaf5] bg-white/75 p-5">
                      <div className="flex items-center gap-2 text-[#3975d6]">
                        <Users
                          size={18}
                        />

                        <p className="text-xs font-extrabold uppercase tracking-wider">
                          Reservation
                        </p>
                      </div>

                      <div className="mt-4 grid grid-cols-2 gap-4">
                        <div>
                          <p className="text-xs font-semibold text-[#8aa0b8]">
                            Guests
                          </p>

                          <p className="mt-1 text-lg font-extrabold text-[#31567f]">
                            {
                              booking.guests
                            }
                          </p>
                        </div>

                        <div>
                          <p className="text-xs font-semibold text-[#8aa0b8]">
                            Rooms
                          </p>

                          <p className="mt-1 text-lg font-extrabold text-[#31567f]">
                            {
                              booking.units
                            }
                          </p>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="mt-4 rounded-[22px] border border-[#e0eaf5] bg-white/75 p-5">
                    <div className="flex items-center gap-2 text-[#3975d6]">
                      <Hotel
                        size={18}
                      />

                      <p className="text-xs font-extrabold uppercase tracking-wider">
                        Payment summary
                      </p>
                    </div>

                    <div className="mt-4 grid gap-4 sm:grid-cols-3">
                      <div>
                        <p className="text-xs font-semibold text-[#8aa0b8]">
                          Price / night
                        </p>

                        <p className="mt-1 text-base font-extrabold text-[#31567f]">
                          {formatMoney(
                            booking.pricePerNight,
                            booking.currency,
                          )}
                        </p>
                      </div>

                      <div>
                        <p className="text-xs font-semibold text-[#8aa0b8]">
                          Total paid /
                          due
                        </p>

                        <p className="mt-1 text-xl font-extrabold text-[#17385f]">
                          {formatMoney(
                            booking.totalAmount,
                            booking.currency,
                          )}
                        </p>
                      </div>

                      <div>
                        <p className="text-xs font-semibold text-[#8aa0b8]">
                          Currency
                        </p>

                        <p className="mt-1 text-base font-extrabold text-[#31567f]">
                          {
                            booking.currency
                          }
                        </p>
                      </div>
                    </div>
                  </div>

                  <div className="mt-4 rounded-[22px] border border-[#e0eaf5] bg-[#f6f9fd] p-5">
                    <p className="text-xs font-extrabold uppercase tracking-wider text-[#7890ad]">
                      Booking timeline
                    </p>

                    <div className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
                      <div>
                        <span className="font-semibold text-[#8296ad]">
                          Created:{" "}
                        </span>

                        <span className="font-bold text-[#31567f]">
                          {formatInstant(
                            booking.createdAt,
                          )}
                        </span>
                      </div>

                      <div>
                        <span className="font-semibold text-[#8296ad]">
                          Confirmed:{" "}
                        </span>

                        <span className="font-bold text-[#31567f]">
                          {formatInstant(
                            booking.confirmedAt,
                          )}
                        </span>
                      </div>

                      {booking.cancelledAt && (
                        <div>
                          <span className="font-semibold text-[#8296ad]">
                            Cancelled:{" "}
                          </span>

                          <span className="font-bold text-[#31567f]">
                            {formatInstant(
                              booking.cancelledAt,
                            )}
                          </span>
                        </div>
                      )}
                    </div>
                  </div>

                  {booking.status ===
                    "CONFIRMED" && (
                    <div className="mt-5 rounded-[22px] border border-[#dce8f6] bg-[linear-gradient(145deg,#f8fbff,#eef5ff)] p-5 sm:p-6">
                      <div className="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
                        <div className="max-w-2xl">
                          <div className="flex items-center gap-2 text-[#3975d6]">
                            <ShieldCheck
                              size={19}
                            />

                            <p className="font-extrabold text-[#17385f]">
                              Manage
                              this
                              booking
                            </p>
                          </div>

                          <p className="mt-2 text-sm leading-6 text-[#7187a2]">
                            Review
                            the
                            authoritative
                            cancellation
                            policy,
                            applicable
                            fee and
                            refund
                            amount
                            before
                            changing
                            this
                            reservation.
                          </p>
                        </div>

                        <div className="flex flex-wrap items-center gap-3">
                          <ModifyBooking booking={booking} onUpdated={setBooking} />
                        <button
                          type="button"
                          disabled={
                            previewLoading
                          }
                          onClick={() =>
                            void openManageBooking()
                          }
                          className="inline-flex shrink-0 items-center justify-center gap-2 rounded-full bg-[#173f76] px-5 py-3 text-sm font-extrabold text-white shadow-sm transition hover:bg-[#12345f] disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {previewLoading ? (
                            <>
                              <RefreshCw
                                size={
                                  16
                                }
                                className="animate-spin"
                              />

                              Checking
                              policy
                            </>
                          ) : (
                            "Manage booking"
                          )}
                        </button>
                        </div>
                      </div>
                    </div>
                  )}

                  {booking.status ===
                    "CANCELLED" && (
                    <div className="mt-5 rounded-[22px] border border-rose-200 bg-rose-50 p-5 sm:p-6">
                      <div className="flex items-start gap-3">
                        <CheckCircle2
                          size={21}
                          className="mt-0.5 shrink-0 text-rose-600"
                        />

                        <div>
                          <p className="font-extrabold text-rose-800">
                            Reservation
                            cancelled
                          </p>

                          <p className="mt-1 text-sm leading-6 text-rose-700">
                            This
                            reservation
                            was
                            cancelled
                            {booking.cancelledAt
                              ? ` on ${formatInstant(
                                  booking.cancelledAt,
                                )}`
                              : ""}
                            .
                          </p>

                          <p className="mt-2 text-xs leading-5 text-rose-600">
                            If a
                            refund
                            applies,
                            SpatialStay
                            processes
                            it
                            securely
                            against
                            the
                            original
                            successful
                            payment.
                            Do not
                            submit
                            another
                            cancellation
                            or
                            payment.
                          </p>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            );
          })()}
      </section>

      {manageOpen && (
        <div className="fixed inset-0 z-[100] flex items-end justify-center bg-[#10233d]/60 backdrop-blur-sm sm:items-center sm:p-5">
          <div className="max-h-[92vh] w-full overflow-y-auto rounded-t-[30px] bg-white shadow-2xl sm:max-w-xl sm:rounded-[30px]">
            <div className="p-5 sm:p-7">
              <div className="flex items-start justify-between gap-5">
                <div>
                  <p className="text-xs font-extrabold uppercase tracking-[.15em] text-[#3975d6]">
                    Manage booking
                  </p>

                  <h2 className="mt-2 text-2xl font-extrabold text-[#17385f]">
                    {cancelResult
                      ? "Cancellation confirmed"
                      : "Review cancellation"}
                  </h2>
                </div>

                <button
                  type="button"
                  disabled={
                    cancelling
                  }
                  onClick={
                    closeManageBooking
                  }
                  aria-label="Close"
                  className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#f1f6fc] text-[#52739b] transition hover:bg-[#e4eef9] disabled:opacity-50"
                >
                  <X size={18} />
                </button>
              </div>

              {cancelResult ? (
                <>
                  <div className="mt-6 rounded-[22px] border border-emerald-200 bg-emerald-50 p-5">
                    <div className="flex items-start gap-3">
                      <CheckCircle2
                        size={22}
                        className="mt-0.5 shrink-0 text-emerald-600"
                      />

                      <div>
                        <p className="font-extrabold text-emerald-800">
                          Booking
                          cancelled
                          successfully
                        </p>

                        <p className="mt-1 text-sm leading-6 text-emerald-700">
                          {
                            cancelResult.message
                          }
                        </p>
                      </div>
                    </div>
                  </div>

                  <div className="mt-5 rounded-[22px] border border-[#e0eaf5] bg-[#f8fbff] p-5">
                    <div className="flex items-center justify-between gap-4 text-sm">
                      <span className="font-semibold text-[#7890ad]">
                        Original
                        payment
                      </span>

                      <span className="font-extrabold text-[#31567f]">
                        {formatMoney(
                          cancelResult.originalAmount,
                          cancelResult.currency,
                        )}
                      </span>
                    </div>

                    <div className="mt-3 flex items-center justify-between gap-4 text-sm">
                      <span className="font-semibold text-[#7890ad]">
                        Cancellation
                        fee
                      </span>

                      <span className="font-extrabold text-[#31567f]">
                        {formatMoney(
                          cancelResult.cancellationFee,
                          cancelResult.currency,
                        )}
                      </span>
                    </div>

                    <div className="my-4 border-t border-[#dce8f6]" />

                    <div className="flex items-center justify-between gap-4">
                      <span className="font-extrabold text-[#17385f]">
                        Refund
                        amount
                      </span>

                      <span className="text-xl font-extrabold text-emerald-600">
                        {formatMoney(
                          cancelResult.refundAmount,
                          cancelResult.currency,
                        )}
                      </span>
                    </div>
                  </div>

                  {cancelResult.refundRequired &&
                    (() => {
                      const refundMeta =
                        refundStatusMeta(
                          cancelResult.refundStatus,
                        );

                      return (
                        <div
                          className={`mt-5 rounded-[22px] border p-5 ${refundMeta.classes}`}
                        >
                          <p className="font-extrabold">
                            {
                              refundMeta.title
                            }
                          </p>

                          <p className="mt-1 text-sm leading-6">
                            {
                              refundMeta.description
                            }
                          </p>

                          {cancelResult.refundId && (
                            <p className="mt-3 break-all font-mono text-[11px] opacity-70">
                              Refund
                              reference:{" "}
                              {
                                cancelResult.refundId
                              }
                            </p>
                          )}
                        </div>
                      );
                    })()}

                  {!cancelResult.refundRequired && (
                    <div className="mt-5 rounded-[22px] border border-slate-200 bg-slate-50 p-5 text-slate-600">
                      <p className="font-extrabold">
                        No refund
                        required
                      </p>

                      <p className="mt-1 text-sm leading-6">
                        This
                        cancellation
                        does not
                        require
                        provider-side
                        refund
                        processing.
                      </p>
                    </div>
                  )}

                  <button
                    type="button"
                    onClick={
                      closeManageBooking
                    }
                    className="mt-6 inline-flex w-full items-center justify-center rounded-full bg-[#173f76] px-5 py-3 text-sm font-extrabold text-white transition hover:bg-[#12345f]"
                  >
                    Back to booking
                  </button>
                </>
              ) : cancellationPreview ? (
                <>
                  <div className="mt-6 rounded-[22px] border border-[#e0eaf5] bg-[#f8fbff] p-5">
                    <p className="text-xs font-extrabold uppercase tracking-wider text-[#7890ad]">
                      Cancellation
                      policy
                    </p>

                    <p className="mt-2 text-sm font-semibold leading-6 text-[#607b9b]">
                      {
                        cancellationPreview.policyDescription
                      }
                    </p>
                  </div>

                  <div className="mt-4 rounded-[22px] border border-[#e0eaf5] bg-white p-5">
                    <div className="flex items-center justify-between gap-4 text-sm">
                      <span className="font-semibold text-[#7890ad]">
                        Original
                        payment
                      </span>

                      <span className="font-extrabold text-[#31567f]">
                        {formatMoney(
                          cancellationPreview.originalAmount,
                          cancellationPreview.currency,
                        )}
                      </span>
                    </div>

                    <div className="mt-3 flex items-center justify-between gap-4 text-sm">
                      <span className="font-semibold text-[#7890ad]">
                        Cancellation
                        fee
                      </span>

                      <span className="font-extrabold text-[#31567f]">
                        {formatMoney(
                          cancellationPreview.cancellationFee,
                          cancellationPreview.currency,
                        )}
                      </span>
                    </div>

                    <div className="my-4 border-t border-[#dce8f6]" />

                    <div className="flex items-center justify-between gap-4">
                      <span className="font-extrabold text-[#17385f]">
                        Expected
                        refund
                      </span>

                      <span className="text-xl font-extrabold text-emerald-600">
                        {formatMoney(
                          cancellationPreview.refundAmount,
                          cancellationPreview.currency,
                        )}
                      </span>
                    </div>
                  </div>

                  {!cancellationPreview.cancellable ? (
                    <div className="mt-4 rounded-[22px] border border-amber-200 bg-amber-50 p-5">
                      <div className="flex items-start gap-3">
                        <Clock3
                          size={20}
                          className="mt-0.5 shrink-0 text-amber-600"
                        />

                        <div>
                          <p className="font-extrabold text-amber-900">
                            Cancellation
                            is not
                            available
                          </p>

                          <p className="mt-1 text-sm leading-6 text-amber-800">
                            {
                              cancellationPreview.message
                            }
                          </p>

                          {cancellationPreview.reason ===
                            "MANAGEMENT_COOLDOWN" &&
                            cancellationPreview.managementAvailableAt && (
                              <p className="mt-3 text-xs font-extrabold text-amber-900">
                                Booking
                                management
                                becomes
                                available{" "}
                                {formatInstant(
                                  cancellationPreview.managementAvailableAt,
                                )}
                              </p>
                            )}
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div className="mt-4 rounded-[22px] border border-rose-200 bg-rose-50 p-5">
                      <div className="flex items-start gap-3">
                        <TriangleAlert
                          size={20}
                          className="mt-0.5 shrink-0 text-rose-600"
                        />

                        <div>
                          <p className="font-extrabold text-rose-900">
                            Cancellation
                            cannot be
                            undone
                          </p>

                          <p className="mt-1 text-sm leading-6 text-rose-800">
                            Your
                            reservation
                            will be
                            cancelled
                            and the
                            applicable
                            refund
                            will be
                            securely
                            submitted
                            against
                            the
                            original
                            successful
                            payment.
                          </p>
                        </div>
                      </div>
                    </div>
                  )}

                  {manageError && (
                    <div className="mt-4 rounded-[20px] border border-rose-200 bg-rose-50 p-4 text-sm font-semibold leading-6 text-rose-700">
                      {manageError}
                    </div>
                  )}

                  <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row">
                    <button
                      type="button"
                      disabled={
                        cancelling
                      }
                      onClick={
                        closeManageBooking
                      }
                      className="inline-flex flex-1 items-center justify-center rounded-full border border-[#d7e4f2] bg-white px-5 py-3 text-sm font-extrabold text-[#52739b] transition hover:bg-[#f6f9fd] disabled:opacity-50"
                    >
                      Keep booking
                    </button>

                    {cancellationPreview.cancellable && (
                      <button
                        type="button"
                        disabled={
                          cancelling
                        }
                        onClick={() =>
                          void confirmCancellation()
                        }
                        className="inline-flex flex-1 items-center justify-center gap-2 rounded-full bg-rose-600 px-5 py-3 text-sm font-extrabold text-white transition hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        {cancelling && (
                          <RefreshCw
                            size={16}
                            className="animate-spin"
                          />
                        )}

                        {cancelling
                          ? "Cancelling securely"
                          : "Confirm cancellation"}
                      </button>
                    )}
                  </div>
                </>
              ) : (
                <div className="mt-6 rounded-[22px] border border-rose-200 bg-rose-50 p-5">
                  <AlertCircle
                    size={22}
                    className="text-rose-600"
                  />

                  <p className="mt-3 font-extrabold text-rose-900">
                    Unable to load
                    cancellation
                    details
                  </p>

                  <p className="mt-1 text-sm leading-6 text-rose-700">
                    {manageError ||
                      "Please close this window and try again."}
                  </p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </main>
  );
}