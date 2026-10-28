"use client";

import {
  AlertCircle,
  CalendarDays,
  CheckCircle2,
  Clock3,
  LoaderCircle,
  LockKeyhole,
  RefreshCw,
  ShieldCheck,
  Users,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { authApi, type AuthUser } from "@/lib/auth-api";
import { BookingApiError, bookingApi } from "@/lib/api/bookings";
import { paymentApi } from "@/lib/api/payments";
import { getRoomAvailability } from "@/lib/api/rooms";
import type {
  BookingResponse,
  InventoryHoldResponse,
} from "@/types/api-booking";

type BookingFlowProps = {
  hotelSlug: string;
  hotelName: string;
  roomSlug: string;
  roomName: string;
  checkIn: string;
  checkOut: string;
  guests: number;
};

type FlowState =
  | "checking-auth"
  | "ready"
  | "creating-hold"
  | "held"
  | "creating-booking"
  | "pending-payment"
  | "starting-payment"
  | "verifying-payment"
  | "confirmed"
  | "expired"
  | "error";

type RazorpaySuccessResponse = {
  razorpay_payment_id: string;
  razorpay_order_id: string;
  razorpay_signature: string;
};

type RazorpayFailureResponse = {
  error?: {
    code?: string;
    description?: string;
    source?: string;
    step?: string;
    reason?: string;
  };
};

type RazorpayOptions = {
  key: string;
  amount: number;
  currency: string;
  name: string;
  description: string;
  order_id: string;
  handler: (response: RazorpaySuccessResponse) => void;
  modal?: {
    ondismiss?: () => void;
  };
  theme?: {
    color?: string;
  };
};

type RazorpayInstance = {
  open(): void;
  on(
    event: "payment.failed",
    handler: (response: RazorpayFailureResponse) => void,
  ): void;
};

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayOptions) => RazorpayInstance;
  }
}

function holdStorageKey(
  hotelSlug: string,
  roomSlug: string,
  checkIn: string,
  checkOut: string,
  guests: number,
) {
  return [
    "spatialstay",
    "hold",
    hotelSlug,
    roomSlug,
    checkIn,
    checkOut,
    guests,
  ].join(":");
}

function bookingStorageKey(holdId: string) {
  return `spatialstay:booking:${holdId}`;
}

let razorpayScriptPromise: Promise<void> | null = null;

function loadRazorpayCheckout(): Promise<void> {
  if (typeof window !== "undefined" && window.Razorpay) {
    return Promise.resolve();
  }

  if (razorpayScriptPromise) {
    return razorpayScriptPromise;
  }

  razorpayScriptPromise = new Promise<void>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(
      'script[data-spatialstay-razorpay="true"]',
    );

    if (existing) {
      if (window.Razorpay) {
        resolve();
        return;
      }

      existing.addEventListener("load", () => resolve(), {
        once: true,
      });

      existing.addEventListener(
        "error",
        () => {
          razorpayScriptPromise = null;
          reject(new Error("Unable to load secure payment checkout."));
        },
        { once: true },
      );

      return;
    }

    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.async = true;
    script.dataset.spatialstayRazorpay = "true";

    script.onload = () => {
      resolve();
    };

    script.onerror = () => {
      razorpayScriptPromise = null;
      reject(new Error("Unable to load secure payment checkout."));
    };

    document.body.appendChild(script);
  });

  return razorpayScriptPromise;
}

export default function BookingFlow({
  hotelSlug,
  hotelName,
  roomSlug,
  roomName,
  checkIn,
  checkOut,
  guests,
}: BookingFlowProps) {
  const [flowState, setFlowState] =
    useState<FlowState>("checking-auth");
  const [user, setUser] = useState<AuthUser | null>(null);
  const [hold, setHold] =
    useState<InventoryHoldResponse | null>(null);
  const [booking, setBooking] =
    useState<BookingResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState(0);

  const storageKey = useMemo(
    () =>
      holdStorageKey(
        hotelSlug,
        roomSlug,
        checkIn,
        checkOut,
        guests,
      ),
    [hotelSlug, roomSlug, checkIn, checkOut, guests],
  );

  const syncRemainingTime = useCallback(
    (currentHold: InventoryHoldResponse) => {
      const expiresAt = new Date(currentHold.expiresAt).getTime();

      const remaining = Math.max(
        0,
        Math.ceil((expiresAt - Date.now()) / 1000),
      );

      setRemainingSeconds(remaining);
      return remaining;
    },
    [],
  );

  /*
   * Initial authentication + refresh recovery.
   */
  useEffect(() => {
    let active = true;

    async function initialise() {
      setFlowState("checking-auth");
      setError(null);

      try {
        const currentUser = await authApi.me();

        if (!active) {
          return;
        }

        setUser(currentUser);

        if (!currentUser) {
          setFlowState("ready");
          return;
        }

        const storedHoldId =
          window.localStorage.getItem(storageKey);

        if (!storedHoldId) {
          setFlowState("ready");
          return;
        }

        try {
          const restoredHold =
            await bookingApi.getHold(storedHoldId);

          if (!active) {
            return;
          }

          setHold(restoredHold);

          const storedBookingId =
            window.localStorage.getItem(
              bookingStorageKey(restoredHold.holdId),
            );

          if (storedBookingId) {
            try {
              const restoredBooking =
                await bookingApi.getBooking(storedBookingId);

              if (!active) {
                return;
              }

              setBooking(restoredBooking);

              if (
                restoredBooking.status === "PENDING_PAYMENT"
              ) {
                setFlowState("pending-payment");
                syncRemainingTime(restoredHold);
                return;
              }

              if (restoredBooking.status === "CONFIRMED") {
                setFlowState("confirmed");
                setError(null);

                window.localStorage.removeItem(storageKey);
                window.localStorage.removeItem(
                  bookingStorageKey(restoredHold.holdId),
                );

                return;
              }

              if (restoredBooking.status === "EXPIRED") {
                setFlowState("expired");
                return;
              }
            } catch {
              window.localStorage.removeItem(
                bookingStorageKey(restoredHold.holdId),
              );
            }
          }

          const remaining =
            syncRemainingTime(restoredHold);

          if (
            restoredHold.status === "ACTIVE" &&
            remaining > 0
          ) {
            setFlowState("held");
            return;
          }

          window.localStorage.removeItem(storageKey);
          setFlowState("expired");
        } catch {
          window.localStorage.removeItem(storageKey);
          setFlowState("ready");
        }
      } catch (initialError) {
        if (!active) {
          return;
        }

        setError(messageFromError(initialError));
        setFlowState("error");
      }
    }

    initialise();

    return () => {
      active = false;
    };
  }, [storageKey, syncRemainingTime]);

  /*
   * Countdown is visual only.
   * Spring Boot's expiresAt remains authoritative.
   *
   * Do not run this countdown while payment verification is
   * occurring. Razorpay browser success does not confirm the
   * booking, but the frontend should wait for the authoritative
   * backend result rather than changing to EXPIRED locally.
   */
  useEffect(() => {
    if (hold === null) {
      return;
    }

    if (
      flowState !== "held" &&
      flowState !== "pending-payment" &&
      flowState !== "starting-payment"
    ) {
      return;
    }

    const activeHold = hold;

    const updateTimer = () => {
      const remaining =
        syncRemainingTime(activeHold);

      if (remaining <= 0) {
        setFlowState("expired");
      }
    };

    updateTimer();

    const timer = window.setInterval(
      updateTimer,
      1000,
    );

    return () => {
      window.clearInterval(timer);
    };
  }, [hold, flowState, syncRemainingTime]);

  async function createHold() {
    if (!user) {
      setError(
        "Please sign in before reserving this room.",
      );
      return;
    }

    setFlowState("creating-hold");
    setError(null);

    try {
      /*
       * Recheck authoritative availability immediately
       * before requesting the hold.
       */
      const availability =
        await getRoomAvailability(hotelSlug, {
          checkIn,
          checkOut,
          guests,
        });

      const selectedRoom = availability.find(
        (item) => item.roomSlug === roomSlug,
      );

      if (
        !selectedRoom ||
        !selectedRoom.available ||
        selectedRoom.availableUnits < 1
      ) {
        throw new Error(
          "This room is no longer available for the selected stay.",
        );
      }

      const createdHold =
        await bookingApi.createHold({
          hotelSlug,
          roomSlug,
          checkIn,
          checkOut,
          guests,
          units: 1,
        });

      setHold(createdHold);
      setBooking(null);

      window.localStorage.setItem(
        storageKey,
        createdHold.holdId,
      );

      syncRemainingTime(createdHold);
      setFlowState("held");
    } catch (holdError) {
      setError(messageFromError(holdError));
      setFlowState("ready");
    }
  }

  async function confirmBooking() {
    if (!hold) {
      setError(
        "The inventory hold is missing. Please reserve the room again.",
      );
      setFlowState("ready");
      return;
    }

    if (
      hold.status !== "ACTIVE" ||
      remainingSeconds <= 0
    ) {
      setFlowState("expired");
      setError(
        "Your temporary room hold has expired.",
      );
      return;
    }

    setFlowState("creating-booking");
    setError(null);

    try {
      /*
       * Explicit customer confirmation before the
       * PENDING_PAYMENT booking is created.
       */
      const createdBooking =
        await bookingApi.createBooking({
          holdId: hold.holdId,
        });

      setBooking(createdBooking);

      window.localStorage.setItem(
        bookingStorageKey(hold.holdId),
        createdBooking.bookingId,
      );

      setFlowState("pending-payment");
    } catch (bookingError) {
      const message =
        messageFromError(bookingError);

      setError(message);

      if (
        bookingError instanceof BookingApiError &&
        (bookingError.status === 409 ||
          message.toLowerCase().includes("expired"))
      ) {
        setFlowState("expired");
        return;
      }

      setFlowState("held");
    }
  }

  /*
   * Poll only SpatialStay's authenticated booking endpoint.
   *
   * Razorpay's browser callback is not payment proof.
   * The webhook remains the only component allowed to move
   * the booking to CONFIRMED.
   */
  async function waitForBookingConfirmation(
    bookingId: string,
  ) {
    setFlowState("verifying-payment");
    setError(null);

    const maxAttempts = 15;
    const delayMs = 2000;

    for (
      let attempt = 0;
      attempt < maxAttempts;
      attempt += 1
    ) {
      try {
        const refreshedBooking =
          await bookingApi.getBooking(bookingId);

        setBooking(refreshedBooking);

        if (
          refreshedBooking.status === "CONFIRMED"
        ) {
          setFlowState("confirmed");
          setError(null);

          /*
           * The backend has now confirmed the reservation
           * and consumed the temporary hold.
           *
           * Remove recovery keys so this completed flow
           * isn't restored as an active hold later.
           */
          window.localStorage.removeItem(storageKey);

          if (hold) {
            window.localStorage.removeItem(
              bookingStorageKey(hold.holdId),
            );
          }

          return;
        }

        if (refreshedBooking.status === "EXPIRED") {
          setFlowState("expired");
          setError(
            "The reservation expired before payment confirmation completed.",
          );
          return;
        }

        if (
          refreshedBooking.status !== "PENDING_PAYMENT"
        ) {
          setFlowState("pending-payment");
          setError(
            `Booking status is ${refreshedBooking.status}. Please refresh before attempting another payment.`,
          );
          return;
        }
      } catch {
        /*
         * A temporary network/read failure must not be
         * interpreted as payment failure. Continue the
         * bounded polling window.
         */
      }

      if (attempt < maxAttempts - 1) {
        await new Promise<void>((resolve) => {
          window.setTimeout(resolve, delayMs);
        });
      }
    }

    /*
     * Timeout is deliberately non-authoritative.
     * Payment may already have succeeded and the webhook
     * may still be processing.
     */
    setFlowState("pending-payment");

    setError(
      "Payment was submitted, but secure server verification is taking longer than expected. Please refresh shortly instead of paying again.",
    );
  }

    async function startPayment() {
      if (!booking || !hold) {
        setError(
          "Booking information is missing. Please refresh and try again.",
        );
        return;
      }

      if (booking.status !== "PENDING_PAYMENT") {
        setError(
          "This booking is not awaiting payment.",
        );
        return;
      }

      if (
        hold.status !== "ACTIVE" ||
        remainingSeconds <= 0
      ) {
        setFlowState("expired");

        setError(
          "Your reservation hold has expired. Please check availability again.",
        );

        return;
      }

      setFlowState("starting-payment");
      setError(null);

      try {
        /*
        * Backend creates the PaymentAttempt and authoritative
        * Razorpay order.
        *
        * The browser never supplies the authoritative amount.
        */
        const checkout =
          await paymentApi.createCheckout(
            booking.bookingId,
          );

        /*
        * Defensive frontend consistency checks only.
        *
        * Spring Boot remains authoritative.
        */
        if (
          checkout.bookingId !== booking.bookingId
        ) {
          throw new Error(
            "Payment checkout does not match this booking.",
          );
        }

        if (
          checkout.currency !== booking.currency
        ) {
          throw new Error(
            "Payment currency does not match this booking.",
          );
        }

        if (
          Number(checkout.amount) !==
          Number(booking.totalAmount)
        ) {
          throw new Error(
            "Payment amount does not match this booking.",
          );
        }

        if (
          !checkout.providerOrderId ||
          !checkout.checkoutKeyId
        ) {
          throw new Error(
            "Secure payment checkout could not be prepared.",
          );
        }

        await loadRazorpayCheckout();

        if (!window.Razorpay) {
          throw new Error(
            "Secure payment checkout is unavailable.",
          );
        }

        /*
        * Prevent Razorpay's modal-close callback from moving
        * the UI back to PENDING_PAYMENT after a successful
        * payment has already started backend verification.
        */
        let paymentSucceeded = false;

        const razorpay =
          new window.Razorpay({
            key: checkout.checkoutKeyId,
            amount: checkout.amountInSubunits,
            currency: checkout.currency,
            name: "SpatialStay",
            description:
              `${booking.hotelName} · ${booking.roomName}`,
            order_id: checkout.providerOrderId,

            /*
            * IMPORTANT:
            *
            * Browser success is NOT authoritative payment proof.
            *
            * It only tells SpatialStay to begin polling its own
            * authenticated booking endpoint.
            *
            * The verified Razorpay webhook remains responsible
            * for changing the backend booking to CONFIRMED.
            */
            handler: () => {
              paymentSucceeded = true;

              void waitForBookingConfirmation(
                booking.bookingId,
              );
            },

            /*
            * Razorpay may fire ondismiss when checkout closes.
            *
            * If payment already succeeded, verification may be
            * running or may already have reached CONFIRMED.
            *
            * Therefore ondismiss must never overwrite that state.
            */
            modal: {
              ondismiss: () => {
                if (!paymentSucceeded) {
                  setFlowState("pending-payment");
                }
              },
            },

            theme: {
              color: "#2f6df6",
            },
          });

        razorpay.on(
          "payment.failed",
          (response) => {
            /*
            * Explicit provider failure means the customer can
            * remain on the pending-payment screen and retry
            * while the authoritative hold is still valid.
            */
            setFlowState("pending-payment");

            setError(
              response.error?.description ??
                "Payment was not completed. You can try again while your reservation hold remains active.",
            );
          },
        );

        /*
        * IMPORTANT ORDER:
        *
        * Set the background state BEFORE opening Razorpay.
        *
        * Do NOT place:
        *
        *   setFlowState("pending-payment")
        *
        * after razorpay.open().
        *
        * Razorpay callbacks are asynchronous and could already
        * have moved the flow to VERIFYING_PAYMENT or CONFIRMED.
        */
        setFlowState("pending-payment");

        razorpay.open();
      } catch (paymentError) {
        const message =
          messageFromError(paymentError);

        setError(message);

        if (
          message
            .toLowerCase()
            .includes("expired")
        ) {
          setFlowState("expired");
          return;
        }

        setFlowState("pending-payment");
      }
    }

  function clearExpiredHold() {
    if (hold) {
      window.localStorage.removeItem(
        bookingStorageKey(hold.holdId),
      );
    }

    window.localStorage.removeItem(storageKey);

    setHold(null);
    setBooking(null);
    setRemainingSeconds(0);
    setError(null);
    setFlowState("ready");
  }

  if (
    flowState === "checking-auth"
  ) {
    return (
      <Panel>
        <div className="flex items-center gap-3">
          <LoaderCircle
            className="animate-spin text-[#2f6df6]"
            size={20}
          />

          <div>
            <h2 className="font-extrabold text-[#17375f]">
              Preparing secure booking
            </h2>

            <p className="mt-1 text-xs text-[#6f87a5]">
              Checking your session and reservation state.
            </p>
          </div>
        </div>
      </Panel>
    );
  }

  if (flowState === "expired") {
    return (
      <Panel>
        <div className="rounded-2xl bg-[#fff4eb] p-4">
          <div className="flex items-start gap-3">
            <Clock3
              size={20}
              className="mt-0.5 shrink-0 text-[#d77a32]"
            />

            <div>
              <h2 className="font-extrabold text-[#17375f]">
                Room hold expired
              </h2>

              <p className="mt-1 text-xs leading-5 text-[#7188a5]">
                The temporary inventory reservation has ended.
                Availability must be checked again before another hold
                can be created.
              </p>
            </div>
          </div>
        </div>

        {error ? (
          <ErrorMessage message={error} />
        ) : null}

        <button
          type="button"
          onClick={clearExpiredHold}
          className="blue-button mt-5 flex w-full items-center justify-center gap-2 rounded-xl px-5 py-3.5 text-sm font-bold"
        >
          <RefreshCw size={16} />
          Check again
        </button>

        <BackToRoom
          hotelSlug={hotelSlug}
          roomSlug={roomSlug}
        />
      </Panel>
    );
  }

  /*
   * Secure provider payment has returned successfully,
   * but only the backend may confirm the booking.
   */
  if (
    flowState === "verifying-payment" &&
    booking
  ) {
    return (
      <Panel>
        <div className="flex justify-center py-2">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-[#eef5ff]">
            <LoaderCircle
              size={30}
              className="animate-spin text-[#2f6df6]"
            />
          </div>
        </div>

        <div className="text-center">
          <p className="mt-3 text-xs font-bold uppercase tracking-[.14em] text-[#2f6df6]">
            Secure verification
          </p>

          <h2 className="mt-2 text-2xl font-extrabold text-[#17375f]">
            Verifying your payment
          </h2>

          <p className="mt-3 text-sm leading-6 text-[#5d7695]">
            Payment was submitted successfully. SpatialStay is waiting
            for trusted server confirmation from Razorpay before
            confirming your reservation.
          </p>
        </div>

        <div className="mt-5 rounded-2xl border border-[#dbe7f6] bg-[#f7faff] p-4">
          <div className="flex items-start gap-3">
            <ShieldCheck
              size={19}
              className="mt-0.5 shrink-0 text-[#2f6df6]"
            />

            <div>
              <p className="text-xs font-extrabold text-[#17375f]">
                Backend verification in progress
              </p>

              <p className="mt-1 text-xs leading-5 text-[#607895]">
                Do not make another payment. This page will update
                automatically when SpatialStay receives the verified
                payment status.
              </p>
            </div>
          </div>
        </div>

        <div className="mt-5 rounded-2xl border border-[#dbe7f6] bg-white p-4">
          <Detail
            label="Booking reference"
            value={booking.bookingReference}
          />
          <Detail
            label="Hotel"
            value={booking.hotelName}
          />
          <Detail
            label="Room"
            value={booking.roomName}
          />
          <Detail
            label="Amount"
            value={formatCurrency(
              booking.totalAmount,
              booking.currency,
            )}
            strong
          />
        </div>
      </Panel>
    );
  }

  /*
   * This screen is shown only after SpatialStay's
   * authoritative booking endpoint reports CONFIRMED.
   */
  if (
    flowState === "confirmed" &&
    booking
  ) {
    return (
      <Panel>
        <div className="flex justify-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-[#eaf7ef]">
            <CheckCircle2
              size={34}
              className="text-[#2f9a67]"
            />
          </div>
        </div>

        <div className="text-center">
          <p className="mt-4 text-xs font-bold uppercase tracking-[.14em] text-[#2f9a67]">
            Payment verified
          </p>

          <h2 className="mt-2 text-2xl font-extrabold text-[#17375f]">
            Booking confirmed
          </h2>

          <p className="mt-3 text-sm leading-6 text-[#5d7695]">
            Your payment has been securely verified and your
            SpatialStay reservation is confirmed.
          </p>
        </div>

        <div className="mt-5 rounded-2xl border border-[#cfe9d9] bg-[#f4fbf7] p-4">
          <div className="flex items-start gap-3">
            <ShieldCheck
              size={19}
              className="mt-0.5 shrink-0 text-[#2f9a67]"
            />

            <div>
              <p className="text-xs font-extrabold text-[#26704b]">
                Reservation secured
              </p>

              <p className="mt-1 text-xs leading-5 text-[#56806a]">
                SpatialStay received verified payment confirmation
                from the payment provider.
              </p>
            </div>
          </div>
        </div>

        <div className="mt-5 rounded-2xl border border-[#dbe7f6] bg-white p-4">
          <Detail
            label="Booking reference"
            value={booking.bookingReference}
            strong
          />
          <Detail
            label="Hotel"
            value={booking.hotelName}
          />
          <Detail
            label="Room"
            value={booking.roomName}
          />
          <Detail
            label="Stay"
            value={`${formatDate(
              booking.checkIn,
            )} → ${formatDate(
              booking.checkOut,
            )}`}
          />
          <Detail
            label="Guests"
            value={`${booking.guests}`}
          />
          <Detail
            label="Rooms"
            value={`${booking.units}`}
          />
          <Detail
            label="Amount paid"
            value={formatCurrency(
              booking.totalAmount,
              booking.currency,
            )}
            strong
          />
          <Detail
            label="Status"
            value="CONFIRMED"
            strong
          />
        </div>

        <BackToRoom
          hotelSlug={hotelSlug}
          roomSlug={roomSlug}
        />
      </Panel>
    );
  }

  /*
   * Step 10 payment state.
   */
  if (
    (flowState === "pending-payment" ||
      flowState === "starting-payment") &&
    booking &&
    hold
  ) {
    return (
      <Panel>
        <div className="flex items-center gap-2">
          <CheckCircle2
            size={19}
            className="text-[#2f9a67]"
          />

          <p className="text-xs font-bold uppercase tracking-[.14em] text-[#2f9a67]">
            Booking created
          </p>
        </div>

        <h2 className="mt-3 text-2xl font-extrabold text-[#17375f]">
          Payment is required
        </h2>

        <p className="mt-2 text-sm leading-6 text-[#5d7695]">
          Your booking has been created as{" "}
          <strong>PENDING_PAYMENT</strong>. It is not confirmed yet.
        </p>

        <div className="mt-5 rounded-2xl border border-[#dbe7f6] bg-white p-4">
          <Detail
            label="Booking reference"
            value={booking.bookingReference}
          />
          <Detail
            label="Hotel"
            value={booking.hotelName}
          />
          <Detail
            label="Room"
            value={booking.roomName}
          />
          <Detail
            label="Stay"
            value={`${formatDate(
              booking.checkIn,
            )} → ${formatDate(
              booking.checkOut,
            )}`}
          />
          <Detail
            label="Guests"
            value={`${booking.guests}`}
          />
          <Detail
            label="Rooms"
            value={`${booking.units}`}
          />
          <Detail
            label="Amount"
            value={formatCurrency(
              booking.totalAmount,
              booking.currency,
            )}
            strong
          />
        </div>

        <Countdown seconds={remainingSeconds} />

        <div className="mt-5 rounded-2xl border border-[#dbe7f6] bg-[#f7faff] p-4">
          <div className="flex items-start gap-3">
            <LockKeyhole
              size={17}
              className="mt-0.5 shrink-0 text-[#2f6df6]"
            />

            <div>
              <p className="text-xs font-extrabold text-[#17375f]">
                Secure Razorpay checkout
              </p>

              <p className="mt-1 text-xs leading-5 text-[#607895]">
                Continue to Razorpay&apos;s secure checkout to choose
                an available payment method. SpatialStay will only mark
                this reservation <strong>CONFIRMED</strong> after
                trusted backend verification.
              </p>
            </div>
          </div>
        </div>

        {error ? (
          <ErrorMessage message={error} />
        ) : null}

        <button
          type="button"
          onClick={startPayment}
          disabled={
            flowState === "starting-payment" ||
            remainingSeconds <= 0
          }
          className="blue-button mt-5 flex w-full items-center justify-center gap-2 rounded-xl px-5 py-3.5 text-sm font-bold disabled:cursor-not-allowed disabled:opacity-60"
        >
          {flowState === "starting-payment" ? (
            <>
              <LoaderCircle
                size={16}
                className="animate-spin"
              />
              Preparing secure payment...
            </>
          ) : (
            <>
              <LockKeyhole size={16} />
              Proceed to Secure Payment
            </>
          )}
        </button>

        <p className="mt-3 text-center text-[11px] leading-5 text-[#7890aa]">
          Amount:{" "}
          <strong>
            {formatCurrency(
              booking.totalAmount,
              booking.currency,
            )}
          </strong>
          . Payment details are handled by Razorpay.
        </p>

        <BackToRoom
          hotelSlug={hotelSlug}
          roomSlug={roomSlug}
        />
      </Panel>
    );
  }

  if (
    hold &&
    (flowState === "held" ||
      flowState === "creating-booking")
  ) {
    return (
      <Panel>
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-xs font-bold uppercase tracking-[.14em] text-[#2f9a67]">
              Room held
            </p>

            <h2 className="mt-2 text-2xl font-extrabold text-[#17375f]">
              Confirm your stay
            </h2>
          </div>

          <ShieldCheck
            size={28}
            className="text-[#2f9a67]"
          />
        </div>

        <p className="mt-3 text-sm leading-6 text-[#5d7695]">
          Review the authoritative reservation details below before
          continuing.
        </p>

        <Countdown seconds={remainingSeconds} />

        <div className="mt-5 rounded-2xl border border-[#dbe7f6] bg-white p-4">
          <Detail
            label="Hotel"
            value={hold.hotelName}
          />
          <Detail
            label="Room"
            value={hold.roomName}
          />
          <Detail
            label="Check-in"
            value={formatDate(hold.checkIn)}
          />
          <Detail
            label="Check-out"
            value={formatDate(hold.checkOut)}
          />
          <Detail
            label="Nights"
            value={`${hold.nights}`}
          />
          <Detail
            label="Guests"
            value={`${hold.guests}`}
          />
          <Detail
            label="Rooms"
            value={`${hold.units}`}
          />
          <Detail
            label="Price / night"
            value={formatCurrency(
              hold.pricePerNight,
              hold.currency,
            )}
          />
          <Detail
            label="Authoritative total"
            value={formatCurrency(
              hold.totalAmount,
              hold.currency,
            )}
            strong
          />
        </div>

        <div className="mt-4 flex items-start gap-2 rounded-2xl bg-[#eef5ff] p-4">
          <ShieldCheck
            size={17}
            className="mt-0.5 shrink-0 text-[#2f6df6]"
          />

          <p className="text-xs leading-5 text-[#607895]">
            By continuing, you confirm the hotel, room, dates, guests,
            room quantity and total shown above. This creates a{" "}
            <strong>PENDING_PAYMENT</strong> booking, not a confirmed
            reservation.
          </p>
        </div>

        {error ? (
          <ErrorMessage message={error} />
        ) : null}

        <button
          type="button"
          onClick={confirmBooking}
          disabled={
            flowState === "creating-booking" ||
            remainingSeconds <= 0
          }
          className="blue-button mt-5 flex w-full items-center justify-center gap-2 rounded-xl px-5 py-3.5 text-sm font-bold disabled:cursor-not-allowed disabled:opacity-60"
        >
          {flowState === "creating-booking" ? (
            <>
              <LoaderCircle
                size={16}
                className="animate-spin"
              />
              Creating booking...
            </>
          ) : (
            <>
              <LockKeyhole size={16} />
              Confirm & Continue
            </>
          )}
        </button>

        <BackToRoom
          hotelSlug={hotelSlug}
          roomSlug={roomSlug}
        />
      </Panel>
    );
  }

  return (
    <Panel>
      <p className="text-xs font-bold uppercase tracking-[.14em] text-[#2f6df6]">
        Secure reservation
      </p>

      <h2 className="mt-2 text-2xl font-extrabold text-[#17375f]">
        Reserve this room
      </h2>

      <p className="mt-3 text-sm leading-6 text-[#5d7695]">
        SpatialStay will recheck availability and create a temporary
        inventory hold before asking you to confirm.
      </p>

      <div className="mt-5 space-y-3">
        <MiniSummary
          icon={<CalendarDays />}
          label="Stay"
          value={`${formatDate(
            checkIn,
          )} → ${formatDate(
            checkOut,
          )}`}
        />

        <MiniSummary
          icon={<Users />}
          label="Guests"
          value={`${guests} ${
            guests === 1
              ? "guest"
              : "guests"
          } · 1 room`}
        />
      </div>

      {!user ? (
        <div className="mt-5 rounded-2xl border border-[#f0dfb8] bg-[#fff9eb] p-4">
          <div className="flex items-start gap-3">
            <AlertCircle
              size={18}
              className="mt-0.5 shrink-0 text-[#b88126]"
            />

            <div>
              <p className="text-sm font-extrabold text-[#6f541f]">
                Sign in required
              </p>

              <p className="mt-1 text-xs leading-5 text-[#8c7444]">
                Booking holds are tied to your authenticated SpatialStay
                account.
              </p>
            </div>
          </div>
        </div>
      ) : (
        <div className="mt-5 rounded-2xl bg-[#eef5ff] p-4">
          <p className="text-xs font-bold text-[#315f9f]">
            Signed in as
          </p>

          <p className="mt-1 break-all text-sm font-extrabold text-[#17375f]">
            {user.email}
          </p>
        </div>
      )}

      {error ? (
        <ErrorMessage message={error} />
      ) : null}

      <button
        type="button"
        onClick={createHold}
        disabled={
          !user ||
          flowState === "creating-hold"
        }
        className="blue-button mt-5 flex w-full items-center justify-center gap-2 rounded-xl px-5 py-3.5 text-sm font-bold disabled:cursor-not-allowed disabled:opacity-50"
      >
        {flowState === "creating-hold" ? (
          <>
            <LoaderCircle
              size={16}
              className="animate-spin"
            />
            Securing room...
          </>
        ) : (
          <>
            <ShieldCheck size={16} />
            Hold this room
          </>
        )}
      </button>

      {!user ? (
        <p className="mt-3 text-center text-xs leading-5 text-[#6f87a5]">
          Use the existing SpatialStay sign-in option in the navigation,
          then return here to continue.
        </p>
      ) : (
        <p className="mt-3 text-center text-xs leading-5 text-[#6f87a5]">
          No payment is taken when creating a hold.
        </p>
      )}

      <BackToRoom
        hotelSlug={hotelSlug}
        roomSlug={roomSlug}
      />
    </Panel>
  );
}

function Panel({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <aside className="glass-panel h-fit rounded-[28px] p-5 sm:p-7">
      {children}
    </aside>
  );
}

function Countdown({
  seconds,
}: {
  seconds: number;
}) {
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;

  return (
    <div className="mt-5 rounded-2xl bg-[#eaf7ef] p-4">
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <Clock3
            size={18}
            className="text-[#2f9a67]"
          />

          <div>
            <p className="text-[10px] font-bold uppercase tracking-[.12em] text-[#619177]">
              Reservation hold
            </p>

            <p className="mt-1 text-xs font-semibold text-[#4f8068]">
              Complete the next step before this hold expires.
            </p>
          </div>
        </div>

        <p className="shrink-0 text-xl font-black tabular-nums text-[#26704b]">
          {String(minutes).padStart(2, "0")}:
          {String(remainder).padStart(2, "0")}
        </p>
      </div>
    </div>
  );
}

function Detail({
  label,
  value,
  strong = false,
}: {
  label: string;
  value: string;
  strong?: boolean;
}) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-[#edf2f8] py-2.5 last:border-b-0">
      <span className="text-xs text-[#7188a5]">
        {label}
      </span>

      <span
        className={
          strong
            ? "text-right text-sm font-black text-[#17375f]"
            : "text-right text-xs font-bold text-[#17375f]"
        }
      >
        {value}
      </span>
    </div>
  );
}

function MiniSummary({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center gap-3 rounded-2xl border border-[#dbe7f6] bg-white p-4">
      <span className="text-[#2f6df6] [&>svg]:h-5 [&>svg]:w-5">
        {icon}
      </span>

      <div>
        <p className="text-[10px] font-bold uppercase tracking-[.11em] text-[#8195ae]">
          {label}
        </p>

        <p className="mt-1 text-xs font-extrabold text-[#17375f]">
          {value}
        </p>
      </div>
    </div>
  );
}

function ErrorMessage({
  message,
}: {
  message: string;
}) {
  return (
    <div className="mt-4 flex items-start gap-2 rounded-2xl bg-[#fff2f2] p-4">
      <AlertCircle
        size={17}
        className="mt-0.5 shrink-0 text-[#b84d4d]"
      />

      <p className="text-xs leading-5 text-[#9b5151]">
        {message}
      </p>
    </div>
  );
}

function BackToRoom({
  hotelSlug,
  roomSlug,
}: {
  hotelSlug: string;
  roomSlug: string;
}) {
  return (
    <Link
      href={`/rooms/${hotelSlug}/${roomSlug}`}
      className="mt-5 block text-center text-xs font-bold text-[#2f6df6]"
    >
      Back to room
    </Link>
  );
}

function formatDate(value: string) {
  const date =
    new Date(`${value}T00:00:00`);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat(
    "en-IN",
    {
      day: "numeric",
      month: "short",
      year: "numeric",
    },
  ).format(date);
}

function formatCurrency(
  amount: number,
  currency: string,
) {
  try {
    return new Intl.NumberFormat(
      "en-IN",
      {
        style: "currency",
        currency,
        maximumFractionDigits: 0,
      },
    ).format(amount);
  } catch {
    return `${currency} ${amount.toLocaleString(
      "en-IN",
    )}`;
  }
}

function messageFromError(
  error: unknown,
) {
  if (error instanceof Error) {
    return error.message;
  }

  return "Something went wrong. Please try again.";
}