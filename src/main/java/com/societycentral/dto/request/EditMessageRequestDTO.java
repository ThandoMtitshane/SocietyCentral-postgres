package com.societycentral.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Payload for editing a message. Only the sender may edit, and only within 15
 * minutes of sending (enforced in the service layer).
 */
@Getter
@Setter
public class EditMessageRequestDTO {

    @NotBlank(message = "Message body is required.")
    @Size(max = 2000, message = "Message must not exceed 2000 characters.")
    private String body;

    private List<MentionRequestDTO> mentions;
}
