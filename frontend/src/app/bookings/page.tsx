"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import {
  CalendarCheck2,
  MapPin,
  XCircle,
  CheckCircle2,
  Compass,
  Loader2,
  AlertCircle,
} from "lucide-react";
import { fetchMyBookings, cancelBooking, formatINR } from "@/lib/api";
import { getStayScaleUserId, newIdempotencyKey } from "@/lib/user";
import { BookingResponse } from "@/lib/types";

export default function MyBookingsPage() {
  const [bookings, setBookings] = useState<BookingResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  const [userId, setUserId] = useState<string>("guest-demo");

  useEffect(() => {
    const uid = getStayScaleUserId();
    let active = true;
    fetchMyBookings(uid)
      .then((list) => {
        if (active) {
          setUserId(uid);
          setBookings(list);
          setLoading(false);
        }
      })
      .catch((err: Error) => {
        if (active) {
          setUserId(uid);
          setError(err.message);
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  const handleCancel = async (bookingId: string) => {
    setCancellingId(bookingId);
    setError(null);
    try {
      const { booking: updated } = await cancelBooking(
        bookingId,
        newIdempotencyKey("cancel")
      );
      setBookings((prev) =>
        prev.map((b) => (b.id === updated.id ? updated : b))
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to cancel booking");
    } finally {
      setCancellingId(null);
    }
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-5">
        <div>
          <div className="flex items-center gap-2 text-blue-600">
            <CalendarCheck2 className="h-5 w-5" />
            <h1 className="text-2xl font-extrabold text-slate-900">
              My Bookings
            </h1>
          </div>
          <p className="mt-1 text-xs text-slate-500">
            Traveler ID: <code className="rounded bg-slate-100 px-1.5 py-0.5">{userId}</code> ·
            Cancelling atomically restores inventory counts in PostgreSQL.
          </p>
        </div>

        <Link
          href="/search"
          className="inline-flex items-center gap-1.5 rounded-xl bg-blue-600 px-4 py-2 text-xs font-bold text-white hover:bg-blue-700"
        >
          <Compass className="h-4 w-4" />
          <span>Book Another Stay</span>
        </Link>
      </div>

      {error && (
        <div className="mt-4 flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 p-4 text-xs text-rose-900">
          <AlertCircle className="h-4 w-4 shrink-0 text-rose-600" />
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <div className="mt-6 space-y-4">
          <div className="h-40 animate-pulse rounded-2xl bg-slate-200" />
          <div className="h-40 animate-pulse rounded-2xl bg-slate-200" />
        </div>
      ) : bookings.length === 0 ? (
        <div className="mt-8 rounded-2xl border border-slate-200 bg-white p-10 text-center">
          <p className="text-base font-bold text-slate-900">
            No bookings yet for your session
          </p>
          <p className="mt-1 text-xs text-slate-500">
            Pick a hotel, reserve a room (10-minute Redis hold), and confirm payment to
            see your receipt here.
          </p>
          <Link
            href="/search"
            className="mt-4 inline-flex items-center gap-1.5 rounded-xl bg-blue-600 px-4 py-2 text-xs font-bold text-white hover:bg-blue-700"
          >
            Explore Hotels
          </Link>
        </div>
      ) : (
        <div className="mt-6 space-y-4">
          {bookings.map((b) => {
            const isConfirmed = b.status === "CONFIRMED";
            const isCancelling = cancellingId === b.id;

            return (
              <div
                key={b.id}
                className="flex flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm sm:flex-row"
              >
                <div className="relative h-44 w-full shrink-0 bg-slate-100 sm:h-auto sm:w-52">
                  <Image
                    src={b.hotelImageUrl}
                    alt={b.hotelName}
                    fill
                    sizes="208px"
                    className="object-cover"
                  />
                </div>

                <div className="flex flex-1 flex-col justify-between p-5">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2">
                        <span
                          className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-bold ${
                            isConfirmed
                              ? "bg-emerald-100 text-emerald-800"
                              : "bg-slate-200 text-slate-700"
                          }`}
                        >
                          {isConfirmed ? (
                            <CheckCircle2 className="h-3 w-3" />
                          ) : (
                            <XCircle className="h-3 w-3" />
                          )}
                          {b.status}
                        </span>
                        <span className="font-mono text-[11px] text-slate-400">
                          {b.id.slice(0, 8)}
                        </span>
                      </div>

                      <Link href={`/hotels/${b.hotelId}?checkIn=${b.checkIn}&checkOut=${b.checkOut}`}>
                        <h2 className="mt-1.5 text-lg font-extrabold text-slate-900 hover:text-blue-600">
                          {b.hotelName}
                        </h2>
                      </Link>
                      <p className="flex items-center gap-1 text-xs text-slate-500">
                        <MapPin className="h-3.5 w-3.5 text-blue-600" />
                        <span>
                          {b.hotelNeighborhood}, {b.hotelCity}
                        </span>
                      </p>
                      <p className="mt-2 text-xs font-semibold text-slate-800">
                        {b.roomName} · {b.roomsCount} room(s) · {b.guestsCount} guest(s)
                      </p>
                      <p className="text-xs text-slate-600">
                        {b.checkIn} to {b.checkOut} ({b.totalNights} nights)
                      </p>

                      {b.pricingBreakdown.length > 0 && (
                        <div className="mt-2 flex flex-wrap gap-1.5">
                          {b.pricingBreakdown.map((rule) => (
                            <span
                              key={rule.ruleCode}
                              className="rounded bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-slate-700"
                            >
                              {rule.label}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>

                    <div className="flex flex-col items-end justify-between text-right">
                      <div>
                        <p className="text-[11px] text-slate-500">{b.paymentReference}</p>
                        <p className="text-xl font-extrabold text-slate-900">
                          {formatINR(b.totalPriceMinor)}
                        </p>
                      </div>

                      {isConfirmed ? (
                        <button
                          type="button"
                          disabled={cancellingId !== null}
                          onClick={() => handleCancel(b.id)}
                          className="mt-4 inline-flex items-center gap-1.5 rounded-xl border border-rose-200 bg-rose-50 px-3.5 py-2 text-xs font-bold text-rose-700 hover:bg-rose-100 disabled:opacity-50"
                        >
                          {isCancelling ? (
                            <>
                              <Loader2 className="h-3.5 w-3.5 animate-spin" />
                              <span>Cancelling...</span>
                            </>
                          ) : (
                            <>
                              <XCircle className="h-3.5 w-3.5" />
                              <span>Cancel &amp; Restore Rooms</span>
                            </>
                          )}
                        </button>
                      ) : (
                        <p className="mt-4 text-[11px] font-semibold text-slate-500">
                          Inventory restored
                        </p>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
