package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * One participant of a conversation, for headers and @mention autocomplete.
 */
@Getter
@Builder
public class ConversationParticipantView {
    /** EXECUTIVE or SDO. */
    private String participantType;
    private String studentNumber;
    private String sdoStaffNumber;
    private String email;
    private String name;
    private String position;
    private String societyID;
    private String societyName;
    private String campus;
}
