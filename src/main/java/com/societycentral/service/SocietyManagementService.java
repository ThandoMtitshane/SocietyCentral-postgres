package com.societycentral.service;

import com.societycentral.dto.request.SocietyRequestDTO;
import com.societycentral.dto.response.*;
import com.societycentral.dto.response.BulkSocietyResultDTO.RowResult;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import com.societycentral.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Handles society registration and updates,  both individual and bulk CSV.
 *
 * ID generation: societyID = "SOC" + zero-padded sequence number, e.g. SOC001.
 * The sequence is derived from the current count of societies in the DB.
 * Not collision-proof under concurrent inserts,  replace with a DB sequence
 * if needed.
 *
 * Bulk operations are best-effort: each row is processed independently.
 * One row failing does NOT roll back previously successful rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocietyManagementService {

    private final SocietyRepository societyRepository;
    private final SDORepository sdoRepository;
    private final FundTransactionService fundTransactionService;
    private final POARepository poaRepository;
    private final TaskAllocationRepository taskAllocationRepository;
    private final SocietyMemberRepository societyMemberRepository;
    private final EventRepository eventRepository;


    // ── Required CSV headers (case-insensitive) ──────────────────────────────

    public static final List<String> REQUIRED_CSV_HEADERS = List.of("societyName");

    public static final List<String> ALL_CSV_HEADERS = List.of(
            "societyName", "acronym", "societyType", "faculty", "school",
            "description", "vision", "mission", "yearEstablished",
            "contactNumber", "email", "logoUrl", "bannerUrl",
            "facebookURL", "instagramURL", "tiktokURL", "campus",
            "activeStatus"
    );

    public static final List<String> UPDATE_CSV_HEADERS;
    static {
        List<String> h = new ArrayList<>();
        h.add("societyID"); // required for update
        h.addAll(ALL_CSV_HEADERS);
        UPDATE_CSV_HEADERS = Collections.unmodifiableList(h);
    }
    public String downloadUpdateTemplate() {

        String[] headers = {
                "SocietyID",
                "societyName",
                "acronym",
                "societyType",
                "faculty",
                "school",
                "description",
                "vision",
                "mission",
                "yearEstablished",
                "contactNumber",
                "email",
                "logoUrl",
                "bannerUrl",
                "facebookURL",
                "instagramURL",
                "tiktokURL",
                "campus",
                "activeStatus"
        };

        String[] demo = {
                "SOC###",
                "DELETE_THIS_DEMO_ROW",
                "CSS",
                "ACADEMIC",
                "SC",
                "",
                "Society for Computer Science students",
                "To empower future innovators",
                "Promote collaboration and learning",
                "2018",
                "0123456789",
                "css@mandela.ac.za.ac.za",
                "https://example.com/logo.png",
                "https://example.com/banner.png",
                "https://facebook.com/example",
                "https://instagram.com/example",
                "https://tiktok.com/@example",
                "NORTH",
                "true"
        };

        return String.join(",", headers)
                + "\n"
                + String.join(",", demo);
    }
    // ── Individual Register ───────────────────────────────────────────────────
    public String downloadTemplate() {

        String[] headers = {
                "societyName",
                "acronym",
                "societyType",
                "faculty",
                "school",
                "description",
                "vision",
                "mission",
                "yearEstablished",
                "contactNumber",
                "email",
                "logoUrl",
                "bannerUrl",
                "facebookURL",
                "instagramURL",
                "tiktokURL",
                "campus",
                "activeStatus"
        };

        String[] demo = {
                "DELETE_THIS_DEMO_ROW",
                "CSS",
                "ACADEMIC",
                "SC",
                "",
                "Society for Computer Science students",
                "To empower future innovators",
                "Promote collaboration and learning",
                "2018",
                "0123456789",
                "css@mandela.ac.za.ac.za",
                "https://example.com/logo.png",
                "https://example.com/banner.png",
                "https://facebook.com/example",
                "https://instagram.com/example",
                "https://tiktok.com/@example",
                "NORTH",
                "true"
        };

        return String.join(",", headers)
                + "\n"
                + String.join(",", demo);
    }

    @Transactional
    public SocietyResponseDTO registerSociety(SocietyRequestDTO request, String sdoEmail) {
        String staffNumber = resolveStaffNumber(sdoEmail);
        validateUniqueName(request.getSocietyName(), null);

        Society society = mapToEntity(new Society(), request);
        society.setSocietyID(generateID());
        society.setSdoStaffNumber(staffNumber);
        if (society.getActiveStatus() == null) society.setActiveStatus(false);

        // Financial initialisation
        BigDecimal allocation = request.getAnnualBudgetAllocation() != null
                ? request.getAnnualBudgetAllocation()
                : BigDecimal.ZERO;
        society.setAnnualBudgetAllocation(allocation);
        society.setCurrentBalance(allocation); // starts equal to allocation
        societyRepository.save(society);

        // Record initial FundTransaction only if allocation > 0
        if (allocation.compareTo(BigDecimal.ZERO) > 0) {
            fundTransactionService.recordTransaction(
                    society.getSocietyID(),
                    allocation,
                    FundTransactionDirection.CREDIT,
                    FundTransactionReason.ANNUAL_ALLOCATION,
                    "Initial budget allocation at society registration",
                    "Registration by SDO: " + staffNumber,
                    sdoEmail
            );
        }

        return toResponseDTO(societyRepository.findById(society.getSocietyID()).orElse(society));
    }

    private boolean isRegisterTemplateRow(CSVRecord record) {
        return "DELETE_THIS_DEMO_ROW".equalsIgnoreCase(
                safeGet(record, "societyName"));
    }

    private boolean isUpdateTemplateRow(CSVRecord record) {
        return "SOC###".equalsIgnoreCase(safeGet(record, "societyID"))
                || isRegisterTemplateRow(record);
    }

    // ── Individual Update ─────────────────────────────────────────────────────

    @Transactional
    public SocietyResponseDTO updateSociety(String societyID,
                                             SocietyRequestDTO request,
                                             String sdoEmail) {
        resolveStaffNumber(sdoEmail);

        Society society = societyRepository.findById(societyID)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Society not found: " + societyID));

        validateUniqueName(request.getSocietyName(), societyID);

        // Handle annualBudgetAllocation change before mapToEntity overwrites it
        if (request.getAnnualBudgetAllocation() != null) {
            BigDecimal prevAllocation = society.getAnnualBudgetAllocation() != null
                    ? society.getAnnualBudgetAllocation()
                    : BigDecimal.ZERO;
            BigDecimal newAllocation = request.getAnnualBudgetAllocation();
            BigDecimal difference = newAllocation.subtract(prevAllocation);

            society.setAnnualBudgetAllocation(newAllocation);

            boolean immediate = Boolean.TRUE.equals(request.getEffectiveImmediately());
            if (immediate && difference.compareTo(BigDecimal.ZERO) != 0) {
                // Apply difference to currentBalance now
                FundTransactionDirection dir = difference.compareTo(BigDecimal.ZERO) > 0
                        ? FundTransactionDirection.CREDIT
                        : FundTransactionDirection.DEBIT;
                BigDecimal absAmount = difference.abs();
                societyRepository.save(society); // save new annualBudgetAllocation first
                fundTransactionService.recordTransaction(
                        societyID,
                        absAmount,
                        dir,
                        FundTransactionReason.ANNUAL_ALLOCATION,
                        "Annual budget allocation adjusted (effective immediately). "
                        + "Previous: R" + prevAllocation + " → New: R" + newAllocation,
                        "SDO adjustment by: " + sdoEmail,
                        sdoEmail
                );
            } else {
                // Save new allocation, balance unchanged - takes effect at year-start rollover
                // Still record a FundTransaction note (no balance change)
                log.info("annualBudgetAllocation updated for {} to {} (effective next year)",
                        societyID, newAllocation);
            }
        }

        mapToEntity(society, request);
        return toResponseDTO(societyRepository.save(society));
    }

    // ── Get all societies (for the societies table on SDO page) ───────────────

    public List<SocietyResponseDTO> getAllSocieties() {
        return societyRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public SocietyResponseDTO getSociety(String societyID) {
        return societyRepository.findById(societyID)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Society not found: " + societyID));
    }

    // ── Bulk Register (CSV) ───────────────────────────────────────────────────

    @Transactional
    public BulkSocietyResultDTO bulkRegister(MultipartFile file, String sdoEmail) {
        String staffNumber = resolveStaffNumber(sdoEmail);
        List<RowResult> rows = new ArrayList<>();
        int success = 0, warnings = 0, errors = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim()
                     .parse(reader)) {

            // Validate required headers exist
            Set<String> headers = parser.getHeaderMap().keySet()
                    .stream()
                    .map(String::toLowerCase)
                    .collect(java.util.stream.Collectors.toSet());

            for (String required : REQUIRED_CSV_HEADERS) {
                if (!headers.contains(required.toLowerCase())) {
                    return BulkSocietyResultDTO.builder()
                            .totalRows(0).successCount(0).warningCount(0).errorCount(1)
                            .rows(List.of(RowResult.builder()
                                    .rowNumber(0)
                                    .societyName("(header check)")
                                    .status("ERROR")
                                    .message("CSV is missing required header: '" + required
                                            + "'. Download the template and try again.")
                                    .build()))
                            .build();
                }
            }

            int rowNum = 1;
            for (CSVRecord record : parser) {
                String societyName = safeGet(record, "societyName");
                if (isRegisterTemplateRow(record)) {
                    rows.add(RowResult.builder()
                            .rowNumber(rowNum)
                            .societyName(societyName)
                            .status("WARNING")
                            .message("Skipped the template demonstration row. Remove it before uploading your actual data.")
                            .isDemoRow(true)
                            .build());

                    warnings++;
                    rowNum++;
                    continue;
                }
                RowResult.RowResultBuilder rowBuilder = RowResult.builder()
                        .rowNumber(rowNum)
                        .societyName(societyName);

                try {
                    if (societyName == null || societyName.isBlank()) {
                        throw new IllegalArgumentException(
                                "societyName is required and cannot be blank.");
                    }

                    // Check for duplicate name — do NOT insert if duplicate found
                    Society existingSociety = societyRepository.findAll().stream()
                            .filter(s -> s.getSocietyName().equalsIgnoreCase(societyName))
                            .findFirst()
                            .orElse(null);

                    SocietyRequestDTO dto = csvRowToDTO(record);
                    dto.setSocietyName(societyName);

                    if (existingSociety != null) {
                        // Build what WOULD be registered (without saving) for diff display
                        Society pendingSociety = mapToEntity(new Society(), dto);
                        pendingSociety.setSocietyID("PENDING");
                        pendingSociety.setSdoStaffNumber(staffNumber);
                        if (pendingSociety.getActiveStatus() == null) pendingSociety.setActiveStatus(false);

                        rows.add(rowBuilder.status("WARNING")
                                .message("A society named '" + societyName
                                        + "' already exists (ID: " + existingSociety.getSocietyID()
                                        + "). Not registered — choose to bypass or update instead.")
                                .societyID(existingSociety.getSocietyID())
                                .existingData(toResponseDTO(existingSociety))
                                .pendingData(toResponseDTO(pendingSociety))
                                .isDemoRow(false)
                                .build());
                        warnings++;
                    } else {
                        Society society = mapToEntity(new Society(), dto);
                        society.setSocietyID(generateID());
                        society.setSdoStaffNumber(staffNumber);
                        if (society.getActiveStatus() == null) society.setActiveStatus(false);

                        SocietyResponseDTO saved = toResponseDTO(societyRepository.save(society));

                        rows.add(rowBuilder.status("SUCCESS")
                                .message("Society registered successfully.")
                                .societyID(saved.getSocietyID())
                                .data(saved)
                                .build());
                        success++;
                    }
                } catch (Exception e) {
                    rows.add(rowBuilder.status("ERROR")
                            .message(e.getMessage())
                            .build());
                    errors++;
                    log.warn("Bulk register row {} failed: {}", rowNum, e.getMessage());
                }
                rowNum++;
            }

        } catch (Exception e) {
            log.error("Failed to parse CSV for bulk register", e);
            throw new IllegalArgumentException(
                    "Could not read the CSV file. Ensure it is a valid UTF-8 CSV: "
                            + e.getMessage());
        }

        return BulkSocietyResultDTO.builder()
                .totalRows(rows.size())
                .successCount(success)
                .warningCount(warnings)
                .errorCount(errors)
                .rows(rows)
                .build();
    }

    // ── Bypass Warning: force-register societies despite duplicate name ────────

    @Transactional
    public BulkSocietyResultDTO bypassWarnings(List<SocietyRequestDTO> societies, String sdoEmail) {
        String staffNumber = resolveStaffNumber(sdoEmail);
        List<RowResult> rows = new ArrayList<>();
        int success = 0, errors = 0;

        for (int i = 0; i < societies.size(); i++) {
            SocietyRequestDTO dto = societies.get(i);
            RowResult.RowResultBuilder rowBuilder = RowResult.builder()
                    .rowNumber(i + 1)
                    .societyName(dto.getSocietyName());
            try {
                Society society = mapToEntity(new Society(), dto);
                society.setSocietyID(generateID());
                society.setSdoStaffNumber(staffNumber);
                if (society.getActiveStatus() == null) society.setActiveStatus(false);

                SocietyResponseDTO saved = toResponseDTO(societyRepository.save(society));
                rows.add(rowBuilder.status("SUCCESS")
                        .message("Society registered (warning bypassed).")
                        .societyID(saved.getSocietyID())
                        .data(saved)
                        .build());
                success++;
            } catch (Exception e) {
                rows.add(rowBuilder.status("ERROR")
                        .message("Failed to register: " + e.getMessage())
                        .build());
                errors++;
            }
        }

        return BulkSocietyResultDTO.builder()
                .totalRows(rows.size())
                .successCount(success)
                .warningCount(0)
                .errorCount(errors)
                .rows(rows)
                .build();
    }

    // ── Update Instead: update existing societies with new CSV data ────────────

    @Transactional
    public BulkSocietyResultDTO updateInstead(List<SocietyRequestDTO> societies,
                                               List<String> existingSocietyIDs,
                                               String sdoEmail) {
        resolveStaffNumber(sdoEmail);
        List<RowResult> rows = new ArrayList<>();
        int success = 0, errors = 0;

        for (int i = 0; i < societies.size(); i++) {
            SocietyRequestDTO dto = societies.get(i);
            String targetID = (i < existingSocietyIDs.size()) ? existingSocietyIDs.get(i) : null;
            RowResult.RowResultBuilder rowBuilder = RowResult.builder()
                    .rowNumber(i + 1)
                    .societyName(dto.getSocietyName());
            try {
                if (targetID == null || targetID.isBlank()) {
                    throw new IllegalArgumentException("No existing society ID provided for update.");
                }
                Society existing = societyRepository.findById(targetID)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Society not found: " + targetID));

                mapToEntity(existing, dto);
                SocietyResponseDTO updated = toResponseDTO(societyRepository.save(existing));
                rows.add(rowBuilder.status("SUCCESS")
                        .message("Existing society updated successfully.")
                        .societyID(updated.getSocietyID())
                        .data(updated)
                        .build());
                success++;
            } catch (Exception e) {
                rows.add(rowBuilder.status("ERROR")
                        .message("Failed to update: " + e.getMessage())
                        .build());
                errors++;
            }
        }

        return BulkSocietyResultDTO.builder()
                .totalRows(rows.size())
                .successCount(success)
                .warningCount(0)
                .errorCount(errors)
                .rows(rows)
                .build();
    }

    // ── Individual Register with Warning (non-throwing duplicate check) ───────

    /**
     * Checks if a society name already exists WITHOUT throwing.
     * Returns the existing society data if found, or null if the name is unique.
     */
    public SocietyResponseDTO checkDuplicateName(String societyName) {
        return societyRepository.findAll().stream()
                .filter(s -> s.getSocietyName().equalsIgnoreCase(societyName))
                .findFirst()
                .map(this::toResponseDTO)
                .orElse(null);
    }

    /**
     * Force-register a society, bypassing the duplicate name check.
     */
    @Transactional
    public SocietyResponseDTO forceRegisterSociety(SocietyRequestDTO request, String sdoEmail) {
        String staffNumber = resolveStaffNumber(sdoEmail);
        // No validateUniqueName call — intentional bypass

        Society society = mapToEntity(new Society(), request);
        society.setSocietyID(generateID());
        society.setSdoStaffNumber(staffNumber);
        if (society.getActiveStatus() == null) society.setActiveStatus(false);

        BigDecimal allocation = request.getAnnualBudgetAllocation() != null
                ? request.getAnnualBudgetAllocation()
                : BigDecimal.ZERO;
        society.setAnnualBudgetAllocation(allocation);
        society.setCurrentBalance(allocation);
        societyRepository.save(society);

        if (allocation.compareTo(BigDecimal.ZERO) > 0) {
            fundTransactionService.recordTransaction(
                    society.getSocietyID(),
                    allocation,
                    FundTransactionDirection.CREDIT,
                    FundTransactionReason.ANNUAL_ALLOCATION,
                    "Initial budget allocation at society registration (warning bypassed)",
                    "Registration by SDO: " + staffNumber,
                    sdoEmail
            );
        }

        return toResponseDTO(societyRepository.findById(society.getSocietyID()).orElse(society));
    }

    // ── Bulk Update (CSV) ─────────────────────────────────────────────────────

    @Transactional
    public BulkSocietyResultDTO bulkUpdate(MultipartFile file, String sdoEmail) {
        resolveStaffNumber(sdoEmail);
        List<RowResult> rows = new ArrayList<>();
        int success = 0, warnings = 0, errors = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim()
                     .parse(reader)) {

            Set<String> headers = parser.getHeaderMap().keySet()
                    .stream()
                    .map(String::toLowerCase)
                    .collect(java.util.stream.Collectors.toSet());

            if (!headers.contains("societyid")) {
                return BulkSocietyResultDTO.builder()
                        .totalRows(0).successCount(0).warningCount(0).errorCount(1)
                        .rows(List.of(RowResult.builder()
                                .rowNumber(0)
                                .societyName("(header check)")
                                .status("ERROR")
                                .message("Bulk update CSV must include 'societyID' column "
                                        + "to identify which society to update.")
                                .build()))
                        .build();
            }

            int rowNum = 1;
            for (CSVRecord record : parser) {
                String societyID = safeGet(record, "societyID");
                String societyName = safeGet(record, "societyName");
                if (isUpdateTemplateRow(record)) {
                    rows.add(RowResult.builder()
                            .rowNumber(rowNum)
                            .societyID(societyID)
                            .societyName(societyName)
                            .status("WARNING")
                            .message("Skipped the template demonstration row. Remove it before uploading your actual data.")
                            .build());

                    warnings++;
                    rowNum++;
                    continue;
                }
                RowResult.RowResultBuilder rowBuilder = RowResult.builder()
                        .rowNumber(rowNum)
                        .societyName(societyName != null ? societyName : societyID)
                        .societyID(societyID);

                try {
                    if (societyID == null || societyID.isBlank()) {
                        throw new IllegalArgumentException(
                                "societyID is required for bulk update.");
                    }

                    Society society = societyRepository.findById(societyID)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "No society found with ID '" + societyID + "'."));

                    SocietyRequestDTO dto = csvRowToDTO(record);
                    if (societyName != null && !societyName.isBlank()) {
                        validateUniqueName(societyName, societyID);
                    }
                    mapToEntity(society, dto);
                    SocietyResponseDTO saved = toResponseDTO(societyRepository.save(society));

                    rows.add(rowBuilder.status("SUCCESS")
                            .message("Society updated successfully.")
                            .data(saved)
                            .build());
                    success++;

                } catch (Exception e) {
                    rows.add(rowBuilder.status("ERROR")
                            .message(e.getMessage())
                            .build());
                    errors++;
                    log.warn("Bulk update row {} failed: {}", rowNum, e.getMessage());
                }
                rowNum++;
            }

        } catch (Exception e) {
            log.error("Failed to parse CSV for bulk update", e);
            throw new IllegalArgumentException(
                    "Could not read the CSV file: " + e.getMessage());
        }

        return BulkSocietyResultDTO.builder()
                .totalRows(rows.size())
                .successCount(success)
                .warningCount(warnings)
                .errorCount(errors)
                .rows(rows)
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String resolveStaffNumber(String sdoEmail) {
        return sdoRepository.findByEmail(sdoEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user is not a registered SDO."))
                .getStaffNumber();
    }

    private void validateUniqueName(String name, String excludeID) {
        societyRepository.findAll().stream()
                .filter(s -> s.getSocietyName().equalsIgnoreCase(name))
                .filter(s -> !s.getSocietyID().equals(excludeID))
                .findFirst()
                .ifPresent(s -> {
                    throw new IllegalStateException(
                            "A society named '" + name + "' already exists.");
                });
    }

    private String generateID() {
        long count = societyRepository.count() + 1;
        return String.format("SOC%03d", count);
    }

    /**
     * Maps non-null DTO fields onto an existing (or new) Society entity.
     * Null DTO fields are ignored,  this allows partial updates.
     */
    private Society mapToEntity(Society society, SocietyRequestDTO dto) {
        if (dto.getSocietyName() != null) society.setSocietyName(dto.getSocietyName());
        if (dto.getAcronym() != null) society.setAcronym(dto.getAcronym());
        if (dto.getDescription() != null) society.setDescription(dto.getDescription());
        if (dto.getVision() != null) society.setVision(dto.getVision());
        if (dto.getMission() != null) society.setMission(dto.getMission());
        if (dto.getYearEstablished() != null) society.setYearEstablished(dto.getYearEstablished());
        if (dto.getContactNumber() != null) society.setContactNumber(dto.getContactNumber());
        if (dto.getEmail() != null) society.setEmail(dto.getEmail());
        if (dto.getLogoUrl() != null) {
            society.setLogoUrl(normaliseMediaUrl(dto.getLogoUrl(), 255));
        }
        if (dto.getBannerUrl() != null) {
            society.setBannerUrl(normaliseMediaUrl(dto.getBannerUrl(), 500));
        }
        if (dto.getFacebookURL() != null) {
            society.setFacebookURL(normaliseSocialUrl(dto.getFacebookURL()));
        }
        if (dto.getInstagramURL() != null) {
            society.setInstagramURL(normaliseSocialUrl(dto.getInstagramURL()));
        }
        if (dto.getTiktokURL() != null) {
            society.setTiktokURL(normaliseSocialUrl(dto.getTiktokURL()));
        }
        if (dto.getCampus() != null) society.setCampus(dto.getCampus());
        if (dto.getActiveStatus() != null) society.setActiveStatus(dto.getActiveStatus());
        if (dto.getSocietyType() != null) society.setSocietyType(dto.getSocietyType());
        if (dto.getMembershipFee() != null) society.setMembershipFee(dto.getMembershipFee());
        // annualBudgetAllocation handled separately in updateSociety (effectiveImmediately logic)

        if (dto.getSchool() != null) {
            society.setSchool(dto.getSchool());
        } else if (dto.getFaculty() != null) {
            society.setFaculty(dto.getFaculty());
        }

        return society;
    }

    private SocietyRequestDTO csvRowToDTO(CSVRecord record) {
        SocietyRequestDTO dto = new SocietyRequestDTO();
        dto.setSocietyName(safeGet(record, "societyName"));
        dto.setAcronym(safeGet(record, "acronym"));
        dto.setDescription(safeGet(record, "description"));
        dto.setVision(safeGet(record, "vision"));
        dto.setMission(safeGet(record, "mission"));
        dto.setEmail(safeGet(record, "email"));
        dto.setContactNumber(safeGet(record, "contactNumber"));
        dto.setLogoUrl(safeGet(record, "logoUrl"));
        dto.setBannerUrl(safeGet(record, "bannerUrl"));
        dto.setFacebookURL(safeGet(record, "facebookURL"));
        dto.setInstagramURL(safeGet(record, "instagramURL"));
        dto.setTiktokURL(safeGet(record, "tiktokURL"));

        String yearStr = safeGet(record, "yearEstablished");
        if (yearStr != null && !yearStr.isBlank()) {
            try { dto.setYearEstablished(Integer.parseInt(yearStr)); }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "yearEstablished must be a 4-digit number, got: '" + yearStr + "'");
            }
        }

        String activeStr = safeGet(record, "activeStatus");
        if (activeStr != null && !activeStr.isBlank()) {
            dto.setActiveStatus(Boolean.parseBoolean(activeStr));
        }

        String campusStr = safeGet(record, "campus");
        if (campusStr != null && !campusStr.isBlank()) {
            try { dto.setCampus(Campus.valueOf(campusStr.toUpperCase())); }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid campus value: '" + campusStr + "'. "
                        + "Valid values: " + Arrays.toString(Campus.values()));
            }
        }

        String typeStr = safeGet(record, "societyType");
        if (typeStr != null && !typeStr.isBlank()) {
            try { dto.setSocietyType(SocietyType.valueOf(typeStr.toUpperCase())); }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid societyType: '" + typeStr + "'. "
                        + "Valid values: " + Arrays.toString(SocietyType.values()));
            }
        }

        String schoolStr = safeGet(record, "school");
        if (schoolStr != null && !schoolStr.isBlank()) {
            try { dto.setSchool(School.valueOf(schoolStr.toUpperCase())); }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid school value: '" + schoolStr + "'.");
            }
        }

        String facultyStr = safeGet(record, "faculty");
        if (facultyStr != null && !facultyStr.isBlank()) {
            try { dto.setFaculty(Faculty.valueOf(facultyStr.toUpperCase())); }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid faculty value: '" + facultyStr + "'.");
            }
        }

        return dto;
    }

    private String safeGet(CSVRecord record, String column) {
        try {
            String val = record.get(column);
            return (val == null || val.isBlank()) ? null : val.trim();
        } catch (IllegalArgumentException e) {
            return null; // column not present - treat as null
        }
    }

    static String normaliseSocialUrl(String value) {
        return normaliseHttpUrl(
                value,
                200,
                "Social media links must be valid HTTP or HTTPS URLs.");
    }

    private static String normaliseMediaUrl(String value, int maximumLength) {
        return normaliseHttpUrl(
                value,
                maximumLength,
                "Society image links must be valid HTTP or HTTPS URLs.");
    }

    private static String normaliseHttpUrl(
            String value,
            int maximumLength,
            String errorMessage) {
        String normalised = value.trim();
        if (normalised.isEmpty()) {
            return null;
        }
        if (normalised.length() > maximumLength) {
            throw new IllegalArgumentException(errorMessage);
        }

        try {
            URI uri = new URI(normalised);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http")
                    || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()) {
                throw new IllegalArgumentException(errorMessage);
            }
            return normalised;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public SocietyResponseDTO toResponseDTO(Society s) {
        return SocietyResponseDTO.builder()
                .societyID(s.getSocietyID())
                .societyName(s.getSocietyName())
                .acronym(s.getAcronym())
                .societyType(s.getSocietyType() != null ? s.getSocietyType().name() : null)
                .faculty(s.getFaculty() != null ? s.getFaculty().name() : null)
                .school(s.getSchool() != null ? s.getSchool().name() : null)
                .description(s.getDescription())
                .vision(s.getVision())
                .mission(s.getMission())
                .yearEstablished(s.getYearEstablished())
                .contactNumber(s.getContactNumber())
                .email(s.getEmail())
                .logoUrl(s.getLogoUrl())
                .bannerUrl(s.getBannerUrl())
                .facebookURL(s.getFacebookURL())
                .instagramURL(s.getInstagramURL())
                .tiktokURL(s.getTiktokURL())
                .campus(s.getCampus() != null ? s.getCampus().name() : null)
                .activeStatus(s.getActiveStatus())
                .isFlagged(s.getIsFlagged())
                .numberOfMembers(s.getNumberOfMembers())
                .sdoStaffNumber(s.getSdoStaffNumber())
                .membershipFee(s.getMembershipFee())
                .annualBudgetAllocation(s.getAnnualBudgetAllocation())
                .currentBalance(s.getCurrentBalance())
                .build();
    }

    public List<SocietyBrowseSummaryDTO> getSocietySummary() {
        String email = SecurityUtils.getCurrentUserEmail();
        Boolean exist = sdoRepository.existsByEmail(email);
        System.out.println("Logged in: "+email);
        if(!exist){
            throw new UsernameNotFoundException("This action must be performed by an SDO");
        }
        return societyRepository.findAllBySdo_Email(email)
                .stream()
                .map(this::toSocietySummary)
                .toList();
    }
    private SocietyBrowseSummaryDTO toSocietySummary(Society s) {
        if(s==null) return null;
        Integer year = LocalDate.now().getYear();
        POA poa = poaRepository.findBySocietyIDAndYear(s.getSocietyID(),year).orElse(null);
        boolean poaSubmitted = poa!=null&&poa.getStatus().equals(POAStatus.SUBMITTED);
        int pendingTasks = countUnattendedTasks(s);
        Integer members = societyMemberRepository.countAllByExpireDateAfterAndIdSocietyID(LocalDate.now(),s.getSocietyID());
        boolean atRisk = checkAtRisk(s,poa);
        return new SocietyBrowseSummaryDTO(
                s.getSocietyID(),
                s.getSocietyName(),
                s.getDescription(),
                s.getSocietyType() != null ? s.getSocietyType().name() : null,
                s.getLogoUrl(),
                members,
                poaSubmitted,
                atRisk,
                pendingTasks
                ,s.getIsFlagged()
        );
    }

    private boolean checkAtRisk(Society s, POA poa){
       Integer count = societyMemberRepository.countAllByExpireDateAfterAndIdSocietyID(LocalDate.now(),s.getSocietyID());
        LocalDate beginningOfYear = LocalDate.now().withDayOfYear(1);
        LocalDate today = LocalDate.now();
        LocalDate firstJuly = LocalDate.of(LocalDate.now().getYear(), 7, 1);
       Integer countEvents = eventRepository.countAllByEventDateBetweenAndEventStatus(beginningOfYear,today,EventStatus.COMPLETED);
       return count<50 ;
       //(today.isAfter(firstJuly)&&countEvents<2);
    }

    private int countUnattendedTasks(Society s){
        List<TaskAllocation> taskAllocations = taskAllocationRepository.findByIdSocietyID(s.getSocietyID());
        int count =0;
        for(TaskAllocation t: taskAllocations){
            Task task = t.getTask();

            if(!task.getStatus().equals(TaskStatus.COMPLETE) && (task.getDueDate().isAfter(LocalDate.now()))){
                count++;
            }
        }
        return count;
    }

    public ApiResponse<String> flagSociety(String societyId) {
        Society s = societyRepository.findById(societyId).orElse(null);
        if(s==null){
            return ApiResponse.error("Society not found.");
        }
        s.setIsFlagged(!s.getIsFlagged());
        societyRepository.save(s);
        return ApiResponse.success("Society flagged successfully",null);
    }

    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    public void deleteLongFlaggedSocieties() {
        LocalDateTime threeYearsAgo = LocalDateTime.now().minusYears(3);

        List<Society> societiesToDelete = societyRepository.findAllByIsFlaggedTrueAndFlaggedDateBefore(threeYearsAgo);

        if(societiesToDelete.isEmpty()){
            log.info("Society cleanup job ran - no societies flagged over 3 years found");
            return;
        }
        log.info("Society cleanup job: deleting {} societies flagged over 3 years.",societiesToDelete.size());
        for(Society s:societiesToDelete){
            log.info("Deleting society '{}' (flagged since {})",s.getSocietyName(),s.getFlaggedDate());
        }
        societyRepository.deleteAll(societiesToDelete);

    }

}
