import type {
  PaymentAttemptResponse,
  ModificationCheckoutResponse,
  PaymentCheckoutResponse,
} from "@/types/api-payment";

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

export class PaymentApiError extends Error {
  status: number;
  fields?: Record<string, string>;

  constructor(
    message: string,
    status: number,
    fields?: Record<string, string>,
  ) {
    super(message);

    this.name = "PaymentApiError";
    this.status = status;
    this.fields = fields;
  }
}

async function readError(
  response: Response,
): Promise<PaymentApiError> {
  let body: ApiErrorBody | null = null;

  try {
    body =
      (await response.json()) as ApiErrorBody;
  } catch {
    // Some Spring Security responses may contain
    // no JSON response body.
  }

  let message =
    body?.message ??
    body?.error ??
    "Unable to start secure payment. Please try again.";

  if (response.status === 401) {
    message =
      "Please sign in before starting payment.";
  }

  if (response.status === 403) {
    message =
      "This payment request was not allowed. Please refresh and try again.";
  }

  if (response.status === 409) {
    message =
      body?.message ??
      "This reservation can no longer be paid. Please check availability again.";
  }

  return new PaymentApiError(
    message,
    response.status,
    body?.fields,
  );
}

async function request<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response =
    await fetch(
      `${API_BASE_URL}${path}`,
      {
        ...init,

        credentials: "include",

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

async function csrfPost<T>(
  path: string,
): Promise<T> {
  const csrfToken =
    await csrf();

  return request<T>(
    path,
    {
      method: "POST",

      headers: {
        [csrfToken.headerName]:
          csrfToken.token,
      },
    },
  );
}

export const paymentApi = {
  createModificationCheckout(id:string) {return csrfPost<ModificationCheckoutResponse>(`/api/payments/modifications/${encodeURIComponent(id)}/checkout`);},
  createCheckout(
    bookingId: string,
  ) {
    return csrfPost<PaymentCheckoutResponse>(
      `/api/payments/bookings/${encodeURIComponent(
        bookingId,
      )}/checkout`,
    );
  },

  getPayment(
    paymentId: string,
  ) {
    return request<PaymentAttemptResponse>(
      `/api/payments/${encodeURIComponent(
        paymentId,
      )}`,
    );
  },

  getBookingPayments(
    bookingId: string,
  ) {
    return request<PaymentAttemptResponse[]>(
      `/api/payments/bookings/${encodeURIComponent(
        bookingId,
      )}`,
    );
  },
};