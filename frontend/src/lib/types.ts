export interface CitySummary {
  city: string;
  country: string;
  hotelCount: number;
  minNightlyPriceMinor: number;
  currency: string;
  sampleImageUrl: string;
  tagline: string;
  centerLat: number;
  centerLng: number;
}

export interface HotelSearchItem {
  id: string;
  name: string;
  city: string;
  country: string;
  neighborhood: string;
  address: string;
  latitude: number;
  longitude: number;
  landmarkName: string;
  distanceToCenterKm: number;
  starRating: number;
  guestScore: number;
  reviewCount: number;
  propertyType: string;
  imageUrl: string;
  amenities: string[];
  startingNightlyPriceMinor: number;
  totalStayPriceMinor: number;
  currency: string;
  totalNights: number;
  minAvailableRooms: number;
  freeCancellationAvailable: boolean;
  breakfastIncludedAvailable: boolean;
}

export interface PagedSearchResponse {
  items: HotelSearchItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  city: string;
  checkIn: string;
  checkOut: string;
  totalNights: number;
  guests: number;
  rooms: number;
  queryDurationMs: number;
}

export interface PricingAdjustment {
  ruleCode: string;
  label: string;
  percentageDelta: number;
  amountDeltaMinor: number;
  explanation: string;
}

export interface NightlyPriceQuote {
  stayDate: string;
  baseNightlyMinor: number;
  dynamicNightlyMinor: number;
  availableRooms: number;
  adjustments: PricingAdjustment[];
}

export interface PriceQuoteResponse {
  baseNightlyMinor: number;
  averageDynamicNightlyMinor: number;
  totalBaseMinor: number;
  totalStayPriceMinor: number;
  currency: string;
  totalNights: number;
  rooms: number;
  ruleSummaries: PricingAdjustment[];
  nightlyBreakdown: NightlyPriceQuote[];
}

export interface RoomType {
  id: string;
  name: string;
  description: string;
  bedType: string;
  roomSizeSqm: number;
  maxGuests: number;
  nightlyPriceMinor: number;
  totalStayPriceMinor: number;
  currency: string;
  totalUnits: number;
  availableRooms: number;
  freeCancellation: boolean;
  breakfastIncluded: boolean;
  imageUrl: string;
  dynamicPricing?: PriceQuoteResponse;
}

export interface HotelDetail {
  id: string;
  name: string;
  city: string;
  country: string;
  neighborhood: string;
  address: string;
  latitude: number;
  longitude: number;
  landmarkName: string;
  distanceToCenterKm: number;
  starRating: number;
  guestScore: number;
  reviewCount: number;
  propertyType: string;
  description: string;
  imageUrl: string;
  galleryUrls: string[];
  amenities: string[];
  checkIn: string;
  checkOut: string;
  totalNights: number;
  guestsRequested: number;
  roomsRequested: number;
  roomTypes: RoomType[];
}

export interface HoldResponse {
  holdId: string;
  userId: string;
  hotelId: string;
  hotelName: string;
  hotelCity: string;
  hotelNeighborhood: string;
  hotelImageUrl: string;
  roomTypeId: string;
  roomName: string;
  roomImageUrl: string;
  checkIn: string;
  checkOut: string;
  totalNights: number;
  rooms: number;
  guests: number;
  expiresAt: string;
  ttlSeconds: number;
  pricing: PriceQuoteResponse;
}

export interface BookingResponse {
  id: string;
  userId: string;
  holdId: string;
  hotelId: string;
  hotelName: string;
  hotelCity: string;
  hotelNeighborhood: string;
  hotelImageUrl: string;
  roomTypeId: string;
  roomName: string;
  checkIn: string;
  checkOut: string;
  totalNights: number;
  roomsCount: number;
  guestsCount: number;
  nightlyBaseMinor: number;
  totalPriceMinor: number;
  currency: string;
  pricingBreakdown: PricingAdjustment[];
  status: "CONFIRMED" | "CANCELLED";
  guestName: string;
  guestEmail: string;
  paymentReference: string;
  experimentVariant: string;
  cancelledAt: string | null;
  createdAt: string;
}

export interface SearchParamsInput {
  city?: string;
  query?: string;
  checkIn?: string;
  checkOut?: string;
  guests?: number;
  rooms?: number;
  maxPrice?: number;
  minRating?: number;
  minStars?: number;
  amenities?: string[];
  sort?: string;
  page?: number;
  size?: number;
}
