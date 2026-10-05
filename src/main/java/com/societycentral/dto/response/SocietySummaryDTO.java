package com.societycentral.dto.response;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Compact public society representation used in dashboard society cards.
 *
 * Member counts and other internal society information are intentionally
 * excluded from this public-facing DTO.
 */

@Data
@AllArgsConstructor
@NoArgsConstructor

public class SocietySummaryDTO {
    private String societyID;
    private String societyName;
    private String description;
    private String societyType;
    private String logoUrl;
    private long upcomingEventCount;

    public SocietySummaryDTO(String societyID, String societyName,
                             String description, String societyType,
                             String logoUrl) {
        this(societyID, societyName, description, societyType, logoUrl, 0);
    }
}
