import { getHotelBySlug } from "@/lib/api/hotels";
import { PropertyGallery } from "@/components/media/PropertyGallery";

import {
  getRoomAvailability,
  getRoomsByHotel,
  type RoomAvailability,
} from "@/lib/api/rooms";

import type { ApiRoom } from "@/types/api-room";

import {
  BadgeCheck,
  BedDouble,
  Box,
  Check,
  MapPin,
  ShieldCheck,
  Sparkles,
  Scale,
  Star,
} from "lucide-react";

import Link from "next/link";

import { notFound } from "next/navigation";

type HotelDetailsPageProps = {
  params: Promise<{
    id: string;
  }>;

  searchParams: Promise<{
    checkIn?: string;
    checkOut?: string;
    guests?: string;
  }>;
};

export default async function HotelDetailsPage({
  params,
  searchParams,
}: HotelDetailsPageProps) {
  const { id } = await params;

  const staySearch = await searchParams;

  const checkIn =
    staySearch.checkIn?.trim() ?? "";

  const checkOut =
    staySearch.checkOut?.trim() ?? "";

  const parsedGuests = Number(
    staySearch.guests,
  );

  const guests =
    Number.isInteger(parsedGuests) &&
    parsedGuests >= 1
      ? parsedGuests
      : null;

  const hasStaySearch =
    /^\d{4}-\d{2}-\d{2}$/.test(
      checkIn,
    ) &&
    /^\d{4}-\d{2}-\d{2}$/.test(
      checkOut,
    ) &&
    guests !== null;

  /*
   * The dynamic route is still named [id], but it now
   * contains the backend hotel slug.
   *
   * Example:
   * /hotels/azure-bay-resort-goa
   */

  const hotel = await getHotelBySlug(id);

  if (!hotel) {
    notFound();
  }

  let rooms: ApiRoom[] = [];

  let availability: RoomAvailability[] =
    [];

  let roomsError = false;

  let availabilityError = false;

  try {
    rooms = await getRoomsByHotel(
      hotel.slug,
    );

    if (hasStaySearch) {
      try {
        availability =
          await getRoomAvailability(
            hotel.slug,
            {
              checkIn,
              checkOut,
              guests,
            },
          );

        const availableRoomSlugs =
          new Set(
            availability
              .filter(
                (item) =>
                  item.available,
              )
              .map(
                (item) =>
                  item.roomSlug,
              ),
          );

        rooms = rooms.filter(
          (room) =>
            availableRoomSlugs.has(
              room.slug,
            ),
        );
      } catch {
        availabilityError = true;
      }
    }
  } catch {
    roomsError = true;
  }

  const stayQuery =
    hasStaySearch
      ? new URLSearchParams({
          checkIn,
          checkOut,
          guests: String(guests),
        }).toString()
      : "";

  const location = [
    hotel.address,
    hotel.city,
    hotel.state,
    hotel.country,
  ]
    .filter(Boolean)
    .filter(
      (value, index, values) =>
        values.indexOf(value) === index,
    )
    .join(", ");

  return (
    <main className="travel-shell min-h-screen pb-24 pt-[100px]">
      <section className="mx-auto max-w-[1480px] px-4 sm:px-6">
        <div className="glass-panel overflow-hidden rounded-[28px] p-3 sm:p-4">
          {/* Hotel gallery */}
          <PropertyGallery
            images={hotel.imageUrls ?? []}
            fallbackImage={hotel.heroImageUrl}
            title={hotel.name}
          >
            <div className="pointer-events-none absolute bottom-5 left-5 right-5 z-10 text-white sm:bottom-7 sm:left-7 sm:right-7">
              <div className="flex flex-wrap items-center gap-2 text-xs font-bold">
                {hotel.verified && (
                  <span className="rounded-full bg-white/18 px-3 py-1.5 backdrop-blur">
                    <BadgeCheck
                      size={13}
                      className="mr-1 inline"
                    />
                    Verified property
                  </span>
                )}

                {hotel.rating !== null && (
                  <span className="rounded-full bg-white/18 px-3 py-1.5 backdrop-blur">
                    <Star
                      size={13}
                      className="mr-1 inline"
                      fill="currentColor"
                    />
                    {hotel.rating.toFixed(1)}
                    {" · "}
                    {hotel.reviewCount.toLocaleString(
                      "en-IN",
                    )}{" "}
                    reviews
                  </span>
                )}
              </div>

              <h1 className="mt-3 text-3xl font-extrabold sm:text-5xl">
                {hotel.name}
              </h1>

              <p className="mt-2 flex items-start gap-2 text-sm text-white/85">
                <MapPin
                  size={15}
                  className="mt-0.5 shrink-0"
                />
                {location}
              </p>
            </div>
          </PropertyGallery>

          {/* Hotel overview */}

          <div className="mt-4 grid gap-4 lg:grid-cols-[1fr_320px]">
            <div className="rounded-[22px] border border-[#dbe8f7] bg-white/80 p-5 sm:p-6">
              <div className="flex flex-wrap gap-2 border-b border-[#e1ebf7] pb-4 text-sm font-bold text-[#567394]">
                <span className="blue-button rounded-full px-4 py-2">
                  Overview
                </span>

                <a
                  href="#rooms"
                  className="rounded-full px-4 py-2 transition hover:bg-[#edf4ff]"
                >
                  Rooms
                </a>

                <span className="px-4 py-2 text-[#9aabc0]">
                  Amenities
                </span>

                <span className="px-4 py-2 text-[#9aabc0]">
                  Reviews
                </span>

                <span className="px-4 py-2 text-[#9aabc0]">
                  Location
                </span>
              </div>

              <h2 className="mt-5 text-xl font-extrabold text-[#173d70]">
                About this hotel
              </h2>

              <p className="mt-2 max-w-4xl text-sm leading-7 text-[#5e7694]">
                {hotel.description ??
                  "Property information will be available soon."}
              </p>

              <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
                <InfoCard
                  icon={<BadgeCheck />}
                  label={
                    hotel.verified
                      ? "Verified property"
                      : "Property details"
                  }
                />

                <InfoCard
                  icon={<MapPin />}
                  label={hotel.city}
                />

                <InfoCard
                  icon={<Star />}
                  label={
                    hotel.rating !== null
                      ? `${hotel.rating.toFixed(
                          1,
                        )} guest rating`
                      : "New property"
                  }
                />

                <InfoCard
                  icon={<ShieldCheck />}
                  label={hotel.status}
                />
              </div>
            </div>

            {/* Starting price */}

            <aside className="rounded-[22px] bg-gradient-to-br from-[#edf5ff] to-white p-5 ring-1 ring-[#dbe8f7]">
              <p className="text-xs font-bold uppercase tracking-[.15em] text-[#7089aa]">
                Starting from
              </p>

              <p className="mt-2 text-3xl font-extrabold text-[#173d70]">
                ₹
                {hotel.startingPrice.toLocaleString(
                  "en-IN",
                )}

                <span className="text-sm font-medium text-[#7890ad]">
                  /night
                </span>
              </p>

              <p className="mt-1 text-xs text-[#7890ad]">
                Room prices may vary by room
                type.
              </p>

              <div className="mt-5 rounded-2xl bg-white p-4 text-sm leading-6 text-[#5d7594] shadow-sm">
                <Sparkles
                  size={18}
                  className="mb-2 text-[#2f6df6]"
                />

                {hasStaySearch ? (
                  <>
                    Room availability is being
                    checked for your selected
                    dates and guest count.
                  </>
                ) : (
                  <>
                    Explore the room types
                    currently configured for
                    this property. Add dates and
                    guests from search to check
                    stay availability.
                  </>
                )}
              </div>

              <a
                href="#rooms"
                className="blue-button mt-4 block rounded-xl px-4 py-3 text-center text-sm font-bold"
              >
                Explore room types
              </a>
            </aside>
          </div>
        </div>

        {/* Rooms */}

        <section
          id="rooms"
          className="mt-7 scroll-mt-28"
        >
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <p className="text-xs font-bold uppercase tracking-[.15em] text-[#718aaa]">
                Choose your space
              </p>

              <h2 className="mt-1 text-3xl font-extrabold text-[#173d70]">
                Room types
              </h2>

              <p className="mt-2 max-w-2xl text-sm text-[#7188a5]">
                {hasStaySearch
                  ? `Showing room types that can accommodate ${guests} ${
                      guests === 1
                        ? "guest"
                        : "guests"
                    } for your selected stay.`
                  : `Explore room types configured for ${hotel.name}. Add dates and guests from search to check stay availability.`}
              </p>
            </div>
          </div>

          {roomsError && (
            <div className="mt-5 rounded-[22px] border border-red-100 bg-red-50 p-6 text-center">
              <p className="font-bold text-red-700">
                We couldn't load the room
                types.
              </p>

              <p className="mt-1 text-xs text-red-500">
                Make sure the Spring Boot
                backend is running and the room
                endpoint is available.
              </p>
            </div>
          )}

          {!roomsError &&
            availabilityError && (
              <div className="mt-5 rounded-[22px] border border-amber-100 bg-amber-50 p-5 text-center">
                <p className="font-bold text-amber-800">
                  We couldn't verify
                  availability for this stay.
                </p>

                <p className="mt-1 text-xs text-amber-600">
                  The room catalogue is still
                  available below. Try the
                  availability check again
                  before continuing.
                </p>
              </div>
            )}

          {!roomsError &&
            rooms.length > 0 && (
              <div className="hide-scrollbar mt-5 flex snap-x gap-4 overflow-x-auto pb-4">
                {rooms.map((room) => (
                  <RoomCard
                    key={room.id}
                    room={room}
                    hotelSlug={hotel.slug}
                    stayQuery={stayQuery}
                    availability={
                      availability.find(
                        (item) =>
                          item.roomSlug ===
                          room.slug,
                      ) ?? null
                    }
                  />
                ))}
              </div>
            )}

          {!roomsError &&
            rooms.length === 0 && (
              <div className="glass-panel mt-5 rounded-[24px] p-10 text-center">
                <BedDouble
                  size={28}
                  className="mx-auto text-[#7d97b9]"
                />

                <h3 className="mt-3 text-lg font-extrabold text-[#173d70]">
                  {hasStaySearch
                    ? "No rooms available for this stay"
                    : "No room types configured yet"}
                </h3>

                <p className="mt-2 text-sm text-[#7188a5]">
                  {hasStaySearch
                    ? "Try different dates or fewer guests to see other room options."
                    : "Room information for this property will appear here when it is added."}
                </p>
              </div>
            )}
        </section>

        {!roomsError &&
          rooms.length >= 2 && (
            <div className="mt-7 text-center">
              <Link
                href={
                  `/hotels/${hotel.slug}/compare` +
                  (stayQuery
                    ? `?${stayQuery}`
                    : "")
                }
                className="blue-button inline-flex items-center gap-2 rounded-xl px-6 py-3 font-bold"
              >
                <Scale size={17} />

                Compare rooms visually
              </Link>
            </div>
          )}
      </section>
    </main>
  );
}

function RoomCard({
  room,
  hotelSlug,
  stayQuery,
  availability,
}: {
  room: ApiRoom;
  hotelSlug: string;
  stayQuery: string;
  availability:
    | RoomAvailability
    | null;
}) {
  const roomDetails = [
    room.roomSizeSqFt
      ? `${room.roomSizeSqFt} sq ft`
      : null,

    `${room.maxGuests} ${
      room.maxGuests === 1
        ? "guest"
        : "guests"
    }`,

    room.bedType,
  ].filter(Boolean);

  return (
    <article className="soft-card min-w-[290px] snap-start overflow-hidden rounded-[24px] p-3 sm:min-w-[360px] lg:min-w-[390px]">
      <div className="relative overflow-hidden rounded-[18px]">
        <img
          src={
            room.heroImageUrl ??
            "https://images.unsplash.com/photo-1611892440504-42a792e24d32"
          }
          alt={room.name}
          className="h-52 w-full object-cover transition duration-500 hover:scale-105"
        />

        {room.spatialAvailable && (
          <span className="absolute bottom-3 left-3 flex items-center gap-1.5 rounded-full bg-white/90 px-3 py-1.5 text-[10px] font-extrabold text-[#2f6df6] shadow-sm backdrop-blur">
            <Box size={12} />

            Spatial view
          </span>
        )}
      </div>

      <div className="p-2 pt-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="text-lg font-extrabold text-[#173d70]">
              {room.name}
            </h3>

            <p className="mt-1 text-xs text-[#7188a5]">
              {roomDetails.join(" · ")}
            </p>
          </div>

          {room.active && (
            <span className="flex shrink-0 items-center gap-1 rounded-full bg-[#eaf7ef] px-2.5 py-1 text-[10px] font-bold text-[#288455]">
              <Check size={11} />

              Active
            </span>
          )}
        </div>

        {room.description && (
          <p className="mt-3 line-clamp-2 text-xs leading-5 text-[#6d84a1]">
            {room.description}
          </p>
        )}

        {room.amenities.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-1.5">
            {room.amenities
              .slice(0, 4)
              .map((amenity) => (
                <span
                  key={`${room.id}-${amenity.id}-${amenity.code}`}
                  className="rounded-full bg-[#edf4ff] px-2.5 py-1 text-[10px] font-bold text-[#4f72a3]"
                >
                  {amenity.name}
                </span>
              ))}
          </div>
        )}

        {availability && (
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-[#eaf7ef] px-3 py-1.5 text-[10px] font-extrabold text-[#288455]">
              Available
            </span>

            <span className="text-[10px] font-semibold text-[#7188a5]">
              {availability.availableUnits}{" "}
              {availability.availableUnits ===
              1
                ? "unit"
                : "units"}{" "}
              available ·{" "}
              {availability.nights}{" "}
              {availability.nights === 1
                ? "night"
                : "nights"}
            </span>
          </div>
        )}

        <div className="mt-4 flex items-end justify-between gap-3 border-t border-[#e5edf8] pt-4">
          <div>
            <p className="text-[9px] font-bold uppercase tracking-[.1em] text-[#8ba0b9]">
              Room rate
            </p>

            <p className="text-xl font-extrabold text-[#173d70]">
              {room.currency === "INR"
                ? "₹"
                : `${room.currency} `}

              {room.pricePerNight.toLocaleString(
                "en-IN",
              )}

              <span className="text-xs font-medium text-[#7890ad]">
                /night
              </span>
            </p>
          </div>

          <Link
            href={
              `/rooms/${hotelSlug}/${room.slug}` +
              (stayQuery
                ? `?${stayQuery}`
                : "")
            }
            className="blue-button rounded-xl px-4 py-2.5 text-sm font-bold"
          >
            View Room
          </Link>
        </div>
      </div>
    </article>
  );
}

function InfoCard({
  icon,
  label,
}: {
  icon: React.ReactNode;
  label: string;
}) {
  return (
    <div className="rounded-2xl bg-[#f3f7fd] p-4 text-center">
      <span className="mx-auto grid h-9 w-9 place-items-center rounded-full bg-white text-[#2f6df6] shadow-sm">
        {icon}
      </span>

      <p className="mt-2 text-xs font-bold text-[#315276]">
        {label}
      </p>
    </div>
  );
}