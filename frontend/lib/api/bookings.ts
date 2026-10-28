import type {
  BookingResponse,
  ModificationRequest,
  ModificationResponse,
  CancelBookingResponse,
  CancellationPreviewResponse,
  CreateBookingPayload,
  CreateInventoryHoldPayload,
  InventoryHoldResponse,
} from "@/types/api-booking";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  "http://localhost:8080";

type CsrfResponse = {
  headerName: string;
  parameterName: string;
  token: string;
};

type ApiErrorBody = {
  error?: string;
  message?: string;
  fields?: Record<string, string>;
};

export class BookingApiError extends Error {
  status: number;
  fields?: Record<string, string>;

  constructor(
    message: string,
    status: number,
    fields?: Record<string, string>,
  ) {
    super(message);

    this.name = "BookingApiError";
    this.status = status;
    this.fields = fields;
  }
}

async function readError(
  response: Response,
): Promise<BookingApiError> {
  let body: ApiErrorBody | null = null;

  try {
    body =
      (await response.json()) as ApiErrorBody;
  } catch {
    // Some Spring Security responses may contain no JSON body.
  }

  let message =
    body?.message ??
    body?.error ??
    "Something went wrong. Please try again.";

  if (response.status === 401) {
    message =
      "Please sign in to continue.";
  }

  if (response.status === 403) {
    message =
      body?.message ??
      "This request was not allowed. Please refresh the page and try again.";
  }

  if (response.status === 409) {
    message =
      body?.message ??
      "The booking changed while your request was being processed. Please refresh and try again.";
  }

  return new BookingApiError(
    message,
    response.status,
    body?.fields,
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

      headers: {
        Accept: "application/json",
        ...init.headers,
      },

      cache: "no-store",
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

async function csrfPost<T>(
  path: string,
  body?: unknown,
): Promise<T> {
  const csrfToken = await csrf();

  const hasBody =
    body !== undefined;

  return request<T>(path, {
    method: "POST",

    headers: {
      ...(hasBody
        ? {
            "Content-Type":
              "application/json",
          }
        : {}),

      [csrfToken.headerName]:
        csrfToken.token,
    },

    ...(hasBody
      ? {
          body: JSON.stringify(body),
        }
      : {}),
  });
}

export const bookingApi = {
  createModification(bookingId:string, payload:ModificationRequest) {return csrfPost<ModificationResponse>(`/api/bookings/${encodeURIComponent(bookingId)}/modifications`,payload);},
  getModification(id:string) {return request<ModificationResponse>(`/api/bookings/modifications/${encodeURIComponent(id)}`);},
  finalizeModification(id:string) {return csrfPost<ModificationResponse>(`/api/bookings/modifications/${encodeURIComponent(id)}/finalize`);},
  createHold(
    payload: CreateInventoryHoldPayload,
  ) {
    return csrfPost<InventoryHoldResponse>(
      "/api/bookings/holds",
      payload,
    );
  },

  getHold(
    holdId: string,
  ) {
    return request<InventoryHoldResponse>(
      `/api/bookings/holds/${encodeURIComponent(
        holdId,
      )}`,
    );
  },

  createBooking(
    payload: CreateBookingPayload,
  ) {
    return csrfPost<BookingResponse>(
      "/api/bookings",
      payload,
    );
  },

  getMyBookings() {
    return request<BookingResponse[]>(
      "/api/bookings/my",
    );
  },

  getBooking(
    bookingId: string,
  ) {
    return request<BookingResponse>(
      `/api/bookings/${encodeURIComponent(
        bookingId,
      )}`,
    );
  },

  getCancellationPreview(
    bookingId: string,
  ) {
    return request<CancellationPreviewResponse>(
      `/api/bookings/${encodeURIComponent(
        bookingId,
      )}/cancellation-preview`,
    );
  },

  cancelBooking(
    bookingId: string,
  ) {
    /*
     * No refund amount, payment ID, refund ID,
     * or cancellation fee comes from the browser.
     *
     * The backend recalculates and controls all
     * financial information.
     */
    return csrfPost<CancelBookingResponse>(
      `/api/bookings/${encodeURIComponent(
        bookingId,
      )}/cancel`,
    );
  },
};