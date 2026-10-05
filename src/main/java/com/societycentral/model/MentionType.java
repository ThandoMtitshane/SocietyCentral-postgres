package com.societycentral.model;

/**
 * Kind of structured @mention embedded in a message.
 *
 * <ul>
 *   <li>{@code USER} - mentions an executive ({@code targetStudentNumber}
 *       is set). The mentioned user is notified.</li>
 *   <li>{@code EVENT} - mentions an event hosted by the sender's own
 *       society ({@code targetEventID} is set). Rendered as a clickable link
 *       to the event.</li>
 * </ul>
 */
public enum MentionType {
    USER,
    EVENT
}
