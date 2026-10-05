package com.societycentral.dto.response;

import com.societycentral.model.TargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementResponseDTO {



    private String announcementID;
    private String subject;
    private String description;
    private TargetType targetType;
    private String sentBy;
    private LocalDateTime datePosted;
    private LocalDateTime publishAt;
    private LocalDateTime expireDate;

    /**
     * Target society for this announcement.
     *
     * A null value indicates that the announcement applies to
     * all societies the sender is authorised to communicate with.
     */
    private String societyID;
    private String societyName;
    private String societyLogoUrl;
    private String fromDisplayName;
    private String status;
    private boolean removed;
    private LocalDateTime removedAt;
    private String removedBy;
    private boolean canEdit;
    private boolean canRemove;

}
