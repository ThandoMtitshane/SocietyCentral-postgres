package com.societycentral.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import com.societycentral.model.MentionType;

/**
 * One structured mention supplied when sending or editing a message. For a
 * {@code USER} mention set {@code targetStudentNumber}; for an {@code EVENT}
 * mention set {@code targetEventID}.
 */
@Getter
@Setter
public class MentionRequestDTO {

    @NotNull(message = "Mention type is required.")
    private MentionType mentionType;

    private String targetStudentNumber;

    private String targetEventID;
}
