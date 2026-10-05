package com.societycentral.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Payload for starting a one-on-one direct conversation and sending its
 * opening message. The recipient may be an executive (identified by
 * {@code recipientStudentNumber}) or an SDO (identified by
 * {@code recipientSdoStaffNumber}); {@code recipientType} selects which.
 * When {@code recipientType} is omitted it defaults to EXECUTIVE for backward
 * compatibility.
 *
 * <p>If the pair already has an ACTIVE conversation, the body is sent there as
 * an ordinary message; a PENDING request cannot be used to send another
 * opening message.</p>
 *
 * <p>{@code contactReason} is required only for a cross-society executive
 * request (the service enforces this). Same-society and any SDO-involved chats
 * open immediately as ACTIVE.</p>
 */
@Getter
@Setter
public class StartDirectConversationRequestDTO {

    /** "EXECUTIVE" (default) or "SDO". */
    private String recipientType;

    /** Set when messaging an executive. */
    private String recipientStudentNumber;

    /** Set when messaging an SDO. */
    private String recipientSdoStaffNumber;

    @Size(max = 500, message = "Reason must not exceed 500 characters.")
    private String contactReason;

    @NotBlank(message = "An opening message is required.")
    @Size(max = 2000, message = "Message must not exceed 2000 characters.")
    private String body;

    private List<MentionRequestDTO> mentions;

    /** True when the recipient is an SDO. */
    public boolean isSdoRecipient() {
        return "SDO".equalsIgnoreCase(recipientType);
    }
}
