package com.societycentral.model;

/**
 * Lifecycle state of a conversation.
 *
 * <ul>
 *   <li>{@code ACTIVE} - open for messaging. Society groups and
 *       same-society direct chats start here.</li>
 *   <li>{@code PENDING} - a cross-society direct request awaiting the
 *       recipient's acceptance. The initiator's opening message and contact
 *       reason are visible, but no further messages may be sent until it is
 *       accepted.</li>
 *   <li>{@code REJECTED} - the recipient declined the request. No further
 *       messages may be sent.</li>
 * </ul>
 */
public enum ConversationStatus {
    ACTIVE,
    PENDING,
    REJECTED
}
