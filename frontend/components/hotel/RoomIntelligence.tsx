import {
  BadgeCheck,
  CircleHelp,
  ShieldCheck,
  Sparkles,
  Star,
  TriangleAlert,
} from "lucide-react";
import { Room } from "@/types/room";

type Props = {
  room: Room;
};

export function RoomIntelligence({ room }: Props) {
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <section className="rounded-[30px] bg-gradient-to-br from-[#174f9f] to-[#466ff1] p-7 text-white">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.2em] text-[#d8e9ff]">
              Spatial match
            </p>
            <h2 className="mt-2 text-3xl font-extrabold">Why this room fits</h2>
          </div>
          <div className="grid h-20 w-20 place-items-center rounded-full border border-white/15 bg-white/10">
            <div className="text-center">
              <strong className="text-2xl">{room.matchScore}%</strong>
              <span className="block text-[10px] text-white/55">MATCH</span>
            </div>
          </div>
        </div>

        <div className="mt-6 space-y-3">
          {room.matchReasons.map((reason) => (
            <div
              key={reason}
              className="flex items-center gap-3 rounded-2xl bg-white/[0.07] p-4 text-sm"
            >
              <Sparkles size={16} className="shrink-0 text-[#d8e9ff]" />
              {reason}
            </div>
          ))}
        </div>

        <p className="mt-5 text-xs leading-5 text-white/45">
          Demo score uses explicit room attributes. Production scoring will use the
          traveler&apos;s selected requirements and verified inventory data.
        </p>
      </section>

      <section className="rounded-[30px] border border-[#dbe8f7] bg-white/90 p-7">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-[#2f6df6]">
          Room reality
        </p>
        <h2 className="mt-2 text-3xl font-extrabold">Know what is actually included</h2>

        <div className="mt-6 space-y-5 text-sm">
          <RealityRow
            icon={<BadgeCheck size={17} />}
            title="Verified"
            items={room.reality.verified}
          />
          <RealityRow
            icon={<ShieldCheck size={17} />}
            title="Guaranteed with this room"
            items={room.reality.guaranteed}
          />
          <RealityRow
            icon={<CircleHelp size={17} />}
            title="Available on request"
            items={room.reality.onRequest}
          />
          <RealityRow
            icon={<TriangleAlert size={17} />}
            title="Not guaranteed"
            items={room.reality.notGuaranteed}
          />
        </div>
      </section>
    </div>
  );
}

type RealityRowProps = {
  icon: React.ReactNode;
  title: string;
  items: string[];
};

function RealityRow({ icon, title, items }: RealityRowProps) {
  return (
    <div className="border-b border-black/5 pb-4 last:border-0 last:pb-0">
      <div className="flex items-center gap-2 font-semibold text-[#123d31]">
        {icon}
        {title}
      </div>
      <p className="mt-2 leading-6 text-black/55">{items.join(" · ")}</p>
    </div>
  );
}

export function ReviewIntelligence({ room }: Props) {
  return (
    <section className="rounded-[30px] bg-[#efe8da] p-7 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-[#8a5d1d]">
            Review intelligence
          </p>
          <h2 className="serif mt-2 text-3xl sm:text-4xl">
            What guests repeatedly mention
          </h2>
        </div>
        <div className="flex items-center gap-2 rounded-full bg-white/70 px-4 py-2 text-sm font-bold">
          <Star size={15} fill="currentColor" />
          {room.rating} · {room.reviews.toLocaleString("en-IN")} room reviews
        </div>
      </div>

      <p className="mt-5 max-w-3xl leading-7 text-black/60">{room.reviewSummary}</p>

      <div className="mt-7 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {room.reviewInsights.map((insight) => (
          <div key={insight.label} className="rounded-2xl bg-white/70 p-4">
            <div className="flex items-center justify-between gap-3">
              <span className="text-sm font-semibold">{insight.label}</span>
              <strong>{insight.score}%</strong>
            </div>
            <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-black/10">
              <div
                className="h-full rounded-full bg-gradient-to-br from-[#174f9f] to-[#466ff1]"
                style={{ width: `${insight.score}%` }}
              />
            </div>
            <span className="mt-2 block text-xs capitalize text-black/45">
              {insight.sentiment}
            </span>
          </div>
        ))}
      </div>
    </section>
  );
}
