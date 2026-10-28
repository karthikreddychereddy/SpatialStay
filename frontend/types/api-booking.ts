export type InventoryHoldStatus =
  | "ACTIVE"
  | "CONSUMED"
  | "EXPIRED"
  | "RELEASED";

export type BookingStatus =
  | "PENDING_PAYMENT"
  | "CONFIRMED"
  | "CANCELLED"
  | "EXPIRED";

export type CancellationEligibilityReason =
  | "ELIGIBLE"
  | "MANAGEMENT_COOLDOWN"
  | "BOOKING_NOT_CONFIRMED"
  | "CHECK_IN_ALREADY_STARTED"
  | "NON_REFUNDABLE_WINDOW";

export type RefundStatus =
  | "PENDING"
  | "PROCESSING"
  | "SUCCEEDED"
  | "FAILED"
  | "REQUIRES_RECONCILIATION";

export type CreateInventoryHoldPayload = {
  hotelSlug: string;
  roomSlug: string;
  checkIn: string;
  checkOut: string;
  guests: number;
  units: number;
};

export type InventoryHoldResponse = {
  holdId: string;
  hotelSlug: string;
  hotelName: string;
  roomSlug: string;
  roomName: string;
  checkIn: string;
  checkOut: string;
  nights: number;
  guests: number;
  units: number;
  pricePerNight: number;
  totalAmount: number;
  currency: string;
  status: InventoryHoldStatus;
  expiresAt: string;
  createdAt: string;
};

export type CreateBookingPayload = {
  holdId: string;
};

export type BookingResponse = {
  bookingId: string;
  bookingReference: string;
  holdId: string;

  hotelSlug: string;
  hotelName: string;

  roomSlug: string;
  roomName: string;

  checkIn: string;
  checkOut: string;

  nights: number;
  guests: number;
  units: number;

  pricePerNight: number;
  totalAmount: number;
  currency: string;

  status: BookingStatus;

  createdAt: string;
  updatedAt: string;

  confirmedAt: string | null;
  cancelledAt: string | null;
  expiredAt: string | null;
};

export type CancellationPreviewResponse = {
  bookingId: string;
  bookingReference: string;

  cancellable: boolean;
  reason: CancellationEligibilityReason;
  message: string;

  confirmedAt: string | null;
  managementAvailableAt: string | null;

  checkIn: string;
  checkOut: string;

  originalAmount: number;
  cancellationFee: number;
  refundAmount: number;
  currency: string;

  policyDescription: string;
};

export type CancelBookingResponse = {
  bookingId: string;
  bookingReference: string;

  status: BookingStatus;
  cancelledAt: string;

  originalAmount: number;
  cancellationFee: number;
  refundAmount: number;
  currency: string;

  refundRequired: boolean;

  refundId: string | null;
  refundStatus: RefundStatus | null;

  message: string;
};
export type ModificationStatus = "PENDING" | "AWAITING_PAYMENT" | "READY_TO_APPLY" | "COMPLETED" | "EXPIRED" | "CANCELLED" | "REQUIRES_RECONCILIATION";
export type ModificationFinancialType = "NO_ADJUSTMENT" | "ADDITIONAL_PAYMENT" | "PARTIAL_REFUND";
export type ModificationRequest = {roomSlug:string;checkIn:string;checkOut:string;guests:number;units:number};
export type ModificationResponse = {
 modificationId:string;bookingId:string;bookingReference:string;status:ModificationStatus;financialType:ModificationFinancialType;
 originalRoomSlug:string;originalCheckIn:string;originalCheckOut:string;originalGuests:number;originalUnits:number;originalTotal:number;
 proposedRoomSlug:string;proposedCheckIn:string;proposedCheckOut:string;proposedGuests:number;proposedUnits:number;proposedPricePerNight:number;proposedTotal:number;priceDifference:number;currency:string;replacementHoldId:string;holdExpiresAt:string;requiresAdditionalPayment:boolean;requiresPartialRefund:boolean;message:string;
};
