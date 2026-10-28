import { DestinationDiscovery } from "@/components/home/DestinationDiscovery";
import { Hero } from "@/components/home/Hero";
import { HowItWorks } from "@/components/home/HowItWorks";
import { SpatialExperience } from "@/components/home/SpatialExperience";
import { Compass, Scale, ShieldCheck, Sparkles, Building2, ArrowRight } from "lucide-react";
import Link from "next/link";

export default function Home() {
  return (
    <main className="travel-shell overflow-hidden pb-24">
      
      <Hero />
      <DestinationDiscovery />
      <SpatialExperience />
      <HowItWorks />

      <section className="px-4 py-10 sm:px-6">
        <div className="mx-auto grid max-w-[1480px] gap-8 overflow-hidden rounded-[30px] border border-[#dce5f2] bg-white p-7 shadow-[0_20px_70px_rgba(29,62,108,.08)] md:grid-cols-[1fr_auto] md:items-center lg:p-10">
          <div className="flex gap-5"><span className="grid h-14 w-14 shrink-0 place-items-center rounded-2xl bg-[#edf3ff] text-[#2463eb]"><Building2 /></span><div><p className="text-sm font-bold uppercase tracking-[.16em] text-[#5071a5]">For hotel owners</p><h2 className="mt-2 text-3xl font-bold text-[#15365f]">Own a property? Grow with SpatialStay.</h2><p className="mt-3 max-w-2xl leading-7 text-[#667b98]">Submit your hotel profile, property photos, rooms, pricing, amenities and policies for review. After approval, manage the property through Hotel Operations.</p></div></div>
          <Link href="/hotel-partner" className="rounded-xl bg-[#2463eb] px-6 py-3.5 text-center font-bold text-white">List your property <ArrowRight className="ml-2 inline" size={18}/></Link>
        </div>
      </section>

      <section className="px-4 py-8 sm:px-6">
        <div className="glass-dark mx-auto grid max-w-[1480px] gap-5 rounded-[28px] p-5 text-white sm:grid-cols-2 lg:grid-cols-4 lg:p-7">
          <JourneyItem icon={<Compass />} title="Discover" text="Start with destinations that fit your trip" />
          <JourneyItem icon={<Scale />} title="Compare" text="Rooms, reality and review intelligence" />
          <JourneyItem icon={<Sparkles />} title="Explore" text="Interactive spatial room experience" />
          <JourneyItem icon={<ShieldCheck />} title="Book" text="Clear summary and confidence checks" />
        </div>
      </section>

    </main>
  );
}

function JourneyItem({ icon, title, text }: { icon: React.ReactNode; title: string; text: string }) {
  return (
    <div className="flex items-center gap-4">
      <span className="grid h-12 w-12 shrink-0 place-items-center rounded-full border border-white/20 bg-white/10 text-[#a9cbff]">{icon}</span>
      <div><p className="font-bold">{title}</p><p className="mt-1 text-xs leading-5 text-white/60">{text}</p></div>
    </div>
  );
}
