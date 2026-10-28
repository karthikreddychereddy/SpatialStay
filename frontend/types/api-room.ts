export interface ApiAmenity {
  id?: number;
  code: string;
  name: string;
}

export interface ApiRoom {
  id: number;
  slug: string;

  hotelId?: number;
  hotelSlug: string;
  hotelName?: string;

  name: string;
  description: string | null;

  roomType: string;
  maxGuests: number;

  bedType: string | null;
  roomSizeSqFt: number | null;

  pricePerNight: number;
  currency: string;

  heroImageUrl: string | null;

  /**
   * Ordered room gallery.
   *
   * The first image is normally the same image used as heroImageUrl.
   */
  imageUrls: string[];

  spatialAvailable: boolean;

  /**
   * Kept for compatibility with existing frontend code.
   * The authoritative backend lifecycle field is status.
   */
  active?: boolean;

  amenities: ApiAmenity[];

  status: string;

  createdAt?: string;
  updatedAt?: string;
}