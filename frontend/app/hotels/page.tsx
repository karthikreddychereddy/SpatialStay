"use client";
import {
  getHotels,
  type HotelSort,
} from "@/lib/api/hotels";
import type { ApiHotel } from "@/types/api-hotel";
import {
  ArrowDownUp,
  Check,
  ChevronDown,
  Filter,
  Heart,
  MapPin,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Star,
  X,
} from "lucide-react";
import Link from "next/link";
import {
  usePathname,
  useRouter,
  useSearchParams,
} from "next/navigation";
import {
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useState,
} from "react";
const sortOptions: {
  value: HotelSort;
  label: string;
}[] = [
  {
    value: "recommended",
    label: "Recommended",
  },
  {
    value: "rating_desc",
    label: "Top Rated",
  },
  {
    value: "reviews_desc",
    label: "Most Reviewed",
  },
  {
    value: "price_asc",
    label: "Price: Low to High",
  },
  {
    value: "price_desc",
    label: "Price: High to Low",
  },
  {
    value: "newest",
    label: "Newest",
  },
];
const destinations = [
  {
    name: "Goa",
    aliases: [] as string[],
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
const MAX_PRICE_LIMIT = 15000;
export default function HotelsPage() {
  return (
    <Suspense fallback={<HotelsPageLoading />}>
      <HotelsPageContent />
    </Suspense>
  );
}
function HotelsPageContent() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  /*
   * Dates and guests are preserved from the Hero.
   * Step 5 will use them for real availability.
   */
  const destination =
    searchParams.get("destination") ?? "";
  const checkIn =
    searchParams.get("checkIn") ?? "";
  const checkOut =
    searchParams.get("checkOut") ?? "";
  const guests =
    searchParams.get("guests") ?? "";
  const [hotels, setHotels] =
    useState<ApiHotel[]>([]);
  const [hotelsLoading, setHotelsLoading] =
    useState(true);
  const [hotelsError, setHotelsError] =
    useState<string | null>(null);
  const [searchText, setSearchText] =
    useState(
      searchParams.get("query") ?? "",
    );
  const [searchDraft, setSearchDraft] =
    useState(
      searchParams.get("query") ?? "",
    );
  const [
    destinationDraft,
    setDestinationDraft,
  ] = useState(destination);
  const [minPrice, setMinPrice] =
    useState(
      readNumberParam(
        searchParams.get("minPrice"),
        0,
      ),
    );
  const [maxPrice, setMaxPrice] =
    useState(
      readNumberParam(
        searchParams.get("maxPrice"),
        MAX_PRICE_LIMIT,
      ),
    );
  const [rating, setRating] =
    useState(
      readNumberParam(
        searchParams.get("minRating"),
        0,
      ),
    );
  const [verifiedOnly, setVerifiedOnly] =
    useState(
      searchParams.get("verified") ===
        "true",
    );
  const [sortBy, setSortBy] =
    useState<HotelSort>(
      readSort(
        searchParams.get("sort"),
      ),
    );
  const [filtersOpen, setFiltersOpen] =
    useState(false);
  const [sortOpen, setSortOpen] =
    useState(false);
  const [
    destinationOpen,
    setDestinationOpen,
  ] = useState(false);
  /*
   * Updates only the parameters that changed.
   *
   * Existing checkIn/checkOut/guests parameters
   * remain untouched.
   */
  const updateUrl = useCallback(
    (
      changes: Record<
        string,
        string | null
      >,
    ) => {
      const params =
        new URLSearchParams(
          searchParams.toString(),
        );
      Object.entries(changes).forEach(
        ([key, value]) => {
          if (
            value === null ||
            value === ""
          ) {
            params.delete(key);
          } else {
            params.set(key, value);
          }
        },
      );
      const queryString =
        params.toString();
      router.replace(
        queryString
          ? `${pathname}?${queryString}`
          : pathname,
        {
          scroll: false,
        },
      );
    },
    [
      pathname,
      router,
      searchParams,
    ],
  );
  /*
   * Live hotel/location search.
   *
   * We wait briefly after typing stops instead of
   * sending one backend request per keystroke.
   */
  useEffect(() => {
    const timer =
      window.setTimeout(() => {
        const cleanSearch =
          searchDraft.trim();
        if (
          cleanSearch === searchText
        ) {
          return;
        }
        setSearchText(cleanSearch);
        updateUrl({
          query:
            cleanSearch || null,
        });
      }, 350);
    return () => {
      window.clearTimeout(timer);
    };
  }, [
    searchDraft,
    searchText,
    updateUrl,
  ]);
  /*
   * Backend-authoritative Step-4 hotel search.
   */
  useEffect(() => {
    let cancelled = false;
    const timer =
      window.setTimeout(
        async () => {
          try {
            setHotelsLoading(true);
            setHotelsError(null);
            const parsedGuests = Number(guests);

            const hasStaySearch =
              checkIn !== "" &&
              checkOut !== "" &&
              Number.isInteger(parsedGuests) &&
              parsedGuests >= 1;

            const data =
              await getHotels({
                destination:
                  destination ||
                  undefined,
                query:
                  searchText ||
                  undefined,
                minPrice:
                  minPrice > 0
                    ? minPrice
                    : undefined,
                maxPrice:
                  maxPrice <
                  MAX_PRICE_LIMIT
                    ? maxPrice
                    : undefined,
                minRating:
                  rating > 0
                    ? rating
                    : undefined,
                verified:
                  verifiedOnly
                    ? true
                    : undefined,
                sort: sortBy,

                checkIn:
                  hasStaySearch
                    ? checkIn
                    : undefined,

                checkOut:
                  hasStaySearch
                    ? checkOut
                    : undefined,

                guests:
                  hasStaySearch
                    ? parsedGuests
                    : undefined,
              });
            if (!cancelled) {
              setHotels(data);
            }
          } catch (error) {
            if (!cancelled) {
              setHotelsError(
                error instanceof Error
                  ? error.message
                  : "Unable to load hotels.",
              );
            }
          } finally {
            if (!cancelled) {
              setHotelsLoading(false);
            }
          }
        },
        180,
      );
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [
    checkIn,
    checkOut,
    destination,
    guests,
    maxPrice,
    minPrice,
    rating,
    searchText,
    sortBy,
    verifiedOnly,
  ]);
  /*
   * Keep destination input synchronized when the
   * URL destination changes from another control.
   */
  useEffect(() => {
    setDestinationDraft(destination);
  }, [destination]);
  const filteredDestinations =
    useMemo(() => {
      const query =
        destinationDraft
          .trim()
          .toLowerCase();
      if (!query) {
        return destinations;
      }
      return destinations.filter(
        (option) =>
          option.name
            .toLowerCase()
            .includes(query) ||
          option.aliases.some(
            (alias) =>
              alias
                .toLowerCase()
                .includes(query),
          ),
      );
    }, [destinationDraft]);
  const activeFilterCount =
    (minPrice > 0 ? 1 : 0) +
    (maxPrice <
    MAX_PRICE_LIMIT
      ? 1
      : 0) +
    (rating > 0 ? 1 : 0) +
    (verifiedOnly ? 1 : 0);
  function applyDestination(
    value: string,
  ) {
    const normalized =
      normalizeDestination(value);
    if (!normalized) {
      return;
    }
    setDestinationDraft(normalized);
    setDestinationOpen(false);
    updateUrl({
      destination: normalized,
    });
  }
  function clearDestination() {
    setDestinationDraft("");
    setDestinationOpen(false);
    updateUrl({
      destination: null,
    });
  }
  function clearFilters() {
    setMinPrice(0);
    setMaxPrice(MAX_PRICE_LIMIT);
    setRating(0);
    setVerifiedOnly(false);
    updateUrl({
      minPrice: null,
      maxPrice: null,
      minRating: null,
      verified: null,
    });
  }
  function handleSort(
    value: HotelSort,
  ) {
    setSortBy(value);
    setSortOpen(false);
    updateUrl({
      sort:
        value === "recommended"
          ? null
          : value,
    });
  }
  return (
    <main className="travel-shell min-h-screen pb-24 pt-[96px]">
      <section className="mx-auto max-w-[1480px] px-3 sm:px-6">
        {/* Hero */}
        <div className="relative overflow-hidden rounded-[28px] px-5 py-6 text-white sm:px-8 sm:py-7">
          <img
            src="https\://images.unsplash.com/photo-1512343879784-a960bf40e7f2?auto=format&fit=crop&w=1900&q=88"
            alt="Destination"
            className="absolute inset-0 h-full w-full object-cover"
          />
          <div className="absolute inset-0 bg-gradient-to-r from-[#082c68]/95 via-[#1d5db0]/68 to-[#315fc0]/30" />
          <div className="relative flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
            <div className="min-w-0">
              <p className="text-xs font-bold uppercase tracking-[.17em] text-[#c9dfff]">
                Hotels · smart discovery
              </p>
              <h1 className="mt-2 text-3xl font-extrabold sm:text-5xl">
                {destination
                  ? `Hotels in ${destination}`
                  : "Discover your next stay"}
              </h1>
              <p className="mt-2 max-w-2xl text-sm text-white/78">
                Search and compare stays
                using SpatialStay catalogue
                filters and transparent
                starting prices.
              </p>
            </div>
            {/* Selected search context */}
            <div className="flex max-w-full flex-wrap items-center gap-2 sm:max-w-[55%] sm:justify-end">
              <span className="flex max-w-full items-center gap-1.5 rounded-full border border-white/25 bg-white/14 px-3 py-2 text-[11px] font-bold text-white/90 backdrop-blur-xl">
                <span className="inline-block h-1.5 w-1.5 shrink-0 rounded-full bg-emerald-300" />
                <MapPin
                  size={12}
                  className="shrink-0"
                />
                <span className="truncate">
                  {destination
                    ? `Showing ${destination}`
                    : "All destinations"}
                </span>
              </span>
              {checkIn &&
                checkOut && (
                  <span className="rounded-full border border-white/25 bg-white/14 px-3 py-2 text-[11px] font-bold text-white/90 backdrop-blur-xl">
                    {formatDate(
                      checkIn,
                    )}{" "}
                    →{" "}
                    {formatDate(
                      checkOut,
                    )}
                  </span>
                )}
              {guests && (
                <span className="rounded-full border border-white/25 bg-white/14 px-3 py-2 text-[11px] font-bold text-white/90 backdrop-blur-xl">
                  {guests}{" "}
                  {guests === "1"
                    ? "Guest"
                    : "Guests"}
                </span>
              )}
            </div>
          </div>
        </div>
        {/* Search / destination / sort toolbar */}
        <div className="results-toolbar relative z-30 mt-4 rounded-[24px] p-3 sm:p-4">
          <div className="flex flex-col gap-2 lg:flex-row">
            {/* Automatic hotel/location search */}
            <div className="flex min-h-12 min-w-0 flex-1 items-center gap-2 rounded-[17px] border border-white bg-white/92 px-3 shadow-[0_8px_24px_rgba(47,83,135,.08)]">
              <Search
                size={16}
                className="shrink-0 text-[#6886aa]"
              />
              <input
                value={searchDraft}
                onChange={(event) =>
                  setSearchDraft(
                    event.target.value,
                  )
                }
                placeholder="Search hotel name or location"
                className="min-w-0 flex-1 bg-transparent py-3 text-xs font-bold text-[#173d70] outline-none placeholder:text-[#93a5ba]"
              />
              {searchDraft && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchDraft("");
                    setSearchText("");
                    updateUrl({
                      query: null,
                    });
                  }}
                  className="grid h-8 w-8 shrink-0 place-items-center rounded-full text-[#7d93ac] transition hover:bg-[#edf4ff]"
                  aria-label="Clear search"
                >
                  <X size={14} />
                </button>
              )}
              {searchDraft.trim() !==
                searchText && (
                <span className="hidden shrink-0 text-[9px] font-bold text-[#8aa0bb] sm:inline">
                  Searching...
                </span>
              )}
            </div>
            <div className="flex flex-wrap gap-2">
              {/* Mobile filter control */}
              <button
                type="button"
                onClick={() =>
                  setFiltersOpen(
                    (value) =>
                      !value,
                  )
                }
                className="flex min-h-12 flex-1 items-center justify-center gap-2 rounded-[17px] border border-white bg-white/92 px-4 text-xs font-extrabold text-[#315276] shadow-[0_8px_24px_rgba(47,83,135,.08)] lg:hidden"
              >
                <SlidersHorizontal
                  size={15}
                />
                Filters
                {activeFilterCount >
                  0 && (
                  <span className="grid h-5 min-w-5 place-items-center rounded-full bg-[#edf2f8] px-1 text-[9px]">
                    {
                      activeFilterCount
                    }
                  </span>
                )}
              </button>
              {/* Destination selector */}
              <div className="relative min-w-[190px] flex-1 sm:flex-none">
                <button
                  type="button"
                  onClick={() => {
                    setDestinationOpen(
                      (value) =>
                        !value,
                    );
                    setSortOpen(false);
                  }}
                  className="flex min-h-12 w-full items-center gap-2 rounded-[17px] border border-white bg-white/92 px-4 text-xs font-extrabold text-[#315276] shadow-[0_8px_24px_rgba(47,83,135,.08)] transition hover:bg-white sm:w-[230px]"
                  aria-expanded={
                    destinationOpen
                  }
                >
                  <MapPin
                    size={15}
                    className="shrink-0 text-[#55789f]"
                  />
                  <span className="min-w-0 flex-1 truncate text-left">
                    {destination ||
                      "All destinations"}
                  </span>
                  <ChevronDown
                    size={14}
                    className={`shrink-0 transition-transform ${
                      destinationOpen
                        ? "rotate-180"
                        : ""
                    }`}
                  />
                </button>
                {destinationOpen && (
                  <>
                    <button
                      type="button"
                      aria-label="Close destination menu"
                      className="fixed inset-0 z-30 cursor-default"
                      onClick={() =>
                        setDestinationOpen(
                          false,
                        )
                      }
                    />
                    <div className="ios-popover absolute right-0 top-[calc(100%+10px)] z-40 w-[min(340px,calc(100vw-32px))] rounded-[28px] border border-white bg-white/95 p-3 shadow-[0_30px_90px_rgba(24,61,116,.22)] backdrop-blur-2xl">
                      <div className="px-1">
                        <p className="text-[10px] font-bold uppercase tracking-[.14em] text-[#8ba0b8]">
                          Destination
                        </p>
                        <p className="mt-1 text-xs text-[#69819f]">
                          Choose or search
                          where you want to
                          stay.
                        </p>
                      </div>
                      <form
                        onSubmit={(
                          event,
                        ) => {
                          event.preventDefault();
                          applyDestination(
                            destinationDraft,
                          );
                        }}
                        className="mt-3 flex items-center gap-2 rounded-[18px] border border-[#dfe8f4] bg-[#f7f9fc] p-1.5"
                      >
                        <Search
                          size={15}
                          className="ml-2 shrink-0 text-[#7890ac]"
                        />
                        <input
                          value={
                            destinationDraft
                          }
                          onChange={(
                            event,
                          ) =>
                            setDestinationDraft(
                              event.target
                                .value,
                            )
                          }
                          placeholder="City or destination"
                          className="min-w-0 flex-1 bg-transparent px-1 py-2 text-xs font-bold text-[#173d70] outline-none"
                          autoFocus
                        />
                        <button
                          type="submit"
                          className="rounded-[13px] bg-[#173d70] px-3 py-2 text-[10px] font-extrabold text-white"
                        >
                          Apply
                        </button>
                      </form>
                      <div className="mt-3 grid gap-1">
                        <button
                          type="button"
                          onClick={
                            clearDestination
                          }
                          className={`flex items-center justify-between rounded-[14px] px-3 py-2.5 text-left text-xs font-bold transition ${
                            !destination
                              ? "bg-[#eef3f9] text-[#173d70]"
                              : "text-[#7188a5] hover:bg-[#f4f7fb]"
                          }`}
                        >
                          All destinations
                          {!destination && (
                            <Check
                              size={12}
                            />
                          )}
                        </button>
                        {filteredDestinations.map(
                          (option) => {
                            const selected =
                              destination.toLowerCase() ===
                              option.name.toLowerCase();
                            return (
                              <button
                                key={
                                  option.name
                                }
                                type="button"
                                onClick={() =>
                                  applyDestination(
                                    option.name,
                                  )
                                }
                                className={`flex items-center justify-between rounded-[14px] px-3 py-2.5 text-left transition ${
                                  selected
                                    ? "bg-[#eef3f9] text-[#173d70]"
                                    : "text-[#526b88] hover:bg-[#f4f7fb]"
                                }`}
                              >
                                <span>
                                  <span className="block text-xs font-extrabold">
                                    {
                                      option.name
                                    }
                                  </span>
                                  {option
                                    .aliases
                                    .length >
                                    0 && (
                                    <span className="mt-0.5 block text-[9px] font-semibold text-[#91a2b5]">
                                      Also
                                      known as{" "}
                                      {option.aliases.join(
                                        ", ",
                                      )}
                                    </span>
                                  )}
                                </span>
                                {selected && (
                                  <Check
                                    size={
                                      12
                                    }
                                    strokeWidth={
                                      3
                                    }
                                  />
                                )}
                              </button>
                            );
                          },
                        )}
                        {filteredDestinations.length ===
                          0 && (
                          <p className="px-3 py-4 text-center text-[11px] font-semibold text-[#879bb2]">
                            Press Apply to
                            search this
                            destination.
                          </p>
                        )}
                      </div>
                    </div>
                  </>
                )}
              </div>
              {/* Sort selector */}
              <div className="relative min-w-[190px] flex-1 sm:flex-none">
                <button
                  type="button"
                  onClick={() => {
                    setSortOpen(
                      (value) =>
                        !value,
                    );
                    setDestinationOpen(
                      false,
                    );
                  }}
                  className="flex min-h-12 w-full items-center gap-2 rounded-[17px] border border-white bg-white/92 px-4 text-xs font-extrabold text-[#315276] shadow-[0_8px_24px_rgba(47,83,135,.08)] transition hover:bg-white sm:min-w-[220px]"
                  aria-expanded={
                    sortOpen
                  }
                >
                  <ArrowDownUp
                    size={15}
                    className="shrink-0 text-[#55789f]"
                  />
                  <span className="min-w-0 flex-1 truncate text-left">
                    {
                      sortOptions.find(
                        (option) =>
                          option.value ===
                          sortBy,
                      )?.label
                    }
                  </span>
                  <ChevronDown
                    size={14}
                    className={`shrink-0 transition-transform ${
                      sortOpen
                        ? "rotate-180"
                        : ""
                    }`}
                  />
                </button>
                {sortOpen && (
                  <>
                    <button
                      type="button"
                      aria-label="Close sort menu"
                      className="fixed inset-0 z-30 cursor-default"
                      onClick={() =>
                        setSortOpen(false)
                      }
                    />
                    <div className="ios-popover absolute right-0 top-[calc(100%+10px)] z-40 w-[min(300px,calc(100vw-32px))] rounded-[28px] border border-white bg-white/95 p-2.5 shadow-[0_30px_90px_rgba(24,61,116,.22)] backdrop-blur-2xl">
                      <div className="px-2 pb-2 pt-1">
                        <p className="text-[10px] font-bold uppercase tracking-[.14em] text-[#8ba0b8]">
                          Sort stays
                        </p>
                      </div>
                      {sortOptions.map(
                        (option) => {
                          const selected =
                            option.value ===
                            sortBy;
                          return (
                            <button
                              key={
                                option.value
                              }
                              type="button"
                              onClick={() =>
                                handleSort(
                                  option.value,
                                )
                              }
                              className={`flex w-full items-center justify-between rounded-[17px] px-3 py-3 text-left text-xs font-extrabold transition ${
                                selected
                                  ? "bg-[#eef3f9] text-[#173d70]"
                                  : "text-[#526b88] hover:bg-[#f4f7fb]"
                              }`}
                            >
                              {
                                option.label
                              }
                              {selected && (
                                <Check
                                  size={
                                    12
                                  }
                                  strokeWidth={
                                    3
                                  }
                                />
                              )}
                            </button>
                          );
                        },
                      )}
                    </div>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
        <div className="mt-5 grid gap-4 lg:grid-cols-[230px_minmax(0,1fr)]">
          {/* Filters */}
          <aside
            className={`${
              filtersOpen
                ? "block"
                : "hidden"
            } glass-panel h-fit rounded-[22px] p-4 lg:sticky lg:top-24 lg:block`}
          >
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-2 text-sm font-extrabold text-[#173d70]">
                <Filter size={16} />
                Refine stays
              </div>
              <button
                type="button"
                onClick={() =>
                  setFiltersOpen(false)
                }
                className="grid h-8 w-8 place-items-center rounded-full bg-[#edf4ff] text-[#53749e] lg:hidden"
                aria-label="Close filters"
              >
                <X size={15} />
              </button>
            </div>
            {activeFilterCount >
              0 && (
              <button
                type="button"
                onClick={clearFilters}
                className="mt-3 text-[11px] font-bold text-[#2f6df6] hover:underline"
              >
                Clear{" "}
                {activeFilterCount}{" "}
                active{" "}
                {activeFilterCount ===
                1
                  ? "filter"
                  : "filters"}
              </button>
            )}
            <FilterBlock title="Price range">
              <div className="grid grid-cols-2 gap-2">
                <PriceInput
                  label="Minimum"
                  value={minPrice}
                  onChange={(value) =>
                    setMinPrice(
                      Math.min(
                        value,
                        maxPrice,
                      ),
                    )
                  }
                />
                <PriceInput
                  label="Maximum"
                  value={maxPrice}
                  onChange={(value) =>
                    setMaxPrice(
                      Math.max(
                        value,
                        minPrice,
                      ),
                    )
                  }
                />
              </div>
              <input
                type="range"
                min="0"
                max={
                  MAX_PRICE_LIMIT
                }
                step="500"
                value={maxPrice}
                onChange={(event) =>
                  setMaxPrice(
                    Math.max(
                      Number(
                        event.target
                          .value,
                      ),
                      minPrice,
                    ),
                  )
                }
                className="mt-3 w-full accent-[#2f6df6]"
              />
              <div className="mt-1 flex justify-between text-[9px] font-semibold text-[#8aa0bb]">
                <span>₹0</span>
                <span>
                  ₹
                  {MAX_PRICE_LIMIT.toLocaleString(
                    "en-IN",
                  )}
                  +
                </span>
              </div>
            </FilterBlock>
            <FilterBlock title="Guest rating">
              <div className="grid grid-cols-2 gap-1.5">
                {[
                  0,
                  4,
                  4.5,
                  4.7,
                ].map((value) => (
                  <button
                    key={value}
                    type="button"
                    onClick={() =>
                      setRating(value)
                    }
                    className={`rounded-xl border px-1 py-2 text-[11px] font-bold transition ${
                      rating === value
                        ? "border-[#2f6df6] bg-[#eaf2ff] text-[#2f6df6]"
                        : "border-[#d8e6f8] bg-white/80 text-[#617a98]"
                    }`}
                  >
                    {value === 0
                      ? "Any"
                      : `${value}+ ★`}
                  </button>
                ))}
              </div>
            </FilterBlock>
            <FilterBlock title="Property">
              <button
                type="button"
                onClick={() =>
                  setVerifiedOnly(
                    (value) =>
                      !value,
                  )
                }
                className={`flex w-full items-center justify-between rounded-xl border px-3 py-2.5 text-left text-[11px] font-bold transition ${
                  verifiedOnly
                    ? "border-[#2f6df6] bg-[#eaf2ff] text-[#2f6df6]"
                    : "border-[#d8e6f8] bg-white/80 text-[#617a98]"
                }`}
              >
                <span className="flex items-center gap-2">
                  <ShieldCheck
                    size={14}
                  />
                  Verified only
                </span>
                {verifiedOnly && (
                  <Check size={13} />
                )}
              </button>
            </FilterBlock>
          </aside>
          {/* Results */}
          <section className="min-w-0">
            <div className="results-toolbar rounded-[24px] p-4 sm:p-5">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                <div>
                  <p className="text-xs font-bold uppercase tracking-[.15em] text-[#6f88aa]">
                    Hotel discovery
                  </p>
                  <h2 className="mt-1 text-2xl font-extrabold text-[#173d70]">
                    {hotelsLoading
                      ? "Finding stays..."
                      : `${hotels.length} ${
                          hotels.length ===
                          1
                            ? "stay"
                            : "stays"
                        } found`}
                  </h2>
                  <p className="mt-1 text-xs text-[#7a91ae]">
                    Results update
                    automatically as you
                    search and refine.
                  </p>
                </div>
                {destination && (
                  <div className="flex self-start items-center gap-1.5 rounded-full border border-[#dce8f7] bg-white/80 px-3 py-2 text-[10px] font-extrabold text-[#41678f] sm:self-auto">
                    <MapPin
                      size={12}
                    />
                    {destination}
                  </div>
                )}
              </div>
            </div>
            {hotelsLoading && (
              <LoadingResults />
            )}
            {!hotelsLoading &&
              hotelsError && (
                <div className="mt-6 rounded-[24px] border border-red-100 bg-red-50 p-8 text-center">
                  <p className="font-bold text-red-700">
                    We couldn't load the
                    hotels.
                  </p>
                  <p className="mt-1 text-xs text-red-500">
                    {hotelsError}
                  </p>
                </div>
              )}
            {!hotelsLoading &&
              !hotelsError &&
              hotels.length > 0 && (
                <div className="mt-6 grid gap-4 md:grid-cols-2 2xl:grid-cols-3">
                  {hotels.map(
                    (hotel) => (
                      <HotelCard
                        key={hotel.id}
                        hotel={hotel}
                        checkIn={checkIn}
                        checkOut={checkOut}
                        guests={guests}
                      />
                    ),
                  )}
                </div>
              )}
            {!hotelsLoading &&
              !hotelsError &&
              hotels.length === 0 && (
                <div className="glass-panel mt-6 rounded-[24px] p-10 text-center">
                  <Search className="mx-auto text-[#7d97b9]" />
                  <h3 className="mt-3 text-xl font-bold text-[#173d70]">
                    No stays match your
                    search
                  </h3>
                  <p className="mt-2 text-sm text-[#7187a3]">
                    Try another
                    destination, a broader
                    price range or a lower
                    guest rating.
                  </p>
                  {activeFilterCount >
                    0 && (
                    <button
                      type="button"
                      onClick={
                        clearFilters
                      }
                      className="mt-4 text-sm font-bold text-[#2f6df6] hover:underline"
                    >
                      Reset filters
                    </button>
                  )}
                </div>
              )}
          </section>
        </div>
      </section>
    </main>
  );
}
function HotelCard({
  hotel,
  checkIn,
  checkOut,
  guests,
}: {
  hotel: ApiHotel;
  checkIn: string;
  checkOut: string;
  guests: string;
}) {
  const stayQuery =
    checkIn && checkOut && guests
      ? new URLSearchParams({
          checkIn,
          checkOut,
          guests,
        }).toString()
      : "";

  return (
    <article className="soft-card group overflow-hidden rounded-[22px] p-2.5 transition duration-300 hover:-translate-y-1 hover:shadow-[0_20px_48px_rgba(39,83,143,.14)]">
      <div className="relative aspect-[1.72/1] overflow-hidden rounded-[17px]">
        <img
          src={
            hotel.heroImageUrl ??
            "https\://images.unsplash.com/photo-1566073771259-6a8506099945"
          }
          alt={hotel.name}
          className="h-full w-full object-cover transition duration-700 group-hover:scale-105"
        />
        <button
          type="button"
          className="absolute right-3 top-3 grid h-9 w-9 place-items-center rounded-full border border-white/60 bg-white/80 text-[#315f9f] backdrop-blur transition hover:bg-white"
          aria-label={`Save ${hotel.name}`}
        >
          <Heart size={16} />
        </button>
        {hotel.verified && (
          <span className="absolute bottom-3 left-3 rounded-full bg-white/86 px-2.5 py-1 text-[10px] font-bold text-[#2f6df6] backdrop-blur">
            Verified stay
          </span>
        )}
      </div>
      <div className="p-2 sm:p-3">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="truncate font-extrabold text-[#173d70] sm:text-[17px]">
              {hotel.name}
            </h3>
            <p className="mt-1 flex items-center gap-1 text-[11px] text-[#7188a5]">
              <MapPin
                size={12}
                className="shrink-0"
              />
              <span className="truncate">
                {hotel.address ??
                  [
                    hotel.city,
                    hotel.state,
                    hotel.country,
                  ]
                    .filter(Boolean)
                    .join(", ")}
              </span>
            </p>
          </div>
          <span className="flex shrink-0 items-center gap-1 rounded-full bg-[#fff7e5] px-2 py-1 text-xs font-bold text-[#b66f00]">
            <Star
              size={12}
              fill="currentColor"
            />
            {hotel.rating?.toFixed(
              1,
            ) ?? "New"}
          </span>
        </div>
        <p className="mt-2 text-[10px] font-semibold text-[#8aa0b9]">
          {hotel.reviewCount.toLocaleString(
            "en-IN",
          )}{" "}
          guest{" "}
          {hotel.reviewCount === 1
            ? "review"
            : "reviews"}
        </p>
        {hotel.description && (
          <p className="mt-2 line-clamp-2 text-xs leading-5 text-[#6d84a1]">
            {hotel.description}
          </p>
        )}
        <div className="mt-3 flex items-end justify-between gap-3 border-t border-[#e5edf8] pt-3">
          <div>
            <p className="text-[9px] font-semibold uppercase tracking-[.08em] text-[#8ba0b9]">
              From
            </p>
            <p className="text-lg font-extrabold text-[#173d70]">
              ₹
              {hotel.startingPrice.toLocaleString(
                "en-IN",
              )}
              <span className="text-[10px] font-medium text-[#7890ac]">
                /night
              </span>
            </p>
          </div>
          <Link
            href={
              `/hotels/${hotel.slug}` +
              (stayQuery
                ? `?${stayQuery}`
                : "")
            }
            className="blue-button rounded-xl px-4 py-2.5 text-xs font-bold transition hover:-translate-y-0.5"
          >
            View Details
          </Link>
        </div>
      </div>
    </article>
  );
}
function FilterBlock({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className="mt-4 border-t border-[#dfe9f6] pt-4">
      <p className="mb-2.5 text-xs font-extrabold text-[#315276]">
        {title}
      </p>
      {children}
    </div>
  );
}
function PriceInput({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
}) {
  return (
    <label>
      <span className="text-[9px] font-bold uppercase tracking-[.08em] text-[#8ba0b8]">
        {label}
      </span>
      <div className="mt-1 flex items-center rounded-xl border border-[#d8e6f8] bg-white/80 px-2">
        <span className="text-xs font-bold text-[#7790ac]">
          ₹
        </span>
        <input
          type="number"
          min="0"
          step="500"
          value={value}
          onChange={(event) =>
            onChange(
              Math.max(
                0,
                Number(
                  event.target.value,
                ) || 0,
              ),
            )
          }
          className="min-w-0 w-full bg-transparent px-1 py-2 text-[11px] font-bold text-[#315276] outline-none"
        />
      </div>
    </label>
  );
}
function LoadingResults() {
  return (
    <div className="glass-panel mt-6 rounded-[24px] p-10 text-center">
      <div className="mx-auto h-8 w-8 animate-spin rounded-full border-2 border-[#d9e6f8] border-t-[#2f6df6]" />
      <p className="mt-4 text-sm font-bold text-[#567394]">
        Searching stays...
      </p>
    </div>
  );
}
function HotelsPageLoading() {
  return (
    <main className="travel-shell min-h-screen pb-24 pt-[96px]">
      <section className="mx-auto max-w-[1480px] px-3 sm:px-6">
        <LoadingResults />
      </section>
    </main>
  );
}
function normalizeDestination(
  value: string,
) {
  const query =
    value.trim().toLowerCase();
  if (!query) {
    return "";
  }
  const match =
    destinations.find(
      (option) =>
        option.name.toLowerCase() ===
          query ||
        option.aliases.some(
          (alias) =>
            alias.toLowerCase() ===
            query,
        ),
    );
  return match?.name ?? value.trim();
}
function readNumberParam(
  value: string | null,
  fallback: number,
) {
  if (!value) {
    return fallback;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed)
    ? parsed
    : fallback;
}
function readSort(
  value: string | null,
): HotelSort {
  return sortOptions.some(
    (option) =>
      option.value === value,
  )
    ? (value as HotelSort)
    : "recommended";
}
function formatDate(
  value: string,
) {
  const date = new Date(
    `${value}T00:00:00`,
  );
  if (
    Number.isNaN(date.getTime())
  ) {
    return value;
  }
  return new Intl.DateTimeFormat(
    "en-IN",
    {
      day: "numeric",
      month: "short",
    },
  ).format(date);
}