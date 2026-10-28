package com.spatialstay.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CapturedPaymentReconciliationCaseRepository
        extends JpaRepository<CapturedPaymentReconciliationCase, Long> {
    boolean existsByPaymentAttemptPublicId(UUID paymentAttemptPublicId);
}
