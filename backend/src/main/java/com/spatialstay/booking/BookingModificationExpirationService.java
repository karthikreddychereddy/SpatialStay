
package com.spatialstay.booking;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class BookingModificationExpirationService {

    private static final EnumSet<BookingModificationStatus>
            EXPIRABLE_STATUSES = EnumSet.of(
                    BookingModificationStatus.PENDING,
                    BookingModificationStatus.AWAITING_PAYMENT,
                    BookingModificationStatus.READY_TO_APPLY
            );

    private final BookingModificationRepository
            modificationRepository;

    private final InventoryHoldRepository
            inventoryHoldRepository;

    public BookingModificationExpirationService(
            BookingModificationRepository modificationRepository,
            InventoryHoldRepository inventoryHoldRepository
    ) {
        this.modificationRepository = modificationRepository;
        this.inventoryHoldRepository = inventoryHoldRepository;
    }

    @Scheduled(
            fixedDelayString =
                    "${spatialstay.booking.modification-expiration-scan-ms:60000}"
    )
    @Transactional
    public void expireStaleModifications() {

        Instant now = Instant.now();

        List<UUID> candidateIds = modificationRepository
                .findExpiredModificationCandidates(
                        EXPIRABLE_STATUSES,
                        now
                );

        for (UUID modificationId : candidateIds) {

            // Match finalization's lock order:
            // modification first, replacement hold second.

            BookingModification modification =
                    modificationRepository
                            .findByPublicIdForUpdate(modificationId)
                            .orElse(null);

            if (modification == null) {
                continue;
            }

            // A payment confirmation or finalization may have
            // changed this status since candidate selection.

            if (!EXPIRABLE_STATUSES.contains(
                    modification.getStatus()
            )) {
                continue;
            }

            UUID holdId = modification
                    .getReplacementHold()
                    .getPublicId();

            InventoryHold hold = inventoryHoldRepository
                    .findByPublicIdForUpdate(holdId)
                    .orElse(null);

            if (hold == null) {
                // Missing inventory is an inconsistency.
                // Do not silently change financial state.
                continue;
            }

            if (hold.getExpiresAt() == null
                    || hold.getExpiresAt().isAfter(now)) {
                continue;
            }

            // A consumed hold may represent an already-applied
            // booking transition. Do not overwrite it.

            if (hold.getStatus()
                    == InventoryHoldStatus.CONSUMED) {
                continue;
            }

            // Preserve reconciliation and completed states.
            // This guard is repeated after both locks.

            if (!EXPIRABLE_STATUSES.contains(
                    modification.getStatus()
            )) {
                continue;
            }

            if (hold.getStatus()
                    == InventoryHoldStatus.ACTIVE) {

                hold.markExpired(now);
            }

            modification.markExpired(now);
        }
    }
}
