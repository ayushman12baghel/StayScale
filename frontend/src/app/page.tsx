import Link from "next/link";
import Image from "next/image";
import {
  MapPin,
  Star,
  Sparkles,
  ShieldCheck,
  Database,
  ArrowRight,
  Terminal,
} from "lucide-react";
import {
  fetchCities,
  searchHotels,
  formatINR,
  getDefaultDates,
} from "@/lib/api";
import { CitySummary, HotelSearchItem } from "@/lib/types";
import { SearchBar } from "@/components/SearchBar";

export const dynamic = "force-dynamic";

export default async function HomePage() {
  const defaults = getDefaultDates();
  let cities: CitySummary[] = [];
  let featuredHotels: HotelSearchItem[] = [];
  let backendOffline = false;

  try {
    const [citiesData, searchData] = await Promise.all([
      fetchCities(),
      searchHotels({
        checkIn: defaults.checkIn,
        checkOut: defaults.checkOut,
        guests: 2,
        rooms: 1,
        sort: "rating_desc",
        size: 6,
      }),
    ]);
    cities = citiesData;
    featuredHotels = searchData.items;
  } catch {
    backendOffline = true;
  }

  return (
    <div>
      {/* Hero Banner */}
      <section className="relative overflow-hidden bg-slate-900">
        <Image
          src="/images/hotels/goa-resort.jpg"
          alt="Luxury coastal resort"
          fill
          priority
          sizes="100vw"
          className="object-cover opacity-35"
        />
        <div className="absolute inset-0 bg-gradient-to-b from-slate-950/75 via-blue-950/70 to-slate-900/90" />

        <div className="relative mx-auto max-w-7xl px-4 pb-14 pt-12 sm:px-6 sm:pb-16 sm:pt-16">
          <div className="max-w-3xl">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-sky-200 ring-1 ring-white/20 backdrop-blur">
              <Sparkles className="h-3.5 w-3.5" />
              Part 1 Live · Multi-Night Date-Partitioned Inventory
            </span>
            <h1 className="mt-4 text-3xl font-extrabold tracking-tight text-white sm:text-5xl">
              Search, compare &amp; book curated stays across Asia.
            </h1>
            <p className="mt-3 text-base text-slate-200 sm:text-lg">
              Built end-to-end to learn real OTA engineering: 100 properties, 90-day
              daily room inventory, indexed multi-night SQL queries, and dynamic
              availability.
            </p>
          </div>

          <div className="mt-8">
            <SearchBar
              cities={cities}
              initialCheckIn={defaults.checkIn}
              initialCheckOut={defaults.checkOut}
            />
          </div>

          {/* Quick city pills */}
          {cities.length > 0 && (
            <div className="mt-5 flex flex-wrap items-center gap-2 text-xs text-slate-200">
              <span className="font-semibold text-slate-300">Popular now:</span>
              {cities.slice(0, 6).map((c) => (
                <Link
                  key={c.city}
                  href={`/search?city=${encodeURIComponent(c.city)}&checkIn=${defaults.checkIn}&checkOut=${defaults.checkOut}&guests=2&rooms=1`}
                  className="rounded-full bg-white/10 px-3 py-1 font-medium text-white ring-1 ring-white/15 hover:bg-white/20 transition"
                >
                  {c.city} · {c.hotelCount} stays
                </Link>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* Backend offline setup helper */}
      {backendOffline && (
        <section className="mx-auto mt-8 max-w-7xl px-4 sm:px-6">
          <div className="rounded-2xl border border-amber-300 bg-amber-50 p-5 text-amber-950 shadow-sm">
            <div className="flex items-start gap-3">
              <Terminal className="mt-0.5 h-5 w-5 shrink-0 text-amber-700" />
              <div className="space-y-2 text-sm">
                <h2 className="font-bold">
                  Backend API is not running yet on localhost:8080
                </h2>
                <p className="text-amber-900">
                  To populate the 100 seeded hotels and live 90-day availability,
                  start PostgreSQL and the Spring Boot Core Service:
                </p>
                <pre className="overflow-x-auto rounded-xl bg-slate-900 p-3 text-xs text-slate-100">
                  {`# 1. Start Docker Desktop, then start Postgres ONLY (no Redis/Kafka yet)\ndocker compose up -d postgres\n\n# 2. Boot backend/core-service (seeds 100 hotels + 90 days inventory)\ncd backend\\core-service\n.\\mvnw.cmd spring-boot:run`}
                </pre>
              </div>
            </div>
          </div>
        </section>
      )}

      {/* Top Destinations */}
      <section className="mx-auto max-w-7xl px-4 py-12 sm:px-6">
        <div className="flex items-end justify-between">
          <div>
            <h2 className="text-2xl font-extrabold text-slate-900">
              Explore Top Destinations
            </h2>
            <p className="mt-1 text-sm text-slate-600">
              Real coordinate clusters, local neighborhoods, and multi-night room
              inventories.
            </p>
          </div>
          <Link
            href={`/search?checkIn=${defaults.checkIn}&checkOut=${defaults.checkOut}`}
            className="hidden sm:inline-flex items-center gap-1 text-sm font-bold text-blue-600 hover:text-blue-700"
          >
            <span>View all 100 stays</span>
            <ArrowRight className="h-4 w-4" />
          </Link>
        </div>

        <div className="mt-6 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {(cities.length > 0 ? cities : FALLBACK_DESTINATIONS).map((dest) => (
            <Link
              key={dest.city}
              href={`/search?city=${encodeURIComponent(dest.city)}&checkIn=${defaults.checkIn}&checkOut=${defaults.checkOut}&guests=2&rooms=1`}
              className="group overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-lg"
            >
              <div className="relative h-44 w-full overflow-hidden bg-slate-100">
                <Image
                  src={dest.sampleImageUrl}
                  alt={dest.city}
                  fill
                  sizes="(max-width: 640px) 100vw, 25vw"
                  className="object-cover transition duration-300 group-hover:scale-105"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-slate-950/75 via-transparent to-transparent" />
                <div className="absolute bottom-3 left-3 right-3 text-white">
                  <div className="flex items-center justify-between">
                    <h3 className="text-lg font-extrabold">{dest.city}</h3>
                    <span className="rounded-full bg-white/20 px-2 py-0.5 text-[11px] font-semibold backdrop-blur">
                      {dest.hotelCount} hotels
                    </span>
                  </div>
                  <p className="text-xs text-slate-200">{dest.country}</p>
                </div>
              </div>
              <div className="p-4">
                <p className="line-clamp-2 text-xs text-slate-600">
                  {dest.tagline}
                </p>
                {dest.minNightlyPriceMinor > 0 && (
                  <p className="mt-2 text-xs font-bold text-blue-600">
                    From {formatINR(dest.minNightlyPriceMinor)} / night
                  </p>
                )}
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* Featured High-Rated Stays */}
      {featuredHotels.length > 0 && (
        <section className="border-t border-slate-200 bg-white py-12">
          <div className="mx-auto max-w-7xl px-4 sm:px-6">
            <h2 className="text-2xl font-extrabold text-slate-900">
              Top-Reviewed Stays Available This Week
            </h2>
            <p className="mt-1 text-sm text-slate-600">
              Verified available across every night ({defaults.checkIn} to{" "}
              {defaults.checkOut}).
            </p>

            <div className="mt-6 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {featuredHotels.map((hotel) => (
                <Link
                  key={hotel.id}
                  href={`/hotels/${hotel.id}?checkIn=${defaults.checkIn}&checkOut=${defaults.checkOut}&guests=2&rooms=1`}
                  className="group overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition hover:border-blue-300 hover:shadow-md"
                >
                  <div className="relative h-48 overflow-hidden bg-slate-100">
                    <Image
                      src={hotel.imageUrl}
                      alt={hotel.name}
                      fill
                      sizes="(max-width: 640px) 100vw, 33vw"
                      className="object-cover transition duration-300 group-hover:scale-105"
                    />
                    <span className="absolute right-3 top-3 rounded-lg bg-blue-600 px-2 py-1 text-xs font-extrabold text-white shadow">
                      {hotel.guestScore.toFixed(1)}
                    </span>
                  </div>
                  <div className="p-4">
                    <div className="flex items-center gap-1 text-amber-400">
                      {Array.from({ length: hotel.starRating }).map((_, idx) => (
                        <Star key={idx} className="h-3 w-3 fill-current" />
                      ))}
                      <span className="ml-1 text-[11px] font-medium text-slate-500">
                        {hotel.propertyType}
                      </span>
                    </div>
                    <h3 className="mt-1 truncate text-base font-bold text-slate-900 group-hover:text-blue-600">
                      {hotel.name}
                    </h3>
                    <p className="mt-1 flex items-center gap-1 text-xs text-slate-500">
                      <MapPin className="h-3.5 w-3.5 text-blue-600" />
                      <span>
                        {hotel.neighborhood}, {hotel.city}
                      </span>
                    </p>
                    <div className="mt-3 flex items-baseline justify-between border-t border-slate-100 pt-3">
                      <span className="text-xs text-slate-500">
                        {hotel.minAvailableRooms} rooms left
                      </span>
                      <span className="text-lg font-extrabold text-rose-600">
                        {formatINR(hotel.startingNightlyPriceMinor)}
                        <span className="text-xs font-normal text-slate-500">
                          /night
                        </span>
                      </span>
                    </div>
                  </div>
                </Link>
              ))}
            </div>
          </div>
        </section>
      )}

      {/* Architecture Highlights Strip */}
      <section className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <Database className="h-5 w-5 text-blue-600" />
            <h3 className="mt-2 text-sm font-bold text-slate-900">
              Date-Partitioned Inventory
            </h3>
            <p className="mt-1 text-xs text-slate-600">
              Every search checks discrete daily rows in{" "}
              <code className="rounded bg-slate-100 px-1">room_inventory_by_date</code>{" "}
              across every night in your stay range.
            </p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <ShieldCheck className="h-5 w-5 text-emerald-600" />
            <h3 className="mt-2 text-sm font-bold text-slate-900">
              Next Phase: Zero Oversell Locking
            </h3>
            <p className="mt-1 text-xs text-slate-600">
              Part 2 adds 10-minute Redis checkout holds, ascending-date Postgres{" "}
              <code className="rounded bg-slate-100 px-1">SELECT FOR UPDATE</code>,
              and idempotent booking confirmation.
            </p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <Sparkles className="h-5 w-5 text-amber-600" />
            <h3 className="mt-2 text-sm font-bold text-slate-900">
              Later: A/B Ranking, Kafka &amp; Ollama AI
            </h3>
            <p className="mt-1 text-xs text-slate-600">
              Parts 3–5 layer on Murmur3 A/B ranking experiments, async fulfillment
              events, and local RTX Ollama -&gt; Gemini natural-language search.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
}

const FALLBACK_DESTINATIONS: CitySummary[] = [
  {
    city: "Goa",
    country: "India",
    hotelCount: 16,
    minNightlyPriceMinor: 340000,
    currency: "INR",
    sampleImageUrl: "/images/hotels/goa-resort.jpg",
    tagline: "Golden beaches, cliffside sunsets & boutique resorts",
    centerLat: 15.54,
    centerLng: 73.75,
  },
  {
    city: "Mumbai",
    country: "India",
    hotelCount: 14,
    minNightlyPriceMinor: 460000,
    currency: "INR",
    sampleImageUrl: "/images/hotels/mumbai-luxury.jpg",
    tagline: "Marine Drive skyline, Colaba heritage & coastal luxury",
    centerLat: 18.94,
    centerLng: 72.82,
  },
  {
    city: "Bengaluru",
    country: "India",
    hotelCount: 14,
    minNightlyPriceMinor: 310000,
    currency: "INR",
    sampleImageUrl: "/images/hotels/bengaluru-boutique.jpg",
    tagline: "Garden city retreats, design hotels & Indiranagar hubs",
    centerLat: 12.97,
    centerLng: 77.59,
  },
  {
    city: "Jaipur",
    country: "India",
    hotelCount: 12,
    minNightlyPriceMinor: 360000,
    currency: "INR",
    sampleImageUrl: "/images/hotels/jaipur-palace.jpg",
    tagline: "Pink sandstone palaces, courtyards & royal hospitality",
    centerLat: 26.91,
    centerLng: 75.78,
  },
  {
    city: "Udaipur",
    country: "India",
    hotelCount: 12,
    minNightlyPriceMinor: 390000,
    currency: "INR",
    sampleImageUrl: "/images/hotels/udaipur-lake.jpg",
    tagline: "Romantic lakefront havelis & Aravalli sunset views",
    centerLat: 24.57,
    centerLng: 73.68,
  },
  {
    city: "Manali",
    country: "India",
    hotelCount: 10,
    minNightlyPriceMinor: 280000,
    currency: "INR",
    sampleImageUrl: "/images/hotels/manali-chalet.jpg",
    tagline: "Pine-forested alpine chalets & Himalayan peak views",
    centerLat: 32.24,
    centerLng: 77.18,
  },
  {
    city: "Kochi",
    country: "India",
    hotelCount: 10,
    minNightlyPriceMinor: 320000,
    currency: "INR",
    sampleImageUrl: "/images/hotels/kerala-backwaters.jpg",
    tagline: "Backwater pool villas, lagoons & historic Fort Kochi",
    centerLat: 9.96,
    centerLng: 76.24,
  },
  {
    city: "Bangkok",
    country: "Thailand",
    hotelCount: 12,
    minNightlyPriceMinor: 420000,
    currency: "INR",
    sampleImageUrl: "/images/hotels/bangkok-riverside.jpg",
    tagline: "Chao Phraya sky pools, vibrant streets & luxury towers",
    centerLat: 13.72,
    centerLng: 100.51,
  },
];
