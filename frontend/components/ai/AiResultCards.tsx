"use client";

import { motion } from "framer-motion";
import {
  BadgeCheck,
  BedDouble,
  CalendarDays,
  ChevronRight,
  Clock3,
  ExternalLink,
  MapPin,
  Maximize2,
  Sparkles,
  Star,
  Users,
} from "lucide-react";
import Link from "next/link";

import type {
  AvailabilityResult,
  HotelResult,
  RoomResult,
} from "@/lib/api/ai";

type Props = {
  hotels?: HotelResult[];
  rooms?: RoomResult[];
  availability?: AvailabilityResult[];
  onPrompt?: (prompt: string) => void;
};

export default function AiResultCards({
  hotels = [],
  rooms = [],
  availability = [],
  onPrompt,
}: Props) {
  if (availability.length > 0) {
    return (
      <AvailabilityResults
        availability={availability}
        onPrompt={onPrompt}
      />
    );
  }

  if (rooms.length > 0) {
    return (
      <RoomResults
        rooms={rooms}
        onPrompt={onPrompt}
      />
    );
  }

  if (hotels.length > 0) {
    return (
      <HotelResults
        hotels={hotels}
        onPrompt={onPrompt}
      />
    );
  }

  return null;
}

function HotelResults({
  hotels,
  onPrompt,
}: {
  hotels: HotelResult[];
  onPrompt?: (prompt: string) => void;
}) {
  return (
    <div className="mt-4">
      <div className="hide-scrollbar flex snap-x gap-4 overflow-x-auto pb-3">
        {hotels.map((hotel, index) => (
          <motion.article
            key={hotel.id}
            initial={{
              opacity: 0,
              y: 14,
              scale: 0.985,
            }}
            animate={{
              opacity: 1,
              y: 0,
              scale: 1,
            }}
            transition={{
              duration: 0.35,
              delay: index * 0.06,
            }}
            className="w-[min(84vw,430px)] shrink-0 snap-start overflow-hidden rounded-[24px] border border-[#dde7f2] bg-white shadow-[0_18px_55px_rgba(36,73,119,.10)]"
          >
            <div className="relative h-[205px] overflow-hidden bg-[#edf3fa]">
              {hotel.heroImageUrl ? (
                <img
                  src={hotel.heroImageUrl}
                  alt={hotel.name}
                  className="h-full w-full object-cover transition duration-700 hover:scale-[1.035]"
                />
              ) : (
                <ImageFallback />
              )}

              <div className="absolute inset-x-0 bottom-0 h-28 bg-gradient-to-t from-[#0a1f3a]/70 via-[#0a1f3a]/20 to-transparent" />

              {hotel.verified ? (
                <span className="absolute left-3 top-3 flex items-center gap-1.5 rounded-full bg-white/92 px-2.5 py-1.5 text-[9px] font-black uppercase tracking-[.08em] text-[#28745e] shadow-sm backdrop-blur-xl">
                  <BadgeCheck size={12} />
                  Verified
                </span>
              ) : null}

              <span className="absolute bottom-3 right-3 flex items-center gap-1 rounded-full bg-white/92 px-2.5 py-1.5 text-xs font-black text-[#173b65] shadow-sm">
                <Star
                  size={12}
                  className="fill-[#e6a62e] text-[#e6a62e]"
                />
                {hotel.rating}
              </span>

              <div className="absolute bottom-3 left-4 text-white">
                <p className="text-lg font-black drop-shadow-sm">
                  {hotel.name}
                </p>

                <p className="mt-0.5 flex items-center gap-1 text-[10px] font-semibold text-white/85">
                  <MapPin size={11} />
                  {hotel.address ||
                    `${hotel.city}, ${hotel.state}`}
                </p>
              </div>
            </div>

            <div className="p-4">
              <p className="line-clamp-2 text-[11px] leading-5 text-[#6b8098]">
                {hotel.description}
              </p>

              <div className="mt-4 flex items-end justify-between gap-4">
                <div>
                  <p className="text-[9px] font-black uppercase tracking-[.13em] text-[#9aa8b7]">
                    From
                  </p>

                  <p className="mt-0.5 text-[21px] font-black tracking-[-0.03em] text-[#173f76]">
                    {formatCurrency(
                      hotel.startingPrice,
                      "INR",
                    )}
                    <span className="ml-1 text-[9px] font-bold tracking-normal text-[#8b9bad]">
                      / night
                    </span>
                  </p>

                  <p className="mt-1 text-[9px] font-semibold text-[#91a0b0]">
                    {hotel.reviewCount.toLocaleString(
                      "en-IN",
                    )}{" "}
                    reviews
                  </p>
                </div>

                <div className="flex gap-2">
                  {onPrompt ? (
                    <button
                      type="button"
                      onClick={() =>
                        onPrompt(
                          `Show me the rooms in ${hotel.name}.`,
                        )
                      }
                      className="rounded-xl border border-[#d9e4f0] bg-[#f7faff] px-3 py-2.5 text-[10px] font-black text-[#486988] transition hover:bg-[#edf4fc]"
                    >
                      Explore rooms
                    </button>
                  ) : null}

                  <Link
                    href={`/hotels/${hotel.slug}`}
                    className="flex items-center gap-1 rounded-xl bg-[#173f76] px-3 py-2.5 text-[10px] font-black text-white transition hover:bg-[#123661]"
                  >
                    View
                    <ChevronRight size={12} />
                  </Link>
                </div>
              </div>
            </div>
          </motion.article>
        ))}
      </div>

      {onPrompt ? (
        <Suggestions
          items={[
            {
              label: "Check availability",
              prompt:
                "Help me check availability for this hotel.",
            },
            {
              label: "Hotel policies",
              prompt:
                "What policies should I know about this hotel?",
            },
          ]}
          onPrompt={onPrompt}
        />
      ) : null}
    </div>
  );
}

function RoomResults({
  rooms,
  onPrompt,
}: {
  rooms: RoomResult[];
  onPrompt?: (prompt: string) => void;
}) {
  return (
    <div className="mt-4">
      <div className="hide-scrollbar flex snap-x gap-4 overflow-x-auto pb-3">
        {rooms.map((room, index) => (
          <motion.article
            key={room.id}
            initial={{
              opacity: 0,
              y: 14,
            }}
            animate={{
              opacity: 1,
              y: 0,
            }}
            transition={{
              duration: 0.34,
              delay: index * 0.06,
            }}
            className="w-[min(84vw,390px)] shrink-0 snap-start overflow-hidden rounded-[24px] border border-[#dfe8f2] bg-white shadow-[0_16px_48px_rgba(36,73,119,.09)]"
          >
            <div className="relative h-[175px] overflow-hidden bg-[#edf3fa]">
              {room.heroImageUrl ? (
                <img
                  src={room.heroImageUrl}
                  alt={room.name}
                  className="h-full w-full object-cover transition duration-700 hover:scale-[1.035]"
                />
              ) : (
                <ImageFallback />
              )}

              <div className="absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-[#091e39]/70 to-transparent" />

              <span className="absolute left-3 top-3 rounded-full bg-white/92 px-2.5 py-1 text-[8px] font-black uppercase tracking-[.09em] text-[#4d6987]">
                {room.roomType.replaceAll(
                  "_",
                  " ",
                )}
              </span>

              {room.spatialAvailable ? (
                <span className="absolute right-3 top-3 flex items-center gap-1 rounded-full bg-[#172e54]/88 px-2.5 py-1 text-[8px] font-black uppercase tracking-[.07em] text-white backdrop-blur-xl">
                  <Sparkles size={10} />
                  Spatial
                </span>
              ) : null}

              <div className="absolute bottom-3 left-4">
                <p className="text-[10px] font-semibold text-white/75">
                  {room.hotelName}
                </p>
                <p className="text-base font-black text-white">
                  {room.name}
                </p>
              </div>
            </div>

            <div className="p-4">
              <div className="flex flex-wrap gap-1.5">
                <InfoPill
                  icon={<BedDouble size={11} />}
                  text={room.bedType}
                />

                <InfoPill
                  icon={<Users size={11} />}
                  text={`${room.maxGuests} guests`}
                />

                {room.roomSizeSqFt ? (
                  <InfoPill
                    icon={<Maximize2 size={11} />}
                    text={`${room.roomSizeSqFt} sq ft`}
                  />
                ) : null}
              </div>

              <div className="mt-3 flex min-h-[24px] flex-wrap gap-1.5">
                {room.amenities
                  .slice(0, 4)
                  .map((amenity) => (
                    <span
                      key={amenity.code}
                      className="rounded-full bg-[#f2f6fa] px-2 py-1 text-[8px] font-bold text-[#6a8199]"
                    >
                      {amenity.name}
                    </span>
                  ))}

                {room.amenities.length > 4 ? (
                  <span className="rounded-full bg-[#eeedff] px-2 py-1 text-[8px] font-black text-[#6963c8]">
                    +
                    {room.amenities.length - 4}
                  </span>
                ) : null}
              </div>

              <div className="mt-4 flex items-end justify-between border-t border-[#edf2f7] pt-3">
                <div>
                  <p className="text-[8px] font-black uppercase tracking-[.12em] text-[#9aa9b8]">
                    Per night
                  </p>

                  <p className="mt-0.5 text-lg font-black text-[#173f76]">
                    {formatCurrency(
                      room.pricePerNight,
                      room.currency,
                    )}
                  </p>
                </div>

                <div className="flex gap-2">
                  {room.spatialAvailable ? (
                    <Link
                      href={`/rooms/${room.hotelSlug}/${room.slug}/spatial`}
                      className="grid h-9 w-9 place-items-center rounded-xl border border-[#dae5f1] text-[#4d6d8e] transition hover:bg-[#f0f5fb]"
                      aria-label={`Spatial view of ${room.name}`}
                    >
                      <ExternalLink size={13} />
                    </Link>
                  ) : null}

                  <Link
                    href={`/rooms/${room.hotelSlug}/${room.slug}`}
                    className="flex items-center gap-1 rounded-xl bg-[#173f76] px-3 py-2 text-[9px] font-black text-white"
                  >
                    Details
                    <ChevronRight size={11} />
                  </Link>
                </div>
              </div>
            </div>
          </motion.article>
        ))}
      </div>

      {onPrompt ? (
        <Suggestions
          items={[
            {
              label: "Check dates",
              prompt:
                "Check availability for these rooms.",
            },
            {
              label: "Compare rooms",
              prompt:
                "Compare these rooms and explain the main differences.",
            },
          ]}
          onPrompt={onPrompt}
        />
      ) : null}
    </div>
  );
}

function AvailabilityResults({
  availability,
  onPrompt,
}: {
  availability: AvailabilityResult[];
  onPrompt?: (prompt: string) => void;
}) {
  return (
    <div className="mt-4">
      <div className="grid gap-3 lg:grid-cols-2">
        {availability.map(
          (stay, index) => (
            <motion.article
              key={`${stay.roomId}-${stay.checkIn}-${stay.checkOut}`}
              initial={{
                opacity: 0,
                y: 12,
              }}
              animate={{
                opacity: 1,
                y: 0,
              }}
              transition={{
                duration: 0.32,
                delay: index * 0.05,
              }}
              className="rounded-[22px] border border-[#dce6f1] bg-[linear-gradient(145deg,#ffffff,#f5f9ff)] p-4 shadow-[0_14px_40px_rgba(37,72,116,.08)]"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <span
                    className={`inline-flex rounded-full px-2.5 py-1 text-[8px] font-black uppercase tracking-[.08em] ${
                      stay.available
                        ? "bg-[#e9f8f2] text-[#2d8065]"
                        : "bg-[#fff0f0] text-[#ad5555]"
                    }`}
                  >
                    {stay.available
                      ? "Configured inventory"
                      : "Unavailable"}
                  </span>

                  <h3 className="mt-2 text-base font-black text-[#183b63]">
                    {stay.roomName}
                  </h3>
                </div>

                <div className="text-right">
                  <p className="text-[9px] font-bold text-[#8799ac]">
                    Estimated total
                  </p>
                  <p className="mt-0.5 text-lg font-black text-[#173f76]">
                    {formatCurrency(
                      stay.estimatedStayTotal,
                      stay.currency,
                    )}
                  </p>
                </div>
              </div>

              <div className="mt-4 grid grid-cols-3 gap-2">
                <StayMetric
                  icon={
                    <CalendarDays size={13} />
                  }
                  label="Stay"
                  value={`${formatShortDate(
                    stay.checkIn,
                  )} – ${formatShortDate(
                    stay.checkOut,
                  )}`}
                />

                <StayMetric
                  icon={<Clock3 size={13} />}
                  label="Duration"
                  value={`${stay.nights} nights`}
                />

                <StayMetric
                  icon={<Users size={13} />}
                  label="Guests"
                  value={`${stay.guests}`}
                />
              </div>

              <div className="mt-4 flex items-center justify-between border-t border-[#e9eff6] pt-3">
                <p className="text-[10px] font-bold text-[#70869e]">
                  {formatCurrency(
                    stay.pricePerNight,
                    stay.currency,
                  )}{" "}
                  / night
                </p>

                <p className="text-[9px] font-semibold text-[#93a2b2]">
                  {stay.availableUnits} configured
                  units
                </p>
              </div>
            </motion.article>
          ),
        )}
      </div>

      <p className="mt-2 text-[9px] leading-4 text-[#95a3b2]">
        Inventory currently reflects configured
        room units. Confirmed booking and
        temporary-hold deductions will be
        integrated with the booking engine.
      </p>

      {onPrompt ? (
        <Suggestions
          items={[
            {
              label: "Change dates",
              prompt:
                "I want to check different stay dates.",
            },
            {
              label: "Compare these rooms",
              prompt:
                "Compare these available room options.",
            },
          ]}
          onPrompt={onPrompt}
        />
      ) : null}
    </div>
  );
}

function Suggestions({
  items,
  onPrompt,
}: {
  items: {
    label: string;
    prompt: string;
  }[];
  onPrompt: (prompt: string) => void;
}) {
  return (
    <div className="mt-2 flex flex-wrap gap-2">
      {items.map((item) => (
        <button
          key={item.label}
          type="button"
          onClick={() =>
            onPrompt(item.prompt)
          }
          className="rounded-full border border-[#dfe7f1] bg-white/75 px-3 py-1.5 text-[9px] font-extrabold text-[#607b99] transition hover:border-[#cbdcf0] hover:bg-white hover:text-[#315d91]"
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}

function StayMetric({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="rounded-xl bg-white/80 p-2.5">
      <div className="flex items-center gap-1 text-[#7690aa]">
        {icon}
        <span className="text-[8px] font-black uppercase tracking-[.08em]">
          {label}
        </span>
      </div>

      <p className="mt-1.5 truncate text-[9px] font-extrabold text-[#345778]">
        {value}
      </p>
    </div>
  );
}

function InfoPill({
  icon,
  text,
}: {
  icon: React.ReactNode;
  text: string;
}) {
  return (
    <span className="flex items-center gap-1 rounded-full border border-[#e3eaf2] bg-[#f8fafd] px-2.5 py-1.5 text-[8px] font-bold text-[#647d97]">
      {icon}
      {text}
    </span>
  );
}

function ImageFallback() {
  return (
    <div className="grid h-full w-full place-items-center bg-gradient-to-br from-[#e8f1ff] via-[#f5f8ff] to-[#ebeaff]">
      <div className="text-center text-[#6683a7]">
        <Sparkles
          size={22}
          className="mx-auto"
        />
        <p className="mt-2 text-[9px] font-black uppercase tracking-[.14em]">
          SpatialStay
        </p>
      </div>
    </div>
  );
}

function formatCurrency(
  amount: number,
  currency: string,
) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}

function formatShortDate(value: string) {
  const date = new Date(
    `${value}T00:00:00`,
  );

  return new Intl.DateTimeFormat("en-IN", {
    day: "numeric",
    month: "short",
  }).format(date);
}