package com.societycentral.model;

/**
 * Lifecycle of a society's bank account verification.
 *
 * Managed by the SDO during B300 (Process Funds), before any
 * payout is issued.
 *
 *   UNVERIFIED  The default state. Bank details are on file but
 *               have not yet been reviewed by the SDO.
 *
 *   VERIFIED    The SDO has confirmed the details are correct.
 *               A payout may proceed.
 *
 *   REJECTED    The SDO has reviewed the details and found a
 *               problem (wrong account number, wrong holder, etc.).
 *               The Executive must correct and resubmit, which
 *               resets the status to UNVERIFIED.
 *
 * Stored as VARCHAR in the database (see CK_BankAccount_VerificationStatus).
 */
public enum BankAccountVerificationStatus {
    UNVERIFIED,
    VERIFIED,
    REJECTED
}
