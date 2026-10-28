export interface ApiHotel {
  id: number;
  slug: string;
  name: string;
  description: string | null;

  city: string;
  state: string | null;
  country: string;
  address: string | null;

  latitude: number | null;
  longitude: number | null;

  rating: number | null;
  reviewCount: number;
  startingPrice: number;

  heroImageUrl: string | null;

  /**
   * Ordered hotel gallery.
   *
   * The first image is normally the same image used as heroImageUrl.
   */
  imageUrls: string[];

  verified: boolean;
  status: string;

  contactEmail?: string | null;
  contactPhone?: string | null;

  checkInTime?: string | null;
  checkOutTime?: string | null;
  checkInInstructions?: string | null;

  amenities?: string[];

  houseRules?: string | null;
  childPolicy?: string | null;
  petPolicy?: string | null;
  smokingPolicy?: string | null;

  createdAt: string;
  updatedAt: string;
}