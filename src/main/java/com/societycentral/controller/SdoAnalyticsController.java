package com.societycentral.controller;

import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.FundTransactionResponseDTO;
import com.societycentral.dto.response.SdoAnalyticsReportDTO;
import com.societycentral.service.SdoAnalyticsService;
import com.societycentral.service.SdoFundHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * SDO portfolio Analytics & Reports and cross-society fund history.
 * Scoped to the authenticated SDO's supervised societies.
 */
@RestController
@RequestMapping("/api/sdo")
@PreAuthorize("hasRole('SDO')")
@RequiredArgsConstructor
public class SdoAnalyticsController {

    private final SdoAnalyticsService sdoAnalyticsService;
    private final SdoFundHistoryService sdoFundHistoryService;

    /**
     * Portfolio-wide analytics across all supervised societies over an optional
     * date range (summary, per-society breakdown, event-report overview).
     * GET /api/sdo/analytics/report?from=YYYY-MM-DD&to=YYYY-MM-DD
     */
    @GetMapping("/analytics/report")
    public ResponseEntity<ApiResponse<SdoAnalyticsReportDTO>> getAnalyticsReport(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserDetails userDetails) {

        SdoAnalyticsReportDTO report = sdoAnalyticsService.buildReport(
                userDetails.getUsername(), from, to);
        return ResponseEntity.ok(
                ApiResponse.success("SDO analytics report generated successfully.", report));
    }

    /**
     * Fund transaction history across all supervised societies, newest first.
     * Optional ?societyID= filters to one society for drill-down.
     * GET /api/sdo/finance/history
     */
    @GetMapping("/finance/history")
    public ResponseEntity<ApiResponse<List<FundTransactionResponseDTO>>> getFundHistory(
            @RequestParam(value = "societyID", required = false) String societyID,
            @AuthenticationPrincipal UserDetails userDetails) {

        List<FundTransactionResponseDTO> history =
                sdoFundHistoryService.getHistoryForSdo(userDetails.getUsername(), societyID);
        return ResponseEntity.ok(
                ApiResponse.success("Fund history retrieved successfully.", history));
    }
}
