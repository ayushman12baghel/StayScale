"use client";

import { Suspense, useEffect, useState } from "react";
import { useParams, useSearchParams } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import {
  MapPin,
  Star,
  CheckCircle2,
  Coffee,
  Users,
  Maximize2,
  BedDouble,
  ExternalLink,
  ChevronLeft,
  ShieldCheck,
  X,
  AlertCircle,
} from "lucide-react";
import {
  fetchHotelDetail,
  formatINR,
  getDefaultDates,
  scoreLabel,
} from "@/lib/api";
import { HotelDetail, RoomType } from "@/lib/types";

function HotelDetailInner() {
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const defaults = getDefaultDates();

  const checkIn = searchParams.get("checkIn") || defaults.checkIn;
  const checkOut = searchParams.get("checkOut") || defaults.checkOut;
  const guests = Number(searchParams.get("guests") || 2);
  const rooms = Number(searchParams.get("rooms") || 1);

  // Derive loading by comparing the current URL params key to the last completed fetch key.
  const requestKey = `${params.id ?? ""}|${checkIn}|${checkOut}|${guests}|${rooms}`;
  const [fetchState, setFetchState] = useState<{
    key: string;
    hotel: HotelDetail | null;
    error: string | null;
  }>({ key: "", hotel: null, error: null });
  const [selectedPhoto, setSelectedPhoto] = useState<string | null>(null);
  const [previewRoom, setPreviewRoom] = useState<RoomType | null>(null);

  const loading = fetchState.key !== requestKey;
  const hotel = fetchState.hotel;
  const error = fetchState.error;
  const activePhoto = selectedPhoto || hotel?.imageUrl || "";

  useEffect(() => {
    if (!params.id) return;
    let active = true;
    fetchHotelDetail(params.id, checkIn, checkOut, guests, rooms)
      .then((data) => {
        if (active) {
          setFetchState({ key: requestKey, hotel: data, error: null });
          setSelectedPhoto(data.imageUrl);
        }
      })
      .catch((err: Error) => {
        if (active) {
          setFetchState({ key: requestKey, hotel: null, error: err.message });
        }
      });
    return () => {
      active = false;
    };
  }, [requestKey, params.id, checkIn, checkOut, guests, rooms]);

  if (loading) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
        <div className="h-80 animate-pulse rounded-2xl bg-slate-200" />
      </div>
    );
  }

  if (error || !hotel) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-12 sm:px-6">
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-rose-900">
          <h1 className="text-lg font-bold">Unable to load property</h1>
          <p className="mt-1 text-sm">{error || "Hotel not found"}</p>
          <Link
            href="/search"
            className="mt-4 inline-flex items-center gap-1 text-sm font-bold text-blue-600 hover:underline"
          >
            <ChevronLeft className="h-4 w-4" />
            Back to Search
          </Link>
        </div>
      </div>
    );
  }

  const gallery = Array.from(
    new Set([hotel.imageUrl, ...(hotel.galleryUrls || [])])
  );

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
      {/* Breadcrumb */}
      <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
        <Link href="/" className="hover:text-blue-600">
          Home
        </Link>
        <span>/</span>
        <Link
          href={`/search?city=${encodeURIComponent(hotel.city)}&checkIn=${checkIn}&checkOut=${checkOut}&guests=${guests}&rooms=${rooms}`}
          className="hover:text-blue-600"
        >
          {hotel.city}
        </Link>
        <span>/</span>
        <span className="font-medium text-slate-700">{hotel.neighborhood}</span>
        <span>/</span>
        <span className="font-semibold text-slate-900">{hotel.name}</span>
      </div>

      {/* Gallery */}
      <div className="mt-4 grid grid-cols-1 gap-3 lg:grid-cols-12">
        <div className="relative h-72 overflow-hidden rounded-2xl bg-slate-100 sm:h-96 lg:col-span-8">
          <Image
            src={activePhoto || hotel.imageUrl}
            alt={hotel.name}
            fill
            sizes="(max-width: 1024px) 100vw, 66vw"
            className="object-cover"
          />
          <span className="absolute left-4 top-4 rounded-full bg-slate-900/75 px-3 py-1 text-xs font-semibold text-white backdrop-blur">
            {hotel.propertyType}
          </span>
        </div>

        <div className="grid grid-cols-4 gap-3 lg:col-span-4 lg:grid-cols-2">
          {gallery.slice(0, 4).map((imgUrl, idx) => (
            <button
              key={imgUrl + idx}
              type="button"
              onClick={() => setSelectedPhoto(imgUrl)}
              className={`relative h-24 overflow-hidden rounded-xl border-2 transition sm:h-46 ${
                activePhoto === imgUrl
                  ? "border-blue-600 ring-2 ring-blue-200"
                  : "border-transparent opacity-85 hover:opacity-100"
              }`}
            >
              <Image
                src={imgUrl}
                alt={`${hotel.name} photo ${idx + 1}`}
                fill
                sizes="200px"
                className="object-cover"
              />
            </button>
          ))}
        </div>
      </div>

      {/* Hotel Title + Location Sidebar */}
      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-12">
        <div className="lg:col-span-8 space-y-6">
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <div className="flex items-center gap-1 text-amber-400">
                  {Array.from({ length: hotel.starRating }).map((_, idx) => (
                    <Star key={idx} className="h-4 w-4 fill-current" />
                  ))}
                  <span className="ml-1 text-xs font-semibold text-slate-500">
                    {hotel.starRating}-Star {hotel.propertyType}
                  </span>
                </div>
                <h1 className="mt-1 text-2xl font-extrabold text-slate-900 sm:text-3xl">
                  {hotel.name}
                </h1>
                <p className="mt-1 flex items-center gap-1.5 text-sm text-slate-600">
                  <MapPin className="h-4 w-4 shrink-0 text-blue-600" />
                  <span>{hotel.address}</span>
                </p>
              </div>

              <div className="flex items-center gap-3 rounded-xl bg-blue-50 px-4 py-2.5">
                <div className="text-right">
                  <p className="text-sm font-extrabold text-slate-900">
                    {scoreLabel(hotel.guestScore)}
                  </p>
                  <p className="text-xs text-slate-500">
                    {hotel.reviewCount.toLocaleString()} verified reviews
                  </p>
                </div>
                <div className="flex h-11 w-11 items-center justify-center rounded-xl rounded-bl-none bg-blue-600 text-base font-extrabold text-white">
                  {hotel.guestScore.toFixed(1)}
                </div>
              </div>
            </div>

            <p className="mt-4 text-sm leading-relaxed text-slate-700">
              {hotel.description}
            </p>

            <div className="mt-5 border-t border-slate-100 pt-4">
              <h2 className="text-xs font-bold uppercase tracking-wide text-slate-500">
                Highlights &amp; Amenities
              </h2>
              <div className="mt-2.5 flex flex-wrap gap-2">
                {hotel.amenities.map((amenity) => (
                  <span
                    key={amenity}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-700"
                  >
                    <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                    {amenity}
                  </span>
                ))}
              </div>
            </div>
          </div>

          {/* Room Selection */}
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h2 className="text-xl font-extrabold text-slate-900">
                  Select Your Room
                </h2>
                <p className="text-xs text-slate-500">
                  Showing availability for {checkIn} to {checkOut} (
                  {hotel.totalNights} {hotel.totalNights === 1 ? "night" : "nights"} ·{" "}
                  {guests} guests · {rooms} {rooms === 1 ? "room" : "rooms"})
                </p>
              </div>
            </div>

            {hotel.roomTypes.map((room) => {
              // Both room inventory count AND per-room guest capacity must satisfy the search.
              const enoughRooms = room.availableRooms >= rooms;
              const fitsGuests =
                room.maxGuests >= Math.ceil(guests / rooms);
              const bookable = enoughRooms && fitsGuests;

              return (
                <div
                  key={room.id}
                  className={`flex flex-col overflow-hidden rounded-2xl border bg-white shadow-sm sm:flex-row ${
                    bookable ? "border-slate-200" : "border-slate-200 opacity-75"
                  }`}
                >
                  <div className="relative h-48 w-full shrink-0 bg-slate-100 sm:h-auto sm:w-56">
                    <Image
                      src={room.imageUrl}
                      alt={room.name}
                      fill
                      sizes="(max-width: 640px) 100vw, 224px"
                      className="object-cover"
                    />
                    {room.availableRooms > 0 && room.availableRooms <= 3 && (
                      <span className="absolute bottom-2.5 left-2.5 inline-flex items-center gap-1 rounded-full bg-rose-600 px-2.5 py-0.5 text-[11px] font-bold text-white">
                        <AlertCircle className="h-3 w-3" />
                        Only {room.availableRooms} left
                      </span>
                    )}
                  </div>

                  <div className="flex flex-1 flex-col justify-between p-5">
                    <div className="flex flex-col gap-4 md:flex-row md:justify-between">
                      <div className="flex-1">
                        <h3 className="text-base font-bold text-slate-900">
                          {room.name}
                        </h3>
                        <p className="mt-1 text-xs text-slate-600">
                          {room.description}
                        </p>

                        <div className="mt-3 flex flex-wrap gap-3 text-xs text-slate-600">
                          <span className="inline-flex items-center gap-1">
                            <BedDouble className="h-3.5 w-3.5 text-slate-400" />
                            {room.bedType}
                          </span>
                          <span className="inline-flex items-center gap-1">
                            <Maximize2 className="h-3.5 w-3.5 text-slate-400" />
                            {room.roomSizeSqm} m²
                          </span>
                          <span className="inline-flex items-center gap-1">
                            <Users className="h-3.5 w-3.5 text-slate-400" />
                            Max {room.maxGuests} guests/room
                          </span>
                        </div>

                        <div className="mt-3 flex flex-wrap gap-3 text-xs">
                          {room.freeCancellation && (
                            <span className="inline-flex items-center gap-1 font-semibold text-emerald-700">
                              <CheckCircle2 className="h-3.5 w-3.5" />
                              Free cancellation
                            </span>
                          )}
                          {room.breakfastIncluded && (
                            <span className="inline-flex items-center gap-1 font-semibold text-amber-700">
                              <Coffee className="h-3.5 w-3.5" />
                              Breakfast included
                            </span>
                          )}
                        </div>
                      </div>

                      <div className="flex shrink-0 flex-col items-end justify-between border-t border-slate-100 pt-3 md:w-52 md:border-l md:border-t-0 md:pl-4 md:pt-0">
                        <div className="text-right">
                          <p className="text-[11px] text-slate-500">
                            {formatINR(room.nightlyPriceMinor)} / night
                          </p>
                          <p className="text-2xl font-extrabold text-rose-600">
                            {formatINR(room.totalStayPriceMinor)}
                          </p>
                          <p className="text-[11px] text-slate-500">
                            Total for {hotel.totalNights} nights, {rooms}{" "}
                            {rooms === 1 ? "room" : "rooms"}
                          </p>
                        </div>

                        {bookable ? (
                          <button
                            type="button"
                            onClick={() => setPreviewRoom(room)}
                            className="mt-3 w-full rounded-xl bg-blue-600 px-4 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-blue-700 transition"
                          >
                            Select &amp; Reserve ({room.availableRooms} left)
                          </button>
                        ) : (
                          <div className="mt-3 w-full rounded-xl bg-slate-100 px-3 py-2 text-center text-xs font-bold text-slate-500">
                            {!enoughRooms
                              ? "Sold out for selected dates"
                              : `Exceeds max ${room.maxGuests} guests/room`}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Location Card Sidebar */}
        <aside className="lg:col-span-4">
          <div className="sticky top-20 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-bold text-slate-900">
              Location &amp; Coordinates
            </h2>
            <p className="mt-1 text-xs text-slate-600">
              {hotel.neighborhood}, {hotel.city}, {hotel.country}
            </p>

            <div className="mt-4 rounded-xl border border-slate-200 bg-gradient-to-br from-sky-50 to-blue-100 p-4">
              <div className="flex items-center justify-between">
                <span className="inline-flex items-center gap-1.5 rounded-full bg-white px-2.5 py-1 text-xs font-bold text-blue-700 shadow-sm">
                  <MapPin className="h-3.5 w-3.5 text-rose-600" />
                  {hotel.distanceToCenterKm} km to {hotel.landmarkName}
                </span>
              </div>
              <p className="mt-4 font-mono text-xs text-slate-700">
                Lat: {hotel.latitude.toFixed(4)}° · Lng: {hotel.longitude.toFixed(4)}°
              </p>
              <a
                href={`https://www.openstreetmap.org/?mlat=${hotel.latitude}&mlon=${hotel.longitude}#map=15/${hotel.latitude}/${hotel.longitude}`}
                target="_blank"
                rel="noreferrer"
                className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-white px-3 py-1.5 text-xs font-bold text-slate-800 shadow-sm hover:text-blue-600"
              >
                <span>Open Area in OpenStreetMap</span>
                <ExternalLink className="h-3.5 w-3.5" />
              </a>
            </div>

            <div className="mt-4 rounded-xl bg-slate-50 p-3.5 text-xs text-slate-600">
              <p className="font-bold text-slate-800">Part 1 Availability Verified</p>
              <p className="mt-1">
                Every available room above passed multi-night inspection across{" "}
                <span className="font-semibold">{hotel.totalNights} nights</span>{" "}
                in <code className="rounded bg-white px-1">room_inventory_by_date</code>.
              </p>
            </div>
          </div>
        </aside>
      </div>

      {/* Part 2 Preview Modal when clicking Reserve */}
      {previewRoom && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-2 text-blue-600">
                <ShieldCheck className="h-5 w-5" />
                <h3 className="text-base font-extrabold text-slate-900">
                  Ready for Part 2 Checkout Hold!
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setPreviewRoom(null)}
                className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="mt-4 rounded-xl bg-slate-50 p-3.5 text-xs text-slate-700 space-y-1">
              <p className="font-bold text-slate-900">{hotel.name}</p>
              <p>{previewRoom.name}</p>
              <p>
                {checkIn} to {checkOut} ({hotel.totalNights} nights · {rooms}{" "}
                {rooms === 1 ? "room" : "rooms"})
              </p>
              <p className="pt-1 text-sm font-extrabold text-rose-600">
                Total: {formatINR(previewRoom.totalStayPriceMinor)}
              </p>
            </div>

            <p className="mt-4 text-xs leading-relaxed text-slate-600">
              In <strong>Part 2</strong>, clicking this button will call{" "}
              <code className="rounded bg-slate-100 px-1">POST /api/v1/holds</code>{" "}
              with an <code className="rounded bg-slate-100 px-1">Idempotency-Key</code>,
              acquire ordered Redis locks, start a live{" "}
              <strong>10-minute countdown timer</strong>, and lock Postgres inventory
              rows with <code className="rounded bg-slate-100 px-1">SELECT FOR UPDATE</code>{" "}
              upon confirmation.
            </p>

            <button
              type="button"
              onClick={() => setPreviewRoom(null)}
              className="mt-5 w-full rounded-xl bg-slate-900 py-2.5 text-xs font-bold text-white hover:bg-slate-800"
            >
              Got it
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default function HotelDetailPage() {
  return (
    <Suspense
      fallback={
        <div className="mx-auto max-w-7xl px-4 py-12 text-sm text-slate-500">
          Loading property details...
        </div>
      }
    >
      <HotelDetailInner />
    </Suspense>
  );
}
