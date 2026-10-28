
package com.spatialstay.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingModificationExpirationServiceTest {

    @Mock
    private BookingModificationRepository modificationRepository;

    @Mock
    private InventoryHoldRepository inventoryHoldRepository;

    @InjectMocks
    private BookingModificationExpirationService service;

    private UUID modificationId;
    private UUID holdId;

    private BookingModification modification;
    private InventoryHold hold;

    @BeforeEach
    void setUp() {

        modificationId = UUID.randomUUID();
        holdId = UUID.randomUUID();

        modification = mock(BookingModification.class);
        hold = mock(InventoryHold.class);
    }

    private void candidate(
            BookingModificationStatus status,
            InventoryHoldStatus holdStatus,
            Instant expiresAt
    ) {

        when(
                modificationRepository.findExpiredModificationCandidates(
                        any(),
                        any()
                )
        ).thenReturn(List.of(modificationId));

        when(
                modificationRepository.findByPublicIdForUpdate(
                        modificationId
                )
        ).thenReturn(Optional.of(modification));

        when(modification.getStatus())
                .thenReturn(status);

        when(modification.getReplacementHold())
                .thenReturn(hold);

        when(hold.getPublicId())
                .thenReturn(holdId);

        when(
                inventoryHoldRepository.findByPublicIdForUpdate(
                        holdId
                )
        ).thenReturn(Optional.of(hold));

        when(hold.getExpiresAt())
                .thenReturn(expiresAt);

        // Use the status supplied by the individual test.
        // Lenient because a valid (non-expired) hold exits
        // before the service reads its status.

        lenient()
                .when(hold.getStatus())
                .thenReturn(holdStatus);
    }

    @Test
    void expiresModificationAndActiveReplacementHold() {

        candidate(
                BookingModificationStatus.AWAITING_PAYMENT,
                InventoryHoldStatus.ACTIVE,
                Instant.now().minusSeconds(120)
        );

        service.expireStaleModifications();

        verify(hold)
                .markExpired(any(Instant.class));

        verify(modification)
                .markExpired(any(Instant.class));
    }

    @Test
    void expiredHoldCanStillExpireItsModification() {

        candidate(
                BookingModificationStatus.READY_TO_APPLY,
                InventoryHoldStatus.EXPIRED,
                Instant.now().minusSeconds(120)
        );

        service.expireStaleModifications();

        verify(hold, never())
                .markExpired(any());

        verify(modification)
                .markExpired(any(Instant.class));
    }

    @Test
    void doesNotExpireModificationWhoseHoldIsStillValid() {

        candidate(
                BookingModificationStatus.READY_TO_APPLY,
                InventoryHoldStatus.ACTIVE,
                Instant.now().plusSeconds(3600)
        );

        service.expireStaleModifications();

        verify(hold, never())
                .markExpired(any());

        verify(modification, never())
                .markExpired(any());
    }

    @Test
    void neverExpiresConsumedHold() {

        candidate(
                BookingModificationStatus.READY_TO_APPLY,
                InventoryHoldStatus.CONSUMED,
                Instant.now().minusSeconds(120)
        );

        service.expireStaleModifications();

        verify(hold, never())
                .markExpired(any());

        verify(modification, never())
                .markExpired(any());
    }

    @Test
    void skipsCompletedModificationAfterCandidateSelection() {

        when(
                modificationRepository.findExpiredModificationCandidates(
                        any(),
                        any()
                )
        ).thenReturn(List.of(modificationId));

        when(
                modificationRepository.findByPublicIdForUpdate(
                        modificationId
                )
        ).thenReturn(Optional.of(modification));

        when(modification.getStatus())
                .thenReturn(
                        BookingModificationStatus.COMPLETED
                );

        service.expireStaleModifications();

        verifyNoInteractions(inventoryHoldRepository);

        verify(modification, never())
                .markExpired(any());
    }

    @Test
    void preservesFinancialReconciliation() {

        when(
                modificationRepository.findExpiredModificationCandidates(
                        any(),
                        any()
                )
        ).thenReturn(List.of(modificationId));

        when(
                modificationRepository.findByPublicIdForUpdate(
                        modificationId
                )
        ).thenReturn(Optional.of(modification));

        when(modification.getStatus())
                .thenReturn(
                        BookingModificationStatus.REQUIRES_RECONCILIATION
                );

        service.expireStaleModifications();

        verifyNoInteractions(inventoryHoldRepository);

        verify(modification, never())
                .markExpired(any());
    }

    @Test
    void noCandidatesDoesNotAcquireLocks() {

        when(
                modificationRepository.findExpiredModificationCandidates(
                        any(),
                        any()
                )
        ).thenReturn(List.of());

        service.expireStaleModifications();

        verify(modificationRepository, never())
                .findByPublicIdForUpdate(any());

        verifyNoInteractions(inventoryHoldRepository);
    }
}
