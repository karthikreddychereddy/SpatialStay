const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export type AuthUser = {
  publicId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: "CUSTOMER" | "HOTEL_STAFF" | "HOTEL_HEAD" | "ADMIN";
  emailVerified: boolean;
  createdAt: string;
};

export type RegisterPayload = {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
};

export type LoginPayload = {
  email: string;
  password: string;
};

type CsrfResponse = {
  headerName: string;
  parameterName: string;
  token: string;
};

type MessageResponse = {
  message: string;
  developmentToken?: string;
};

type ApiErrorBody = {
  error?: string;
  message?: string;
  fields?: Record<string, string>;
};

export class AuthApiError extends Error {
  status: number;
  fields?: Record<string, string>;

  constructor(
    message: string,
    status: number,
    fields?: Record<string, string>,
  ) {
    super(message);
    this.name = "AuthApiError";
    this.status = status;
    this.fields = fields;
  }
}

async function readError(response: Response): Promise<AuthApiError> {
  let body: ApiErrorBody | null = null;

  try {
    body = (await response.json()) as ApiErrorBody;
  } catch {
    // Security responses may intentionally have no JSON body.
  }

  return new AuthApiError(
    body?.message ?? "Something went wrong. Please try again.",
    response.status,
    body?.fields,
  );
}

async function request<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      Accept: "application/json",
      ...init.headers,
    },
  });

  if (!response.ok) {
    throw await readError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

async function csrf(): Promise<CsrfResponse> {
  return request<CsrfResponse>("/api/auth/csrf");
}

async function csrfPost<T>(
  path: string,
  body?: unknown,
): Promise<T> {
  const csrfToken = await csrf();

  return request<T>(path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      [csrfToken.headerName]: csrfToken.token,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export const authApi = {
  async me(): Promise<AuthUser | null> {
    try {
      return await request<AuthUser>("/api/auth/me");
    } catch (error) {
      if (error instanceof AuthApiError && error.status === 401) {
        return null;
      }

      throw error;
    }
  },

  register(payload: RegisterPayload) {
    return request<AuthUser>("/api/auth/register", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
  },

  login(payload: LoginPayload) {
    return request<AuthUser>("/api/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
  },

  logout() {
    return csrfPost<void>("/api/auth/logout");
  },

  forgotPassword(email: string) {
    return request<MessageResponse>("/api/auth/forgot-password", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ email }),
    });
  },

  resetPassword(token: string, newPassword: string) {
    return request<MessageResponse>("/api/auth/reset-password", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        token,
        newPassword,
      }),
    });
  },

  requestEmailVerification() {
    return csrfPost<MessageResponse>(
      "/api/auth/email-verification/request",
    );
  },

  confirmEmailVerification(token: string) {
    return request<MessageResponse>(
      `/api/auth/email-verification/confirm?token=${encodeURIComponent(token)}`,
      {
        method: "POST",
      },
    );
  },

  googleLoginUrl() {
    return `${API_BASE_URL}/oauth2/authorization/google`;
  },
};