import { RoomStaySelector } from "@/components/hotel/RoomStaySelector";
import { getHotelBySlug } from "@/lib/api/hotels";
import { getRoomBySlug } from "@/lib/api/rooms";
import { PropertyGallery } from "@/components/media/PropertyGallery";
import {
  ArrowLeft,
  BadgeCheck,
  BedDouble,
  Box,
  Check,
  Maximize2,
  ShieldCheck,
  Sparkles,
  Users,
} from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";

interface RoomDetailPageProps {
  params: Promise<{
    hotelId: string;
    roomId: string;
  }>;
}

export default async function RoomDetailPage({
  params,
}: RoomDetailPageProps) {
  const { hotelId, roomId } = await params;

  const [hotel, room] = await Promise.all([
    getHotelBySlug(hotelId),
    getRoomBySlug(hotelId, roomId),
  ]);

  if (!hotel || !room) {
    notFound();
  }

  const roomSize =
    room.roomSizeSqFt !== null
      ? `${room.roomSizeSqFt} sq ft`
      : "Size not listed";

  const bedType =
    room.bedType?.trim() || "Bed details not listed";

  const description =
    room.description?.trim() ||
    "Room description is not available yet.";

  return (
    <main className="travel-shell min-h-screen pb-24 pt-[102px]">
      <section className="mx-auto max-w-[1480px] px-4 sm:px-6">
        <Link
          href={`/hotels/${hotel.slug}#rooms`}
          className="inline-flex items-center gap-2 text-sm font-bold text-[#587595] transition hover:text-[#2f6df6]"
        >
          <ArrowLeft size={16} />
          Back to {hotel.name}
        </Link>

        <div className="mt-5 grid items-start gap-5 lg:grid-cols-[minmax(0,1.2fr)_minmax(360px,.8fr)]">
          {/* Left side */}
          <div className="space-y-5">
            {/* Room gallery */}
            <div className="glass-panel rounded-[28px] p-3">
              <PropertyGallery
                images={room.imageUrls ?? []}
                fallbackImage={
                  room.heroImageUrl ??
                  hotel.heroImageUrl
                }
                title={`${hotel.name} - ${room.name}`}
              >
                <div className="pointer-events-none absolute bottom-5 left-5 right-5 z-10 flex flex-wrap items-end justify-between gap-4 pr-0 text-white lg:pr-44">
                  <div>
                    <p className="text-xs font-bold uppercase tracking-[.15em] text-white/70">
                      Room experience
                    </p>

                    <h1 className="mt-1 text-3xl font-extrabold sm:text-5xl">
                      {room.name}
                    </h1>
                  </div>

                  {room.spatialAvailable ? (
                    <Link
                      href={`/rooms/${hotel.slug}/${room.slug}/spatial`}
                      className="pointer-events-auto blue-button flex items-center gap-2 rounded-xl px-5 py-3 text-sm font-bold"
                    >
                      <Box size={17} />
                      Enter 3D View
                    </Link>
                  ) : (
                    <span className="rounded-xl bg-black/30 px-5 py-3 text-sm font-bold backdrop-blur-md">
                      3D View not available
                    </span>
                  )}
                </div>
              </PropertyGallery>
            </div>

            {/* About */}
            <article className="glass-panel rounded-[28px] p-5 sm:p-6">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-bold uppercase tracking-[.16em] text-[#2f6df6]">
                    About this room
                  </p>

                  <h2 className="mt-2 text-2xl font-extrabold text-[#173d70]">
                    {room.name}
                  </h2>
                </div>

                <span className="rounded-full bg-[#eef5ff] px-4 py-2 text-xs font-bold text-[#416a9d]">
                  {formatRoomType(room.roomType)}
                </span>
              </div>

              <p className="mt-4 max-w-4xl text-sm leading-7 text-[#607894]">
                {description}
              </p>

              <div className="mt-5 grid gap-3 sm:grid-cols-3">
                <Highlight
                  icon={<Maximize2 size={18} />}
                  label="Room size"
                  value={roomSize}
                />

                <Highlight
                  icon={<Users size={18} />}
                  label="Capacity"
                  value={`Up to ${room.maxGuests} ${
                    room.maxGuests === 1
                      ? "guest"
                      : "guests"
                  }`}
                />

                <Highlight
                  icon={<BedDouble size={18} />}
                  label="Bed"
                  value={bedType}
                />
              </div>
            </article>

            {/* Amenities */}
            <article className="glass-panel rounded-[28px] p-5 sm:p-6">
              <p className="text-xs font-bold uppercase tracking-[.16em] text-[#2f6df6]">
                Room features
              </p>

              <h2 className="mt-2 text-2xl font-extrabold text-[#173d70]">
                Included room amenities
              </h2>

              <p className="mt-2 text-sm leading-6 text-[#7188a5]">
                Features currently listed for this room
                type by the hotel catalogue.
              </p>

              {room.amenities.length > 0 ? (
                <div className="mt-5 grid gap-3 sm:grid-cols-2">
                  {room.amenities.map((amenity) => (
                    <div
                      key={`${room.id}-${amenity.id}-${amenity.code}`}
                      className="flex items-center gap-3 rounded-2xl border border-[#e1ebf7] bg-[#f3f7fd] p-4 text-sm font-semibold text-[#56718f]"
                    >
                      <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-white text-[#2f6df6] shadow-sm">
                        <Check size={15} />
                      </span>

                      {amenity.name}
                    </div>
                  ))}
                </div>
              ) : (
                <div className="mt-5 rounded-2xl bg-[#eef5ff] p-5 text-sm text-[#607894]">
                  Room amenities have not been listed yet.
                </div>
              )}
            </article>
          </div>

          {/* Right side */}
          <aside className="glass-panel rounded-[28px] p-5 sm:p-6 lg:sticky lg:top-[105px]">
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-bold text-[#2f6df6]">
                {hotel.name}
              </p>

              {room.active ? (
                <span className="inline-flex items-center gap-1 rounded-full bg-[#eaf7ef] px-3 py-1 text-[10px] font-bold text-[#288455]">
                  <BadgeCheck size={12} />
                  Listed
                </span>
              ) : null}
            </div>

            <h2 className="mt-2 text-3xl font-extrabold text-[#173d70]">
              {room.name}
            </h2>

            <p className="mt-3 text-sm leading-6 text-[#607894]">
              {description}
            </p>

            <div className="mt-5 grid grid-cols-3 gap-2 text-center text-xs font-semibold text-[#55708f]">
              <Spec
                icon={<Maximize2 size={18} />}
                text={roomSize}
              />

              <Spec
                icon={<Users size={18} />}
                text={`Up to ${room.maxGuests}`}
              />

              <Spec
                icon={<BedDouble size={18} />}
                text={bedType}
              />
            </div>

            <div className="my-5 h-px bg-[#dfe9f6]" />

            <div className="flex items-end justify-between gap-4">
              <div>
                <p className="text-[10px] font-bold uppercase tracking-[.14em] text-[#7188a5]">
                  Room type
                </p>

                <p className="mt-1 text-sm font-bold text-[#173d70]">
                  {formatRoomType(room.roomType)}
                </p>
              </div>

              {room.spatialAvailable ? (
                <span className="inline-flex items-center gap-1.5 rounded-full bg-[#edf4ff] px-3 py-1.5 text-[10px] font-bold text-[#2f6df6]">
                  <Box size={12} />
                  3D available
                </span>
              ) : null}
            </div>

            <div className="mt-5 rounded-[20px] bg-gradient-to-br from-[#edf5ff] to-white p-4 ring-1 ring-[#dbe8f7]">
              <span className="text-[10px] font-bold uppercase tracking-[.13em] text-[#7188a5]">
                Catalogue rate
              </span>

              <p className="mt-1 text-3xl font-extrabold text-[#173d70]">
                {formatCurrency(
                  room.pricePerNight,
                  room.currency,
                )}{" "}
                <span className="text-sm font-medium text-[#7188a5]">
                  / night
                </span>
              </p>

              <p className="mt-1 text-[10px] leading-4 text-[#8296ad]">
                Date-specific pricing and availability will
                be confirmed by the booking service.
              </p>
            </div>

            <RoomStaySelector
              hotelId={hotel.slug}
              roomId={room.slug}
              nightlyPrice={room.pricePerNight}
              currency={room.currency}
              maxGuests={room.maxGuests}
            />

            <div className="mt-4 rounded-2xl bg-[#eef5ff] p-4">
              <div className="flex gap-3">
                <ShieldCheck
                  size={17}
                  className="mt-0.5 shrink-0 text-[#2f6df6]"
                />

                <div>
                  <p className="text-xs font-bold text-[#315276]">
                    Booking protection
                  </p>

                  <p className="mt-1 text-[11px] leading-5 text-[#7188a5]">
                    Final availability, rate conditions and
                    cancellation terms will be confirmed
                    before a real reservation is created.
                  </p>
                </div>
              </div>
            </div>
          </aside>
        </div>

        {/* Spatial information */}
        <section className="mt-5 rounded-[28px] bg-gradient-to-br from-[#174f9f] to-[#456ff1] p-6 text-white sm:p-7">
          <div className="grid gap-6 lg:grid-cols-[1fr_auto] lg:items-center">
            <div>
              <Sparkles
                size={24}
                className="text-[#d8e9ff]"
              />

              <h2 className="mt-4 text-2xl font-extrabold">
                Spatial room experience
              </h2>

              <p className="mt-3 max-w-3xl text-sm leading-6 text-white/75">
                {room.spatialAvailable
                  ? "An interactive spatial demonstration is available for this room type. Exact room representation will be enabled only when a verified spatial asset is attached."
                  : "A spatial experience has not been published for this room type. SpatialStay does not substitute a different room as an exact representation."}
              </p>

              <div className="mt-4 flex flex-wrap gap-3 text-xs font-semibold">
                <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-2">
                  <ShieldCheck size={14} />
                  Backend room details
                </span>

                {room.spatialAvailable ? (
                  <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-2">
                    <BadgeCheck size={14} />
                    Spatial demo available
                  </span>
                ) : null}
              </div>
            </div>

            {room.spatialAvailable ? (
              <Link
                href={`/rooms/${hotel.slug}/${room.slug}/spatial`}
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-white px-5 py-3 text-sm font-extrabold text-[#174f9f] transition hover:scale-[1.02]"
              >
                <Box size={17} />
                Open 3D experience
              </Link>
            ) : (
              <span className="rounded-xl bg-white/10 px-5 py-3 text-center text-sm font-bold text-white/75">
                3D View not available
              </span>
            )}
          </div>
        </section>
      </section>
    </main>
  );
}

function Spec({
  icon,
  text,
}: {
  icon: React.ReactNode;
  text: string;
}) {
  return (
    <div className="rounded-2xl bg-[#eef5ff] p-3">
      <span className="mx-auto mb-2 block w-fit text-[#2f6df6]">
        {icon}
      </span>

      <span className="block leading-4">
        {text}
      </span>
    </div>
  );
}

function Highlight({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="rounded-2xl border border-[#e1ebf7] bg-[#f3f7fd] p-4">
      <span className="grid h-9 w-9 place-items-center rounded-full bg-white text-[#2f6df6] shadow-sm">
        {icon}
      </span>

      <p className="mt-3 text-[9px] font-bold uppercase tracking-[.12em] text-[#8195ae]">
        {label}
      </p>

      <p className="mt-1 text-sm font-extrabold text-[#315276]">
        {value}
      </p>
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