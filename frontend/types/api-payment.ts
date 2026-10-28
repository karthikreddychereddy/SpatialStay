export type PaymentProvider =
  | "RAZORPAY";

export type PaymentStatus =
  | "CREATED"
  | "PENDING"
  | "PROCESSING"
  | "SUCCEEDED"
  | "FAILED"
  | "EXPIRED"
  | "CANCELLED";

export type PaymentCheckoutResponse = {
  paymentId: string;

  bookingId: string;
  bookingReference: string;

  provider: PaymentProvider;
  paymentStatus: PaymentStatus;

  amount: number;
  amountInSubunits: number;
  currency: string;

  providerOrderId: string;

  /*
   * Razorpay Key ID is intentionally public checkout
   * configuration.
   *
   * The Razorpay Key Secret must NEVER be returned
   * to the frontend.
   */
  checkoutKeyId: string;

  holdExpiresAt: string;
};

export type PaymentAttemptResponse = {
  paymentId: string;

  bookingId: string;
  bookingReference: string;

  provider: PaymentProvider;

  amount: number;
  currency: string;

  status: PaymentStatus;

  providerOrderId: string | null;

  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
};
export type ModificationCheckoutResponse = PaymentCheckoutResponse & {modificationId:string;purpose:string};
