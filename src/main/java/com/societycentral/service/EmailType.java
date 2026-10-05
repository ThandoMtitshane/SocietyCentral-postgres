package com.societycentral.service;

/**
 * All email notification types used in SocietyCentral.
 * Each type maps to a specific HTML template and subject line in EmailService.
 *
 * Add new types here as new use cases are built.
 */
public enum EmailType {

    // ── Auth ──────────────────────────────────────────────────────────────────
    /** Student or SDO requests a password reset link */
    FORGOT_PASSWORD,

    /** New SDO account setup link. */
    SDO_ONBOARDING,

    /** The system reminds Executive of a Task */
    TASK_REMINDER,

    // ── POA lifecycle ─────────────────────────────────────────────────────────
    /** Executive submits POA → SDO is notified to review */
    POA_SUBMITTED_TO_SDO,

    /** SDO approves POA → exec who submitted is notified */
    POA_APPROVED,

    /** SDO requests revision → exec is notified with review notes */
    POA_REVISION_REQUESTED,

    /** SDO sends a reminder to a society to submit/fix their POA */
    POA_REMINDER,

    /** SDO requests a POA from a society that has not submitted one yet */
    POA_REQUEST_FROM_SDO,

    // ── Co-hosting ────────────────────────────────────────────────────────────
    /** Society A invites Society B to co-host an event in their POA */
    POA_COHOST_INVITE,

    /** Society B accepts a co-host invitation */
    POA_COHOST_ACCEPTED,

    /** Society B declines a co-host invitation */
    POA_COHOST_DECLINED,

    // ── Society membership ────────────────────────────────────────────────────
    /** Student submits a membership application for executive review */
    MEMBERSHIP_APPLICATION_SUBMITTED,

    /** Student's membership request is approved by an executive */
    MEMBERSHIP_APPROVED,

    /** Student's membership request is rejected */
    MEMBERSHIP_REJECTED,

    // ── Events ────────────────────────────────────────────────────────────────
    /** Executive submits an event proposal → SDO is notified */
    EVENT_PROPOSAL_SUBMITTED,

    /** SDO approves an event → exec is notified */
    EVENT_APPROVED,

    /** SDO rejects an event → exec is notified */
    EVENT_REJECTED,

    /** Student RSVP confirmed,  sends ticket/QR code */
    RSVP_CONFIRMATION,

    /** Event published — invite society members to RSVP via unique link */
    EVENT_RSVP_INVITE,

    /** Advance notice sent before RSVP opens. */
    EVENT_UPCOMING,

    /** RSVP window has opened. */
    EVENT_RSVP_OPEN,

    // ── Budget ────────────────────────────────────────────────────────────────
    /** Executive submits a budget request → SDO is notified */
    BUDGET_REQUEST_SUBMITTED,

    /** SDO approves a budget request → exec is notified */
    BUDGET_REQUEST_APPROVED,

    /** SDO rejects a budget request → exec is notified */
    BUDGET_REQUEST_REJECTED,

    // ── Tasks ─────────────────────────────────────────────────────────────────
    /** A task is assigned to a student or society → recipient notified */
    TASK_ASSIGNED,

    // ── Announcements ─────────────────────────────────────────────────────────
    /** An announcement is sent to a target group */
    ANNOUNCEMENT,

    /** 1hr after event ends: ask attendees to submit feedback */
    EVENT_FEEDBACK_REQUEST,

    /** 1hr after event ends: ask executives to submit event report */
    EVENT_REPORT_REQUEST,

    /** 7 days after event: generated report PDF sent to SDO and executives */
    EVENT_REPORT_GENERATED,
}
