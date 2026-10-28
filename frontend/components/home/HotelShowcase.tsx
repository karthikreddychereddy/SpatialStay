"use client";

import { HotelCard } from "@/components/hotel/HotelCard";
import { getHotels } from "@/lib/api/hotels";
import type { ApiHotel } from "@/types/api-hotel";
import {
  ArrowLeft,
  ArrowRight,
  MapPin,
} from "lucide-react";
import {
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

export function HotelShowcase() {
  const rail = useRef<HTMLDivElement>(null);

  const [hotels, setHotels] = useState<ApiHotel[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(
    null,
  );

  useEffect(() => {
    let active = true;

    async function loadHotels() {
      try {
        setLoading(true);
        setError(null);

        const data = await getHotels();

        if (active) {
          setHotels(data);
        }
      } catch {
        if (active) {
          setError(
            "We couldn't load the hotel collection right now.",
          );
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadHotels();

    return () => {
      active = false;
    };
  }, []);

  const ranked = useMemo(() => {
    return [...hotels]
      .filter((hotel) => hotel.status === "ACTIVE")
      .sort((a, b) => {
        const ratingA = a.rating ?? 0;
        const ratingB = b.rating ?? 0;

        if (ratingB !== ratingA) {
          return ratingB - ratingA;
        }

        return b.reviewCount - a.reviewCount;
      })
      .slice(0, 10);
  }, [hotels]);

  function move(direction: number) {
    rail.current?.scrollBy({
      left: direction * 420,
      behavior: "smooth",
    });
  }

  return (
    <section
      id="discover"
      className="overflow-hidden py-24"
    >
      <div className="mx-auto max-w-7xl px-6">
        <div className="mb-8 flex flex-col justify-between gap-6 lg:flex-row lg:items-end">
          <div>
            <span className="text-xs font-bold uppercase tracking-[.24em] text-[#2f6df6]">
              Top rated stays
            </span>

            <h2 className="serif mt-3 max-w-3xl text-4xl md:text-6xl">
              Discover highly rated stays.
            </h2>

            <p className="mt-4 max-w-2xl text-black/55">
              Explore hotels currently available in our
              catalogue. Real-time availability and
              distance-based discovery will be connected
              through the booking and search services.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-2 rounded-full border border-black/10 bg-white px-4 py-3 text-sm">
              <MapPin size={16} />
              <span>Explore destinations</span>
            </div>

            <button
              type="button"
              onClick={() => move(-1)}
              aria-label="Previous hotels"
              className="grid h-11 w-11 place-items-center rounded-full border border-black/10 bg-white"
            >
              <ArrowLeft size={18} />
            </button>

            <button
              type="button"
              onClick={() => move(1)}
              aria-label="Next hotels"
              className="grid h-11 w-11 place-items-center rounded-full bg-[#174f9f] text-white"
            >
              <ArrowRight size={18} />
            </button>
          </div>
        </div>
      </div>

      {loading ? (
        <HotelShowcaseSkeleton />
      ) : error ? (
        <div className="mx-auto max-w-7xl px-6">
          <div className="rounded-[28px] border border-[#dfe9f6] bg-white p-8 text-center shadow-sm">
            <p className="font-bold text-[#17345d]">
              Hotels are temporarily unavailable
            </p>

            <p className="mt-2 text-sm text-[#607894]">
              {error}
            </p>
          </div>
        </div>
      ) : ranked.length === 0 ? (
        <div className="mx-auto max-w-7xl px-6">
          <div className="rounded-[28px] border border-[#dfe9f6] bg-white p-8 text-center">
            <p className="font-bold text-[#17345d]">
              No hotels are available yet.
            </p>

            <p className="mt-2 text-sm text-[#607894]">
              New stays will appear here when they are
              added to the catalogue.
            </p>
          </div>
        </div>
      ) : (
        <div
          ref={rail}
          className="hide-scroll flex snap-x snap-mandatory gap-5 overflow-x-auto px-[max(1.5rem,calc((100vw-80rem)/2+1.5rem))] pb-8"
        >
          {ranked.map((hotel, index) => (
            <div
              key={hotel.id}
              className="w-[86vw] max-w-[390px] shrink-0 snap-start"
            >
              <HotelCard
                hotel={hotel}
                index={index}
                rank={index + 1}
              />
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function HotelShowcaseSkeleton() {
  return (
    <div className="flex gap-5 overflow-hidden px-[max(1.5rem,calc((100vw-80rem)/2+1.5rem))] pb-8">
      {[0, 1, 2].map((item) => (
        <div
          key={item}
          className="w-[86vw] max-w-[390px] shrink-0 overflow-hidden rounded-[30px] bg-white shadow-[0_20px_60px_rgba(18,61,49,.08)]"
        >
          <div className="h-72 animate-pulse bg-[#eaf0f7]" />

          <div className="space-y-4 p-5">
            <div className="h-7 w-2/3 animate-pulse rounded-lg bg-[#eaf0f7]" />

            <div className="h-4 w-1/2 animate-pulse rounded-lg bg-[#eaf0f7]" />

            <div className="space-y-2">
              <div className="h-4 animate-pulse rounded-lg bg-[#eaf0f7]" />
              <div className="h-4 w-5/6 animate-pulse rounded-lg bg-[#eaf0f7]" />
            </div>

            <div className="h-10 animate-pulse rounded-xl bg-[#eaf0f7]" />
          </div>
        </div>
      ))}
    </div>
  );
}