"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import {
  Clock,
  ShieldCheck,
  CheckCircle2,
  CreditCard,
  AlertTriangle,
  RefreshCw,
  ArrowRight,
  ChevronLeft,
  Lock,
} from "lucide-react";
import {
  fetchHold,
  confirmBooking,
  formatINR,
} from "@/lib/api";
import { getStayScaleUserId, newIdempotencyKey } from "@/lib/user";
import { BookingResponse, HoldResponse } from "@/lib/types";

export default function CheckoutHoldPage() {
  const params = useParams<{ holdId: string }>();
  const holdId = params.holdId;

  const [holdState, setHoldState] = useState<{
    loadedId: string;
    hold: HoldResponse | null;
    error: string | null;
  }>({ loadedId: "", hold: null, error: null });

  const [remainingSeconds, setRemainingSeconds] = useState<number>(600);
  const [guestName, setGuestName] = useState("Ayush Baghel");
  const [guestEmail, setGuestEmail] = useState("ayush@stayscale.dev");
  const [paymentToken, setPaymentToken] = useState("tok_visa_4242");

  // Keep ONE stable Idempotency-Key across the checkout form so double-clicks or network retries never double-book.
  const [confirmKey] = useState(() => newIdempotencyKey("confirm"));
  const [submitting, setSubmitting] = useState(false);
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [confirmedResult, setConfirmedResult] = useState<{
    booking: BookingResponse;
    replayed: boolean;
    traceId: string | null;
  } | null>(null);

  const loading = holdState.loadedId !== holdId;
  const hold = holdState.hold;
  const loadError = holdState.error;

  useEffect(() => {
    if (!holdId) return;
    let active = true;
    fetchHold(holdId)
      .then((data) => {
        if (active) {
          setHoldState({ loadedId: holdId, hold: data, error: null });
          setRemainingSeconds(Math.max(0, data.ttlSeconds));
        }
      })
      .catch((err: Error) => {
        if (active) {
          setHoldState({ loadedId: holdId, hold: null, error: err.message });
        }
      });
    return () => {
      active = false;
    };
  }, [holdId]);

  // Live 1-second countdown synced directly against server hold.expiresAt timestamp.
  useEffect(() => {
    if (!hold || confirmedResult) return;
    const targetMs = new Date(hold.expiresAt).getTime();
    const tick = () => {
      const secs = Math.max(0, Math.floor((targetMs - Date.now()) / 1000));
      setRemainingSeconds(secs);
    };
    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [hold, confirmedResult]);

  const submitConfirmation = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!hold || remainingSeconds <= 0) return;

    setSubmitting(true);
    setConfirmError(null);
    try {
      const userId = getStayScaleUserId();
      const res = await confirmBooking(
        {
          holdId: hold.holdId,
          guestName,
          guestEmail,
          paymentToken,
        },
        confirmKey,
        userId
      );
      setConfirmedResult(res);
    } catch (err) {
      setConfirmError(
        err instanceof Error ? err.message : "Booking confirmation failed"
      );
    } finally {
      setSubmitting(false);
    }
  };

  const mm = String(Math.floor(remainingSeconds / 60)).padStart(2, "0");
  const ss = String(remainingSeconds % 60).padStart(2, "0");
  const isExpired = Boolean(hold) && remainingSeconds <= 0;

  if (loading) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-12 sm:px-6">
        <div className="h-72 animate-pulse rounded-2xl bg-slate-200" />
      </div>
    );
  }

  if (loadError || !hold) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-12 sm:px-6">
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-rose-900">
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-rose-600" />
            <h1 className="text-lg font-extrabold">Checkout Hold Expired or Invalid</h1>
          </div>
          <p className="mt-2 text-sm">
            {loadError || "This 10-minute Redis hold is no longer active."}
          </p>
          <Link
            href="/search"
            className="mt-4 inline-flex items-center gap-1.5 rounded-xl bg-blue-600 px-4 py-2 text-xs font-bold text-white hover:bg-blue-700"
          >
            <ChevronLeft className="h-4 w-4" />
            Back to Search Stays
          </Link>
        </div>
      </div>
    );
  }

  if (confirmedResult) {
    const { booking, replayed, traceId } = confirmedResult;
    return (
      <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
        <div className="rounded-2xl border border-emerald-200 bg-white p-6 shadow-md sm:p-8">
          <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-100 pb-5">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-700">
                <CheckCircle2 className="h-7 w-7" />
              </div>
              <div>
                <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-[11px] font-bold text-emerald-800">
                  {booking.status}
                </span>
                <h1 className="mt-1 text-2xl font-extrabold text-slate-900">
                  Reservation Confirmed!
                </h1>
                <p className="text-xs text-slate-500">
                  Booking ID: <code className="font-mono text-slate-700">{booking.id}</code>
                </p>
              </div>
            </div>

            <div className="text-right">
              <p className="text-xs text-slate-500">Total Charged</p>
              <p className="text-2xl font-extrabold text-slate-900">
                {formatINR(booking.totalPriceMinor)}
              </p>
              <p className="text-[11px] text-slate-500">{booking.paymentReference}</p>
            </div>
          </div>

          <div className="mt-5 flex flex-col gap-4 sm:flex-row">
            <div className="relative h-32 w-full shrink-0 overflow-hidden rounded-xl sm:w-44">
              <Image
                src={booking.hotelImageUrl}
                alt={booking.hotelName}
                fill
                sizes="176px"
                className="object-cover"
              />
            </div>
            <div className="flex-1 text-sm space-y-1">
              <h2 className="font-extrabold text-slate-900">{booking.hotelName}</h2>
              <p className="text-xs text-slate-600">
                {booking.hotelNeighborhood}, {booking.hotelCity}
              </p>
              <p className="text-xs font-semibold text-slate-800">{booking.roomName}</p>
              <p className="text-xs text-slate-600">
                {booking.checkIn} to {booking.checkOut} ({booking.totalNights}{" "}
                {booking.totalNights === 1 ? "night" : "nights"} · {booking.roomsCount}{" "}
                {booking.roomsCount === 1 ? "room" : "rooms"} · {booking.guestsCount} guests)
              </p>
              <p className="text-xs text-slate-600">
                Guest: {booking.guestName} ({booking.guestEmail})
              </p>
            </div>
          </div>

          {/* Idempotency Verification Box */}
          <div className="mt-6 rounded-xl border border-blue-200 bg-blue-50 p-4 text-xs text-blue-950">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2 font-bold">
                <ShieldCheck className="h-4 w-4 text-blue-700" />
                <span>Idempotency-Key Protection Verified</span>
              </div>
              <span
                className={`rounded-full px-2.5 py-0.5 text-[11px] font-extrabold ${
                  replayed
                    ? "bg-amber-200 text-amber-900"
                    : "bg-emerald-200 text-emerald-900"
                }`}
              >
                X-Idempotency-Replayed: {String(replayed)}
              </span>
            </div>
            <p className="mt-1.5 text-blue-900">
              Key: <code className="rounded bg-white/80 px-1">{confirmKey}</code>
              {traceId && (
                <>
                  {" "}
                  · Trace: <code className="rounded bg-white/80 px-1">{traceId}</code>
                </>
              )}
            </p>
            <button
              type="button"
              disabled={submitting}
              onClick={() => submitConfirmation()}
              className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-white px-3 py-1.5 text-xs font-bold text-blue-700 shadow-sm ring-1 ring-blue-300 hover:bg-blue-100"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${submitting ? "animate-spin" : ""}`} />
              <span>Test Network Retry (Replay Same Idempotency-Key)</span>
            </button>
          </div>

          <div className="mt-6 flex flex-wrap gap-3">
            <Link
              href="/bookings"
              className="inline-flex items-center gap-1.5 rounded-xl bg-blue-600 px-5 py-2.5 text-xs font-bold text-white hover:bg-blue-700"
            >
              <span>View in My Bookings</span>
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
            <Link
              href="/search"
              className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-5 py-2.5 text-xs font-bold text-slate-700 hover:bg-slate-50"
            >
              Search More Stays
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-6 sm:px-6">
      {/* Live Countdown Banner */}
      <div
        className={`flex flex-wrap items-center justify-between gap-3 rounded-2xl border px-5 py-3.5 shadow-sm ${
          isExpired
            ? "border-rose-300 bg-rose-50 text-rose-900"
            : remainingSeconds < 120
              ? "border-amber-300 bg-amber-50 text-amber-950"
              : "border-blue-200 bg-blue-50 text-blue-950"
        }`}
      >
        <div className="flex items-center gap-2.5 text-sm font-bold">
          <Clock className="h-5 w-5 shrink-0" />
          <span>
            {isExpired
              ? "Your 10-minute checkout hold has expired."
              : "Room & price locked via Redis distributed hold! Complete booking before timer expires:"}
          </span>
        </div>
        <div className="rounded-xl bg-white px-4 py-1.5 font-mono text-lg font-extrabold tracking-wider shadow-xs">
          {mm}:{ss}
        </div>
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-12">
        {/* Traveler & Payment Form */}
        <form
          onSubmit={submitConfirmation}
          className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm lg:col-span-7 space-y-5"
        >
          <div>
            <h1 className="text-xl font-extrabold text-slate-900">
              Complete Your Reservation
            </h1>
            <p className="mt-1 text-xs text-slate-500">
              Hold ID: <code className="rounded bg-slate-100 px-1">{hold.holdId}</code> ·
              Protected by PostgreSQL <code className="rounded bg-slate-100 px-1">SELECT FOR UPDATE</code>{" "}
              and <code className="rounded bg-slate-100 px-1">Idempotency-Key</code>.
            </p>
          </div>

          {confirmError && (
            <div className="flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 p-3.5 text-xs text-rose-900">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" />
              <span>{confirmError}</span>
            </div>
          )}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-semibold text-slate-700">
                Full Name (Primary Guest)
              </label>
              <input
                type="text"
                required
                value={guestName}
                onChange={(e) => setGuestName(e.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-sm focus:border-blue-500 focus:bg-white focus:outline-none"
              />
            </div>

            <div>
              <label className="mb-1 block text-xs font-semibold text-slate-700">
                Confirmation Email
              </label>
              <input
                type="email"
                required
                value={guestEmail}
                onChange={(e) => setGuestEmail(e.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-sm focus:border-blue-500 focus:bg-white focus:outline-none"
              />
            </div>
          </div>

          <div>
            <label className="mb-1.5 block text-xs font-semibold text-slate-700">
              Demo Payment Token
            </label>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
              {[
                { token: "tok_visa_4242", label: "Visa •••• 4242" },
                { token: "tok_mastercard_8819", label: "Mastercard •••• 8819" },
                { token: "tok_upi_stayscale", label: "UPI Instant Pay" },
              ].map((opt) => (
                <button
                  key={opt.token}
                  type="button"
                  onClick={() => setPaymentToken(opt.token)}
                  className={`flex items-center gap-2 rounded-xl border px-3 py-2.5 text-xs font-bold transition ${
                    paymentToken === opt.token
                      ? "border-blue-600 bg-blue-50 text-blue-700"
                      : "border-slate-200 bg-white text-slate-700 hover:bg-slate-50"
                  }`}
                >
                  <CreditCard className="h-4 w-4 shrink-0" />
                  <span>{opt.label}</span>
                </button>
              ))}
            </div>
          </div>

          {isExpired ? (
            <Link
              href={`/hotels/${hold.hotelId}?checkIn=${hold.checkIn}&checkOut=${hold.checkOut}&guests=${hold.guests}&rooms=${hold.rooms}`}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-rose-600 py-3 text-sm font-bold text-white hover:bg-rose-700"
            >
              Hold Expired — Return to Hotel &amp; Re-reserve
            </Link>
          ) : (
            <button
              type="submit"
              disabled={submitting}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 py-3 text-sm font-bold text-white shadow-sm hover:bg-blue-700 disabled:opacity-60 transition"
            >
              <Lock className="h-4 w-4" />
              <span>
                {submitting
                  ? "Confirming Atomically..."
                  : `Confirm & Pay ${formatINR(hold.pricing.totalStayPriceMinor)}`}
              </span>
            </button>
          )}
        </form>

        {/* Locked Price Summary Sidebar */}
        <aside className="lg:col-span-5">
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex gap-3 border-b border-slate-100 pb-4">
              <div className="relative h-20 w-24 shrink-0 overflow-hidden rounded-xl">
                <Image
                  src={hold.hotelImageUrl}
                  alt={hold.hotelName}
                  fill
                  sizes="96px"
                  className="object-cover"
                />
              </div>
              <div className="min-w-0">
                <h2 className="truncate text-base font-extrabold text-slate-900">
                  {hold.hotelName}
                </h2>
                <p className="text-xs text-slate-500">
                  {hold.hotelNeighborhood}, {hold.hotelCity}
                </p>
                <p className="mt-1 text-xs font-bold text-blue-700">{hold.roomName}</p>
              </div>
            </div>

            <div className="mt-4 space-y-2 text-xs text-slate-600 border-b border-slate-100 pb-4">
              <div className="flex justify-between">
                <span>Check-in</span>
                <span className="font-bold text-slate-900">{hold.checkIn}</span>
              </div>
              <div className="flex justify-between">
                <span>Check-out</span>
                <span className="font-bold text-slate-900">{hold.checkOut}</span>
              </div>
              <div className="flex justify-between">
                <span>Stay &amp; Occupancy</span>
                <span className="font-bold text-slate-900">
                  {hold.totalNights} nights · {hold.rooms} room(s) · {hold.guests} guests
                </span>
              </div>
            </div>

            <div className="mt-4 space-y-2 text-xs">
              <div className="flex justify-between text-slate-600">
                <span>Base price ({hold.totalNights} nights × {hold.rooms} room)</span>
                <span className="font-semibold text-slate-900">
                  {formatINR(hold.pricing.totalBaseMinor)}
                </span>
              </div>

              {hold.pricing.ruleSummaries.map((rule) => (
                <div key={rule.ruleCode} className="flex justify-between">
                  <span className="text-slate-600">{rule.label}</span>
                  <span
                    className={`font-semibold ${
                      rule.amountDeltaMinor < 0
                        ? "text-emerald-700"
                        : "text-amber-700"
                    }`}
                  >
                    {rule.amountDeltaMinor >= 0 ? "+" : ""}
                    {formatINR(rule.amountDeltaMinor)}
                  </span>
                </div>
              ))}

              <div className="flex items-baseline justify-between border-t border-slate-200 pt-3">
                <span className="text-sm font-extrabold text-slate-900">
                  Locked Total Price
                </span>
                <span className="text-2xl font-extrabold text-rose-600">
                  {formatINR(hold.pricing.totalStayPriceMinor)}
                </span>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
