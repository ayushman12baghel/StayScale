import {
  CitySummary,
  HotelDetail,
  PagedSearchResponse,
  SearchParamsInput,
} from "./types";

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ||
  "http://localhost:8080";

// Backend stores all prices in integer paise (e.g. 340000 = ₹3,400) to avoid floating-point rounding drift.
export function formatINR(minorUnits: number): string {
  const major = Math.round(minorUnits / 100);
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(major);
}

/** Defaults searches to tomorrow -> +2 nights so inventory is always available out of the box. */
export function getDefaultDates(): { checkIn: string; checkOut: string } {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const checkout = new Date(tomorrow);
  checkout.setDate(checkout.getDate() + 2);
  return {
    checkIn: toIsoDate(tomorrow),
    checkOut: toIsoDate(checkout),
  };
}

export function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function scoreLabel(score: number): string {
  if (score >= 9.0) return "Exceptional";
  if (score >= 8.5) return "Excellent";
  if (score >= 8.0) return "Very Good";
  return "Good";
}

/** Extracts human-friendly RFC 7807 ProblemDetail messages returned by GlobalExceptionHandler. */
async function parseErrorMessage(res: Response): Promise<string> {
  try {
    const body = await res.json();
    return body.detail || body.title || `Request failed (${res.status})`;
  } catch {
    return `Request failed (${res.status})`;
  }
}

export async function fetchCities(): Promise<CitySummary[]> {
  // cache: "no-store" ensures Next.js always reads live database counts instead of stale build snapshots.
  const res = await fetch(`${API_BASE}/api/v1/hotels/cities`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(await parseErrorMessage(res));
  }
  return res.json();
}

export async function searchHotels(
  params: SearchParamsInput
): Promise<PagedSearchResponse> {
  const qs = new URLSearchParams();
  if (params.city) qs.set("city", params.city);
  if (params.query) qs.set("query", params.query);
  if (params.checkIn) qs.set("checkIn", params.checkIn);
  if (params.checkOut) qs.set("checkOut", params.checkOut);
  if (params.guests) qs.set("guests", String(params.guests));
  if (params.rooms) qs.set("rooms", String(params.rooms));
  if (params.maxPrice) qs.set("maxPrice", String(params.maxPrice));
  if (params.minRating) qs.set("minRating", String(params.minRating));
  if (params.minStars) qs.set("minStars", String(params.minStars));
  if (params.amenities && params.amenities.length > 0) {
    qs.set("amenities", params.amenities.join(","));
  }
  if (params.sort) qs.set("sort", params.sort);
  if (params.page !== undefined) qs.set("page", String(params.page));
  if (params.size !== undefined) qs.set("size", String(params.size));

  const res = await fetch(`${API_BASE}/api/v1/hotels/search?${qs.toString()}`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(await parseErrorMessage(res));
  }
  return res.json();
}

export async function fetchHotelDetail(
  id: string,
  checkIn?: string,
  checkOut?: string,
  guests = 2,
  rooms = 1
): Promise<HotelDetail> {
  const qs = new URLSearchParams();
  if (checkIn) qs.set("checkIn", checkIn);
  if (checkOut) qs.set("checkOut", checkOut);
  qs.set("guests", String(guests));
  qs.set("rooms", String(rooms));

  const res = await fetch(`${API_BASE}/api/v1/hotels/${id}?${qs.toString()}`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(await parseErrorMessage(res));
  }
  return res.json();
}
