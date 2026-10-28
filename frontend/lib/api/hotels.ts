import type { ApiHotel } from "@/types/api-hotel";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  "http://localhost:8080";

export type HotelSort =
  | "recommended"
  | "price_asc"
  | "price_desc"
  | "rating_desc"
  | "reviews_desc"
  | "newest";

export type HotelSearchParams = {
  destination?: string;
  query?: string;
  minPrice?: number;
  maxPrice?: number;
  minRating?: number;
  verified?: boolean;
  sort?: HotelSort;

  // Step 5 availability search
  checkIn?: string;
  checkOut?: string;
  guests?: number;
};

function resolveMediaUrl(
  url: string | null | undefined,
): string | null {
  if (!url) {
    return null;
  }

  if (
    url.startsWith("http://") ||
    url.startsWith("https://") ||
    url.startsWith("data:") ||
    url.startsWith("blob:")
  ) {
    return url;
  }

  const normalizedPath =
    url.startsWith("/")
      ? url
      : `/${url}`;

  return `${API_BASE_URL}${normalizedPath}`;
}

function resolveMediaUrls(
  urls: string[] | null | undefined,
): string[] {
  if (!urls?.length) {
    return [];
  }

  return urls
    .map((url) => resolveMediaUrl(url))
    .filter(
      (url): url is string =>
        Boolean(url),
    );
}

function normalizeHotel(
  hotel: ApiHotel,
): ApiHotel {
  const heroImageUrl =
    resolveMediaUrl(
      hotel.heroImageUrl,
    );

  const imageUrls =
    resolveMediaUrls(
      hotel.imageUrls,
    );

  /*
   * Compatibility fallback:
   *
   * Older hotels may currently have only heroImageUrl
   * because they were created before gallery persistence
   * was introduced.
   */
  const normalizedGallery =
    imageUrls.length > 0
      ? imageUrls
      : heroImageUrl
        ? [heroImageUrl]
        : [];

  return {
    ...hotel,
    heroImageUrl:
      heroImageUrl ??
      normalizedGallery[0] ??
      null,
    imageUrls: normalizedGallery,
  };
}

export async function getHotels(
  params: HotelSearchParams = {},
): Promise<ApiHotel[]> {
  const searchParams =
    new URLSearchParams();

  if (params.destination?.trim()) {
    searchParams.set(
      "destination",
      params.destination.trim(),
    );
  }

  if (params.query?.trim()) {
    searchParams.set(
      "query",
      params.query.trim(),
    );
  }

  if (params.minPrice !== undefined) {
    searchParams.set(
      "minPrice",
      String(params.minPrice),
    );
  }

  if (params.maxPrice !== undefined) {
    searchParams.set(
      "maxPrice",
      String(params.maxPrice),
    );
  }

  if (params.minRating !== undefined) {
    searchParams.set(
      "minRating",
      String(params.minRating),
    );
  }

  if (params.verified !== undefined) {
    searchParams.set(
      "verified",
      String(params.verified),
    );
  }

  if (params.sort) {
    searchParams.set(
      "sort",
      params.sort,
    );
  }

  /*
   * Step 5:
   * Stay parameters must travel together.
   *
   * The backend validates the actual dates,
   * guest count and room availability.
   */
  if (
    params.checkIn &&
    params.checkOut &&
    params.guests !== undefined
  ) {
    searchParams.set(
      "checkIn",
      params.checkIn,
    );

    searchParams.set(
      "checkOut",
      params.checkOut,
    );

    searchParams.set(
      "guests",
      String(params.guests),
    );
  }

  const queryString =
    searchParams.toString();

  const response = await fetch(
    `${API_BASE_URL}/api/hotels${
      queryString
        ? `?${queryString}`
        : ""
    }`,
    {
      cache: "no-store",
    },
  );

  if (!response.ok) {
    let message =
      `Failed to load hotels: ${response.status}`;

    try {
      const body =
        (await response.json()) as {
          message?: string;
        };

      if (body.message) {
        message = body.message;
      }
    } catch {
      // Keep the normal HTTP error message.
    }

    throw new Error(message);
  }

  const hotels =
    (await response.json()) as ApiHotel[];

  return hotels.map(normalizeHotel);
}

export async function getHotelBySlug(
  slug: string,
): Promise<ApiHotel | null> {
  const response = await fetch(
    `${API_BASE_URL}/api/hotels/${encodeURIComponent(
      slug,
    )}`,
    {
      cache: "no-store",
    },
  );

  if (response.status === 404) {
    return null;
  }

  if (!response.ok) {
    throw new Error(
      `Failed to load hotel: ${response.status}`,
    );
  }

  const hotel =
    (await response.json()) as ApiHotel;

  return normalizeHotel(hotel);
}