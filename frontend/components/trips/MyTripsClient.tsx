"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";

import {
  BookingApiError,
  bookingApi,
} from "@/lib/api/bookings";

import type {
  BookingResponse,
  CancelBookingResponse,
  CancellationPreviewResponse,
  RefundStatus,
} from "@/types/api-booking";

type PreviewState = {
  booking: BookingResponse;
  preview: CancellationPreviewResponse;
};

function money(
  value: number,
  currency: string,
) {
  try {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
    }).format(value);
  } catch {
    return `${currency} ${value}`;
  }
}

function dateOnly(
  value: string,
) {
  try {
    return new Intl.DateTimeFormat(
      "en-IN",
      {
        day: "numeric",
        month: "short",
        year: "numeric",
      },
    ).format(
      new Date(`${value}T00:00:00`),
    );
  } catch {
    return value;
  }
}

function dateTime(
  value: string | null,
) {
  if (!value) {
    return "—";
  }

  try {
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
  } catch {
    return value;
  }
}

function statusClass(
  status: BookingResponse["status"],
) {
  switch (status) {
    case "CONFIRMED":
      return "border-emerald-200 bg-emerald-50 text-emerald-700";

    case "CANCELLED":
      return "border-rose-200 bg-rose-50 text-rose-700";

    case "PENDING_PAYMENT":
      return "border-amber-200 bg-amber-50 text-amber-700";

    case "EXPIRED":
      return "border-slate-200 bg-slate-100 text-slate-600";

    default:
      return "border-slate-200 bg-slate-50 text-slate-700";
  }
}

function statusLabel(
  status: BookingResponse["status"],
) {
  return status
    .toLowerCase()
    .split("_")
    .map(
      (part) =>
        part.charAt(0).toUpperCase() +
        part.slice(1),
    )
    .join(" ");
}

function refundLabel(
  status: RefundStatus | null,
) {
  if (!status) {
    return "Not required";
  }

  switch (status) {
    case "PENDING":
      return "Refund queued";

    case "PROCESSING":
      return "Refund processing";

    case "SUCCEEDED":
      return "Refund completed";

    case "FAILED":
      return "Refund needs attention";

    case "REQUIRES_RECONCILIATION":
      return "Refund verification in progress";
  }
}

function refundClass(
  status: RefundStatus | null,
) {
  switch (status) {
    case "SUCCEEDED":
      return "border-emerald-200 bg-emerald-50 text-emerald-700";

    case "FAILED":
      return "border-rose-200 bg-rose-50 text-rose-700";

    case "REQUIRES_RECONCILIATION":
      return "border-violet-200 bg-violet-50 text-violet-700";

    case "PROCESSING":
    case "PENDING":
      return "border-blue-200 bg-blue-50 text-blue-700";

    default:
      return "border-slate-200 bg-slate-50 text-slate-600";
  }
}

function errorMessage(
  error: unknown,
) {
  if (error instanceof BookingApiError) {
    return error.message;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return "Something went wrong. Please try again.";
}

export default function MyTripsClient() {
  const [bookings, setBookings] =
    useState<BookingResponse[]>([]);

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState<string | null>(null);

  const [previewState, setPreviewState] =
    useState<PreviewState | null>(null);

  const [previewLoadingId, setPreviewLoadingId] =
    useState<string | null>(null);

  const [cancelling, setCancelling] =
    useState(false);

  const [cancelResult, setCancelResult] =
    useState<CancelBookingResponse | null>(
      null,
    );

  const [modalError, setModalError] =
    useState<string | null>(null);

  const loadBookings =
    useCallback(async () => {
      setLoading(true);
      setError(null);

      try {
        const response =
          await bookingApi.getMyBookings();

        setBookings(response);
      } catch (loadError) {
        setError(
          errorMessage(loadError),
        );
      } finally {
        setLoading(false);
      }
    }, []);

  useEffect(() => {
    void loadBookings();
  }, [loadBookings]);

  const sortedBookings =
    useMemo(() => {
      return [...bookings].sort(
        (a, b) =>
          new Date(b.createdAt).getTime() -
          new Date(a.createdAt).getTime(),
      );
    }, [bookings]);

  async function openCancellation(
    booking: BookingResponse,
  ) {
    if (booking.status !== "CONFIRMED") {
      return;
    }

    setPreviewLoadingId(
      booking.bookingId,
    );

    setModalError(null);
    setCancelResult(null);

    try {
      /*
       * Always obtain a fresh authoritative preview.
       * Never calculate refund eligibility in the browser.
       */
      const preview =
        await bookingApi.getCancellationPreview(
          booking.bookingId,
        );

      setPreviewState({
        booking,
        preview,
      });
    } catch (previewError) {
      setError(
        errorMessage(previewError),
      );
    } finally {
      setPreviewLoadingId(null);
    }
  }

  function closeModal() {
    if (cancelling) {
      return;
    }

    setPreviewState(null);
    setCancelResult(null);
    setModalError(null);
  }

  async function confirmCancellation() {
    if (
      !previewState ||
      !previewState.preview.cancellable ||
      cancelling
    ) {
      return;
    }

    setCancelling(true);
    setModalError(null);

    try {
      /*
       * No financial data is sent from the UI.
       *
       * The backend locks the booking, recalculates
       * eligibility and determines the trusted refund.
       */
      const result =
        await bookingApi.cancelBooking(
          previewState.booking.bookingId,
        );

      setCancelResult(result);

      /*
       * Refresh authoritative booking status.
       */
      const refreshed =
        await bookingApi.getMyBookings();

      setBookings(refreshed);
    } catch (cancelError) {
      setModalError(
        errorMessage(cancelError),
      );

      /*
       * Cancellation is server-authoritative.
       *
       * If the HTTP response was interrupted after the
       * backend committed, refresh bookings so the UI
       * doesn't encourage a blind duplicate request.
       */
      try {
        const refreshed =
          await bookingApi.getMyBookings();

        setBookings(refreshed);

        const refreshedBooking =
          refreshed.find(
            (item) =>
              item.bookingId ===
              previewState.booking.bookingId,
          );

        if (
          refreshedBooking?.status ===
          "CANCELLED"
        ) {
          setModalError(
            "The booking is cancelled. Refresh My Trips if you need the latest refund information.",
          );
        }
      } catch {
        // Preserve the original cancellation error.
      }
    } finally {
      setCancelling(false);
    }
  }

  if (loading) {
    return (
      <main className="min-h-screen bg-slate-50">
        <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
          <div className="animate-pulse">
            <div className="h-10 w-52 rounded-xl bg-slate-200" />
            <div className="mt-3 h-5 w-80 max-w-full rounded-lg bg-slate-200" />

            <div className="mt-10 grid gap-6 lg:grid-cols-2">
              {[1, 2, 3, 4].map(
                (item) => (
                  <div
                    key={item}
                    className="h-80 rounded-3xl bg-white shadow-sm"
                  />
                ),
              )}
            </div>
          </div>
        </div>
      </main>
    );
  }

  return (
    <>
      <main className="min-h-screen bg-slate-50">
        <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
          <div className="flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="text-sm font-semibold uppercase tracking-[0.18em] text-blue-600">
                SpatialStay
              </p>

              <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl">
                My Trips
              </h1>

              <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600 sm:text-base">
                View your reservations,
                stay details and manage
                eligible confirmed bookings.
              </p>
            </div>

            <button
              type="button"
              onClick={() =>
                void loadBookings()
              }
              className="inline-flex h-11 items-center justify-center rounded-xl border border-slate-200 bg-white px-5 text-sm font-semibold text-slate-700 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
            >
              Refresh trips
            </button>
          </div>

          {error && (
            <div className="mt-7 flex flex-col gap-3 rounded-2xl border border-rose-200 bg-rose-50 p-4 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-sm text-rose-700">
                {error}
              </p>

              <button
                type="button"
                onClick={() =>
                  void loadBookings()
                }
                className="text-sm font-semibold text-rose-700 underline underline-offset-4"
              >
                Try again
              </button>
            </div>
          )}

          {!error &&
            sortedBookings.length === 0 && (
              <div className="mt-10 rounded-3xl border border-slate-200 bg-white p-10 text-center shadow-sm">
                <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-blue-50 text-2xl">
                  ✈
                </div>

                <h2 className="mt-5 text-xl font-bold text-slate-950">
                  No trips yet
                </h2>

                <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-600">
                  Your SpatialStay
                  reservations will appear
                  here after you create a
                  booking.
                </p>

                <Link
                  href="/hotels"
                  className="mt-6 inline-flex h-11 items-center justify-center rounded-xl bg-slate-950 px-6 text-sm font-semibold text-white transition hover:bg-slate-800"
                >
                  Explore hotels
                </Link>
              </div>
            )}

          <div className="mt-9 grid gap-6 lg:grid-cols-2">
            {sortedBookings.map(
              (booking) => {
                const isConfirmed =
                  booking.status ===
                  "CONFIRMED";

                const isCancelled =
                  booking.status ===
                  "CANCELLED";

                const previewLoading =
                  previewLoadingId ===
                  booking.bookingId;

                return (
                  <article
                    key={booking.bookingId}
                    className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
                  >
                    <div className="border-b border-slate-100 bg-gradient-to-r from-slate-950 to-slate-800 p-6 text-white">
                      <div className="flex items-start justify-between gap-4">
                        <div>
                          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-slate-300">
                            Booking
                          </p>

                          <p className="mt-1 font-mono text-sm font-semibold">
                            {
                              booking.bookingReference
                            }
                          </p>
                        </div>

                        <span
                          className={`rounded-full border px-3 py-1 text-xs font-bold ${statusClass(
                            booking.status,
                          )}`}
                        >
                          {statusLabel(
                            booking.status,
                          )}
                        </span>
                      </div>

                      <h2 className="mt-6 text-2xl font-bold">
                        {booking.hotelName}
                      </h2>

                      <p className="mt-1 text-sm text-slate-300">
                        {booking.roomName}
                      </p>
                    </div>

                    <div className="p-6">
                      <div className="grid grid-cols-2 gap-5">
                        <div>
                          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                            Check-in
                          </p>

                          <p className="mt-1 text-sm font-semibold text-slate-900">
                            {dateOnly(
                              booking.checkIn,
                            )}
                          </p>
                        </div>

                        <div>
                          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                            Check-out
                          </p>

                          <p className="mt-1 text-sm font-semibold text-slate-900">
                            {dateOnly(
                              booking.checkOut,
                            )}
                          </p>
                        </div>

                        <div>
                          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                            Guests
                          </p>

                          <p className="mt-1 text-sm font-semibold text-slate-900">
                            {booking.guests}
                          </p>
                        </div>

                        <div>
                          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                            Rooms
                          </p>

                          <p className="mt-1 text-sm font-semibold text-slate-900">
                            {booking.units}
                          </p>
                        </div>
                      </div>

                      <div className="my-6 border-t border-slate-100" />

                      <div className="flex items-end justify-between gap-4">
                        <div>
                          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                            Total paid
                          </p>

                          <p className="mt-1 text-xl font-bold text-slate-950">
                            {money(
                              booking.totalAmount,
                              booking.currency,
                            )}
                          </p>
                        </div>

                        <div className="text-right">
                          <p className="text-xs text-slate-500">
                            {booking.nights}{" "}
                            {booking.nights === 1
                              ? "night"
                              : "nights"}
                          </p>

                          {booking.confirmedAt && (
                            <p className="mt-1 text-xs text-slate-400">
                              Confirmed{" "}
                              {dateTime(
                                booking.confirmedAt,
                              )}
                            </p>
                          )}
                        </div>
                      </div>

                      {isCancelled &&
                        booking.cancelledAt && (
                          <div className="mt-5 rounded-2xl border border-rose-100 bg-rose-50 p-4">
                            <p className="text-sm font-semibold text-rose-800">
                              Booking cancelled
                            </p>

                            <p className="mt-1 text-xs leading-5 text-rose-700">
                              Cancelled{" "}
                              {dateTime(
                                booking.cancelledAt,
                              )}
                              . Any applicable
                              refund is handled
                              securely by
                              SpatialStay.
                            </p>
                          </div>
                        )}

                      <div className="mt-6 flex flex-col gap-3 sm:flex-row">
                        <Link
                          href={`/hotels/${booking.hotelSlug}`}
                          className="inline-flex h-11 flex-1 items-center justify-center rounded-xl border border-slate-200 px-4 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                        >
                          View hotel
                        </Link>

                        {isConfirmed && (
                          <button
                            type="button"
                            disabled={
                              previewLoading
                            }
                            onClick={() =>
                              void openCancellation(
                                booking,
                              )
                            }
                            className="inline-flex h-11 flex-1 items-center justify-center rounded-xl bg-slate-950 px-4 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            {previewLoading
                              ? "Checking policy..."
                              : "Manage booking"}
                          </button>
                        )}
                      </div>
                    </div>
                  </article>
                );
              },
            )}
          </div>
        </div>
      </main>

      {previewState && (
        <div className="fixed inset-0 z-[100] flex items-end justify-center bg-slate-950/60 p-0 backdrop-blur-sm sm:items-center sm:p-5">
          <div className="max-h-[92vh] w-full overflow-y-auto rounded-t-3xl bg-white shadow-2xl sm:max-w-xl sm:rounded-3xl">
            {cancelResult ? (
              <div className="p-6 sm:p-8">
                <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-emerald-50 text-2xl text-emerald-700">
                  ✓
                </div>

                <h2 className="mt-5 text-2xl font-bold text-slate-950">
                  Booking cancelled
                </h2>

                <p className="mt-2 text-sm leading-6 text-slate-600">
                  {cancelResult.message}
                </p>

                <div className="mt-6 rounded-2xl border border-slate-200 bg-slate-50 p-5">
                  <div className="flex items-center justify-between gap-4">
                    <span className="text-sm text-slate-600">
                      Original payment
                    </span>

                    <strong className="text-sm text-slate-950">
                      {money(
                        cancelResult.originalAmount,
                        cancelResult.currency,
                      )}
                    </strong>
                  </div>

                  <div className="mt-3 flex items-center justify-between gap-4">
                    <span className="text-sm text-slate-600">
                      Cancellation fee
                    </span>

                    <strong className="text-sm text-slate-950">
                      {money(
                        cancelResult.cancellationFee,
                        cancelResult.currency,
                      )}
                    </strong>
                  </div>

                  <div className="my-4 border-t border-slate-200" />

                  <div className="flex items-center justify-between gap-4">
                    <span className="font-semibold text-slate-900">
                      Refund
                    </span>

                    <strong className="text-lg text-emerald-700">
                      {money(
                        cancelResult.refundAmount,
                        cancelResult.currency,
                      )}
                    </strong>
                  </div>
                </div>

                {cancelResult.refundRequired && (
                  <div
                    className={`mt-5 rounded-2xl border p-4 ${refundClass(
                      cancelResult.refundStatus,
                    )}`}
                  >
                    <p className="text-sm font-bold">
                      {refundLabel(
                        cancelResult.refundStatus,
                      )}
                    </p>

                    <p className="mt-1 text-xs leading-5">
                      {cancelResult.refundStatus ===
                      "SUCCEEDED"
                        ? "SpatialStay has received confirmation that the refund completed."
                        : cancelResult.refundStatus ===
                            "FAILED"
                          ? "The refund could not be completed automatically. Do not make another payment or cancellation request."
                          : cancelResult.refundStatus ===
                              "REQUIRES_RECONCILIATION"
                            ? "SpatialStay is verifying the refund with the payment provider. No action is required from you."
                            : "Your refund has been securely queued with the payment provider. Processing can continue after this page is closed."}
                    </p>

                    {cancelResult.refundId && (
                      <p className="mt-3 break-all font-mono text-[11px] opacity-70">
                        Refund reference:{" "}
                        {
                          cancelResult.refundId
                        }
                      </p>
                    )}
                  </div>
                )}

                <button
                  type="button"
                  onClick={closeModal}
                  className="mt-7 inline-flex h-12 w-full items-center justify-center rounded-xl bg-slate-950 px-5 text-sm font-semibold text-white transition hover:bg-slate-800"
                >
                  Back to My Trips
                </button>
              </div>
            ) : (
              <div className="p-6 sm:p-8">
                <div className="flex items-start justify-between gap-5">
                  <div>
                    <p className="text-sm font-semibold text-blue-600">
                      Manage booking
                    </p>

                    <h2 className="mt-1 text-2xl font-bold text-slate-950">
                      Cancellation
                    </h2>
                  </div>

                  <button
                    type="button"
                    disabled={cancelling}
                    onClick={closeModal}
                    aria-label="Close"
                    className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 text-lg text-slate-600 transition hover:bg-slate-200 disabled:opacity-50"
                  >
                    ×
                  </button>
                </div>

                <div className="mt-6 rounded-2xl border border-slate-200 p-5">
                  <p className="font-bold text-slate-950">
                    {
                      previewState.booking
                        .hotelName
                    }
                  </p>

                  <p className="mt-1 text-sm text-slate-600">
                    {
                      previewState.booking
                        .roomName
                    }
                  </p>

                  <p className="mt-3 text-xs text-slate-500">
                    {
                      previewState.booking
                        .bookingReference
                    }
                  </p>
                </div>

                <div className="mt-5 rounded-2xl bg-slate-50 p-5">
                  <p className="text-sm font-bold text-slate-950">
                    Cancellation policy
                  </p>

                  <p className="mt-2 text-sm leading-6 text-slate-600">
                    {
                      previewState.preview
                        .policyDescription
                    }
                  </p>
                </div>

                <div className="mt-5 rounded-2xl border border-slate-200 p-5">
                  <div className="flex items-center justify-between gap-4">
                    <span className="text-sm text-slate-600">
                      Booking amount
                    </span>

                    <strong className="text-sm text-slate-950">
                      {money(
                        previewState.preview
                          .originalAmount,
                        previewState.preview
                          .currency,
                      )}
                    </strong>
                  </div>

                  <div className="mt-3 flex items-center justify-between gap-4">
                    <span className="text-sm text-slate-600">
                      Cancellation fee
                    </span>

                    <strong className="text-sm text-slate-950">
                      {money(
                        previewState.preview
                          .cancellationFee,
                        previewState.preview
                          .currency,
                      )}
                    </strong>
                  </div>

                  <div className="my-4 border-t border-slate-200" />

                  <div className="flex items-center justify-between gap-4">
                    <span className="font-semibold text-slate-950">
                      Expected refund
                    </span>

                    <strong className="text-xl text-emerald-700">
                      {money(
                        previewState.preview
                          .refundAmount,
                        previewState.preview
                          .currency,
                      )}
                    </strong>
                  </div>
                </div>

                {!previewState.preview
                  .cancellable ? (
                  <div className="mt-5 rounded-2xl border border-amber-200 bg-amber-50 p-4">
                    <p className="text-sm font-bold text-amber-900">
                      Cancellation is not
                      available yet
                    </p>

                    <p className="mt-1 text-sm leading-6 text-amber-800">
                      {
                        previewState.preview
                          .message
                      }
                    </p>

                    {previewState.preview
                      .reason ===
                      "MANAGEMENT_COOLDOWN" &&
                      previewState.preview
                        .managementAvailableAt && (
                        <p className="mt-3 text-xs font-semibold text-amber-900">
                          Manage this booking
                          after{" "}
                          {dateTime(
                            previewState
                              .preview
                              .managementAvailableAt,
                          )}
                        </p>
                      )}
                  </div>
                ) : (
                  <div className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 p-4">
                    <p className="text-sm font-bold text-rose-900">
                      This action cannot be
                      undone
                    </p>

                    <p className="mt-1 text-sm leading-6 text-rose-800">
                      SpatialStay will cancel
                      this reservation and
                      securely start the
                      applicable refund using
                      the original successful
                      payment.
                    </p>
                  </div>
                )}

                {modalError && (
                  <div className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm leading-6 text-rose-700">
                    {modalError}
                  </div>
                )}

                <div className="mt-7 flex flex-col-reverse gap-3 sm:flex-row">
                  <button
                    type="button"
                    disabled={cancelling}
                    onClick={closeModal}
                    className="inline-flex h-12 flex-1 items-center justify-center rounded-xl border border-slate-200 px-5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-50"
                  >
                    Keep booking
                  </button>

                  {previewState.preview
                    .cancellable && (
                    <button
                      type="button"
                      disabled={cancelling}
                      onClick={() =>
                        void confirmCancellation()
                      }
                      className="inline-flex h-12 flex-1 items-center justify-center rounded-xl bg-rose-600 px-5 text-sm font-semibold text-white transition hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {cancelling
                        ? "Cancelling securely..."
                        : "Confirm cancellation"}
                    </button>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
}