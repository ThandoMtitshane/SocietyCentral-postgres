package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * One person in the messaging directory. Either an executive (identified by
 * {@code studentNumber}, with a society + position) or an SDO (identified by
 * {@code sdoStaffNumber}). {@code type} tells the client how to label the row
 * and which identifier to send when starting a conversation.
 */
@Getter
@Builder
public class DirectoryEntryView {
    /** "EXECUTIVE" or "SDO". */
    private String type;

    private String name;

    /** Set when type = EXECUTIVE. */
    private String studentNumber;
    /** Executive portfolio, e.g. "President". Null for SDOs. */
    private String position;
    private String societyID;
    private String societyName;

    /** Set when type = SDO. */
    private String sdoStaffNumber;

    /**
     * True when messaging this person needs no contact reason (same society for
     * execs, or a supervising relationship). Cross-society exec chats require a
     * reason and open as a request.
     */
    private boolean sameSociety;
}
