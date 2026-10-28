import Link from "next/link";
import { notFound } from "next/navigation";
import {
  ArrowLeft,
  BadgeCheck,
  BedDouble,
  Box,
  Check,
  Scale,
  Users,
} from "lucide-react";

import { getHotelBySlug } from "@/lib/api/hotels";
import { getRoomsByHotel } from "@/lib/api/rooms";
import type { ApiRoom } from "@/types/api-room";

interface CompareRoomsPageProps {
  params: Promise<{
    id: string;
  }>;
}

export default async function CompareRoomsPage({
  params,
}: CompareRoomsPageProps) {
  const { id: hotelSlug } = await params;

  const hotel = await getHotelBySlug(hotelSlug);

  if (!hotel) {
    notFound();
  }

  let rooms: ApiRoom[] = [];

  try {
    const hotelRooms = await getRoomsByHotel(hotelSlug);

    rooms = hotelRooms
      .filter((room) => room.active)
      .slice(0, 3);
  } catch {
    rooms = [];
  }

  return (
    <main className="min-h-screen bg-[#eef5ff] pb-28 pt-28">
      <section className="mx-auto max-w-7xl px-4 sm:px-6">
        <Link
          href={`/hotels/${hotel.slug}#rooms`}
          className="inline-flex items-center gap-2 text-sm font-semibold text-black/55 transition hover:text-[#174f9f]"
        >
          <ArrowLeft size={16} />
          Back to {hotel.name}
        </Link>

        <div className="mt-8 flex flex-wrap items-end justify-between gap-5">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.2em] text-[#2f6df6]">
              Room comparison
            </p>

            <h1 className="serif mt-2 max-w-4xl text-4xl sm:text-6xl">
              Compare the spaces, not just the prices.
            </h1>

            <p className="mt-4 max-w-2xl text-sm leading-6 text-black/55">
              Compare room details currently provided by{" "}
              {hotel.name}.
            </p>
          </div>

          {rooms.length > 0 ? (
            <div className="flex items-center gap-2 rounded-full bg-[#174f9f] px-4 py-2 text-sm font-semibold text-white">
              <Scale size={16} />
              {rooms.length}{" "}
              {rooms.length === 1 ? "room" : "rooms"} to compare
            </div>
          ) : null}
        </div>

        {rooms.length === 0 ? (
          <div className="mt-10 rounded-[28px] border border-[#dce7f5] bg-white p-8 text-center shadow-sm">
            <BedDouble
              size={36}
              className="mx-auto text-[#2f6df6]"
            />

            <h2 className="mt-4 text-xl font-bold text-[#17345d]">
              No rooms available for comparison
            </h2>

            <p className="mx-auto mt-2 max-w-lg text-sm leading-6 text-[#607894]">
              Room types will appear here when they are
              available in the hotel catalogue.
            </p>
          </div>
        ) : (
          <div className="mt-10 overflow-x-auto pb-4">
            <div
              className="grid min-w-[760px] gap-4"
              style={{
                gridTemplateColumns: `repeat(${rooms.length}, minmax(300px, 1fr))`,
              }}
            >
              {rooms.map((room) => (
                <RoomComparisonCard
                  key={`${hotel.id}-${room.id}-${room.slug}`}
                  hotelSlug={hotel.slug}
                  room={room}
                />
              ))}
            </div>
          </div>
        )}

        {rooms.length > 0 ? (
          <div className="mt-4 rounded-2xl border border-[#dbe7f5] bg-white/70 p-4">
            <p className="text-sm leading-6 text-black/50">
              This comparison uses current room catalogue
              information. Date-specific availability,
              inventory, rate plans and cancellation terms
              will be shown after the booking and
              availability services are connected.
            </p>
          </div>
        ) : null}
      </section>
    </main>
  );
}

function RoomComparisonCard({
  hotelSlug,
  room,
}: {
  hotelSlug: string;
  room: ApiRoom;
}) {
  const description =
    room.description?.trim() ||
    "Room description is not available yet.";

  return (
    <article className="overflow-hidden rounded-[28px] border border-black/5 bg-white shadow-[0_18px_50px_rgba(20,61,110,.08)]">
      <RoomImage room={room} />

      <div className="p-6">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.15em] text-[#2f6df6]">
            {formatRoomType(room.roomType)}
          </p>

          <h2 className="serif mt-2 text-3xl">
            {room.name}
          </h2>

          <p className="mt-3 line-clamp-3 min-h-[72px] text-sm leading-6 text-black/50">
            {description}
          </p>
        </div>

        <div className="mt-6">
          <CompareRow
            label="Nightly rate"
            value={formatCurrency(
              room.pricePerNight,
              room.currency,
            )}
          />

          <CompareRow
            label="Room size"
            value={
              room.roomSizeSqFt !== null
                ? `${room.roomSizeSqFt} sq ft`
                : "Not listed"
            }
          />

          <CompareRow
            label="Guests"
            value={`Up to ${room.maxGuests}`}
          />

          <CompareRow
            label="Bed"
            value={room.bedType?.trim() || "Not listed"}
          />

          <CompareRow
            label="Spatial view"
            value={
              room.spatialAvailable
                ? "Available"
                : "Not available"
            }
          />
        </div>

        <div className="mt-5 rounded-2xl bg-[#eef5ff] p-4">
          <p className="flex items-center gap-2 text-xs font-bold uppercase tracking-[0.15em] text-[#2f6df6]">
            <Check size={14} />
            Room amenities
          </p>

          {room.amenities.length > 0 ? (
            <div className="mt-3 flex flex-wrap gap-2">
              {room.amenities
                .slice(0, 6)
                .map((amenity) => (
                  <span
                    key={`${room.id}-${amenity.id}-${amenity.code}`}
                    className="rounded-full bg-white px-3 py-1.5 text-xs font-semibold text-[#56718f]"
                  >
                    {amenity.name}
                  </span>
                ))}
            </div>
          ) : (
            <p className="mt-3 text-sm text-black/50">
              Amenities have not been listed yet.
            </p>
          )}
        </div>

        <div className="mt-5 flex flex-wrap items-center gap-2">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-[#f5f0e6] px-3 py-1.5 text-xs font-semibold text-[#5d6571]">
            <Users size={14} />
            {room.maxGuests}{" "}
            {room.maxGuests === 1 ? "guest" : "guests"}
          </span>

          {room.spatialAvailable ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-[#e8f5ee] px-3 py-1.5 text-xs font-semibold text-[#276749]">
              <Box size={14} />
              3D ready
            </span>
          ) : null}

          {room.active ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-[#edf4ff] px-3 py-1.5 text-xs font-semibold text-[#174f9f]">
              <BadgeCheck size={14} />
              Listed
            </span>
          ) : null}
        </div>

        <Link
          href={`/rooms/${hotelSlug}/${room.slug}`}
          className="mt-6 flex w-full items-center justify-center rounded-full bg-[#174f9f] px-5 py-3 text-sm font-bold text-white transition hover:bg-[#123f80]"
        >
          Explore room
        </Link>
      </div>
    </article>
  );
}

function RoomImage({
  room,
}: {
  room: ApiRoom;
}) {
  if (!room.heroImageUrl) {
    return (
      <div className="flex h-56 items-center justify-center bg-gradient-to-br from-[#eaf3ff] to-[#dce9fb]">
        <div className="text-center text-[#174f9f]">
          <BedDouble
            size={38}
            className="mx-auto"
          />

          <p className="mt-3 text-sm font-bold">
            Room image coming soon
          </p>
        </div>
      </div>
    );
  }

  return (
    <div
      className="h-56 bg-cover bg-center"
      style={{
        backgroundImage: `url(${room.heroImageUrl})`,
      }}
      role="img"
      aria-label={room.name}
    />
  );
}

function CompareRow({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-black/5 py-3 text-sm">
      <span className="text-black/45">
        {label}
      </span>

      <strong className="text-right font-semibold">
        {value}
      </strong>
    </div>
  );
}

function formatCurrency(
  amount: number,
  currency: string,
): string {
  try {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency,
      maximumFractionDigits: 0,
    }).format(amount);
  } catch {
    return `${currency} ${amount.toLocaleString("en-IN")}`;
  }
}

function formatRoomType(
  roomType: string,
): string {
  return roomType
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (character) =>
      character.toUpperCase(),
    );
}