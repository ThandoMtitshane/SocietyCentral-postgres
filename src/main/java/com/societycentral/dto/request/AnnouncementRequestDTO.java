package com.societycentral.dto.request;

import com.societycentral.model.TargetType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementRequestDTO {

    /**
     * Optional target society.
     *
     * For SDO:
     * - Providing a societyID targets that specific society.
     * - Null means all societies handled by the SDO.
     *
     * For Executive:
     * - This value will be ignored.
     * - The system will determine the executive's society.
     */
    @Size(max = 20, message = "Society ID must not exceed 20 characters.")
    private String societyID;

    /**
     * Announcement title.
     */
    @NotBlank(message = "Announcement subject is required.")
    @Size(max = 100, message = "Announcement subject must not exceed 100 characters.")
    private String subject;

    /**
     * Announcement body.
     */
    @NotBlank(message = "Announcement description is required.")
    @Size(max = 500, message = "Announcement description must not exceed 500 characters.")
    private String description;

    /**
     * Who should receive the announcement.
     */
    @NotNull(message = "Announcement target type is required.")
    private TargetType targetType;

    /**
     * Announcement expiry date.
     */
    @NotNull(message = "Announcement expiry date is required.")
    @Future(message = "Announcement expiry date must be in the future.")
    private LocalDateTime expireDate;

    private LocalDateTime publishAt;
}
