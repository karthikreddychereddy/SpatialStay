const BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  "http://localhost:8080";

export type AdminUser = {
  userPublicId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: "ADMIN";
};

export type AdminDashboard = {
  hotels: number;
  users: number;
  bookings: number;
  payments: number;
  pendingApplications: number;
  reconciliationCases: number;
};

export type HotelApplicationRoom = {
  id: number;
  name: string;
  roomType: string;
  description: string | null;
  maxGuests: number;
  totalUnits: number;
  bedType: string;
  roomSizeSqFt: number | null;
  pricePerNight: number;
  imageUrls: string[];
};

export type HotelApplication = {
  id: number;
  reference: string;
  applicantPublicId: string;
  applicantName: string;
  applicantEmail: string;
  ownerPhone: string;
  hotelName: string;
  propertyType: string;
  starCategory: number | null;
  city: string;
  state: string | null;
  country: string;
  address: string;
  contactPhone: string;
  contactEmail: string;
  website: string | null;
  startingPrice: number;
  description: string | null;
  totalRooms: number | null;
  checkInTime: string | null;
  checkOutTime: string | null;
  houseRules: string | null;
  childPolicy: string | null;
  petPolicy: string | null;
  smokingPolicy: string | null;
  amenities: string[];
  hotelImageUrls: string[];
  rooms: HotelApplicationRoom[];
  status: string;
  adminNote: string | null;
  approvedHotelId: number | null;
  createdAt: string;
  decidedAt: string | null;
};

export type AdminHotel = {
  id: number;
  slug: string;
  name: string;
  city: string;
  state: string | null;
  country: string;
  verified: boolean;
  status: string;
  rating: number;
  reviewCount: number;
  startingPrice: number;
  contactEmail: string | null;
  createdAt: string;
};

export type AdminAccount = {
  publicId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  status: string;
  emailVerified: boolean;
  createdAt: string;
  lastLoginAt: string | null;
};

export type AdminBooking = {
  publicId: string;
  reference: string;
  customerPublicId: string;
  customerEmail: string;
  hotelId: number;
  hotelName: string;
  checkIn: string;
  checkOut: string;
  guests: number;
  rooms: number;
  totalAmount: number;
  currency: string;
  status: string;
  createdAt: string;
  confirmedAt: string | null;
};

export type AdminPayment = {
  publicId: string;
  bookingPublicId: string;
  bookingReference: string;
  customerEmail: string;
  provider: string;
  providerOrderId: string;
  providerPaymentId: string | null;
  amount: number;
  currency: string;
  status: string;
  createdAt: string;
  succeededAt: string | null;
};

export type AdminRefund = {
  publicId: string;
  bookingPublicId: string;
  bookingReference: string;
  customerEmail: string;
  provider: string;
  providerPaymentId: string | null;
  providerRefundId: string | null;
  refundAmount: number;
  currency: string;
  status: string;
  failureReason: string | null;
  createdAt: string;
  succeededAt: string | null;
};

export type AdminReconciliation = {
  id: number;
  paymentAttemptPublicId: string;
  bookingPublicId: string;
  provider: string;
  providerOrderId: string;
  providerPaymentId: string;
  bookingStatusAtDiscovery: string;
  status: string;
  discoveredAt: string;
};

export type AdminAudit = {
  id: number;
  adminPublicId: string;
  adminEmail: string;
  action: string;
  targetType: string;
  targetId: string;
  detail: string;
  createdAt: string;
};

export type AdminAnalytics = {
  hotels: number;
  users: number;
  customers: number;
  hotelHeads: number;
  hotelStaff: number;
  bookings: number;
  confirmedBookings: number;
  cancelledBookings: number;
  paymentAttempts: number;
  successfulPayments: number;
  refunds: number;
  successfulRefunds: number;
  pendingApplications: number;
  reconciliationCases: number;
  confirmedBookingValue: number;
  successfulPaymentValue: number;
  successfulRefundValue: number;
};

export class AdminApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message);
    this.name = "AdminApiError";
  }
}

async function request<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await fetch(
    `${BASE}${path}`,
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
    let message =
      `Request failed (${response.status}).`;

    try {
      const body = await response.json();

      message =
        body.message ??
        body.error ??
        message;
    } catch {
      /*
       * Keep the status-based error message when
       * the backend response has no JSON body.
       */
    }

    throw new AdminApiError(
      message,
      response.status,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

async function mutation<T>(
  path: string,
  method: "PATCH" | "POST",
  body: unknown,
): Promise<T> {
  const csrf =
    await request<{
      headerName: string;
      token: string;
    }>(
      "/api/auth/csrf",
    );

  return request<T>(
    path,
    {
      method,
      headers: {
        "Content-Type": "application/json",
        [csrf.headerName]: csrf.token,
      },
      body: JSON.stringify(body),
    },
  );
}

export const adminApi = {
  login: (
    email: string,
    password: string,
  ) =>
    request<AdminUser>(
      "/api/admin/auth/login",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          email,
          password,
        }),
      },
    ),

  me: () =>
    request<AdminUser>(
      "/api/admin/auth/me",
    ),

  logout: async () => {
    const csrf =
      await request<{
        headerName: string;
        token: string;
      }>(
        "/api/auth/csrf",
      );

    return request<void>(
      "/api/auth/logout",
      {
        method: "POST",
        headers: {
          [csrf.headerName]: csrf.token,
        },
      },
    );
  },

  dashboard: () =>
    request<AdminDashboard>(
      "/api/admin/dashboard",
    ),

  applications: () =>
    request<HotelApplication[]>(
      "/api/admin/hotel-applications",
    ),

  decideApplication: (
    id: number,
    decision: "approve" | "reject",
    note: string,
  ) =>
    mutation<HotelApplication>(
      `/api/admin/hotel-applications/${id}/${decision}`,
      "POST",
      {
        note,
      },
    ),

  hotels: () =>
    request<AdminHotel[]>(
      "/api/admin/hotels",
    ),

  setHotelStatus: (
    id: number,
    status: "ACTIVE" | "SUSPENDED",
  ) =>
    mutation<AdminHotel>(
      `/api/admin/hotels/${id}/status`,
      "PATCH",
      {
        status,
      },
    ),

  users: () =>
    request<AdminAccount[]>(
      "/api/admin/users",
    ),

  setUserStatus: (
    id: string,
    status: "ACTIVE" | "DISABLED",
  ) =>
    mutation<AdminAccount>(
      `/api/admin/users/${id}/status`,
      "PATCH",
      {
        status,
      },
    ),

  bookings: () =>
    request<AdminBooking[]>(
      "/api/admin/bookings",
    ),

  payments: () =>
    request<AdminPayment[]>(
      "/api/admin/payments",
    ),

  refunds: () =>
    request<AdminRefund[]>(
      "/api/admin/refunds",
    ),

  reconciliation: () =>
    request<AdminReconciliation[]>(
      "/api/admin/reconciliation",
    ),

  analytics: () =>
    request<AdminAnalytics>(
      "/api/admin/analytics",
    ),

  audit: () =>
    request<AdminAudit[]>(
      "/api/admin/audit",
    ),
};