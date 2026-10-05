package com.societycentral.controller;

import com.societycentral.dto.request.BulkRegisterExecutiveRequestDTO;
import com.societycentral.dto.request.BulkUpdateExecutiveRequestDTO;
import com.societycentral.dto.request.RegisterExecutiveRequestDTO;
import com.societycentral.dto.request.UpdateExecutiveRequestDTO;
import com.societycentral.dto.response.AnalyticsReportDTO;
import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.ExecutiveResponseDTO;
import com.societycentral.dto.response.ExecutiveSummaryDTO;
import com.societycentral.dto.response.FundTransactionResponseDTO;
import com.societycentral.model.Executive;
import com.societycentral.model.SDO;
import com.societycentral.model.Society;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.SDORepository;
import com.societycentral.repository.SocietyRepository;
import com.societycentral.service.ExecutiveAnalyticsService;
import com.societycentral.service.ExecutiveService;
import com.societycentral.service.FundTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only endpoints for looking up executives, used to populate
 * assignee-selection dropdowns (e.g. Assign Task).
 */
@RestController
@RequestMapping("/api/executives")
@RequiredArgsConstructor
public class ExecutiveController {


    private final ExecutiveService executiveService;
    private final ExecutiveRepository executiveRepository;
    private final SDORepository sdoRepository;
    private final SocietyRepository societyRepository;
    private final FundTransactionService fundTransactionService;
    private final ExecutiveAnalyticsService executiveAnalyticsService;

    /**
     * Fund transaction history (bank-statement style) for the authenticated
     * executive's own society. Newest first, with the person behind each
     * transaction resolved to a display name.
     * GET /api/executives/finance/history
     */
    @GetMapping("/finance/history")
    public ResponseEntity<ApiResponse<List<FundTransactionResponseDTO>>> getFundHistory(
            Authentication authentication) {
        List<FundTransactionResponseDTO> history =
                fundTransactionService.getHistoryForExecutive(authentication.getName());
        return ResponseEntity.ok(
                ApiResponse.success("Fund history retrieved successfully.", history));
    }

    /**
     * Aggregated Analytics & Reports for the authenticated executive's own
     * society over an optional date range (financial, event performance,
     * membership). Dates are inclusive; omit either for an open bound.
     * GET /api/executives/analytics/report?from=YYYY-MM-DD&to=YYYY-MM-DD
     */
    @GetMapping("/analytics/report")
    public ResponseEntity<ApiResponse<AnalyticsReportDTO>> getAnalyticsReport(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {

        AnalyticsReportDTO report = executiveAnalyticsService.buildReport(
                authentication.getName(), from, to);
        return ResponseEntity.ok(
                ApiResponse.success("Analytics report generated successfully.", report));
    }

    /**
     * Returns the current executives of the given society.
     * Used to populate the "Assign To" dropdown when assigning tasks.
     */
    @GetMapping("/society/{societyID}")
    public ResponseEntity<ApiResponse<List<ExecutiveSummaryDTO>>> getCurrentExecutives(
            @PathVariable String societyID) {

        List<Executive> executives = executiveRepository
                .findCurrentExecutivesBySociety(societyID, LocalDate.now());

        List<ExecutiveSummaryDTO> result = executives.stream()
                .map(e -> new ExecutiveSummaryDTO(
                        e.getId().getStudentNumber(),
                        e.getStudent() != null ? e.getStudent().getUser().getFirstName() : null,
                        e.getStudent() != null ? e.getStudent().getUser().getLastName() : null,
                        e.getPosition()
                ))
                .toList();

        return ResponseEntity.ok(
                ApiResponse.success("Current executives retrieved successfully", result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SDO EXECUTIVE REGISTRATION ENDPOINTS (B901)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Register a single executive (SDO only)
     * POST /api/executives/sdo/register
     */
    @PostMapping("/sdo/register")
    public ResponseEntity<ApiResponse<ExecutiveResponseDTO>> registerExecutive(
            @Valid @RequestBody RegisterExecutiveRequestDTO request,
            Authentication authentication) {

        ExecutiveResponseDTO executive = executiveService.registerExecutive(
                request,
                authentication.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Executive registered successfully.", executive));
    }

    /**
     * Bulk register executives (SDO only)
     * POST /api/executives/sdo/register/bulk
     */
    @PostMapping("/sdo/register/bulk")
    public ResponseEntity<ApiResponse<List<ExecutiveResponseDTO>>> bulkRegisterExecutives(
            @Valid @RequestBody BulkRegisterExecutiveRequestDTO request,
            Authentication authentication) {

        List<ExecutiveResponseDTO> executives = executiveService.bulkRegisterExecutives(
                request.getExecutives(),
                authentication.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Executives registered successfully.", executives));
    }



    /**
     * Update a single executive (SDO only)
     * PUT /api/executives/sdo/update
     */
    @PutMapping("/sdo/update")
    public ResponseEntity<ApiResponse<ExecutiveResponseDTO>> updateExecutive(
            @Valid @RequestBody UpdateExecutiveRequestDTO request,
            Authentication authentication) {

        ExecutiveResponseDTO executive = executiveService.updateExecutive(
                request,
                authentication.getName()
        );

        return ResponseEntity.ok(ApiResponse.success("Executive updated successfully.", executive));
    }

    /**
     * Bulk update executives (SDO only)
     * PUT /api/executives/sdo/update/bulk
     */
    @PutMapping("/sdo/update/bulk")
    public ResponseEntity<ApiResponse<List<ExecutiveResponseDTO>>> bulkUpdateExecutives(
            @Valid @RequestBody BulkUpdateExecutiveRequestDTO request,
            Authentication authentication) {

        List<ExecutiveResponseDTO> executives = executiveService.bulkUpdateExecutives(
                request.getExecutives(),
                authentication.getName()
        );

        return ResponseEntity.ok(ApiResponse.success("Executives updated successfully.", executives));
    }

    @GetMapping("/sdo/society/{societyID}")
    @Transactional
    public ResponseEntity<ApiResponse<List<ExecutiveResponseDTO>>> getExecutivesForUpdate(
            @PathVariable String societyID,
            Authentication authentication) {

        SDO sdo = sdoRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("SDO not found"));

        Society society = societyRepository.findById(societyID)
                .orElseThrow(() -> new IllegalArgumentException("Society not found"));

        if (!society.getSdoStaffNumber().equals(sdo.getStaffNumber())) {
            throw new IllegalArgumentException("You do not supervise this society");
        }

        List<Executive> executives = executiveRepository.findByIdSocietyIDWithUser(societyID);

        List<ExecutiveResponseDTO> result = executives.stream()
                .map(e -> {
                    ExecutiveResponseDTO dto = new ExecutiveResponseDTO();
                    dto.setStudentNumber(e.getId().getStudentNumber());
                    dto.setSocietyID(e.getId().getSocietyID());
                    dto.setTermStartDate(e.getId().getTermStartDate());
                    dto.setPosition(e.getPosition());
                    dto.setTermEndDate(e.getTermEndDate());

                    if (e.getStudent() != null) {
                        dto.setEmail(e.getStudent().getEmail());

                        if (e.getStudent().getUser() != null) {
                            String first = e.getStudent().getUser().getFirstName();
                            String last = e.getStudent().getUser().getLastName();

                            String full = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                            dto.setFullName(full.isEmpty() ? "—" : full);
                        } else {
                            dto.setFullName("—");
                        }
                    } else {
                        dto.setEmail("—");
                        dto.setFullName("—");
                    }

                    if (e.getSociety() != null) {
                        dto.setSocietyName(e.getSociety().getSocietyName());
                    }

                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.success("Executives retrieved successfully", result));
    }

    /**
     * Get all executives for a society with full details (for viewing)
     * GET /api/executives/sdo/view/society/{societyID}
     */
    @GetMapping("/sdo/view/society/{societyID}")
    @Transactional
    public ResponseEntity<ApiResponse<List<ExecutiveResponseDTO>>> viewExecutives(
            @PathVariable String societyID,
            Authentication authentication) {

        // Verify SDO supervises this society
        SDO sdo = sdoRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("SDO not found"));

        Society society = societyRepository.findById(societyID)
                .orElseThrow(() -> new IllegalArgumentException("Society not found"));

        if (!society.getSdoStaffNumber().equals(sdo.getStaffNumber())) {
            throw new IllegalArgumentException("You do not supervise this society");
        }

        // Get all executives (including past) for this society
        List<Executive> executives = executiveRepository.findByIdSocietyIDWithUser(societyID);

        List<ExecutiveResponseDTO> result = executives.stream()
                .map(e -> {
                    ExecutiveResponseDTO dto = new ExecutiveResponseDTO();
                    dto.setStudentNumber(e.getId().getStudentNumber());
                    dto.setSocietyID(e.getId().getSocietyID());
                    dto.setTermStartDate(e.getId().getTermStartDate());
                    dto.setPosition(e.getPosition());
                    dto.setTermEndDate(e.getTermEndDate());
                    dto.setEmail(e.getStudent() != null ? e.getStudent().getEmail() : "—");

                    String fullName = "—";
                    if (e.getStudent() != null && e.getStudent().getUser() != null) {
                        String fn = e.getStudent().getUser().getFirstName();
                        String ln = e.getStudent().getUser().getLastName();
                        if (fn != null) fullName = fn;
                        if (ln != null) fullName += " " + ln;
                        fullName = fullName.trim();
                        if (fullName.isEmpty()) fullName = "—";
                    }
                    dto.setFullName(fullName);

                    if (e.getSociety() != null) {
                        dto.setSocietyName(e.getSociety().getSocietyName());
                    }

                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.success("Executives retrieved successfully", result));
    }
}