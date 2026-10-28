package com.spatialstay.refund;

/**
 * Durable refund lifecycle.
 *
 * PENDING:
 * SpatialStay owes a refund but no provider request has been confirmed yet.
 *
 * PROCESSING:
 * A refund request has been submitted / is being processed.
 *
 * SUCCEEDED:
 * Provider has authoritatively confirmed the refund.
 *
 * FAILED:
 * A provider request failed in a way that may safely be retried.
 *
 * REQUIRES_RECONCILIATION:
 * Provider/local state cannot safely be resolved automatically.
 */
public enum RefundStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    REQUIRES_RECONCILIATION
}