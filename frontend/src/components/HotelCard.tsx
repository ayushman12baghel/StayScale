import Link from "next/link";
import Image from "next/image";
import {
  MapPin,
  Star,
  CheckCircle2,
  Coffee,
  AlertCircle,
  ArrowRight,
} from "lucide-react";
import { HotelSearchItem } from "@/lib/types";
import { formatINR, scoreLabel } from "@/lib/api";

interface HotelCardProps {
  hotel: HotelSearchItem;
  checkIn: string;
  checkOut: string;
  guests: number;
  rooms: number;
}

export function HotelCard({
  hotel,
  checkIn,
  checkOut,
  guests,
  rooms,
}: HotelCardProps) {
  const detailHref = `/hotels/${hotel.id}?checkIn=${checkIn}&checkOut=${checkOut}&guests=${guests}&rooms=${rooms}`;

  return (
    <article className="group flex flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition hover:border-blue-300 hover:shadow-md sm:flex-row">
      {/* Photo */}
      <Link
        href={detailHref}
        className="relative h-52 w-full shrink-0 overflow-hidden bg-slate-100 sm:h-auto sm:w-64"
      >
        <Image
          src={hotel.imageUrl}
          alt={hotel.name}
          fill
          sizes="(max-width: 640px) 100vw, 256px"
          className="object-cover transition duration-300 group-hover:scale-105"
        />
        <span className="absolute left-3 top-3 rounded-full bg-slate-900/75 px-2.5 py-0.5 text-[11px] font-semibold text-white backdrop-blur">
          {hotel.propertyType}
        </span>
        {hotel.minAvailableRooms <= 3 && (
          <span className="absolute bottom-3 left-3 inline-flex items-center gap-1 rounded-full bg-rose-600 px-2.5 py-0.5 text-[11px] font-bold text-white shadow">
            <AlertCircle className="h-3 w-3" />
            Only {hotel.minAvailableRooms}{" "}
            {hotel.minAvailableRooms === 1 ? "room" : "rooms"} left!
          </span>
        )}
      </Link>

      {/* Details */}
      <div className="flex flex-1 flex-col justify-between p-4 sm:p-5">
        <div className="flex flex-col gap-3 lg:flex-row lg:justify-between">
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1 text-amber-400">
              {Array.from({ length: hotel.starRating }).map((_, idx) => (
                <Star key={idx} className="h-3.5 w-3.5 fill-current" />
              ))}
              <span className="ml-1 text-xs font-semibold text-slate-500">
                {hotel.starRating}-star stay
              </span>
            </div>

            <Link href={detailHref}>
              <h3 className="mt-1 text-lg font-bold text-slate-900 group-hover:text-blue-600 transition-colors">
                {hotel.name}
              </h3>
            </Link>

            <p className="mt-1 flex flex-wrap items-center gap-1 text-xs text-slate-600">
              <MapPin className="h-3.5 w-3.5 shrink-0 text-blue-600" />
              <span className="font-semibold text-slate-800">
                {hotel.neighborhood}, {hotel.city}
              </span>
              <span className="text-slate-400">·</span>
              <span>
                {hotel.distanceToCenterKm} km from {hotel.landmarkName}
              </span>
            </p>

            {/* Amenities */}
            <div className="mt-3 flex flex-wrap gap-1.5">
              {hotel.amenities.slice(0, 5).map((amenity) => (
                <span
                  key={amenity}
                  className="rounded-md bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-700"
                >
                  {amenity}
                </span>
              ))}
            </div>

            {/* Perks */}
            <div className="mt-3 flex flex-wrap items-center gap-3 text-xs">
              {hotel.freeCancellationAvailable && (
                <span className="inline-flex items-center gap-1 font-semibold text-emerald-700">
                  <CheckCircle2 className="h-3.5 w-3.5" />
                  Free cancellation
                </span>
              )}
              {hotel.breakfastIncludedAvailable && (
                <span className="inline-flex items-center gap-1 font-semibold text-amber-700">
                  <Coffee className="h-3.5 w-3.5" />
                  Breakfast available
                </span>
              )}
            </div>
          </div>

          {/* Right column: Score + Price */}
          <div className="flex shrink-0 flex-row items-end justify-between border-t border-slate-100 pt-3 lg:w-48 lg:flex-col lg:border-l lg:border-t-0 lg:pl-4 lg:pt-0">
            <div className="flex items-center gap-2 lg:text-right">
              <div>
                <p className="text-xs font-bold text-slate-900">
                  {scoreLabel(hotel.guestScore)}
                </p>
                <p className="text-[11px] text-slate-500">
                  {hotel.reviewCount.toLocaleString()} reviews
                </p>
              </div>
              <div className="flex h-9 w-9 items-center justify-center rounded-lg rounded-bl-none bg-blue-600 text-sm font-extrabold text-white">
                {hotel.guestScore.toFixed(1)}
              </div>
            </div>

            <div className="text-right lg:mt-4">
              <p className="text-[11px] text-slate-500">Starting per night</p>
              <p className="text-2xl font-extrabold tracking-tight text-rose-600">
                {formatINR(hotel.startingNightlyPriceMinor)}
                <span className="text-xs font-semibold text-slate-500"> /night</span>
              </p>
              <p className="text-xs font-bold text-slate-800">
                Total {formatINR(hotel.totalStayPriceMinor)}{" "}
                <span className="font-normal text-slate-500">
                  ({hotel.totalNights} {hotel.totalNights === 1 ? "night" : "nights"})
                </span>
              </p>

              <Link
                href={detailHref}
                className="mt-2.5 inline-flex items-center justify-center gap-1.5 rounded-xl bg-blue-600 px-4 py-2 text-xs font-bold text-white shadow-sm hover:bg-blue-700 transition"
              >
                <span>See Availability</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            </div>
          </div>
        </div>
      </div>
    </article>
  );
}
