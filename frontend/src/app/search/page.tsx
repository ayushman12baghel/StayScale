"use client";

import { Suspense, useEffect, useState, useCallback } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import {
  SlidersHorizontal,
  Map as MapIcon,
  List,
  Clock,
  RotateCcw,
  Star,
  AlertTriangle,
} from "lucide-react";
import {
  fetchCities,
  searchHotels,
  formatINR,
  getDefaultDates,
} from "@/lib/api";
import { CitySummary, PagedSearchResponse } from "@/lib/types";
import { SearchBar } from "@/components/SearchBar";
import { HotelCard } from "@/components/HotelCard";
import { HotelMapView } from "@/components/HotelMapView";

const AMENITY_OPTIONS = [
  "Free WiFi",
  "Pool",
  "Beachfront",
  "Spa",
  "Breakfast Included",
  "Gym",
  "Airport Shuttle",
  "Mountain View",
];

const SORT_OPTIONS = [
  { id: "recommended", label: "Recommended" },
  { id: "price_asc", label: "Lowest Price" },
  { id: "price_desc", label: "Highest Price" },
  { id: "rating_desc", label: "Top Reviewed" },
];

function SearchPageInner() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const defaults = getDefaultDates();

  const city = searchParams.get("city") || "";
  const query = searchParams.get("query") || "";
  const checkIn = searchParams.get("checkIn") || defaults.checkIn;
  const checkOut = searchParams.get("checkOut") || defaults.checkOut;
  const guests = Number(searchParams.get("guests") || 2);
  const rooms = Number(searchParams.get("rooms") || 1);
  const sort = searchParams.get("sort") || "recommended";
  const page = Number(searchParams.get("page") || 0);
  const maxPriceParam = searchParams.get("maxPrice");
  const minStarsParam = searchParams.get("minStars");
  const minRatingParam = searchParams.get("minRating");
  const amenitiesParam = searchParams.get("amenities");

  // URL query string is the single source of truth for search & sidebar filters.
  // Tracking requestKey derives `loading` without calling setState synchronously inside useEffect (React 19 rule).
  const [cities, setCities] = useState<CitySummary[]>([]);
  const requestKey = `${city}|${query}|${checkIn}|${checkOut}|${guests}|${rooms}|${maxPriceParam ?? ""}|${minStarsParam ?? ""}|${minRatingParam ?? ""}|${amenitiesParam ?? ""}|${sort}|${page}`;
  const [fetchState, setFetchState] = useState<{
    key: string;
    results: PagedSearchResponse | null;
    error: string | null;
  }>({ key: "", results: null, error: null });

  const loading = fetchState.key !== requestKey;
  const results = fetchState.results;
  const error = fetchState.error;
  const [showMap, setShowMap] = useState(false);

  const [maxPriceINR, setMaxPriceINR] = useState<number>(
    maxPriceParam ? Math.round(Number(maxPriceParam) / 100) : 15000
  );
  const [minStars, setMinStars] = useState<number | undefined>(
    minStarsParam ? Number(minStarsParam) : undefined
  );
  const [minRating, setMinRating] = useState<number | undefined>(
    minRatingParam ? Number(minRatingParam) : undefined
  );
  const [selectedAmenities, setSelectedAmenities] = useState<string[]>(
    amenitiesParam ? amenitiesParam.split(",").filter(Boolean) : []
  );

  useEffect(() => {
    fetchCities().then(setCities).catch(() => {});
  }, []);

  const updateUrlParams = useCallback(
    (updates: Record<string, string | undefined>) => {
      const qs = new URLSearchParams(searchParams.toString());
      Object.entries(updates).forEach(([k, v]) => {
        if (!v) qs.delete(k);
        else qs.set(k, v);
      });
      if (!("page" in updates)) {
        qs.set("page", "0");
      }
      router.push(`/search?${qs.toString()}`);
    },
    [router, searchParams]
  );

  useEffect(() => {
    let active = true;

    searchHotels({
      city: city || undefined,
      query: query || undefined,
      checkIn,
      checkOut,
      guests,
      rooms,
      maxPrice: maxPriceParam ? Number(maxPriceParam) : undefined,
      minStars: minStarsParam ? Number(minStarsParam) : undefined,
      minRating: minRatingParam ? Number(minRatingParam) : undefined,
      amenities: amenitiesParam ? amenitiesParam.split(",").filter(Boolean) : undefined,
      sort,
      page,
      size: 10,
    })
      .then((res) => {
        if (active) {
          setFetchState({ key: requestKey, results: res, error: null });
        }
      })
      .catch((err: Error) => {
        if (active) {
          setFetchState({ key: requestKey, results: null, error: err.message });
        }
      });

    return () => {
      active = false;
    };
  }, [
    requestKey,
    city,
    query,
    checkIn,
    checkOut,
    guests,
    rooms,
    maxPriceParam,
    minStarsParam,
    minRatingParam,
    amenitiesParam,
    sort,
    page,
  ]);

  const toggleAmenity = (amenity: string) => {
    const next = selectedAmenities.includes(amenity)
      ? selectedAmenities.filter((a) => a !== amenity)
      : [...selectedAmenities, amenity];
    setSelectedAmenities(next);
    updateUrlParams({
      amenities: next.length > 0 ? next.join(",") : undefined,
    });
  };

  const resetFilters = () => {
    setMaxPriceINR(15000);
    setMinStars(undefined);
    setMinRating(undefined);
    setSelectedAmenities([]);
    updateUrlParams({
      maxPrice: undefined,
      minStars: undefined,
      minRating: undefined,
      amenities: undefined,
      sort: "recommended",
    });
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
      {/* Top sticky-style search bar */}
      <SearchBar
        cities={cities}
        initialCity={city}
        initialQuery={query}
        initialCheckIn={checkIn}
        initialCheckOut={checkOut}
        initialGuests={guests}
        initialRooms={rooms}
        compact
      />

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-12">
        {/* Sidebar Filters */}
        <aside className="lg:col-span-3">
          <div className="sticky top-20 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2 text-sm font-bold text-slate-900">
                <SlidersHorizontal className="h-4 w-4 text-blue-600" />
                <span>Filter Results</span>
              </div>
              <button
                type="button"
                onClick={resetFilters}
                className="inline-flex items-center gap-1 text-xs font-semibold text-blue-600 hover:underline"
              >
                <RotateCcw className="h-3 w-3" />
                Reset
              </button>
            </div>

            {/* Max Nightly Budget */}
            <div className="mt-4">
              <div className="flex items-center justify-between text-xs font-semibold text-slate-700">
                <span>Max Price / night</span>
                <span className="text-blue-600">
                  {formatINR(maxPriceINR * 100)}
                </span>
              </div>
              <input
                type="range"
                min={2500}
                max={15000}
                step={500}
                value={maxPriceINR}
                onChange={(e) => setMaxPriceINR(Number(e.target.value))}
                onMouseUp={() =>
                  updateUrlParams({
                    maxPrice:
                      maxPriceINR < 15000 ? String(maxPriceINR * 100) : undefined,
                  })
                }
                onTouchEnd={() =>
                  updateUrlParams({
                    maxPrice:
                      maxPriceINR < 15000 ? String(maxPriceINR * 100) : undefined,
                  })
                }
                className="mt-2 w-full accent-blue-600"
              />
              <div className="flex justify-between text-[11px] text-slate-400">
                <span>₹2,500</span>
                <span>₹15,000+</span>
              </div>
            </div>

            {/* Star Rating */}
            <div className="mt-5 border-t border-slate-100 pt-4">
              <p className="text-xs font-semibold text-slate-700">Star Rating</p>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {[undefined, 5, 4, 3].map((stars) => {
                  const active = minStars === stars;
                  return (
                    <button
                      key={String(stars ?? "any")}
                      type="button"
                      onClick={() => {
                        setMinStars(stars);
                        updateUrlParams({
                          minStars: stars ? String(stars) : undefined,
                        });
                      }}
                      className={`inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-semibold transition ${
                        active
                          ? "bg-blue-600 text-white"
                          : "bg-slate-100 text-slate-700 hover:bg-slate-200"
                      }`}
                    >
                      {stars ? (
                        <>
                          <span>{stars}</span>
                          <Star className="h-3 w-3 fill-current" />
                          {stars < 5 && <span>+</span>}
                        </>
                      ) : (
                        "Any"
                      )}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Guest Score */}
            <div className="mt-5 border-t border-slate-100 pt-4">
              <p className="text-xs font-semibold text-slate-700">
                Minimum Guest Score
              </p>
              <div className="mt-2 grid grid-cols-2 gap-1.5">
                {[
                  { label: "Any", val: undefined },
                  { label: "8.0+ Very Good", val: 8.0 },
                  { label: "8.5+ Excellent", val: 8.5 },
                  { label: "9.0+ Exceptional", val: 9.0 },
                ].map((opt) => {
                  const active = minRating === opt.val;
                  return (
                    <button
                      key={opt.label}
                      type="button"
                      onClick={() => {
                        setMinRating(opt.val);
                        updateUrlParams({
                          minRating: opt.val ? String(opt.val) : undefined,
                        });
                      }}
                      className={`rounded-lg px-2 py-1.5 text-xs font-semibold transition ${
                        active
                          ? "bg-blue-600 text-white"
                          : "bg-slate-100 text-slate-700 hover:bg-slate-200"
                      }`}
                    >
                      {opt.label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Amenities */}
            <div className="mt-5 border-t border-slate-100 pt-4">
              <p className="text-xs font-semibold text-slate-700">
                Property Amenities (GIN Index)
              </p>
              <div className="mt-2 space-y-2">
                {AMENITY_OPTIONS.map((amenity) => (
                  <label
                    key={amenity}
                    className="flex cursor-pointer items-center gap-2 text-xs text-slate-700"
                  >
                    <input
                      type="checkbox"
                      checked={selectedAmenities.includes(amenity)}
                      onChange={() => toggleAmenity(amenity)}
                      className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                    />
                    <span>{amenity}</span>
                  </label>
                ))}
              </div>
            </div>
          </div>
        </aside>

        {/* Results Column */}
        <section className="lg:col-span-9">
          {/* Header & Sort Bar */}
          <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h1 className="text-lg font-extrabold text-slate-900">
                {results
                  ? `${results.totalElements} properties found in ${results.city}`
                  : "Searching available properties..."}
              </h1>
              <p className="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-slate-500">
                <span>
                  {checkIn} to {checkOut} · {guests} guests · {rooms}{" "}
                  {rooms === 1 ? "room" : "rooms"}
                </span>
                {results && (
                  <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 font-semibold text-emerald-700">
                    <Clock className="h-3 w-3" />
                    SQL {results.queryDurationMs} ms
                  </span>
                )}
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={() => setShowMap((m) => !m)}
                className={`inline-flex items-center gap-1.5 rounded-xl border px-3 py-1.5 text-xs font-bold transition ${
                  showMap
                    ? "border-blue-600 bg-blue-50 text-blue-700"
                    : "border-slate-200 bg-white text-slate-700 hover:bg-slate-50"
                }`}
              >
                {showMap ? (
                  <>
                    <List className="h-3.5 w-3.5" />
                    <span>Hide Map</span>
                  </>
                ) : (
                  <>
                    <MapIcon className="h-3.5 w-3.5" />
                    <span>Show Map</span>
                  </>
                )}
              </button>
            </div>
          </div>

          {/* Sort Tabs */}
          <div className="mt-3 flex flex-wrap gap-2">
            {SORT_OPTIONS.map((opt) => {
              const active = sort === opt.id;
              return (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => updateUrlParams({ sort: opt.id })}
                  className={`rounded-xl px-3.5 py-2 text-xs font-bold transition ${
                    active
                      ? "bg-slate-900 text-white shadow-sm"
                      : "bg-white text-slate-700 ring-1 ring-slate-200 hover:bg-slate-50"
                  }`}
                >
                  {opt.label}
                </button>
              );
            })}
          </div>

          {/* Error state */}
          {error && (
            <div className="mt-4 flex items-start gap-3 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
              <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
              <div>
                <p className="font-bold">Could not load search results</p>
                <p className="mt-0.5 text-xs">{error}</p>
              </div>
            </div>
          )}

          {/* Optional Interactive Map */}
          {showMap && results && results.items.length > 0 && (
            <div className="mt-4">
              <HotelMapView
                hotels={results.items}
                checkIn={checkIn}
                checkOut={checkOut}
                guests={guests}
                rooms={rooms}
              />
            </div>
          )}

          {/* Loading Skeletons */}
          {loading && (
            <div className="mt-4 space-y-4">
              {Array.from({ length: 4 }).map((_, i) => (
                <div
                  key={i}
                  className="h-48 animate-pulse rounded-2xl border border-slate-200 bg-white"
                />
              ))}
            </div>
          )}

          {/* Results List */}
          {!loading && results && results.items.length === 0 && (
            <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-10 text-center">
              <p className="text-base font-bold text-slate-900">
                No properties match all of these filters for {checkIn} – {checkOut}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Try increasing your max nightly budget, clearing amenity filters, or
                picking another date range.
              </p>
              <button
                type="button"
                onClick={resetFilters}
                className="mt-4 rounded-xl bg-blue-600 px-4 py-2 text-xs font-bold text-white hover:bg-blue-700"
              >
                Reset All Filters
              </button>
            </div>
          )}

          {!loading && results && results.items.length > 0 && (
            <div className="mt-4 space-y-4">
              {results.items.map((hotel) => (
                <HotelCard
                  key={hotel.id}
                  hotel={hotel}
                  checkIn={checkIn}
                  checkOut={checkOut}
                  guests={guests}
                  rooms={rooms}
                />
              ))}
            </div>
          )}

          {/* Pagination */}
          {results && results.totalPages > 1 && (
            <div className="mt-6 flex items-center justify-between rounded-2xl border border-slate-200 bg-white px-4 py-3 text-xs font-semibold">
              <button
                type="button"
                disabled={page <= 0}
                onClick={() => updateUrlParams({ page: String(page - 1) })}
                className="rounded-lg border border-slate-200 px-3 py-1.5 disabled:opacity-40 hover:bg-slate-50"
              >
                Previous
              </button>
              <span className="text-slate-600">
                Page {page + 1} of {results.totalPages}
              </span>
              <button
                type="button"
                disabled={page + 1 >= results.totalPages}
                onClick={() => updateUrlParams({ page: String(page + 1) })}
                className="rounded-lg border border-slate-200 px-3 py-1.5 disabled:opacity-40 hover:bg-slate-50"
              >
                Next
              </button>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

// Next.js App Router requires wrapping useSearchParams() clients in <Suspense> during builds.
export default function SearchPage() {
  return (
    <Suspense
      fallback={
        <div className="mx-auto max-w-7xl px-4 py-12 text-sm text-slate-500">
          Loading StayScale search...
        </div>
      }
    >
      <SearchPageInner />
    </Suspense>
  );
}
