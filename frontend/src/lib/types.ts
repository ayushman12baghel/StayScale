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
