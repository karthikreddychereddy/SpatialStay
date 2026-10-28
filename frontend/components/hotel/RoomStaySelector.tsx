"use client";

import {
  CalendarDays,
  CheckCircle2,
  LoaderCircle,
  Minus,
  Plus,
  RefreshCw,
  Users,
  XCircle,
} from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  useEffect,
  useMemo,
  useState,
} from "react";

import {
  getRoomAvailability,
  type RoomAvailability,
} from "@/lib/api/rooms";

type RoomStaySelectorProps = {
  hotelId: string;
  roomId: string;
  nightlyPrice: number;
  currency: string;
  maxGuests: number;
};

type AvailabilityState =
  | "checking"
  | "available"
  | "unavailable"
  | "error";

function toDateInputValue(date: Date) {
  const year = date.getFullYear();

  const month = String(
    date.getMonth() + 1,
  ).padStart(2, "0");

  const day = String(
    date.getDate(),
  ).padStart(2, "0");

  return `${year}-${month}-${day}`;
}

function addDays(
  value: string,
  numberOfDays: number,
) {
  const [year, month, day] = value
    .split("-")
    .map(Number);

  const date = new Date(
    year,
    month - 1,
    day,
  );

  date.setDate(
    date.getDate() + numberOfDays,
  );

  return toDateInputValue(date);
}

function createInitialStay() {
  const today = new Date();

  const checkInDate = new Date(
    today.getFullYear(),
    today.getMonth(),
    today.getDate() + 1,
  );

  const checkOutDate = new Date(
    today.getFullYear(),
    today.getMonth(),
    today.getDate() + 2,
  );

  return {
    today: toDateInputValue(today),
    checkIn: toDateInputValue(checkInDate),
    checkOut: toDateInputValue(checkOutDate),
  };
}

function differenceInNights(
  checkIn: string,
  checkOut: string,
) {
  const [startYear, startMonth, startDay] =
    checkIn.split("-").map(Number);

  const [endYear, endMonth, endDay] =
    checkOut.split("-").map(Number);

  const start = Date.UTC(
    startYear,
    startMonth - 1,
    startDay,
  );

  const end = Date.UTC(
    endYear,
    endMonth - 1,
    endDay,
  );

  return Math.max(
    1,
    Math.round(
      (end - start) / 86_400_000,
    ),
  );
}

function isValidDateValue(value: string | null) {
  return Boolean(
    value &&
      /^\d{4}-\d{2}-\d{2}$/.test(value),
  );
}

export function RoomStaySelector({
  hotelId,
  roomId,
  nightlyPrice,
  currency,
  maxGuests,
}: RoomStaySelectorProps) {
  const searchParams = useSearchParams();

  const [initialStay] = useState(
    createInitialStay,
  );

  const requestedCheckIn =
    searchParams.get("checkIn");

  const requestedCheckOut =
    searchParams.get("checkOut");

  const requestedGuests =
    Number(searchParams.get("guests"));

  const initialCheckIn =
    isValidDateValue(requestedCheckIn) &&
    requestedCheckIn! >= initialStay.today
      ? requestedCheckIn!
      : initialStay.checkIn;

  const initialCheckOut =
    isValidDateValue(requestedCheckOut) &&
    requestedCheckOut! > initialCheckIn
      ? requestedCheckOut!
      : addDays(initialCheckIn, 1);

  const initialGuests =
    Number.isInteger(requestedGuests) &&
    requestedGuests >= 1
      ? Math.min(
          requestedGuests,
          maxGuests,
        )
      : Math.min(2, maxGuests);

  const [checkIn, setCheckIn] =
    useState(initialCheckIn);

  const [checkOut, setCheckOut] =
    useState(initialCheckOut);

  const [guests, setGuests] =
    useState(initialGuests);

  const [
    availabilityState,
    setAvailabilityState,
  ] =
    useState<AvailabilityState>(
      "checking",
    );

  const [
    availability,
    setAvailability,
  ] =
    useState<RoomAvailability | null>(
      null,
    );

  const [
    availabilityError,
    setAvailabilityError,
  ] = useState<string | null>(null);

  const nights = useMemo(
    () =>
      differenceInNights(
        checkIn,
        checkOut,
      ),
    [checkIn, checkOut],
  );

  useEffect(() => {
    const controller =
      new AbortController();

    let active = true;

    async function checkAvailability() {
      setAvailabilityState("checking");
      setAvailability(null);
      setAvailabilityError(null);

      try {
        const results =
          await getRoomAvailability(
            hotelId,
            {
              checkIn,
              checkOut,
              guests,
            },
            controller.signal,
          );

        if (!active) {
          return;
        }

        const selectedRoom =
          results.find(
            (item) =>
              item.roomSlug === roomId,
          ) ?? null;

        if (
          selectedRoom &&
          selectedRoom.available
        ) {
          setAvailability(
            selectedRoom,
          );

          setAvailabilityState(
            "available",
          );

          return;
        }

        setAvailabilityState(
          "unavailable",
        );
      } catch (error) {
        if (
          error instanceof DOMException &&
          error.name === "AbortError"
        ) {
          return;
        }

        if (!active) {
          return;
        }

        setAvailabilityState("error");

        setAvailabilityError(
          error instanceof Error
            ? error.message
            : "Availability could not be checked.",
        );
      }
    }

    const timer = window.setTimeout(
      checkAvailability,
      250,
    );

    return () => {
      active = false;

      window.clearTimeout(timer);

      controller.abort();
    };
  }, [
    hotelId,
    roomId,
    checkIn,
    checkOut,
    guests,
  ]);

  const estimatedRoomTotal =
    availability?.estimatedStayTotal ??
    nightlyPrice * nights;

  const bookingUrl =
    `/booking/${hotelId}/${roomId}` +
    `?checkIn=${encodeURIComponent(
      checkIn,
    )}` +
    `&checkOut=${encodeURIComponent(
      checkOut,
    )}` +
    `&guests=${guests}`;

  function updateCheckIn(
    value: string,
  ) {
    setCheckIn(value);

    if (value >= checkOut) {
      setCheckOut(
        addDays(value, 1),
      );
    }
  }

  return (
    <div className="mt-5 rounded-[22px] border border-white/90 bg-white/72 p-3 shadow-[0_12px_34px_rgba(48,83,130,.09)] backdrop-blur-xl">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-[10px] font-bold uppercase tracking-[.13em] text-[#8398b1]">
            Select your stay
          </p>

          <p className="mt-1 text-xs font-semibold leading-5 text-[#58718f]">
            Choose your dates and guests
            to check this room.
          </p>
        </div>

        <span className="hidden shrink-0 rounded-full bg-[#eef5ff] px-3 py-1.5 text-[10px] font-bold text-[#416a9d] sm:inline-flex">
          {nights}{" "}
          {nights === 1
            ? "night"
            : "nights"}
        </span>
      </div>

      <div className="mt-3 grid gap-2 sm:grid-cols-2">
        <label className="rounded-2xl border border-[#d9e6f6] bg-white/90 px-3 py-2.5">
          <span className="flex items-center gap-2 text-[9px] font-bold uppercase tracking-[.1em] text-[#8398b1]">
            <CalendarDays
              size={13}
              className="text-[#527bb1]"
            />
            Check-in
          </span>

          <input
            type="date"
            value={checkIn}
            min={initialStay.today}
            onChange={(event) =>
              updateCheckIn(
                event.target.value,
              )
            }
            className="mt-1 w-full bg-transparent text-xs font-extrabold text-[#173d70] outline-none"
          />
        </label>

        <label className="rounded-2xl border border-[#d9e6f6] bg-white/90 px-3 py-2.5">
          <span className="flex items-center gap-2 text-[9px] font-bold uppercase tracking-[.1em] text-[#8398b1]">
            <CalendarDays
              size={13}
              className="text-[#527bb1]"
            />
            Check-out
          </span>

          <input
            type="date"
            value={checkOut}
            min={addDays(
              checkIn,
              1,
            )}
            onChange={(event) =>
              setCheckOut(
                event.target.value,
              )
            }
            className="mt-1 w-full bg-transparent text-xs font-extrabold text-[#173d70] outline-none"
          />
        </label>
      </div>

      <div className="mt-2 flex items-center justify-between rounded-2xl border border-[#d9e6f6] bg-white/90 px-3 py-2.5">
        <div className="flex items-center gap-2">
          <Users
            size={16}
            className="text-[#527bb1]"
          />

          <div>
            <p className="text-[9px] font-bold uppercase tracking-[.1em] text-[#8398b1]">
              Guests
            </p>

            <p className="text-xs font-extrabold text-[#173d70]">
              {guests}{" "}
              {guests === 1
                ? "guest"
                : "guests"}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() =>
              setGuests((value) =>
                Math.max(
                  1,
                  value - 1,
                ),
              )
            }
            disabled={guests <= 1}
            className="grid h-8 w-8 place-items-center rounded-full border border-[#d6e3f3] bg-white text-[#315f9f] disabled:opacity-35"
            aria-label="Remove guest"
          >
            <Minus size={14} />
          </button>

          <span className="w-5 text-center text-xs font-extrabold text-[#173d70]">
            {guests}
          </span>

          <button
            type="button"
            onClick={() =>
              setGuests((value) =>
                Math.min(
                  maxGuests,
                  value + 1,
                ),
              )
            }
            disabled={
              guests >= maxGuests
            }
            className="grid h-8 w-8 place-items-center rounded-full border border-[#d6e3f3] bg-white text-[#315f9f] disabled:opacity-35"
            aria-label="Add guest"
          >
            <Plus size={14} />
          </button>
        </div>
      </div>

      <AvailabilityMessage
        state={availabilityState}
        availability={availability}
        error={availabilityError}
      />

      <div className="mt-4 flex items-end justify-between gap-3 border-t border-[#e1eaf5] pt-4">
        <div>
          <p className="text-[9px] font-bold uppercase tracking-[.1em] text-[#8398b1]">
            Estimated room total
          </p>

          <p className="mt-1 text-xl font-extrabold text-[#173d70]">
            {formatCurrency(
              estimatedRoomTotal,
              currency,
            )}
          </p>

          <p className="mt-1 max-w-[210px] text-[10px] leading-4 text-[#8296ad]">
            {availabilityState ===
            "available"
              ? `${nights} ${
                  nights === 1
                    ? "night"
                    : "nights"
                } at the current room rate. Taxes and fees are confirmed before payment.`
              : "A stay total is confirmed after availability is checked."}
          </p>
        </div>

        {availabilityState ===
        "available" ? (
          <Link
            href={bookingUrl}
            className="blue-button shrink-0 rounded-xl px-5 py-3 text-center text-sm font-bold"
          >
            Continue
          </Link>
        ) : (
          <button
            type="button"
            disabled
            className="shrink-0 cursor-not-allowed rounded-xl bg-[#dce6f3] px-5 py-3 text-center text-sm font-bold text-[#8093aa]"
          >
            Continue
          </button>
        )}
      </div>
    </div>
  );
}

function AvailabilityMessage({
  state,
  availability,
  error,
}: {
  state: AvailabilityState;
  availability: RoomAvailability | null;
  error: string | null;
}) {
  if (state === "checking") {
    return (
      <div className="mt-3 flex items-center gap-2 rounded-2xl bg-[#eef5ff] px-3 py-3 text-xs font-bold text-[#416a9d]">
        <LoaderCircle
          size={15}
          className="animate-spin"
        />

        Checking availability...
      </div>
    );
  }

  if (
    state === "available" &&
    availability
  ) {
    return (
      <div className="mt-3 flex items-start gap-2 rounded-2xl bg-[#eaf7ef] px-3 py-3 text-xs text-[#28704b]">
        <CheckCircle2
          size={16}
          className="mt-0.5 shrink-0"
        />

        <div>
          <p className="font-extrabold">
            Available for this stay
          </p>

          <p className="mt-0.5 text-[10px] leading-4 text-[#4f8068]">
            {availability.availableUnits}{" "}
            {availability.availableUnits ===
            1
              ? "unit"
              : "units"}{" "}
            currently available in the
            development inventory.
          </p>
        </div>
      </div>
    );
  }

  if (state === "unavailable") {
    return (
      <div className="mt-3 flex items-start gap-2 rounded-2xl bg-[#fff2f2] px-3 py-3 text-xs text-[#a44a4a]">
        <XCircle
          size={16}
          className="mt-0.5 shrink-0"
        />

        <div>
          <p className="font-extrabold">
            Not available for this stay
          </p>

          <p className="mt-0.5 text-[10px] leading-4 text-[#a96a6a]">
            Try different dates, fewer
            guests, or another room type.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="mt-3 flex items-start gap-2 rounded-2xl bg-[#fff8e8] px-3 py-3 text-xs text-[#8b6a28]">
      <RefreshCw
        size={15}
        className="mt-0.5 shrink-0"
      />

      <div>
        <p className="font-extrabold">
          Availability could not be checked
        </p>

        <p className="mt-0.5 text-[10px] leading-4">
          {error ??
            "Please try changing the stay details."}
        </p>
      </div>
    </div>
  );
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