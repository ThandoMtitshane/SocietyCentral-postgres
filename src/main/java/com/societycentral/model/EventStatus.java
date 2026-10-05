package com.societycentral.model;

/**
 * Represents the lifecycle of an event within SocietyCentral.
 */
public enum EventStatus {


    //1. Event is being prepared by an executive and has not yet been submitted.
    DRAFT,

    //2. Event has been submitted to the Student Development Office and is awaiting review.
    PROPOSED,

    //3. Event proposal was withdrawn by its primary hosting society before review.
    WITHDRAWN,

    //4. Event has been approved and may be published.
    APPROVED,

    //5.Event proposal was rejected.
    REJECTED,

    //6. Event is visible to students.
    PUBLISHED,

    //7. Event has been cancelled.
    CANCELLED,

    //8.Event has taken place and has been closed.
    COMPLETED
}
