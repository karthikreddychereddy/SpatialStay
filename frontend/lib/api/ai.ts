const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  "http://localhost:8080";

export type AiResponseType =
  | "TEXT"
  | "HOTELS"
  | "ROOMS"
  | "AVAILABILITY"
  | "KNOWLEDGE";

export type HotelResult = {
  id: number;
  slug: string;
  name: string;
  description: string;
  city: string;
  state: string;
  country: string;
  address: string;
  latitude: number;
  longitude: number;
  rating: number;
  reviewCount: number;
  startingPrice: number;
  heroImageUrl: string | null;
  verified: boolean;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type RoomAmenity = {
  code: string;
  name: string;
};

export type RoomResult = {
  id: number;
  slug: string;
  name: string;
  description: string;
  roomType: string;
  maxGuests: number;
  bedType: string;
  roomSizeSqFt: number | null;
  pricePerNight: number;
  currency: string;
  heroImageUrl: string | null;
  spatialAvailable: boolean;
  status: string;
  hotelId: number;
  hotelSlug: string;
  hotelName: string;
  amenities: RoomAmenity[];
  createdAt: string;
  updatedAt: string;
};

export type AvailabilityResult = {
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

export type AiChatResponse = {
  message: string;
  responseType: AiResponseType | string;
  hotels: HotelResult[];
  rooms: RoomResult[];
  availability: AvailabilityResult[];
};

export type AiConversationResetResponse = {
  message: string;
};

type CsrfResponse = {
  headerName: string;
  parameterName: string;
  token: string;
};

type ApiErrorBody = {
  error?: string;
  message?: string;
};

export class AiApiError extends Error {
  status: number;
  code?: string;

  constructor(
    message: string,
    status: number,
    code?: string,
  ) {
    super(message);
    this.name = "AiApiError";
    this.status = status;
    this.code = code;
  }
}

async function readError(
  response: Response,
): Promise<AiApiError> {
  let body: ApiErrorBody | null = null;

  try {
    body =
      (await response.json()) as ApiErrorBody;
  } catch {
    // Security responses may have no JSON body.
  }

  return new AiApiError(
    body?.message ??
      "Spatial AI could not complete the request.",
    response.status,
    body?.error,
  );
}

async function request<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await fetch(
    `${API_BASE_URL}${path}`,
    {
      ...init,
      credentials: "include",
      cache: "no-store",
      headers: {
        Accept: "application/json",
        ...init.headers,
      },
    },
  );

  if (!response.ok) {
    throw await readError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

async function csrf(): Promise<CsrfResponse> {
  return request<CsrfResponse>(
    "/api/auth/csrf",
  );
}

async function protectedRequest<T>(
  path: string,
  method: "POST" | "DELETE",
  body?: unknown,
): Promise<T> {
  const csrfToken = await csrf();

  return request<T>(path, {
    method,
    headers: {
      "Content-Type": "application/json",
      [csrfToken.headerName]:
        csrfToken.token,
    },
    body:
      body === undefined
        ? undefined
        : JSON.stringify(body),
  });
}

export const aiApi = {
  chat(
    message: string,
  ): Promise<AiChatResponse> {
    return protectedRequest<AiChatResponse>(
      "/api/ai/chat",
      "POST",
      { message },
    );
  },

  resetConversation():
    Promise<AiConversationResetResponse> {
    return protectedRequest<AiConversationResetResponse>(
      "/api/ai/conversation",
      "DELETE",
    );
  },
};