package com.societycentral.model;

/**
 * Kind of institutional messaging conversation.
 *
 * <ul>
 *   <li>{@code SOCIETY_GROUP} - one automatically managed group per society
 *       containing all current executives of that society.</li>
 *   <li>{@code DIRECT} - an optional one-on-one chat between two
 *       executives.</li>
 *   <li>{@code SDO_SOCIETY} - the institutional channel between a society's
 *       assigned SDO and its current President and Secretary.</li>
 * </ul>
 */
public enum ConversationType {
    SOCIETY_GROUP,
    DIRECT,
    SDO_SOCIETY
}
