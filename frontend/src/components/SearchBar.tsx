"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { MapPin, Calendar, Users, Search, Minus, Plus } from "lucide-react";
import { CitySummary } from "@/lib/types";
import { getDefaultDates } from "@/lib/api";

interface SearchBarProps {
  cities: CitySummary[];
  initialCity?: string;
  initialQuery?: string;
  initialCheckIn?: string;
  initialCheckOut?: string;
  initialGuests?: number;
  initialRooms?: number;
  compact?: boolean;
}

export function SearchBar({
  cities,
  initialCity = "",
  initialQuery = "",
  initialCheckIn,
  initialCheckOut,
  initialGuests = 2,
  initialRooms = 1,
  compact = false,
}: SearchBarProps) {
  const router = useRouter();
  const defaults = getDefaultDates();

  const [city, setCity] = useState(initialCity);
  const [query, setQuery] = useState(initialQuery);
  const [checkIn, setCheckIn] = useState(initialCheckIn || defaults.checkIn);
  const [checkOut, setCheckOut] = useState(initialCheckOut || defaults.checkOut);
  const [guests, setGuests] = useState(initialGuests);
  const [rooms, setRooms] = useState(initialRooms);
  const [guestOpen, setGuestOpen] = useState(false);

  // Compute stay nights dynamically for the pill above the Check-out date picker.
  const nights = (() => {
    const d1 = new Date(checkIn);
    const d2 = new Date(checkOut);
    const diff = Math.round((d2.getTime() - d1.getTime()) / (1000 * 60 * 60 * 24));
    return Number.isFinite(diff) && diff > 0 ? diff : 0;
  })();

  // Encode all search choices into URL query params so /search links are shareable and bookmarkable.
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setGuestOpen(false);
    const qs = new URLSearchParams();
    if (city.trim()) qs.set("city", city.trim());
    if (query.trim()) qs.set("query", query.trim());
    if (checkIn) qs.set("checkIn", checkIn);
    if (checkOut) qs.set("checkOut", checkOut);
    qs.set("guests", String(guests));
    qs.set("rooms", String(rooms));
    router.push(`/search?${qs.toString()}`);
  };

  return (
    <form
      onSubmit={handleSubmit}
      className={`rounded-2xl bg-white shadow-xl ring-1 ring-slate-200 ${
        compact ? "p-3" : "p-4 sm:p-5"
      }`}
    >
      <div className="grid grid-cols-1 gap-3 md:grid-cols-12 md:items-end">
        {/* Destination */}
        <div className="md:col-span-3">
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500">
            Destination
          </label>
          <div className="relative flex items-center">
            <MapPin className="pointer-events-none absolute left-3 h-4 w-4 text-blue-600" />
            <select
              value={city}
              onChange={(e) => setCity(e.target.value)}
              className="w-full appearance-none rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-9 pr-8 text-sm font-medium text-slate-900 focus:border-blue-500 focus:bg-white focus:outline-none"
            >
              <option value="">All Destinations (100 Stays)</option>
              {cities.map((c) => (
                <option key={c.city} value={c.city}>
                  {c.city}, {c.country} ({c.hotelCount})
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Property / Area keyword */}
        <div className="md:col-span-2">
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500">
            Area / Hotel
          </label>
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="e.g. Calangute, Lake"
            className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:bg-white focus:outline-none"
          />
        </div>

        {/* Check-in */}
        <div className="md:col-span-2">
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500">
            Check-in
          </label>
          <div className="relative flex items-center">
            <Calendar className="pointer-events-none absolute left-3 h-4 w-4 text-slate-400" />
            <input
              type="date"
              value={checkIn}
              onChange={(e) => {
                const nextIn = e.target.value;
                setCheckIn(nextIn);
                // Auto-bump check-out by +1 day so users can't accidentally pick checkOut <= checkIn.
                if (nextIn >= checkOut) {
                  const d = new Date(nextIn);
                  d.setDate(d.getDate() + 1);
                  setCheckOut(d.toISOString().slice(0, 10));
                }
              }}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-9 pr-2 text-sm font-medium text-slate-900 focus:border-blue-500 focus:bg-white focus:outline-none"
              required
            />
          </div>
        </div>

        {/* Check-out */}
        <div className="md:col-span-2">
          <div className="mb-1 flex items-center justify-between">
            <label className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              Check-out
            </label>
            {nights > 0 && (
              <span className="rounded bg-blue-50 px-1.5 py-0.5 text-[10px] font-bold text-blue-700">
                {nights} {nights === 1 ? "night" : "nights"}
              </span>
            )}
          </div>
          <div className="relative flex items-center">
            <Calendar className="pointer-events-none absolute left-3 h-4 w-4 text-slate-400" />
            <input
              type="date"
              value={checkOut}
              min={checkIn}
              onChange={(e) => setCheckOut(e.target.value)}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-9 pr-2 text-sm font-medium text-slate-900 focus:border-blue-500 focus:bg-white focus:outline-none"
              required
            />
          </div>
        </div>

        {/* Guests & Rooms */}
        <div className="relative md:col-span-2">
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500">
            Guests &amp; Rooms
          </label>
          <button
            type="button"
            onClick={() => setGuestOpen((o) => !o)}
            className="flex w-full items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-left text-sm font-medium text-slate-900 hover:bg-white"
          >
            <Users className="h-4 w-4 shrink-0 text-slate-500" />
            <span className="truncate">
              {guests} {guests === 1 ? "guest" : "guests"}, {rooms}{" "}
              {rooms === 1 ? "room" : "rooms"}
            </span>
          </button>

          {guestOpen && (
            <div className="absolute right-0 z-50 mt-2 w-64 rounded-2xl border border-slate-200 bg-white p-4 shadow-xl">
              <div className="flex items-center justify-between py-2">
                <div>
                  <p className="text-sm font-semibold text-slate-900">Guests</p>
                  <p className="text-xs text-slate-500">Max 16 travelers</p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setGuests((g) => Math.max(1, g - 1))}
                    className="flex h-7 w-7 items-center justify-center rounded-full border border-slate-300 text-slate-700 hover:bg-slate-100"
                  >
                    <Minus className="h-3.5 w-3.5" />
                  </button>
                  <span className="w-5 text-center text-sm font-bold">{guests}</span>
                  <button
                    type="button"
                    onClick={() => setGuests((g) => Math.min(16, g + 1))}
                    className="flex h-7 w-7 items-center justify-center rounded-full border border-slate-300 text-slate-700 hover:bg-slate-100"
                  >
                    <Plus className="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>

              <div className="mt-2 flex items-center justify-between border-t border-slate-100 pt-3">
                <div>
                  <p className="text-sm font-semibold text-slate-900">Rooms</p>
                  <p className="text-xs text-slate-500">Min availability checked</p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setRooms((r) => Math.max(1, r - 1))}
                    className="flex h-7 w-7 items-center justify-center rounded-full border border-slate-300 text-slate-700 hover:bg-slate-100"
                  >
                    <Minus className="h-3.5 w-3.5" />
                  </button>
                  <span className="w-5 text-center text-sm font-bold">{rooms}</span>
                  <button
                    type="button"
                    onClick={() => setRooms((r) => Math.min(8, r + 1))}
                    className="flex h-7 w-7 items-center justify-center rounded-full border border-slate-300 text-slate-700 hover:bg-slate-100"
                  >
                    <Plus className="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>

              <button
                type="button"
                onClick={() => setGuestOpen(false)}
                className="mt-3 w-full rounded-lg bg-slate-900 py-1.5 text-xs font-semibold text-white hover:bg-slate-800"
              >
                Done
              </button>
            </div>
          )}
        </div>

        {/* Submit */}
        <div className="md:col-span-1">
          <button
            type="submit"
            className="flex w-full items-center justify-center gap-1.5 rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-bold text-white shadow-sm hover:bg-blue-700 active:scale-[0.99] transition"
          >
            <Search className="h-4 w-4" />
            <span className="md:hidden">Search Stays</span>
          </button>
        </div>
      </div>
    </form>
  );
}
