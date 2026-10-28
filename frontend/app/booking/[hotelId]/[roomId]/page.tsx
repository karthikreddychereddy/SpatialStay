import {
  CalendarDays,
  ShieldCheck,
  Users,
} from "lucide-react";

import { notFound } from "next/navigation";

import BookingFlow from "@/components/booking/BookingFlow";

import {
  getHotelBySlug,
} from "@/lib/api/hotels";

import {
  getRoomBySlug,
} from "@/lib/api/rooms";

type BookingPageProps = {
  params: Promise<{
    hotelId: string;
    roomId: string;
  }>;

  searchParams: Promise<{
    checkIn?: string;
    checkOut?: string;
    guests?: string;
  }>;
};

export default async function BookingPage({
  params,
  searchParams,
}: BookingPageProps) {
  const {
    hotelId: hotelSlug,
    roomId: roomSlug,
  } = await params;

  const selectedStay =
    await searchParams;

  const hotel =
    await getHotelBySlug(
      hotelSlug,
    );

  if (!hotel) {
    notFound();
  }

  const room =
    await getRoomBySlug(
      hotelSlug,
      roomSlug,
    );

  if (!room) {
    notFound();
  }

  const stay =
    buildStaySelection(
      selectedStay.checkIn,
      selectedStay.checkOut,
      selectedStay.guests,
      room.maxGuests,
    );

  /*
   * The booking transaction requires an explicit valid stay.
   *
   * If someone manually opens the booking URL without valid stay
   * parameters, send them back to the room rather than constructing
   * a fake booking state.
   */
  if (
    !stay.checkIn ||
    !stay.checkOut ||
    stay.guests === null ||
    stay.nights === null
  ) {
    return (
      <main className="travel-shell min-h-screen pb-20 pt-[105px]">
        <section className="mx-auto max-w-3xl px-4 sm:px-6">
          <div className="glass-panel rounded-[28px] p-6 text-center sm:p-8">
            <h1 className="text-2xl font-extrabold text-[#17375f]">
              Select your stay first
            </h1>

            <p className="mx-auto mt-3 max-w-xl text-sm leading-6 text-[#5d7695]">
              Choose valid check-in and check-out dates and the number of
              guests from the room page before starting a reservation.
            </p>

            <a
              href={`/rooms/${hotel.slug}/${room.slug}`}
              className="blue-button mt-6 inline-flex rounded-xl px-6 py-3 text-sm font-bold"
            >
              Return to room
            </a>
          </div>
        </section>
      </main>
    );
  }

  const location = [
    hotel.city,
    hotel.state,
    hotel.country,
  ]
    .filter(Boolean)
    .join(", ");

  /*
   * This remains a catalogue estimate only.
   *
   * Once the hold exists, BookingFlow switches to the backend
   * hold response for all authoritative pricing.
   */
  const estimatedRoomCharge =
    room.pricePerNight *
    stay.nights;

  return (
    <main className="travel-shell min-h-screen pb-20 pt-[105px]">
      <section className="mx-auto max-w-5xl px-4 sm:px-6">
        <div className="grid gap-5 lg:grid-cols-[1.15fr_.85fr]">
          <div className="glass-panel rounded-[28px] p-5 sm:p-7">
            <p className="text-xs font-bold uppercase tracking-[.16em] text-[#2f6df6]">
              Stay summary
            </p>

            <div className="mt-5 flex gap-4">
              {room.heroImageUrl ? (
                <img
                  src={
                    room.heroImageUrl
                  }
                  alt={
                    room.name
                  }
                  className="h-24 w-28 rounded-2xl object-cover"
                />
              ) : (
                <div className="grid h-24 w-28 shrink-0 place-items-center rounded-2xl bg-[#e8f1fc] text-center text-xs font-bold text-[#56718f]">
                  Room image
                  <br />
                  coming soon
                </div>
              )}

              <div>
                <h1 className="text-xl font-extrabold text-[#17375f]">
                  {hotel.name}
                </h1>

                <p className="mt-1 text-sm font-bold text-[#56718f]">
                  {room.name}
                </p>

                <p className="mt-2 text-xs text-[#7188a5]">
                  {location}
                </p>
              </div>
            </div>

            <div className="mt-6 grid gap-3 sm:grid-cols-3">
              <Summary
                icon={
                  <CalendarDays />
                }
                title="Check-in"
                value={formatDate(
                  stay.checkIn,
                )}
              />

              <Summary
                icon={
                  <CalendarDays />
                }
                title="Check-out"
                value={formatDate(
                  stay.checkOut,
                )}
              />

              <Summary
                icon={
                  <Users />
                }
                title="Guests"
                value={`${stay.guests} ${
                  stay.guests === 1
                    ? "Guest"
                    : "Guests"
                } · 1 Room`}
              />
            </div>

            <div className="mt-6 rounded-2xl bg-[#eef5ff] p-5">
              <PriceText
                label="Current catalogue rate"
                value={`${formatCurrency(
                  room.pricePerNight,
                  room.currency,
                )} / night`}
              />

              <PriceText
                label={`Preliminary estimate · ${
                  stay.nights
                } ${
                  stay.nights === 1
                    ? "night"
                    : "nights"
                }`}
                value={formatCurrency(
                  estimatedRoomCharge,
                  room.currency,
                )}
                strong
              />

              <p className="mt-3 text-xs leading-5 text-[#6d84a1]">
                This amount is only the current catalogue estimate.
                SpatialStay will recheck availability and the backend
                will return the authoritative price when your temporary
                inventory hold is created.
              </p>
            </div>

            <div className="mt-5 rounded-2xl border border-[#dbe7f6] bg-white/75 p-4">
              <div className="flex items-start gap-3">
                <ShieldCheck
                  size={18}
                  className="mt-0.5 shrink-0 text-[#2f6df6]"
                />

                <div>
                  <p className="text-sm font-extrabold text-[#17375f]">
                    Backend-authoritative reservation
                  </p>

                  <p className="mt-1 text-xs leading-5 text-[#6d84a1]">
                    Room inventory, ownership, dates, pricing and hold
                    expiration are validated by SpatialStay's Spring
                    booking engine. The browser cannot confirm its own
                    price or reservation.
                  </p>
                </div>
              </div>
            </div>
          </div>

          <BookingFlow
            hotelSlug={
              hotel.slug
            }
            hotelName={
              hotel.name
            }
            roomSlug={
              room.slug
            }
            roomName={
              room.name
            }
            checkIn={
              stay.checkIn
            }
            checkOut={
              stay.checkOut
            }
            guests={
              stay.guests
            }
          />
        </div>
      </section>
    </main>
  );
}

function Summary({
  icon,
  title,
  value,
}: {
  icon:
    React.ReactNode;
  title: string;
  value: string;
}) {
  return (
    <div className="rounded-2xl border border-[#dbe7f6] bg-white p-4">
      <span className="text-[#2f6df6] [&>svg]:h-5 [&>svg]:w-5">
        {icon}
      </span>

      <p className="mt-3 text-[10px] font-bold uppercase tracking-[.12em] text-[#8195ae]">
        {title}
      </p>

      <p className="mt-1 text-sm font-bold text-[#17375f]">
        {value}
      </p>
    </div>
  );
}

function PriceText({
  label,
  value,
  strong = false,
}: {
  label: string;
  value: string;
  strong?: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-[#dce7f4] py-2.5 last:border-b-0">
      <span className="text-xs font-semibold text-[#6d84a1]">
        {label}
      </span>

      <span
        className={
          strong
            ? "text-right text-base font-black text-[#17375f]"
            : "text-right text-sm font-extrabold text-[#17375f]"
        }
      >
        {value}
      </span>
    </div>
  );
}

function buildStaySelection(
  rawCheckIn:
    | string
    | undefined,
  rawCheckOut:
    | string
    | undefined,
  rawGuests:
    | string
    | undefined,
  maxGuests: number,
) {
  const checkIn =
    validDate(
      rawCheckIn,
    )
      ? rawCheckIn!
      : null;

  const checkOut =
    validDate(
      rawCheckOut,
    )
      ? rawCheckOut!
      : null;

  const parsedGuests =
    Number(
      rawGuests,
    );

  const guests =
    Number.isInteger(
      parsedGuests,
    ) &&
    parsedGuests >= 1 &&
    parsedGuests <= maxGuests
      ? parsedGuests
      : null;

  let nights:
    | number
    | null =
    null;

  if (
    checkIn &&
    checkOut
  ) {
    const start =
      new Date(
        `${checkIn}T00:00:00`,
      );

    const end =
      new Date(
        `${checkOut}T00:00:00`,
      );

    const difference =
      Math.round(
        (end.getTime() -
          start.getTime()) /
          86_400_000,
      );

    if (
      difference > 0
    ) {
      nights =
        difference;
    }
  }

  return {
    checkIn,
    checkOut,
    guests,
    nights,
  };
}

function validDate(
  value:
    | string
    | undefined,
) {
  if (
    !value ||
    !/^\d{4}-\d{2}-\d{2}$/.test(
      value,
    )
  ) {
    return false;
  }

  const parsed =
    new Date(
      `${value}T00:00:00`,
    );

  return !Number.isNaN(
    parsed.getTime(),
  );
}

function formatDate(
  value: string,
) {
  const date =
    new Date(
      `${value}T00:00:00`,
    );

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