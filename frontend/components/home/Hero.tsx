"use client";

import { AnimatePresence, motion } from "framer-motion";
import {
  Bot,
  CalendarDays,
  ChevronDown,
  MapPin,
  Minus,
  Plus,
  Search,
  ShieldCheck,
  Sparkles,
  Users,
} from "lucide-react";
import { useRouter } from "next/navigation";
import {
  FormEvent,
  ReactNode,
  useEffect,
  useRef,
  useState,
} from "react";

const scenes = [
  "https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=2200&q=90",
  "https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=2200&q=90",
  "https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?auto=format&fit=crop&w=2200&q=90",
];

type DestinationOption = {
  name: string;
  aliases: string[];
};

const destinations: DestinationOption[] = [
  {
    name: "Goa",
    aliases: [],
  },
  {
    name: "Hyderabad",
    aliases: ["Hyd"],
  },
  {
    name: "Visakhapatnam",
    aliases: ["Vizag"],
  },
];

export function Hero() {
  const router = useRouter();

  const [scene, setScene] = useState(0);

  const [destination, setDestination] =
    useState("");

  const [checkIn, setCheckIn] =
    useState("");

  const [checkOut, setCheckOut] =
    useState("");

  const [guests, setGuests] =
    useState<number | null>(null);

  const [guestPickerOpen, setGuestPickerOpen] =
    useState(false);

  const [destinationOpen, setDestinationOpen] =
    useState(false);

  const guestPickerRef =
    useRef<HTMLDivElement>(null);

  const destinationRef =
    useRef<HTMLDivElement>(null);

  useEffect(() => {
    const timer = window.setInterval(() => {
      setScene(
        (value) =>
          (value + 1) % scenes.length,
      );
    }, 5200);

    return () =>
      window.clearInterval(timer);
  }, []);

  useEffect(() => {
    function handleOutsideClick(
      event: MouseEvent,
    ) {
      const target = event.target as Node;

      if (
        guestPickerRef.current &&
        !guestPickerRef.current.contains(
          target,
        )
      ) {
        setGuestPickerOpen(false);
      }

      if (
        destinationRef.current &&
        !destinationRef.current.contains(
          target,
        )
      ) {
        setDestinationOpen(false);
      }
    }

    document.addEventListener(
      "mousedown",
      handleOutsideClick,
    );

    return () => {
      document.removeEventListener(
        "mousedown",
        handleOutsideClick,
      );
    };
  }, []);

  const today = getTodayDate();

  const minimumCheckOut =
    checkIn || today;

  const filteredDestinations =
    getMatchingDestinations(destination);

  function handleCheckInChange(
    value: string,
  ) {
    setCheckIn(value);

    if (
      checkOut &&
      value &&
      checkOut <= value
    ) {
      setCheckOut("");
    }
  }

  function handleDestinationSelect(
    option: DestinationOption,
  ) {
    setDestination(option.name);
    setDestinationOpen(false);
  }

  function handleSearch(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    const cleanDestination =
      normalizeDestination(destination);

    if (!cleanDestination) {
      setDestinationOpen(true);
      return;
    }

    const params = new URLSearchParams();

    params.set(
      "destination",
      cleanDestination,
    );

    if (checkIn) {
      params.set("checkIn", checkIn);
    }

    if (checkOut) {
      params.set("checkOut", checkOut);
    }

    if (guests !== null) {
      params.set(
        "guests",
        String(guests),
      );
    }

    router.push(
      `/hotels?${params.toString()}`,
    );
  }

  return (
    <section className="px-3 pt-[88px] sm:px-5 sm:pt-[96px]">
      <div className="relative mx-auto min-h-[690px] max-w-[1480px] overflow-hidden rounded-[28px] shadow-[0_30px_100px_rgba(21,61,122,.24)] sm:min-h-[720px] sm:rounded-[34px]">
        <AnimatePresence mode="sync">
          <motion.img
            key={scene}
            src={scenes[scene]}
            alt="Premium hotel destination"
            initial={{
              opacity: 0,
              scale: 1.08,
            }}
            animate={{
              opacity: 1,
              scale: 1.03,
            }}
            exit={{
              opacity: 0,
            }}
            transition={{
              duration: 1.4,
            }}
            className="absolute inset-0 h-full w-full object-cover"
          />
        </AnimatePresence>

        <div className="absolute inset-0 bg-gradient-to-r from-[#071d48]/80 via-[#163c74]/45 to-[#432451]/20" />

        <div className="absolute inset-0 bg-gradient-to-t from-[#071d48]/80 via-transparent to-transparent" />

        <div className="relative z-10 flex min-h-[690px] flex-col justify-end p-4 pb-6 sm:min-h-[720px] sm:p-8 lg:p-12">
          {/* Hero content */}
          <div className="max-w-3xl pb-5 text-white sm:pb-8">
            <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-white/25 bg-white/12 px-4 py-2 text-xs font-bold backdrop-blur-xl">
              <Sparkles size={15} />
              AI + verified spatial hotel discovery
            </div>

            <h1 className="max-w-3xl text-4xl font-extrabold leading-[1.02] tracking-[-.04em] sm:text-6xl lg:text-7xl">
              More than a stay. Your AI
              travel companion.
            </h1>

            <p className="mt-5 max-w-2xl text-sm leading-7 text-white/82 sm:text-lg">
              Discover hotels, compare real
              room details, explore spatial
              experiences and move from search
              to booking with one intelligent
              journey.
            </p>
          </div>

          {/* Search */}
          <form
            onSubmit={handleSearch}
            className="glass-panel rounded-[24px] p-3 sm:p-4"
          >
            <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-[1.35fr_1fr_1fr_.8fr_auto]">
              {/* Destination */}
              <div
                ref={destinationRef}
                className="relative"
              >
                <div className="flex min-h-16 items-center gap-3 rounded-2xl border border-[#d8e6f8] bg-white/85 px-4 transition focus-within:border-[#8cb7f8] focus-within:bg-white">
                  <span className="shrink-0 text-[#2f6df6]">
                    <MapPin size={18} />
                  </span>

                  <div className="min-w-0 flex-1">
                    <label
                      htmlFor="hero-destination"
                      className="block text-[10px] font-bold uppercase tracking-[.12em] text-[#7c91ad]"
                    >
                      Where to?
                    </label>

                    <input
                      id="hero-destination"
                      type="text"
                      value={destination}
                      autoComplete="off"
                      placeholder="Select destination"
                      onFocus={() =>
                        setDestinationOpen(
                          true,
                        )
                      }
                      onChange={(event) => {
                        setDestination(
                          event.target.value,
                        );

                        setDestinationOpen(
                          true,
                        );
                      }}
                      className="mt-1 w-full bg-transparent text-sm font-bold text-[#17375f] outline-none placeholder:font-semibold placeholder:text-[#93a5ba]"
                    />
                  </div>

                  <button
                    type="button"
                    onClick={() =>
                      setDestinationOpen(
                        (current) =>
                          !current,
                      )
                    }
                    className="grid h-8 w-8 shrink-0 place-items-center rounded-full text-[#8297b0] transition hover:bg-[#edf4ff]"
                    aria-label="Choose destination"
                  >
                    <ChevronDown size={15} />
                  </button>
                </div>

                <AnimatePresence>
                  {destinationOpen && (
                    <motion.div
                      initial={{
                        opacity: 0,
                        y: 7,
                      }}
                      animate={{
                        opacity: 1,
                        y: 0,
                      }}
                      exit={{
                        opacity: 0,
                        y: 5,
                      }}
                      transition={{
                        duration: 0.16,
                      }}
                      className="absolute left-0 right-0 top-[calc(100%+8px)] z-50 overflow-hidden rounded-[18px] border border-[#d9e6f5] bg-white p-2 shadow-[0_18px_45px_rgba(30,72,126,.18)]"
                    >
                      <p className="px-3 pb-2 pt-1 text-[9px] font-extrabold uppercase tracking-[.14em] text-[#91a2b6]">
                        Current destinations
                      </p>

                      {filteredDestinations.length >
                      0 ? (
                        filteredDestinations.map(
                          (option) => (
                            <button
                              key={option.name}
                              type="button"
                              onClick={() =>
                                handleDestinationSelect(
                                  option,
                                )
                              }
                              className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition hover:bg-[#edf4ff]"
                            >
                              <MapPin
                                size={14}
                                className="shrink-0 text-[#3475f6]"
                              />

                              <div className="min-w-0">
                                <p className="text-sm font-bold text-[#315474]">
                                  {option.name}
                                </p>

                                {option.aliases.length >
                                  0 && (
                                  <p className="mt-0.5 text-[10px] font-medium text-[#8b9db1]">
                                    Also known as{" "}
                                    {option.aliases.join(
                                      ", ",
                                    )}
                                  </p>
                                )}
                              </div>
                            </button>
                          ),
                        )
                      ) : (
                        <div className="px-3 py-3">
                          <p className="text-xs font-semibold text-[#8195ac]">
                            No current destination
                            matches “
                            {destination.trim()}”.
                          </p>
                        </div>
                      )}
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>

              {/* Check-in */}
              <DateField
                id="hero-check-in"
                label="Check-in"
                value={checkIn}
                min={today}
                placeholder="Add date"
                onChange={
                  handleCheckInChange
                }
              />

              {/* Check-out */}
              <DateField
                id="hero-check-out"
                label="Check-out"
                value={checkOut}
                min={minimumCheckOut}
                placeholder="Add date"
                disabled={!checkIn}
                onChange={setCheckOut}
              />

              {/* Guests */}
              <div
                ref={guestPickerRef}
                className="relative"
              >
                <button
                  type="button"
                  onClick={() =>
                    setGuestPickerOpen(
                      (current) =>
                        !current,
                    )
                  }
                  className="flex min-h-16 w-full items-center gap-3 rounded-2xl border border-[#d8e6f8] bg-white/85 px-4 text-left transition hover:bg-white"
                >
                  <span className="shrink-0 text-[#2f6df6]">
                    <Users size={18} />
                  </span>

                  <div className="min-w-0 flex-1">
                    <p className="text-[10px] font-bold uppercase tracking-[.12em] text-[#7c91ad]">
                      Guests
                    </p>

                    <p
                      className={`mt-1 text-sm font-bold ${
                        guests === null
                          ? "text-[#93a5ba]"
                          : "text-[#17375f]"
                      }`}
                    >
                      {guests === null
                        ? "Add guests"
                        : `${guests} ${
                            guests === 1
                              ? "Guest"
                              : "Guests"
                          }`}
                    </p>
                  </div>

                  <ChevronDown
                    size={15}
                    className="shrink-0 text-[#8297b0]"
                  />
                </button>

                <AnimatePresence>
                  {guestPickerOpen && (
                    <motion.div
                      initial={{
                        opacity: 0,
                        y: 7,
                      }}
                      animate={{
                        opacity: 1,
                        y: 0,
                      }}
                      exit={{
                        opacity: 0,
                        y: 5,
                      }}
                      transition={{
                        duration: 0.16,
                      }}
                      className="absolute right-0 top-[calc(100%+8px)] z-50 w-[230px] rounded-[18px] border border-[#d9e6f5] bg-white p-4 shadow-[0_18px_45px_rgba(30,72,126,.18)]"
                    >
                      <p className="text-[10px] font-extrabold uppercase tracking-[.14em] text-[#8ba0b8]">
                        Guests
                      </p>

                      <div className="mt-3 flex items-center justify-between">
                        <div>
                          <p className="text-sm font-extrabold text-[#294d72]">
                            Travellers
                          </p>

                          <p className="mt-0.5 text-[10px] text-[#8a9db2]">
                            Maximum 8 for search
                          </p>
                        </div>

                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            disabled={
                              guests ===
                                null ||
                              guests <= 1
                            }
                            onClick={() =>
                              setGuests(
                                (current) =>
                                  current ===
                                  null
                                    ? null
                                    : Math.max(
                                        1,
                                        current -
                                          1,
                                      ),
                              )
                            }
                            className="grid h-8 w-8 place-items-center rounded-full border border-[#d5e2f1] text-[#42688f] transition hover:bg-[#edf4ff] disabled:cursor-not-allowed disabled:opacity-35"
                            aria-label="Remove guest"
                          >
                            <Minus size={14} />
                          </button>

                          <span className="min-w-6 text-center text-sm font-extrabold text-[#17375f]">
                            {guests ?? "—"}
                          </span>

                          <button
                            type="button"
                            disabled={
                              guests !==
                                null &&
                              guests >= 8
                            }
                            onClick={() =>
                              setGuests(
                                (current) =>
                                  current ===
                                  null
                                    ? 1
                                    : Math.min(
                                        8,
                                        current +
                                          1,
                                      ),
                              )
                            }
                            className="grid h-8 w-8 place-items-center rounded-full border border-[#d5e2f1] text-[#42688f] transition hover:bg-[#edf4ff] disabled:cursor-not-allowed disabled:opacity-35"
                            aria-label="Add guest"
                          >
                            <Plus size={14} />
                          </button>
                        </div>
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>

              {/* Search */}
              <button
                type="submit"
                className="blue-button flex min-h-16 items-center justify-center gap-2 rounded-2xl px-7 font-bold"
              >
                <Search size={18} />
                Search
              </button>
            </div>
          </form>

          {/* Feature cards */}
          <div className="mt-4 grid gap-3 md:grid-cols-3">
            <FeatureCard
              icon={<Bot size={22} />}
              title="AI Agent"
              text="Chat or voice. Search, compare and explain rooms."
            />

            <FeatureCard
              icon={<Sparkles size={22} />}
              title="Spatial stays"
              text="Room Reality, Spatial Match and interactive 3D exploration."
            />

            <FeatureCard
              icon={
                <ShieldCheck size={22} />
              }
              title="Book with confidence"
              text="Clear room details, price breakdown and verified attributes."
            />
          </div>
        </div>

        {/* Scene controls */}
        <div className="absolute right-6 top-6 z-20 hidden gap-2 sm:flex">
          {scenes.map((_, index) => (
            <button
              key={index}
              type="button"
              onClick={() =>
                setScene(index)
              }
              className={`h-2 rounded-full transition-all ${
                scene === index
                  ? "w-8 bg-white"
                  : "w-2 bg-white/55"
              }`}
              aria-label={`Show hero scene ${
                index + 1
              }`}
            />
          ))}
        </div>
      </div>
    </section>
  );
}

type DateFieldProps = {
  id: string;
  label: string;
  value: string;
  placeholder: string;
  min: string;
  disabled?: boolean;
  onChange: (value: string) => void;
};

function DateField({
  id,
  label,
  value,
  placeholder,
  min,
  disabled = false,
  onChange,
}: DateFieldProps) {
  return (
    <label
      htmlFor={id}
      className={`flex min-h-16 items-center gap-3 rounded-2xl border border-[#d8e6f8] bg-white/85 px-4 transition ${
        disabled
          ? "cursor-not-allowed opacity-65"
          : "cursor-pointer hover:bg-white focus-within:border-[#8cb7f8]"
      }`}
    >
      <span className="shrink-0 text-[#2f6df6]">
        <CalendarDays size={18} />
      </span>

      <div className="min-w-0 flex-1">
        <span className="block text-[10px] font-bold uppercase tracking-[.12em] text-[#7c91ad]">
          {label}
        </span>

        <div className="relative mt-1">
          {!value && (
            <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center text-sm font-semibold text-[#93a5ba]">
              {placeholder}
            </span>
          )}

          <input
            id={id}
            type="date"
            value={value}
            min={min}
            disabled={disabled}
            onChange={(event) =>
              onChange(
                event.target.value,
              )
            }
            className={`w-full bg-transparent text-sm font-bold text-[#17375f] outline-none ${
              value
                ? "opacity-100"
                : "text-transparent opacity-0"
            }`}
          />
        </div>
      </div>
    </label>
  );
}

type FeatureCardProps = {
  icon: ReactNode;
  title: string;
  text: string;
};

function FeatureCard({
  icon,
  title,
  text,
}: FeatureCardProps) {
  return (
    <div className="glass-dark flex items-center gap-4 rounded-2xl p-4 text-white">
      <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-white/14 text-[#b9d6ff]">
        {icon}
      </span>

      <div>
        <p className="font-bold">
          {title}
        </p>

        <p className="mt-1 text-xs leading-5 text-white/68">
          {text}
        </p>
      </div>
    </div>
  );
}

function getMatchingDestinations(
  input: string,
) {
  const query = input
    .trim()
    .toLowerCase();

  if (!query) {
    return destinations;
  }

  return destinations.filter(
    (destination) => {
      const matchesName =
        destination.name
          .toLowerCase()
          .includes(query);

      const matchesAlias =
        destination.aliases.some(
          (alias) =>
            alias
              .toLowerCase()
              .includes(query),
        );

      return matchesName || matchesAlias;
    },
  );
}

function normalizeDestination(
  value: string,
) {
  const query = value
    .trim()
    .toLowerCase();

  if (!query) {
    return "";
  }

  const exactDestination =
    destinations.find(
      (destination) =>
        destination.name.toLowerCase() ===
          query ||
        destination.aliases.some(
          (alias) =>
            alias.toLowerCase() ===
            query,
        ),
    );

  return (
    exactDestination?.name ??
    value.trim()
  );
}

function getTodayDate() {
  const now = new Date();

  const year = now.getFullYear();

  const month = String(
    now.getMonth() + 1,
  ).padStart(2, "0");

  const day = String(
    now.getDate(),
  ).padStart(2, "0");

  return `${year}-${month}-${day}`;
}