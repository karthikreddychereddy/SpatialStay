package com.spatialstay.refund;

import java.util.UUID;

public interface RefundReconciliationCandidate {

    UUID getPublicId();

    String getProviderRefundId();
}