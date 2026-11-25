"use client";

import { useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { MapPin, Navigation, ExternalLink } from "lucide-react";
import { HotelSearchItem } from "@/lib/types";
import { formatINR } from "@/lib/api";

interface HotelMapViewProps {
  hotels: HotelSearchItem[];
  checkIn: string;
  checkOut: string;
  guests: number;
  rooms: number;
}

export function HotelMapView({
  hotels,
  checkIn,
  checkOut,
  guests,
  rooms,
}: HotelMapViewProps) {
  const [selectedId, setSelectedId] = useState<string | null>(
    hotels[0]?.id ?? null
  );

  if (hotels.length === 0) {
    return null;
  }

  // Project hotel lat/lng into normalized 10%..90% canvas coordinates so pins work locally
  // without requiring an external Maps API key or network tile service.
  const lats = hotels.map((h) => h.latitude);
  const lngs = hotels.map((h) => h.longitude);
  const minLat = Math.min(...lats);
  const maxLat = Math.max(...lats);
  const minLng = Math.min(...lngs);
  const maxLng = Math.max(...lngs);
  const latSpan = Math.max(0.01, maxLat - minLat);
  const lngSpan = Math.max(0.01, maxLng - minLng);

  const selected = hotels.find((h) => h.id === selectedId) || hotels[0];

  return (
    <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="flex items-center justify-between border-b border-slate-200 bg-slate-50 px-4 py-3">
        <div className="flex items-center gap-2 text-sm font-semibold text-slate-800">
          <Navigation className="h-4 w-4 text-blue-600" />
          <span>Interactive Property Coordinate Map ({hotels.length} pins)</span>
        </div>
        <span className="text-xs text-slate-500">Click any price pin to inspect</span>
      </div>

      <div className="relative h-96 w-full bg-gradient-to-br from-sky-50 via-blue-50 to-emerald-50 overflow-hidden">
        {/* Coordinate grid lines */}
        <div
          className="absolute inset-0 opacity-30"
          style={{
            backgroundImage:
              "linear-gradient(to right, #94a3b8 1px, transparent 1px), linear-gradient(to bottom, #94a3b8 1px, transparent 1px)",
            backgroundSize: "48px 48px",
          }}
        />

        {hotels.map((hotel) => {
          const xPct = 10 + ((hotel.longitude - minLng) / lngSpan) * 80;
          const yPct = 85 - ((hotel.latitude - minLat) / latSpan) * 70;
          const isSelected = hotel.id === selected.id;
          return (
            <button
              key={hotel.id}
              type="button"
              onClick={() => setSelectedId(hotel.id)}
              style={{ left: `${xPct}%`, top: `${yPct}%` }}
              className={`absolute -translate-x-1/2 -translate-y-1/2 rounded-full px-2.5 py-1 text-xs font-bold shadow-md transition-all ${
                isSelected
                  ? "z-20 scale-110 bg-rose-600 text-white ring-4 ring-rose-200"
                  : "z-10 bg-white text-slate-900 ring-1 ring-slate-300 hover:z-20 hover:bg-blue-600 hover:text-white"
              }`}
            >
              {formatINR(hotel.startingNightlyPriceMinor)}
            </button>
          );
        })}

        {/* Selected hotel floating preview */}
        <div className="absolute bottom-4 left-4 right-4 z-30 mx-auto max-w-md rounded-xl border border-slate-200 bg-white/95 p-3 shadow-lg backdrop-blur">
          <div className="flex gap-3">
            <div className="relative h-20 w-24 shrink-0 overflow-hidden rounded-lg">
              <Image
                src={selected.imageUrl}
                alt={selected.name}
                fill
                sizes="96px"
                className="object-cover"
              />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-start justify-between gap-2">
                <h4 className="truncate text-sm font-bold text-slate-900">
                  {selected.name}
                </h4>
                <span className="shrink-0 rounded bg-blue-600 px-1.5 py-0.5 text-xs font-bold text-white">
                  {selected.guestScore.toFixed(1)}
                </span>
              </div>
              <p className="mt-0.5 flex items-center gap-1 text-xs text-slate-500">
                <MapPin className="h-3 w-3 shrink-0 text-blue-600" />
                <span className="truncate">
                  {selected.neighborhood} · {selected.distanceToCenterKm} km from{" "}
                  {selected.landmarkName}
                </span>
              </p>
              <div className="mt-2 flex items-center justify-between">
                <span className="text-sm font-extrabold text-rose-600">
                  {formatINR(selected.startingNightlyPriceMinor)}
                  <span className="text-xs font-normal text-slate-500">/night</span>
                </span>
                <div className="flex items-center gap-2">
                  <a
                    href={`https://www.openstreetmap.org/?mlat=${selected.latitude}&mlon=${selected.longitude}#map=15/${selected.latitude}/${selected.longitude}`}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1 text-xs font-medium text-slate-600 hover:text-blue-600"
                  >
                    <span>OSM</span>
                    <ExternalLink className="h-3 w-3" />
                  </a>
                  <Link
                    href={`/hotels/${selected.id}?checkIn=${checkIn}&checkOut=${checkOut}&guests=${guests}&rooms=${rooms}`}
                    className="rounded-lg bg-blue-600 px-2.5 py-1 text-xs font-semibold text-white hover:bg-blue-700"
                  >
                    View Rooms
                  </Link>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
