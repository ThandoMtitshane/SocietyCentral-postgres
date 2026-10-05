package com.societycentral.dto.request;

import lombok.Data;
import java.util.List;

/**
 * Used for both save-as-draft and submit.
 * action: "DRAFT" or "SUBMIT"
 */
@Data
public class POARequestDTO {
    private Integer year;
    private String action; // "DRAFT" | "SUBMIT"
    private List<POAEventDTO> events;
}