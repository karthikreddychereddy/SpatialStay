package com.spatialstay.booking;

import com.spatialstay.user.CurrentUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class InventoryHoldAccessService {

    private final InventoryHoldRepository inventoryHoldRepository;
    private final CurrentUser currentUser;

    public InventoryHoldAccessService(
            InventoryHoldRepository inventoryHoldRepository,
            CurrentUser currentUser
    ) {
        this.inventoryHoldRepository =
                inventoryHoldRepository;

        this.currentUser =
                currentUser;
    }

    /**
     * Returns a hold only when it belongs to the currently
     * authenticated customer.
     *
     * Ownership is enforced directly in the database query.
     */
    @Transactional(readOnly = true)
    public InventoryHold requireOwnedHold(
            UUID holdPublicId
    ) {
        if (holdPublicId == null) {
            throw new IllegalArgumentException(
                    "Hold ID is required."
            );
        }

        UUID userPublicId =
                currentUser.publicId();

        return inventoryHoldRepository
                .findByPublicIdAndUserPublicId(
                        holdPublicId,
                        userPublicId
                )
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Inventory hold does not exist or is not accessible."
                        )
                );
    }

    /**
     * Requires an owned hold that is still authoritative and active.
     */
    @Transactional(readOnly = true)
    public InventoryHold requireActiveOwnedHold(
            UUID holdPublicId
    ) {
        InventoryHold hold =
                requireOwnedHold(
                        holdPublicId
                );

        Instant now =
                Instant.now();

        if (!hold.isActiveAt(now)) {
            throw new IllegalStateException(
                    "Inventory hold is no longer active."
            );
        }

        return hold;
    }

    @Transactional(readOnly = true)
    public InventoryHoldResponse getOwnedHold(
            UUID holdPublicId
    ) {
        InventoryHold hold =
                requireOwnedHold(
                        holdPublicId
                );

        return toResponse(
                hold
        );
    }

    private InventoryHoldResponse toResponse(
            InventoryHold hold
    ) {
        return new InventoryHoldResponse(
                hold.getPublicId(),

                hold.getHotel().getSlug(),
                hold.getHotel().getName(),

                hold.getRoom().getSlug(),
                hold.getRoom().getName(),

                hold.getCheckIn(),
                hold.getCheckOut(),

                hold.getNights(),
                hold.getGuests(),
                hold.getUnits(),

                hold.getPricePerNight(),
                hold.getTotalAmount(),
                hold.getCurrency(),

                hold.getStatus(),

                hold.getExpiresAt(),
                hold.getCreatedAt()
        );
    }
}