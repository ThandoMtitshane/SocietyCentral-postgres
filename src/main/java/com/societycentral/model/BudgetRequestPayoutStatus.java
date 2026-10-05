package com.societycentral.model;

/**
 * Tracks whether an approved budget request's payout has been
 * executed by the SDO (B300 — Process Funds).
 *
 * Separate from {@link BudgetRequestStatus} (which records the
 * SDO's approval decision) because approval and payout are two
 * different actions that happen at different times.
 *
 *   NOT_PROCESSED  Approved and awaiting payout execution.
 *   PROCESSED      Payout recorded — funds have been released.
 */
public enum BudgetRequestPayoutStatus {
    NOT_PROCESSED,
    PROCESSED
}
