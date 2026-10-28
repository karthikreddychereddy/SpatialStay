import type { ApiRoom } from "@/types/api-room";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  "http://localhost:8080";

export type RoomAvailability = {
  roomId: number;
  roomSlug: string;
  roomName: string;
  checkIn: string;
  checkOut: string;
  nights: number;
  guests: number;
  maxGuests: number;
  totalUnits: number;
  availableUnits: number;
  available: boolean;
  pricePerNight: number;
  estimatedStayTotal: number;
  currency: string;
};

export type RoomAvailabilityParams = {
  checkIn: string;
  checkOut: string;
  guests: number;
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

function normalizeRoom(
  room: ApiRoom,
): ApiRoom {
  const heroImageUrl =
    resolveMediaUrl(
      room.heroImageUrl,
    );

  const imageUrls =
    resolveMediaUrls(
      room.imageUrls,
    );

  /*
   * Compatibility fallback for rooms created before
   * full gallery persistence was introduced.
   */
  const normalizedGallery =
    imageUrls.length > 0
      ? imageUrls
      : heroImageUrl
        ? [heroImageUrl]
        : [];

  return {
    ...room,
    heroImageUrl:
      heroImageUrl ??
      normalizedGallery[0] ??
      null,
    imageUrls: normalizedGallery,
  };
}

export async function getRoomsByHotel(
  hotelSlug: string,
): Promise<ApiRoom[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/hotels/${encodeURIComponent(
      hotelSlug,
    )}/rooms`,
    {
      cache: "no-store",
    },
  );

  if (!response.ok) {
    throw new Error(
      `Failed to load rooms: ${response.status}`,
    );
  }

  const rooms =
    (await response.json()) as ApiRoom[];

  return rooms.map(normalizeRoom);
}

export async function getRoomBySlug(
  hotelSlug: string,
  roomSlug: string,
): Promise<ApiRoom | null> {
  const response = await fetch(
    `${API_BASE_URL}/api/hotels/${encodeURIComponent(
      hotelSlug,
    )}/rooms/${encodeURIComponent(
      roomSlug,
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
      `Failed to load room: ${response.status}`,
    );
  }

  const room =
    (await response.json()) as ApiRoom;

  return normalizeRoom(room);
}

export async function getRoomAvailability(
  hotelSlug: string,
  {
    checkIn,
    checkOut,
    guests,
  }: RoomAvailabilityParams,
  signal?: AbortSignal,
): Promise<RoomAvailability[]> {
  const searchParams =
    new URLSearchParams({
      checkIn,
      checkOut,
      guests: String(guests),
    });

  const response = await fetch(
    `${API_BASE_URL}/api/hotels/${encodeURIComponent(
      hotelSlug,
    )}/rooms/availability?${searchParams.toString()}`,
    {
      cache: "no-store",
      signal,
    },
  );

  if (!response.ok) {
    let message =
      `Failed to check availability: ${response.status}`;

    try {
      const body =
        (await response.json()) as {
          message?: string;
        };

      if (body.message) {
        message = body.message;
      }
    } catch {
      // Keep the fallback message when the
      // backend does not return JSON.
    }

    throw new Error(message);
  }

  return response.json();
}