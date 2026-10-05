package com.societycentral.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Payload for sending a message into an existing conversation. Identity and
 * the conversation are resolved from the authenticated principal and the URL,
 * never trusted from the body.
 */
@Getter
@Setter
public class SendMessageRequestDTO {

    @NotBlank(message = "Message body is required.")
    @Size(max = 2000, message = "Message must not exceed 2000 characters.")
    private String body;

    /** Optional message being replied to, within the same conversation. */
    private String replyToMessageID;

    /** Optional structured mentions embedded in the message. */
    private List<MentionRequestDTO> mentions;
}
