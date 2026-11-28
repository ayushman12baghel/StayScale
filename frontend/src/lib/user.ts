/**
 * Client-side traveler identity & idempotency helpers.
 * Persisting X-User-Id in localStorage keeps "My Bookings" (Part 2) and
 * deterministic A/B experiment bucketing (Part 3) consistent across reloads.
 */

const USER_STORAGE_KEY = "stayscale_user_id";

export function getStayScaleUserId(): string {
  if (typeof window === "undefined") {
    return "guest-demo";
  }
  let existing = window.localStorage.getItem(USER_STORAGE_KEY);
  if (!existing) {
    const suffix =
      typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID().slice(0, 8)
        : Math.random().toString(36).slice(2, 10);
    existing = `usr_${suffix}`;
    window.localStorage.setItem(USER_STORAGE_KEY, existing);
  }
  return existing;
}

/** Generates a fresh UUID-backed Idempotency-Key for a new checkout or cancel action. */
export function newIdempotencyKey(prefix: string): string {
  const token =
    typeof crypto !== "undefined" && "randomUUID" in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  return `${prefix}-${token}`;
}
