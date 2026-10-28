package com.spatialstay.booking;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final InventoryHoldService inventoryHoldService;
    private final InventoryHoldAccessService inventoryHoldAccessService;
    private final BookingService bookingService;
    private final BookingCancellationPolicyService cancellationPolicyService;
    private final BookingCancellationService bookingCancellationService;

    private final BookingModificationService
           bookingModificationService;

    private final BookingModificationFinalizationService
           bookingModificationFinalizationService;

    public BookingController(
            InventoryHoldService inventoryHoldService,
            InventoryHoldAccessService inventoryHoldAccessService,
            BookingService bookingService,
            BookingCancellationPolicyService cancellationPolicyService,
            BookingCancellationService bookingCancellationService,
            BookingModificationService bookingModificationService,
            BookingModificationFinalizationService
                        bookingModificationFinalizationService
    ) {
        this.inventoryHoldService =
                inventoryHoldService;

        this.inventoryHoldAccessService =
                inventoryHoldAccessService;

        this.bookingService =
                bookingService;

        this.cancellationPolicyService =
                cancellationPolicyService;

        this.bookingCancellationService =
                bookingCancellationService;

        this.bookingModificationService =
                bookingModificationService;

        this.bookingModificationFinalizationService =
                bookingModificationFinalizationService;
    }

    /**
     * Creates an authoritative 10-minute inventory hold.
     *
     * Customer identity comes from Spring Security.
     * Price and totals come from the backend room record.
     */
    @PostMapping("/holds")
    public ResponseEntity<InventoryHoldResponse> createHold(
            @RequestBody CreateInventoryHoldRequest request
    ) {
        InventoryHoldResponse response =
                inventoryHoldService.createHold(
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Retrieves only the authenticated customer's hold.
     */
    @GetMapping("/holds/{holdId}")
    public ResponseEntity<InventoryHoldResponse> getHold(
            @PathVariable UUID holdId
    ) {
        InventoryHoldResponse response =
                inventoryHoldAccessService.getOwnedHold(
                        holdId
                );

        return ResponseEntity.ok(
                response
        );
    }

    /**
     * Converts an owned ACTIVE hold into a PENDING_PAYMENT booking.
     *
     * The hold remains ACTIVE so it continues protecting inventory
     * during the remaining payment window.
     */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @RequestBody CreateBookingFromHoldRequest request
    ) {
        BookingResponse response =
                bookingService.createFromHold(
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Returns all bookings belonging to the authenticated customer.
     */
    @GetMapping("/my")
    public ResponseEntity<List<BookingResponse>> getMyBookings() {

        List<BookingResponse> response =
                bookingService.getMyBookings();

        return ResponseEntity.ok(
                response
        );
    }

    /**
     * Calculates authoritative cancellation eligibility.
     *
     * This endpoint:
     *
     * - verifies booking ownership,
     * - checks booking lifecycle state,
     * - enforces the 30-minute management cooldown,
     * - calculates the applicable cancellation fee,
     * - calculates the expected refund amount.
     *
     * It does NOT cancel the booking and does NOT issue a refund.
     */
    @GetMapping("/{bookingId}/cancellation-preview")
    public ResponseEntity<CancellationPreviewResponse>
    getCancellationPreview(
            @PathVariable UUID bookingId
    ) {
        CancellationPreviewResponse response =
                cancellationPolicyService.preview(
                        bookingId
                );

        return ResponseEntity.ok(
                response
        );
    }

    /**
     * Cancels an eligible booking owned by the authenticated customer.
     *
     * The cancellation service:
     *
     * - acquires a pessimistic booking lock,
     * - verifies ownership,
     * - recalculates cancellation eligibility,
     * - enforces the 30-minute cooldown,
     * - performs the authoritative booking state transition.
     *
     * Razorpay refund execution is NOT performed here.
     *
     * Step 11.6 introduces the durable refund obligation/domain and
     * Step 11.7 performs provider-side refund integration.
     */
    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<CancelBookingResponse> cancelBooking(
            @PathVariable UUID bookingId
    ) {
        CancelBookingResponse response =
                bookingCancellationService.cancel(
                        bookingId
                );

        return ResponseEntity.ok(
                response
        );
    }

        @PostMapping(
                "/modifications/{modificationId}/finalize"
        )
        public ResponseEntity<BookingModificationResponse>
        finalizeModification(
                @PathVariable UUID modificationId
        ) {
        BookingModificationResponse response =
                bookingModificationFinalizationService
                        .finalizeNoAdjustment(
                                modificationId
                        );

        return ResponseEntity.ok(
                response
        );
        }

    /**
     * Retrieves one booking only when it belongs to the authenticated
     * customer.
     */
    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBooking(
            @PathVariable UUID bookingId
    ) {
        BookingResponse response =
                bookingService.getOwnedBooking(
                        bookingId
                );

        return ResponseEntity.ok(
                response
        );
    }

        /**
         * Creates an authoritative modification proposal.
         *
         * The existing confirmed booking is not changed by this operation.
         *
         * Proposed replacement inventory is protected by a temporary hold.
         * All prices and financial differences are calculated by the backend.
         */
        @PostMapping("/{bookingId}/modifications")
        public ResponseEntity<BookingModificationResponse>
        createModification(
                @PathVariable UUID bookingId,
                @RequestBody BookingModificationRequest request
        ) {
        BookingModificationResponse response =
                bookingModificationService
                        .createModification(
                                bookingId,
                                request
                        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
        }

        /**
         * Returns one modification only when it belongs to the authenticated
         * customer.
         */
        @GetMapping("/modifications/{modificationId}")
        public ResponseEntity<BookingModificationResponse>
        getModification(
                @PathVariable UUID modificationId
        ) {
        BookingModificationResponse response =
                bookingModificationService
                        .getModification(
                                modificationId
                        );

        return ResponseEntity.ok(
                response
        );
        }
}