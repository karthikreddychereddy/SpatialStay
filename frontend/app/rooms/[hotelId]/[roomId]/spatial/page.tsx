import Link from "next/link";
import { notFound } from "next/navigation";
import {
  ArrowLeft,
  BadgeCheck,
  Box,
  Info,
  Sparkles,
} from "lucide-react";

import { SpatialRoomViewer } from "@/components/three/SpatialRoomViewer";
import { getHotelBySlug } from "@/lib/api/hotels";
import { getRoomBySlug } from "@/lib/api/rooms";

interface SpatialRoomPageProps {
  params: Promise<{
    hotelId: string;
    roomId: string;
  }>;
}

export default async function SpatialRoomPage({
  params,
}: SpatialRoomPageProps) {
  const {
    hotelId: hotelSlug,
    roomId: roomSlug,
  } = await params;

  const hotel = await getHotelBySlug(hotelSlug);

  if (!hotel) {
    notFound();
  }

  const room = await getRoomBySlug(
    hotelSlug,
    roomSlug,
  );

  if (!room) {
    notFound();
  }

  /*
   * The spatial route should only be available for
   * room types that explicitly support it.
   */
  if (!room.spatialAvailable) {
    notFound();
  }

  const hasBalcony = room.amenities.some(
    (amenity) =>
      amenity.code === "BALCONY" ||
      amenity.name.toLowerCase() === "balcony",
  );

  return (
    <main className="min-h-screen bg-[#edf5ff] pb-20 pt-32 sm:pt-36">
      <section className="mx-auto max-w-[1500px] px-4 sm:px-6 lg:px-8">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Link
            href={`/rooms/${hotel.slug}/${room.slug}`}
            className="inline-flex items-center gap-2 text-sm font-semibold text-black/60 transition hover:text-[#174f9f]"
          >
            <ArrowLeft size={16} />
            Back to room details
          </Link>

          <span className="rounded-full border border-[#174f9f]/10 bg-white px-4 py-2 text-xs font-bold text-[#174f9f] shadow-sm sm:text-sm">
            {hotel.name}
          </span>
        </div>

        <div className="mt-7 max-w-4xl">
          <p className="text-xs font-bold uppercase tracking-[.2em] text-[#2f6df6]">
            Spatial room explorer
          </p>

          <h1 className="serif mt-2 text-4xl leading-[1.02] text-[#0b2857] sm:text-5xl lg:text-6xl">
            Explore {room.name} spatially
          </h1>

          <p className="mt-4 max-w-3xl text-sm leading-7 text-black/55 sm:text-base">
            Explore an interactive demonstration of the
            SpatialStay room-viewing experience. The
            environment below demonstrates navigation and
            feature-focused camera movement and is not an
            exact digital replica of this room.
          </p>
        </div>

        <div className="mt-7">
          <SpatialRoomViewer
            roomName={room.name}
            hasBalcony={hasBalcony}
          />
        </div>

        <div className="mt-6 grid gap-4 lg:grid-cols-3">
          <article className="rounded-[26px] bg-white p-5 shadow-sm">
            <Box
              className="text-[#2f6df6]"
              size={22}
            />

            <h2 className="mt-3 text-lg font-bold text-[#0b2857]">
              Feature-guided exploration
            </h2>

            <p className="mt-2 text-sm leading-6 text-black/55">
              Navigate between demonstration areas such as
              the bed, bathroom, window, workspace,
              storage and entrance using guided camera
              targets.
            </p>
          </article>

          <article className="rounded-[26px] bg-white p-5 shadow-sm">
            <BadgeCheck
              className="text-[#2f6df6]"
              size={22}
            />

            <h2 className="mt-3 text-lg font-bold text-[#0b2857]">
              Truthful spatial assets
            </h2>

            <p className="mt-2 text-sm leading-6 text-black/55">
              Production properties can attach verified
              GLB/glTF models or 360-degree captures.
              Until then, this demonstration is kept
              separate from exact room representation.
            </p>
          </article>

          <article className="rounded-[26px] bg-[#174f9f] p-5 text-white shadow-sm">
            <Sparkles
              className="text-[#d8e9ff]"
              size={22}
            />

            <h2 className="mt-3 text-lg font-bold">
              Spatial AI ready
            </h2>

            <p className="mt-2 text-sm leading-6 text-white/65">
              Stable feature identifiers prepare the
              viewer for future commands such as
              “show me the balcony” while keeping
              authoritative hotel and booking logic
              outside the AI.
            </p>
          </article>
        </div>

        <div className="mt-5 flex gap-3 rounded-[24px] border border-[#174f9f]/10 bg-[#eaf2ff] p-4 text-sm leading-6 text-black/60">
          <Info
            className="mt-0.5 shrink-0 text-[#174f9f]"
            size={18}
          />

          <p>
            This is a development spatial environment for
            validating interaction, camera movement and
            room navigation. Exact hotel-room
            representation will only be enabled when this
            room has a verified spatial asset.
          </p>
        </div>
      </section>
    </main>
  );
}