package com.societycentral.model;

public enum FundTransactionReason {
    ANNUAL_ALLOCATION,   // SDO sets/adjusts the annual budget allocation
    MEMBERSHIP_FEE,      // new member joins, membership fee added
    BUDGET_APPROVED,     // a BudgetRequest approved, funds deducted
    MANUAL_ADJUSTMENT,   // SDO manual correction
    OTHER
}