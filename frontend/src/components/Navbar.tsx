"use client";

import Link from "next/link";
import { Building2, Compass, CalendarCheck2, Database } from "lucide-react";

export function Navbar() {
  return (
    <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/95 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
        <Link href="/" className="flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-600 text-white shadow-sm">
            <Building2 className="h-5 w-5" />
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <span className="text-lg font-extrabold tracking-tight text-slate-900">
                StayScale
              </span>
              <span className="flex items-center gap-0.5" aria-hidden="true">
                <span className="h-1.5 w-1.5 rounded-full bg-rose-500" />
                <span className="h-1.5 w-1.5 rounded-full bg-amber-400" />
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                <span className="h-1.5 w-1.5 rounded-full bg-blue-500" />
              </span>
            </div>
            <p className="text-[11px] font-medium text-slate-500 leading-none">
              Agoda-style End-to-End Lab
            </p>
          </div>
        </Link>

        <nav className="flex items-center gap-2 sm:gap-4 text-sm font-medium">
          <Link
            href="/search"
            className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-slate-700 hover:bg-slate-100 hover:text-blue-600 transition-colors"
          >
            <Compass className="h-4 w-4" />
            <span>Explore Stays</span>
          </Link>

          <span
            title="Checkout holds & My Bookings unlock in Part 2"
            className="hidden sm:inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-slate-400 cursor-not-allowed"
          >
            <CalendarCheck2 className="h-4 w-4" />
            <span>My Bookings</span>
            <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-semibold text-slate-500">
              Part 2
            </span>
          </span>

          <div className="hidden md:flex items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700">
            <Database className="h-3.5 w-3.5" />
            <span>Part 1 · Postgres Only</span>
          </div>
        </nav>
      </div>
    </header>
  );
}
