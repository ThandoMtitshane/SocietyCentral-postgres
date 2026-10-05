package com.societycentral.model;

/**
 * Visibility state of a single chat message.
 *
 * <ul>
 *   <li>{@code VISIBLE} - a normal, readable message.</li>
 *   <li>{@code DELETED} - deleted for everyone (soft delete). The row is
 *       kept so replies referencing it stay valid, but the body is no longer
 *       shown. Deletion is only permitted within 15 minutes of sending.</li>
 * </ul>
 */
public enum MessageStatus {
    VISIBLE,
    DELETED
}
