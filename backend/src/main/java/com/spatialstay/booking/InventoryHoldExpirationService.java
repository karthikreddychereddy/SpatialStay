package com.spatialstay.booking;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryHoldExpirationService {

        private final InventoryHoldRepository inventoryHoldRepository;
        private final BookingRepository bookingRepository;

        public InventoryHoldExpirationService(
                InventoryHoldRepository inventoryHoldRepository,
                BookingRepository bookingRepository
        ) {
                this.inventoryHoldRepository =
                        inventoryHoldRepository;

                this.bookingRepository =
                        bookingRepository;
        }

        /**
         * Normalizes stale ACTIVE holds and their corresponding
         * PENDING_PAYMENT bookings.
         *
         * Availability correctness does not depend on this scheduler.
         * expiresAt remains authoritative.
         *
         * Important concurrency rule:
         *
         * Hold lifecycle transitions are serialized through a
         * PESSIMISTIC_WRITE lock on the hold row.
         *
         * Step 10 payment confirmation must use this same locking
         * boundary before consuming a hold and confirming a booking.
         */
        @Scheduled(
                fixedDelayString =
                        "${spatialstay.booking.hold-expiration-scan-ms:60000}"
        )
        @Transactional
        public void expireStaleHolds() {

                Instant now =
                        Instant.now();

                /*
                * First obtain candidates.
                *
                * Do not trust this list as authoritative after it is read.
                * Each candidate is locked and re-checked before mutation.
                */
                List<InventoryHold> candidates =
                        inventoryHoldRepository
                                .findByStatusAndExpiresAtLessThanEqual(
                                        InventoryHoldStatus.ACTIVE,
                                        now
                                );

                List<UUID> holdPublicIds =
                        candidates
                                .stream()
                                .map(InventoryHold::getPublicId)
                                .toList();

                for (UUID holdPublicId : holdPublicIds) {

                InventoryHold hold =
                        inventoryHoldRepository
                                .findByPublicIdForUpdate(
                                        holdPublicId
                                )
                                .orElse(null);

                if (hold == null) {
                        continue;
                }

                /*
                * Re-check after acquiring the lifecycle lock.
                *
                * This is essential because another transaction may
                * have changed the hold between candidate selection
                * and lock acquisition.
                */
                if (
                        hold.getStatus()
                                != InventoryHoldStatus.ACTIVE
                ) {
                        continue;
                }

                if (
                        hold.getExpiresAt() == null
                                || hold.getExpiresAt().isAfter(now)
                ) {
                        continue;
                }

                bookingRepository
                        .findByInventoryHoldPublicId(
                                hold.getPublicId()
                        )
                        .filter(
                                booking ->
                                        booking.getStatus()
                                                == BookingStatus.PENDING_PAYMENT
                        )
                        .ifPresent(
                                booking ->
                                        booking.markExpired(now)
                        );

                hold.markExpired(now);
                }
        }
}