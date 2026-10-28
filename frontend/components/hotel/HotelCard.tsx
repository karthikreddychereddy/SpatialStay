"use client";

import type { ApiHotel } from "@/types/api-hotel";
import { motion } from "framer-motion";
import {
  ArrowUpRight,
  BadgeCheck,
  BedDouble,
  MapPin,
  Star,
} from "lucide-react";
import Link from "next/link";

interface HotelCardProps {
  hotel: ApiHotel;
  index: number;
  rank?: number;
}

export function HotelCard({
  hotel,
  index,
  rank,
}: HotelCardProps) {
  const location = formatHotelLocation(hotel);

  return (
    <motion.article
      initial={{ opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.2 }}
      transition={{
        delay: Math.min(index * 0.035, 0.2),
      }}
      whileHover={{ y: -6 }}
      className="group overflow-hidden rounded-[30px] bg-white shadow-[0_20px_60px_rgba(18,61,49,.10)]"
    >
      <div className="relative h-72 overflow-hidden">
        {hotel.heroImageUrl ? (
          <div
            className="absolute inset-0 bg-cover bg-center transition duration-700 group-hover:scale-105"
            style={{
              backgroundImage: `url(${hotel.heroImageUrl})`,
            }}
          />
        ) : (
          <div className="absolute inset-0 flex items-center justify-center bg-gradient-to-br from-[#eaf3ff] to-[#dce9fb]">
            <div className="text-center text-[#174f9f]">
              <BedDouble size={38} className="mx-auto" />
              <p className="mt-3 text-sm font-bold">
                Hotel image coming soon
              </p>
            </div>
          </div>
        )}

        <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-black/10" />

        {rank ? (
          <span className="absolute left-4 top-4 grid h-10 min-w-10 place-items-center rounded-full bg-[#174f9f] px-2 text-sm font-bold text-white shadow-lg">
            #{rank}
          </span>
        ) : null}

        {hotel.verified ? (
          <span className="absolute right-4 top-4 inline-flex items-center gap-1.5 rounded-full bg-white/90 px-3 py-1.5 text-xs font-semibold text-[#174f9f] backdrop-blur">
            <BadgeCheck size={14} />
            Verified
          </span>
        ) : null}

        <Link
          href={`/hotels/${hotel.slug}#rooms`}
          className="absolute bottom-4 right-4 flex items-center gap-2 rounded-full bg-[#d8e9ff] px-4 py-2 text-sm font-semibold text-[#0b2857]"
        >
          <BedDouble size={15} />
          Explore rooms
        </Link>
      </div>

      <div className="p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="serif text-2xl">
              {hotel.name}
            </h3>

            <p className="mt-1 flex items-start gap-1 text-sm text-black/55">
              <MapPin
                size={14}
                className="mt-0.5 shrink-0"
              />
              <span>{location}</span>
            </p>
          </div>

          {hotel.rating !== null ? (
            <div className="shrink-0 text-right">
              <span className="inline-flex items-center gap-1 rounded-full bg-[#edf4ff] px-2.5 py-1 text-sm font-semibold">
                <Star size={14} fill="currentColor" />
                {hotel.rating}
              </span>

              <div className="mt-1 text-[11px] text-black/40">
                {hotel.reviewCount.toLocaleString("en-IN")}{" "}
                {hotel.reviewCount === 1
                  ? "review"
                  : "reviews"}
              </div>
            </div>
          ) : (
            <span className="shrink-0 rounded-full bg-[#edf4ff] px-3 py-1 text-xs font-semibold text-[#587595]">
              New
            </span>
          )}
        </div>

        <p className="mt-4 line-clamp-3 min-h-[72px] text-sm leading-6 text-black/60">
          {hotel.description?.trim() ||
            "Hotel description is not available yet."}
        </p>

        <div className="mt-4 flex flex-wrap gap-2">
          <span className="rounded-full bg-[#f5f0e6] px-3 py-1 text-xs">
            {hotel.city}
          </span>

          {hotel.state ? (
            <span className="rounded-full bg-[#f5f0e6] px-3 py-1 text-xs">
              {hotel.state}
            </span>
          ) : null}

          <span className="rounded-full bg-[#f5f0e6] px-3 py-1 text-xs">
            {hotel.country}
          </span>
        </div>

        <div className="mt-5 flex items-end justify-between border-t border-black/5 pt-4">
          <div>
            <small className="text-black/45">
              starting from
            </small>

            <div className="text-xl font-bold">
              ₹
              {hotel.startingPrice.toLocaleString(
                "en-IN",
              )}{" "}
              <span className="text-xs font-normal text-black/45">
                / night
              </span>
            </div>
          </div>

          <Link
            aria-label={`Open ${hotel.name}`}
            href={`/hotels/${hotel.slug}`}
            className="grid h-11 w-11 place-items-center rounded-full bg-[#174f9f] text-white"
          >
            <ArrowUpRight size={18} />
          </Link>
        </div>
      </div>
    </motion.article>
  );
}

function formatHotelLocation(
  hotel: ApiHotel,
): string {
  return [hotel.city, hotel.state, hotel.country]
    .filter(Boolean)
    .join(", ");
}