"use client";

import { ArrowRight, CalendarDays, MapPin, Snowflake, Sparkles } from "lucide-react";
import Link from "next/link";

const destinations = [
  { city: "Goa", note: "Best for winter", image: "https://images.unsplash.com/photo-1512343879784-a960bf40e7f2?auto=format&fit=crop&w=900&q=85" },
  { city: "Hyderabad", note: "Best for all seasons", image: "https://images.unsplash.com/photo-1572431447238-425af66a273b?auto=format&fit=crop&w=900&q=85" },
  { city: "Vizag", note: "Best for winter", image: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=900&q=85" },
  { city: "Kerala", note: "Best for nature", image: "https://images.unsplash.com/photo-1602216056096-3b40cc0c9944?auto=format&fit=crop&w=900&q=85" },
];

const seasonal = [
  { title: "Winter sun escapes", place: "Goa · Kerala · Pondicherry", detail: "Warm coastlines, outdoor dining and calmer evenings.", icon: Snowflake },
  { title: "Cool hill weekends", place: "Coorg · Ooty · Munnar", detail: "Mist, scenic stays and slower weekend itineraries.", icon: Sparkles },
  { title: "City break season", place: "Hyderabad · Bengaluru", detail: "Food, culture, business stays and short escapes.", icon: CalendarDays },
];

export function DestinationDiscovery() {
  return (
    <section className="px-4 py-7 sm:px-6 sm:py-10">
      <div className="mx-auto max-w-[1480px]">
        <div className="mb-5 flex items-end justify-between gap-4">
          <div>
            <p className="text-xs font-extrabold uppercase tracking-[.18em] text-[#5f83b6]">Explore by destination</p>
            <h2 className="mt-2 text-2xl font-extrabold tracking-[-.03em] text-[#123a70] sm:text-3xl">Top destinations for you</h2>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-[#66809f]">Start with a city, then let SpatialStay find the right hotels and rooms inside it.</p>
          </div>
          <Link href="/hotels" className="hidden items-center gap-2 text-sm font-bold text-[#2f6df6] sm:flex">View destinations <ArrowRight size={16} /></Link>
        </div>

        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          {destinations.map((destination) => (
            <Link key={destination.city} href={`/hotels?destination=${encodeURIComponent(destination.city)}`} className="group soft-card overflow-hidden rounded-[22px] p-2.5">
              <div className="relative aspect-[1.45/1] overflow-hidden rounded-[17px]">
                <img src={destination.image} alt={destination.city} className="h-full w-full object-cover transition duration-700 group-hover:scale-110" />
                <div className="absolute inset-0 bg-gradient-to-t from-[#092d66]/55 via-transparent to-transparent" />
                <span className="absolute left-3 top-3 grid h-8 w-8 place-items-center rounded-full border border-white/50 bg-white/25 text-white backdrop-blur-lg"><MapPin size={15} /></span>
              </div>
              <div className="px-1 pb-1 pt-3">
                <p className="font-extrabold text-[#173d70]">{destination.city}</p>
                <p className="mt-1 text-xs font-medium text-[#738aa6]">{destination.note}</p>
              </div>
            </Link>
          ))}
        </div>

        <div className="mt-6 overflow-hidden rounded-[28px] border border-white/70 bg-gradient-to-r from-[#102e79] via-[#3157bb] to-[#61409b] p-[1px] shadow-[0_22px_70px_rgba(44,83,173,.20)]">
          <div className="rounded-[27px] bg-[linear-gradient(110deg,rgba(15,45,119,.97),rgba(61,70,172,.92),rgba(92,55,147,.92))] p-5 text-white sm:p-7">
            <div className="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
              <div className="max-w-xl">
                <span className="inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-3 py-1.5 text-[11px] font-bold uppercase tracking-[.15em]"><Snowflake size={14} /> Seasonal inspiration</span>
                <h3 className="mt-3 text-2xl font-extrabold sm:text-3xl">Where the season feels right</h3>
                <p className="mt-2 text-sm leading-6 text-white/70">Destination ideas change with the season so the homepage stays useful without duplicating the hotel rankings.</p>
              </div>
              <div className="grid flex-1 gap-3 md:grid-cols-3 lg:max-w-3xl">
                {seasonal.map(({ title, place, detail, icon: Icon }) => (
                  <article key={title} className="rounded-[20px] border border-white/15 bg-white/10 p-4 backdrop-blur-xl">
                    <Icon size={19} className="text-[#cde1ff]" />
                    <h4 className="mt-3 font-bold">{title}</h4>
                    <p className="mt-1 text-xs font-semibold text-[#cde1ff]">{place}</p>
                    <p className="mt-2 text-xs leading-5 text-white/60">{detail}</p>
                  </article>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
