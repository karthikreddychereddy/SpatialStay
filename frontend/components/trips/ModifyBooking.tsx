
"use client";

import { useEffect, useRef, useState } from "react";

import { bookingApi } from "@/lib/api/bookings";
import { paymentApi } from "@/lib/api/payments";
import { getRoomsByHotel } from "@/lib/api/rooms";

import type { ApiRoom } from "@/types/api-room";

import type {
  BookingResponse,
  ModificationResponse,
} from "@/types/api-booking";

import type {
  ModificationCheckoutResponse,
} from "@/types/api-payment";

// ------------------------------------------
// Types
// ------------------------------------------

interface ModifyBookingProps {
  booking: BookingResponse;
  onUpdated: (booking: BookingResponse) => void;
}

// ------------------------------------------
// Helpers
// ------------------------------------------

function formatMoney(
  amount: number,
  currency: string
): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
  }).format(amount);
}

function isActiveModification(
  status: string
): boolean {
  return [
    "PENDING",
    "AWAITING_PAYMENT",
    "READY_TO_APPLY",
    "REQUIRES_RECONCILIATION",
  ].includes(status);
}

async function ensureRazorpay(): Promise<void> {
  if (window.Razorpay) {
    return;
  }

  await new Promise<void>((resolve, reject) => {
    const existingScript =
      document.querySelector<HTMLScriptElement>(
        'script[data-modification-razorpay]'
      );

    const script =
      existingScript ??
      document.createElement("script");

    script.addEventListener(
      "load",
      () => resolve(),
      { once: true }
    );

    script.addEventListener(
      "error",
      () => {
        reject(
          new Error("Unable to load Razorpay checkout.")
        );
      },
      { once: true }
    );

    if (!existingScript) {
      script.src =
        "https://checkout.razorpay.com/v1/checkout.js";

      script.async = true;
      script.dataset.modificationRazorpay = "true";

      document.body.appendChild(script);
    }
  });

  if (!window.Razorpay) {
    throw new Error("Razorpay checkout is unavailable.");
  }
}

// ------------------------------------------
// Main component
// ------------------------------------------

export default function ModifyBooking({
  booking,
  onUpdated,
}: ModifyBookingProps) {
  // Modal state
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  // Booking modification form
  const [roomSlug, setRoomSlug] = useState(
    booking.roomSlug
  );

  const [checkIn, setCheckIn] = useState(
    booking.checkIn
  );

  const [checkOut, setCheckOut] = useState(
    booking.checkOut
  );

  const [guests, setGuests] = useState(
    booking.guests
  );

  const [units, setUnits] = useState(
    booking.units
  );

  // Available hotel rooms
  const [rooms, setRooms] = useState<ApiRoom[]>([]);
  const [roomsLoading, setRoomsLoading] =
    useState(false);

  const [roomsError, setRoomsError] = useState("");

  // Modification proposal
  const [proposal, setProposal] =
    useState<ModificationResponse | null>(null);

  // Payment checkout
  const [checkout, setCheckout] =
    useState<ModificationCheckoutResponse | null>(
      null
    );

  const [paymentStatus, setPaymentStatus] =
    useState("");

  // Prevent duplicate mutations
  const requestLock = useRef(false);

  // ------------------------------------------
  // Load rooms belonging to the booked hotel
  // ------------------------------------------

  useEffect(() => {
    if (!open) {
      return;
    }

    let cancelled = false;

    async function loadRooms() {
      setRoomsLoading(true);
      setRoomsError("");

      try {
        const response = await getRoomsByHotel(
        booking.hotelSlug
        );

        if (!cancelled) {
        const activeRooms = response.filter(
            (room) => room.status === "ACTIVE"
        );

        setRooms(activeRooms);
        }
      } catch {
        if (!cancelled) {
          setRoomsError(
            "Room options are unavailable. Try reopening this window."
          );
        }
      } finally {
        if (!cancelled) {
          setRoomsLoading(false);
        }
      }
    }

    void loadRooms();

    return () => {
      cancelled = true;
    };
  }, [open, booking.hotelSlug]);

  // ------------------------------------------
  // Refresh authoritative modification status
  // ------------------------------------------

  async function refreshModification(
    modificationId: string
  ): Promise<ModificationResponse> {
    const result =
      await bookingApi.getModification(modificationId);

    setProposal(result);

    if (result.status === "COMPLETED") {
      const updatedBooking =
        await bookingApi.getBooking(
          booking.bookingId
        );

      onUpdated(updatedBooking);
    }

    return result;
  }

  // ------------------------------------------
  // Poll modification status
  // ------------------------------------------

  useEffect(() => {
    if (
      !open ||
      !proposal ||
      !isActiveModification(proposal.status)
    ) {
      return;
    }

    const interval = setInterval(() => {
      void refreshModification(
        proposal.modificationId
      ).catch(() => {
        // Manual refresh remains available.
      });
    }, 5000);

    return () => {
      clearInterval(interval);
    };
  }, [
    open,
    proposal?.modificationId,
    proposal?.status,
  ]);

  // ------------------------------------------
  // Poll authoritative payment status
  // ------------------------------------------

  useEffect(() => {
    if (
      !open ||
      !checkout?.paymentId ||
      !proposal ||
      !isActiveModification(proposal.status)
    ) {
      return;
    }

    const interval = setInterval(() => {
      void paymentApi
        .getPayment(checkout.paymentId)
        .then((payment) => {
          setPaymentStatus(payment.status);
        })
        .catch(() => {
          // Do not infer payment success on failure.
        });
    }, 5000);

    return () => {
      clearInterval(interval);
    };
  }, [
    open,
    checkout?.paymentId,
    proposal?.status,
  ]);

  // ------------------------------------------
  // Safe request execution
  // ------------------------------------------

  async function runAction(
    action: () => Promise<void>
  ): Promise<void> {
    if (requestLock.current) {
      return;
    }

    requestLock.current = true;

    setBusy(true);
    setError("");

    try {
      await action();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Request failed. Please refresh the booking status."
      );
    } finally {
      requestLock.current = false;
      setBusy(false);
    }
  }

  // ------------------------------------------
  // Validate form values
  // ------------------------------------------

  function validateModification(): void {
    if (!roomSlug.trim()) {
      throw new Error("Please select a room.");
    }

    if (!checkIn || !checkOut) {
      throw new Error(
        "Please select check-in and check-out dates."
      );
    }

    if (checkOut <= checkIn) {
      throw new Error(
        "Check-out must be after check-in."
      );
    }

    if (
      !Number.isInteger(guests) ||
      guests < 1
    ) {
      throw new Error(
        "Guests must be a positive whole number."
      );
    }

    if (
      !Number.isInteger(units) ||
      units < 1
    ) {
      throw new Error(
        "Rooms must be a positive whole number."
      );
    }

    const noChanges =
      roomSlug === booking.roomSlug &&
      checkIn === booking.checkIn &&
      checkOut === booking.checkOut &&
      guests === booking.guests &&
      units === booking.units;

    if (noChanges) {
      throw new Error(
        "Change at least one booking detail."
      );
    }
  }

  // ------------------------------------------
  // Request authoritative modification quote
  // ------------------------------------------

  function requestQuote(): void {
    void runAction(async () => {
      validateModification();

      const result =
        await bookingApi.createModification(
          booking.bookingId,
          {
            roomSlug: roomSlug.trim(),
            checkIn,
            checkOut,
            guests,
            units,
          }
        );

      setProposal(result);

      setNotice(
        "Replacement inventory is temporarily held. Review the backend quote before proceeding."
      );
    });
  }

  // ------------------------------------------
  // Finalize modification
  // ------------------------------------------

  function finalizeModification(): void {
    if (!proposal) {
      return;
    }

    void runAction(async () => {
      const result =
        await bookingApi.finalizeModification(
          proposal.modificationId
        );

      setProposal(result);

      if (result.status === "COMPLETED") {
        const updatedBooking =
          await bookingApi.getBooking(
            booking.bookingId
          );

        onUpdated(updatedBooking);
      }

      setNotice(
        result.message ||
          "Modification status refreshed."
      );
    });
  }

  // ------------------------------------------
  // Start additional-payment checkout
  // ------------------------------------------

  function startPayment(): void {
    if (!proposal) {
      return;
    }

    void runAction(async () => {
      const result =
        checkout ??
        (await paymentApi.createModificationCheckout(
          proposal.modificationId
        ));

      setCheckout(result);

      await ensureRazorpay();

      const Razorpay = window.Razorpay;

      if (!Razorpay) {
        throw new Error(
          "Razorpay checkout is unavailable."
        );
      }

      const options = {
        key: result.checkoutKeyId,
        amount: result.amountInSubunits,
        currency: result.currency,
        order_id: result.providerOrderId,

        name: "SpatialStay",
        description:
          "Booking modification difference",

        handler: () => {
          setNotice(
            "Payment submitted. Waiting for secure backend verification."
          );

          void refreshModification(
            proposal.modificationId
          ).catch(() => {
            setError(
              "Unable to refresh payment status. Please try again."
            );
          });
        },

        modal: {
          ondismiss: () => {
            setNotice(
              "Checkout closed. Check verified payment status before retrying."
            );
          },
        },
      } satisfies ConstructorParameters<
        NonNullable<Window["Razorpay"]>
      >[0];

      const razorpay = new Razorpay(options);

      razorpay.on("payment.failed", () => {
        setNotice(
          "Payment failed or was interrupted. Check verified status before retrying."
        );
      });

      razorpay.open();
    });
  }

  // ------------------------------------------
  // Derived modification state
  // ------------------------------------------

  const holdExpired =
    proposal !== null &&
    new Date(
      proposal.holdExpiresAt
    ).getTime() <= Date.now();

  const canFinalize =
    proposal !== null &&
    proposal.status === "READY_TO_APPLY" &&
    !holdExpired &&
    (
      proposal.financialType === "NO_ADJUSTMENT" ||
      proposal.financialType === "PARTIAL_REFUND"
    );

  const canPayDifference =
    proposal !== null &&
    proposal.status === "AWAITING_PAYMENT" &&
    proposal.financialType ===
      "ADDITIONAL_PAYMENT" &&
    !holdExpired;

    // Keep floating AI widgets behind the booking
    // modification dialog while it is open.

    useEffect(() => {
    if (!open) {
        return;
    }

    const previousOverflow = document.body.style.overflow;

    document.body.style.overflow = "hidden";

    return () => {
        document.body.style.overflow = previousOverflow;
    };
    }, [open]);

  // ------------------------------------------
  // UI
  // ------------------------------------------

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="rounded-full bg-indigo-600 px-5 py-3 text-sm font-bold text-white transition hover:bg-indigo-700"
      >
        Modify booking
      </button>

      {open && (
        <div
            className="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-950/70 p-4"
            role="presentation"
        >
          <section
            role="dialog"
            aria-modal="true"
            aria-label="Modify booking"
            className="
                relative
                w-full
                max-w-xl
                max-h-[calc(100dvh-2rem)]
                overflow-y-auto
                overscroll-contain
                rounded-3xl
                bg-white
                p-5
                text-slate-900
                shadow-2xl
                sm:p-7
                "
          >
            {/* Header */}

            <div className="flex items-center justify-between gap-3">
              <h2 className="text-xl font-extrabold">
                Modify your stay
              </h2>

              <button
                type="button"
                onClick={() => setOpen(false)}
                className="rounded-full border px-4 py-2 text-sm font-semibold hover:bg-slate-50"
              >
                Close
              </button>
            </div>

            <p className="mt-2 text-sm text-slate-600">
              Booking {booking.bookingReference}.
              Prices and availability are confirmed
              by SpatialStay.
            </p>

            {/* Modification form */}

            {!proposal ? (
              <div className="mt-6 grid gap-4 sm:grid-cols-2">
                {/* Room selection */}

                <label className="sm:col-span-2 text-sm font-semibold">
                  Choose room

                  <select
                    value={roomSlug}
                    disabled={roomsLoading || !!roomsError}
                    onChange={(event) => {
                      setRoomSlug(event.target.value);
                    }}
                    className="mt-2 w-full rounded-xl border border-slate-300 bg-white p-3 outline-none focus:border-indigo-500 disabled:opacity-60"
                  >
                    <option value={booking.roomSlug}>
                      {booking.roomName} (Current)
                    </option>

                    {rooms
                      .filter(
                        (room) =>
                          room.slug !== booking.roomSlug
                      )
                      .map((room) => (
                        <option
                          key={room.id}
                          value={room.slug}
                        >
                          {room.name} — Up to{" "}
                          {room.maxGuests} guests
                        </option>
                      ))}
                  </select>

                  {roomsLoading && (
                    <span className="mt-2 block text-xs text-slate-500">
                      Loading available room types...
                    </span>
                  )}

                  {roomsError && (
                    <span className="mt-2 block text-xs text-rose-700">
                      {roomsError}
                    </span>
                  )}
                </label>

                {/* Check-in */}

                <label className="text-sm font-semibold">
                  Check-in

                  <input
                    type="date"
                    value={checkIn}
                    onChange={(event) => {
                      setCheckIn(event.target.value);
                    }}
                    className="mt-2 w-full rounded-xl border border-slate-300 p-3 outline-none focus:border-indigo-500"
                  />
                </label>

                {/* Check-out */}

                <label className="text-sm font-semibold">
                  Check-out

                  <input
                    type="date"
                    value={checkOut}
                    onChange={(event) => {
                      setCheckOut(event.target.value);
                    }}
                    className="mt-2 w-full rounded-xl border border-slate-300 p-3 outline-none focus:border-indigo-500"
                  />
                </label>

                {/* Guests */}

                <label className="text-sm font-semibold">
                  Guests

                  <input
                    type="number"
                    min={1}
                    step={1}
                    value={guests}
                    onChange={(event) => {
                      setGuests(
                        Number(event.target.value)
                      );
                    }}
                    className="mt-2 w-full rounded-xl border border-slate-300 p-3 outline-none focus:border-indigo-500"
                  />
                </label>

                {/* Units */}

                <label className="text-sm font-semibold">
                  Rooms / Units

                  <input
                    type="number"
                    min={1}
                    step={1}
                    value={units}
                    onChange={(event) => {
                      setUnits(
                        Number(event.target.value)
                      );
                    }}
                    className="mt-2 w-full rounded-xl border border-slate-300 p-3 outline-none focus:border-indigo-500"
                  />
                </label>

                {/* Quote button */}

                <button
                  type="button"
                  disabled={busy}
                  onClick={requestQuote}
                  className="sm:col-span-2 rounded-full bg-indigo-600 px-5 py-3 font-bold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {busy
                    ? "Checking availability..."
                    : "Get authoritative quote"}
                </button>
              </div>
            ) : (
              /* Modification proposal */

              <div className="mt-6 space-y-4">
                <div className="rounded-2xl bg-slate-50 p-5 text-sm">
                  <p className="font-bold text-slate-900">
                    Status:{" "}
                    {proposal.status.replaceAll(
                      "_",
                      " "
                    )}
                  </p>

                  <div className="mt-4 space-y-2">
                    <p>
                      New room:{" "}
                      {proposal.proposedRoomSlug}
                    </p>

                    <p>
                      Dates:{" "}
                      {proposal.proposedCheckIn}
                      {" → "}
                      {proposal.proposedCheckOut}
                    </p>

                    <p>
                      Guests:{" "}
                      {proposal.proposedGuests}
                    </p>

                    <p>
                      Units:{" "}
                      {proposal.proposedUnits}
                    </p>
                  </div>

                  <div className="mt-4 border-t border-slate-200 pt-4">
                    <p>
                      Current total:{" "}
                      {formatMoney(
                        proposal.originalTotal,
                        proposal.currency
                      )}
                    </p>

                    <p className="mt-2">
                      New total:{" "}
                      {formatMoney(
                        proposal.proposedTotal,
                        proposal.currency
                      )}
                    </p>

                    <p className="mt-3 font-extrabold text-indigo-700">
                      Adjustment:{" "}
                      {formatMoney(
                        Math.abs(
                          proposal.priceDifference
                        ),
                        proposal.currency
                      )}
                    </p>

                    <p className="mt-1 text-xs font-semibold text-slate-500">
                      {proposal.financialType.replaceAll(
                        "_",
                        " "
                      )}
                    </p>
                  </div>

                  {proposal.message && (
                    <p className="mt-4 text-slate-600">
                      {proposal.message}
                    </p>
                  )}

                  <p className="mt-4 text-xs text-slate-500">
                    Inventory hold expires:{" "}
                    {new Date(
                      proposal.holdExpiresAt
                    ).toLocaleString("en-IN")}
                  </p>
                </div>

                {/* Expired hold */}

                {holdExpired &&
                  isActiveModification(
                    proposal.status
                  ) && (
                    <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">
                      The inventory hold deadline
                      has passed. Refresh the
                      authoritative modification
                      status before continuing.
                    </p>
                  )}

                {/* Finalize no-adjustment or refund */}

                {canFinalize && (
                  <button
                    type="button"
                    disabled={busy}
                    onClick={finalizeModification}
                    className="w-full rounded-full bg-indigo-600 p-3 font-bold text-white hover:bg-indigo-700 disabled:opacity-50"
                  >
                    {proposal.financialType ===
                    "PARTIAL_REFUND"
                      ? "Confirm modification and request refund difference"
                      : "Confirm modification"}
                  </button>
                )}

                {/* Additional payment */}

                {canPayDifference && (
                  <button
                    type="button"
                    disabled={busy}
                    onClick={startPayment}
                    className="w-full rounded-full bg-indigo-600 p-3 font-bold text-white hover:bg-indigo-700 disabled:opacity-50"
                  >
                    {checkout
                      ? "Open payment checkout"
                      : "Pay difference securely"}
                  </button>
                )}

                {/* Pending */}

                {proposal.status === "PENDING" && (
                  <p className="rounded-xl bg-blue-50 p-3 text-sm text-blue-800">
                    Your modification proposal
                    is pending. Refresh the
                    verified status before
                    confirming.
                  </p>
                )}

                {/* Payment status */}

                {paymentStatus && (
                  <p className="rounded-xl bg-blue-50 p-3 text-sm text-blue-800">
                    Payment verification:{" "}
                    {paymentStatus}
                  </p>
                )}

                {/* Completed */}

                {proposal.status ===
                  "COMPLETED" && (
                  <p className="rounded-xl bg-emerald-50 p-3 text-sm font-semibold text-emerald-800">
                    Your booking modification
                    is completed. Any applicable
                    refund is processed
                    separately by the backend.
                  </p>
                )}

                {/* Reconciliation */}

                {proposal.status ===
                  "REQUIRES_RECONCILIATION" && (
                  <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">
                    Financial reconciliation
                    is pending. Do not initiate
                    another modification.
                  </p>
                )}

                {/* Refresh status */}

                <button
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    void runAction(async () => {
                      await refreshModification(
                        proposal.modificationId
                      );
                    });
                  }}
                  className="w-full rounded-full border border-slate-300 p-3 font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-50"
                >
                  Refresh verified status
                </button>
              </div>
            )}

            {/* Success / information */}

            {notice && (
              <p
                role="status"
                className="mt-4 rounded-xl bg-blue-50 p-3 text-sm text-blue-800"
              >
                {notice}
              </p>
            )}

            {/* Errors */}

            {error && (
              <p
                role="alert"
                className="mt-4 rounded-xl bg-rose-50 p-3 text-sm text-rose-800"
              >
                {error}
              </p>
            )}
          </section>
        </div>
      )}
    </>
  );
}
