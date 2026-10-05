package com.societycentral.controller;

import com.societycentral.dto.request.SocietyRequestDTO;
import com.societycentral.dto.response.*;
import com.societycentral.model.SocietyImageType;
import com.societycentral.service.SocietyMediaService;
import com.societycentral.service.SocietyManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for SDO society registration and management
 * (B500/B600 use cases). Every endpoint requires {@code ROLE_SDO}.
 *
 * Endpoints:
 *   GET    /api/sdo/societies                    - list all societies
 *   GET    /api/sdo/societies/{societyID}         - get one society
 *   POST   /api/sdo/societies/register            - register individual society
 *   PUT    /api/sdo/societies/{societyID}          - update individual society
 *   POST   /api/sdo/societies/register/bulk       - bulk register via CSV
 *   PUT    /api/sdo/societies/update/bulk         - bulk update via CSV
 *   GET    /api/sdo/societies/csv-template/register - download register CSV template
 *   GET    /api/sdo/societies/csv-template/update   - download update CSV template
 */
@RestController
@RequestMapping("/api/sdo/societies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SDO')")
public class SocietyManagementController {

    private final SocietyManagementService societyManagementService;
    private final SocietyMediaService societyMediaService;

    // ── List / Get ────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<SocietyResponseDTO>>> getAllSocieties() {
        return ResponseEntity.ok(ApiResponse.success(
                "Societies retrieved", societyManagementService.getAllSocieties()));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<List<SocietyBrowseSummaryDTO>>> getSocietiesSummary(){
        return ResponseEntity.ok(ApiResponse.success("Society summary retrieved",societyManagementService.getSocietySummary()));
    }

    @GetMapping("/{societyID}")
    public ResponseEntity<ApiResponse<SocietyResponseDTO>> getSociety(
            @PathVariable String societyID) {
        return ResponseEntity.ok(ApiResponse.success(
                "Society retrieved", societyManagementService.getSociety(societyID)));
    }

    // ── Individual Register ───────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<SocietyResponseDTO>> registerSociety(
            @Valid @RequestBody SocietyRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        SocietyResponseDTO result = societyManagementService
                .registerSociety(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Society registered successfully", result));
    }

    // ── Individual Update ─────────────────────────────────────────────────────

    @PutMapping("/{societyID}")
    public ResponseEntity<ApiResponse<SocietyResponseDTO>> updateSociety(
            @PathVariable String societyID,
            @Valid @RequestBody SocietyRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        SocietyResponseDTO result = societyManagementService
                .updateSociety(societyID, request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Society updated successfully", result));
    }

    /**
     * Validates and stores a society logo or banner.
     */
    @PostMapping(
            value = "/{societyID}/media",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SocietyMediaUploadResponseDTO>>
    uploadSocietyMedia(
            @PathVariable String societyID,
            @RequestPart("file") MultipartFile file,
            @RequestParam("imageType") SocietyImageType imageType,
            @AuthenticationPrincipal UserDetails userDetails) {
        SocietyMediaUploadResponseDTO uploaded = societyMediaService.upload(
                userDetails.getUsername(),
                societyID,
                file,
                imageType);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Society image uploaded successfully.",
                        uploaded));
    }

    // ── Bulk Register (CSV) ───────────────────────────────────────────────────

    @PostMapping(value = "/register/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BulkSocietyResultDTO>> bulkRegister(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Uploaded file is empty."));
        }
        if (!file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Only CSV files are accepted."));
        }

        BulkSocietyResultDTO result = societyManagementService
                .bulkRegister(file, userDetails.getUsername());

        String message = String.format(
                "Bulk register complete: %d succeeded, %d warnings, %d failed",
                result.getSuccessCount(), result.getWarningCount(), result.getErrorCount());

        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    // ── Bypass Warnings (force-register societies that had duplicates) ─────────

    @PostMapping("/register/bypass")
    public ResponseEntity<ApiResponse<BulkSocietyResultDTO>> bypassWarnings(
            @RequestBody List<SocietyRequestDTO> societies,
            @AuthenticationPrincipal UserDetails userDetails) {

        BulkSocietyResultDTO result = societyManagementService
                .bypassWarnings(societies, userDetails.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        result.getSuccessCount() + " societies registered (warnings bypassed).",
                        result));
    }

    // ── Update Instead (update existing societies with new data from CSV) ──────

    @PutMapping("/register/update-instead")
    public ResponseEntity<ApiResponse<BulkSocietyResultDTO>> updateInstead(
            @RequestBody java.util.Map<String, Object> payload,
            @AuthenticationPrincipal UserDetails userDetails) {

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> societyMaps =
                (List<java.util.Map<String, Object>>) payload.get("societies");
        @SuppressWarnings("unchecked")
        List<String> existingIDs = (List<String>) payload.get("existingSocietyIDs");

        List<SocietyRequestDTO> societies = new java.util.ArrayList<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (java.util.Map<String, Object> map : societyMaps) {
            societies.add(mapper.convertValue(map, SocietyRequestDTO.class));
        }

        BulkSocietyResultDTO result = societyManagementService
                .updateInstead(societies, existingIDs, userDetails.getUsername());

        return ResponseEntity.ok(ApiResponse.success(
                result.getSuccessCount() + " existing societies updated.", result));
    }

    // ── Check Duplicate Name (for individual registration warning) ─────────────

    @GetMapping("/register/check-duplicate")
    public ResponseEntity<ApiResponse<SocietyResponseDTO>> checkDuplicate(
            @RequestParam String societyName) {

        SocietyResponseDTO existing = societyManagementService.checkDuplicateName(societyName);
        if (existing != null) {
            return ResponseEntity.ok(ApiResponse.warning(
                    "A society named '" + societyName + "' already exists.",
                    existing));
        }
        return ResponseEntity.ok(ApiResponse.success("Name is available.", null));
    }

    // ── Force Register (bypass duplicate check for individual) ─────────────────

    @PostMapping("/register/force")
    public ResponseEntity<ApiResponse<SocietyResponseDTO>> forceRegister(
            @Valid @RequestBody SocietyRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        SocietyResponseDTO result = societyManagementService
                .forceRegisterSociety(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Society registered (warning bypassed).", result));
    }

    // ── Bulk Update (CSV) ─────────────────────────────────────────────────────

    @PutMapping(value = "/update/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BulkSocietyResultDTO>> bulkUpdate(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Uploaded file is empty."));
        }
        if (!file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Only CSV files are accepted."));
        }

        BulkSocietyResultDTO result = societyManagementService
                .bulkUpdate(file, userDetails.getUsername());

        String message = String.format(
                "Bulk update complete: %d succeeded, %d warnings, %d failed",
                result.getSuccessCount(), result.getWarningCount(), result.getErrorCount());

        return ResponseEntity.ok(ApiResponse.success(message, result));
    }
    @GetMapping("/csv-template/register")
    public ResponseEntity<ApiResponse<String>> downloadTemplate() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "CSV template generated successfully.",
                        societyManagementService.downloadTemplate()
                )
        );
    }
    @PutMapping("/flag/{societyId}")
    public ResponseEntity<ApiResponse<String>> flagSociety(@PathVariable String societyId){
        return ResponseEntity.ok(societyManagementService.flagSociety(societyId));
    }

    @PutMapping("/unflag/{societyId}")
    public ResponseEntity<ApiResponse<String>> unflagSociety(@PathVariable String societyId){
        return ResponseEntity.ok(societyManagementService.flagSociety(societyId));
    }

    @GetMapping("/csv-template/update")
    public ResponseEntity<ApiResponse<String>> downloadUpdateTemplate() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "CSV template generated successfully.",
                        societyManagementService.downloadTemplate()
                )
        );
    }

}
