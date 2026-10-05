package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Returned after a bulk register or bulk update operation.
 * Contains per-row results so the SDO can see exactly which rows
 * succeeded, warned, or failed,  and why.
 */
@Data
@Builder
public class BulkSocietyResultDTO {

    private int totalRows;
    private int successCount;
    private int warningCount;
    private int errorCount;

    private List<RowResult> rows;

    /**
     * Result for one CSV row.
     */
    @Data
    @Builder
    public static class RowResult {

        private int rowNumber;           // 1-based (row 1 = first data row after header)
        private String societyName;      // the name attempted (for identification in the table)
        private String societyID;        // populated if the society was created/found
        private String status;           // "SUCCESS", "WARNING", "ERROR"
        private String message;          // human-readable explanation
        private SocietyResponseDTO data; // populated only on SUCCESS (the registered society)
        private SocietyResponseDTO existingData; // populated on WARNING for duplicates (the existing society for diff)
        private SocietyResponseDTO pendingData;  // populated on WARNING (what the user tried to register, for diff/bypass)
        private boolean isDemoRow;       // true if this is the template demo row (cannot be bypassed)
    }
}