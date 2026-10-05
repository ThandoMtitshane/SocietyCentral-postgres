package com.societycentral.service;

import com.societycentral.dto.request.POAEventDTO;
import com.societycentral.dto.request.POARequestDTO;
import com.societycentral.dto.response.POAEventResponseDTO;
import com.societycentral.dto.response.POAResponseDTO;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class POAService {

    private final POARepository poaRepository;
    private final POAEventRepository poaEventRepository;
    private final POAEventCoHostRepository coHostRepository;
    private final SocietyRepository societyRepository;
    private final StudentRepository studentRepository;
    private final SDORepository sdoRepository;
    private final UserRepository userRepository;
    private final ExecutiveRepository executiveRepository;
    private final EmailService emailService;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    /**
     * SDO stores firstName/lastName in User table, not on SDO itself.
     * Always use this helper,  never sdo.getFirstName().
     */
    private String sdoDisplayName(SDO sdo) {
        return userRepository.findById(sdo.getEmail())
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse("your SDO");
    }

    // ── Executive: Save or Submit ─────────────────────────────────────────────

    @Transactional
    public POAResponseDTO saveOrSubmit(POARequestDTO request, String execEmail) {
        var student = studentRepository.findByEmail(execEmail)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        var execRole = executiveRepository
                .findByIdStudentNumber(student.getStudentNumber())
                .stream()
                .filter(e -> e.getTermEndDate() == null
                        || e.getTermEndDate().isAfter(LocalDate.now()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No active executive role found"));

        String societyID = execRole.getId().getSocietyID();
        int year = request.getYear() != null
                ? request.getYear() : LocalDate.now().getYear();

        POA poa = poaRepository.findBySocietyIDAndYear(societyID, year)
                .orElseGet(() -> {
                    POA p = new POA();
                    p.setPoaID(POA.newID());
                    p.setSocietyID(societyID);
                    p.setYear(year);
                    p.setCreatedAt(LocalDateTime.now());
                    return p;
                });

        if (poa.getStatus() == POAStatus.SUBMITTED
                || poa.getStatus() == POAStatus.APPROVED) {
            throw new IllegalStateException(
                    "This POA has already been "
                            + poa.getStatus().name().toLowerCase()
                            + " and cannot be edited.");
        }

        boolean submitting = "SUBMIT".equalsIgnoreCase(request.getAction());
        if (submitting && request.getEvents() != null) {
            validateBudgetBalance(request.getEvents());
        }

        poa.setSubmittedByStudentNumber(student.getStudentNumber());
        poa.setLastUpdatedAt(LocalDateTime.now());
        poa.setStatus(submitting ? POAStatus.SUBMITTED : POAStatus.DRAFT);
        if (submitting) poa.setSubmittedDate(LocalDate.now());

        POA saved = poaRepository.save(poa);

        if (request.getEvents() != null) {
            syncEvents(saved.getPoaID(), request.getEvents());
        }

        if (submitting) {
            societyRepository.findById(societyID).ifPresent(society ->
                    sdoRepository.findById(society.getSdoStaffNumber()).ifPresent(sdo ->
                            emailService.send(
                                    EmailType.POA_SUBMITTED_TO_SDO,
                                    sdo.getEmail(),
                                    Map.of(
                                            "societyName", society.getSocietyName(),
                                            "year",        String.valueOf(year),
                                            "submittedBy", student.getStudentNumber(),
                                            "reviewLink",  baseUrl + "/sdo/poa/management"
                                    )
                            )
                    )
            );
            log.info("POA {} submitted for society {} year {} by {}",
                    saved.getPoaID(), societyID, year, execEmail);
        }

        return toResponseDTO(saved);
    }

    /**
     * Smart sync,  avoids FK_CoHost_POAEvent violation.
     * UPDATE existing events in place, INSERT new, DELETE removed.
     * For deleted events: removes co-hosts first, then deletes event.
     */
    private void syncEvents(String poaID, List<POAEventDTO> incoming) {
        Map<String, POAEvent> existingByID = poaEventRepository
                .findByPoaIDOrderBySortOrderAsc(poaID)
                .stream()
                .collect(Collectors.toMap(POAEvent::getPoaEventID, e -> e));

        Set<String> incomingIDs = incoming.stream()
                .map(POAEventDTO::getPoaEventID)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // DELETE: in DB but not in incoming
        for (String existingID : existingByID.keySet()) {
            if (!incomingIDs.contains(existingID)) {
                coHostRepository.deleteByPoaEventID(existingID);
                poaEventRepository.deleteById(existingID);
                log.debug("Deleted POAEvent {} and its CoHosts", existingID);
            }
        }

        // UPDATE or INSERT
        for (int i = 0; i < incoming.size(); i++) {
            POAEventDTO dto = incoming.get(i);
            if (dto.getPoaEventID() != null
                    && existingByID.containsKey(dto.getPoaEventID())) {
                POAEvent existing = existingByID.get(dto.getPoaEventID());
                applyEventDTO(existing, dto, poaID, i);
                poaEventRepository.save(existing);
                syncCoHosts(existing.getPoaEventID(), dto.getCoHostSocietyIDs());
            } else {
                POAEvent newEvent = new POAEvent();
                newEvent.setPoaEventID(POAEvent.newID());
                applyEventDTO(newEvent, dto, poaID, i);
                poaEventRepository.save(newEvent);
                syncCoHosts(newEvent.getPoaEventID(), dto.getCoHostSocietyIDs());
            }
        }
    }

    /**
     * Syncs co-hosts for a POAEvent,  handles both additions and removals.
     *
     * ADD:    in incoming, not already PENDING/ACCEPTED → create + email executives
     * KEEP:   PENDING/ACCEPTED still in incoming → untouched
     * REMOVE: PENDING/ACCEPTED no longer in incoming → delete + email notification
     * DECLINED co-hosts are ignored (they were never active)
     */
    private void syncCoHosts(String poaEventID, List<String> incomingSocietyIDs) {
        List<POAEventCoHost> existingCoHosts =
                coHostRepository.findByPoaEventID(poaEventID);

        Set<String> incomingSet = incomingSocietyIDs != null
                ? new HashSet<>(incomingSocietyIDs) : new HashSet<>();

        // REMOVE: existing PENDING/ACCEPTED not in incoming
        for (POAEventCoHost existing : existingCoHosts) {
            if (existing.getStatus() == POACoHostStatus.DECLINED) continue;
            if (!incomingSet.contains(existing.getInvitedSocietyID())) {
                String invitingSocietyName = resolveInvitingSocietyName(poaEventID);
                String eventName = resolveEventName(poaEventID);
                notifyExecutives(
                        existing.getInvitedSocietyID(),
                        EmailType.POA_COHOST_DECLINED,
                        Map.of(
                                "decliningeSociety", invitingSocietyName,
                                "eventName",         eventName
                        )
                );
                coHostRepository.delete(existing);
                log.debug("Removed co-host {} from event {}",
                        existing.getInvitedSocietyID(), poaEventID);
            }
        }

        // ADD: incoming not already PENDING/ACCEPTED
        Set<String> alreadyActive = existingCoHosts.stream()
                .filter(c -> c.getStatus() != POACoHostStatus.DECLINED)
                .map(POAEventCoHost::getInvitedSocietyID)
                .collect(Collectors.toSet());

        for (String invitedSocietyID : incomingSet) {
            if (!alreadyActive.contains(invitedSocietyID)) {
                POAEventCoHost coHost = new POAEventCoHost();
                coHost.setCoHostID(POAEventCoHost.newID());
                coHost.setPoaEventID(poaEventID);
                coHost.setInvitedSocietyID(invitedSocietyID);
                coHost.setStatus(POACoHostStatus.PENDING);
                coHostRepository.save(coHost);

                notifyExecutives(
                        invitedSocietyID,
                        EmailType.POA_COHOST_INVITE,
                        Map.of(
                                "invitingSociety", resolveInvitingSocietyName(poaEventID),
                                "eventName",       resolveEventName(poaEventID),
                                "respondLink",     baseUrl + "/executive/society/poa/new"
                        )
                );
                log.debug("Co-host invitation created: {} → event {}",
                        invitedSocietyID, poaEventID);
            }
        }
    }

    private void applyEventDTO(POAEvent event, POAEventDTO dto,
                               String poaID, int order) {
        event.setPoaID(poaID);
        event.setOrganizationName(dto.getOrganizationName());
        event.setMonth(dto.getMonth());
        event.setTheme(dto.getTheme());
        event.setProgramName(dto.getProgramName());
        event.setEventDate(dto.getEventDate());
        event.setVenue(dto.getVenue());
        if (dto.getAttendance() != null) {
            try { event.setAttendance(AttendingType.valueOf(dto.getAttendance())); }
            catch (IllegalArgumentException ignored) {}
        }
        event.setPurpose(dto.getPurpose());
        event.setProjectedIncomeFromAccount(safe(dto.getProjectedIncomeFromAccount()));
        event.setProjectedIncomeSponsorship(safe(dto.getProjectedIncomeSponsorship()));
        event.setExpensePromoMaterial(safe(dto.getExpensePromoMaterial()));
        event.setExpenseDataAirtime(safe(dto.getExpenseDataAirtime()));
        event.setExpenseGifts(safe(dto.getExpenseGifts()));
        event.setExpenseOther(safe(dto.getExpenseOther()));
        event.setExpenseOtherSpecification(dto.getExpenseOtherSpecification());
        event.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : order);
    }

    // ── Executive: Get current year POA ──────────────────────────────────────

    public POAResponseDTO getCurrentPOA(String execEmail) {
        var student = studentRepository.findByEmail(execEmail)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        var execRole = executiveRepository
                .findByIdStudentNumber(student.getStudentNumber())
                .stream()
                .filter(e -> e.getTermEndDate() == null
                        || e.getTermEndDate().isAfter(LocalDate.now()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active executive role"));
        String societyID = execRole.getId().getSocietyID();
        int year = LocalDate.now().getYear();
        return poaRepository.findBySocietyIDAndYear(societyID, year)
                .map(this::toResponseDTO).orElse(null);
    }

    // ── SDO: POA Management overview ──────────────────────────────────────────

    public List<POASocietyCardDTO> getPOAManagementOverview(String sdoEmail) {
        var sdo = sdoRepository.findByEmail(sdoEmail)
                .orElseThrow(() -> new IllegalArgumentException("SDO not found"));
        int year = LocalDate.now().getYear();
        return societyRepository.findAll().stream()
                .filter(s -> sdo.getStaffNumber().equals(s.getSdoStaffNumber()))
                .map(society -> {
                    POA poa = poaRepository
                            .findBySocietyIDAndYear(society.getSocietyID(), year)
                            .orElse(null);
                    return POASocietyCardDTO.builder()
                            .societyID(society.getSocietyID())
                            .societyName(society.getSocietyName())
                            .acronym(society.getAcronym())
                            .email(society.getEmail())
                            .campus(society.getCampus() != null
                                    ? society.getCampus().name() : null)
                            .poaID(poa != null ? poa.getPoaID() : null)
                            .poaStatus(poa != null ? poa.getStatus().name() : null)
                            .poaYear(year)
                            .lastUpdatedAt(poa != null ? poa.getLastUpdatedAt() : null)
                            .submittedDate(poa != null ? poa.getSubmittedDate() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── SDO: View POA ─────────────────────────────────────────────────────────

    public POAResponseDTO getPOAForSociety(String societyID, String sdoEmail) {
        validateSDOOwnership(societyID, sdoEmail);
        int year = LocalDate.now().getYear();
        return poaRepository.findBySocietyIDAndYear(societyID, year)
                .map(this::toResponseDTO).orElse(null);
    }

    public List<POAResponseDTO> getAllPOAsForSDO(String sdoEmail) {
        var sdo = sdoRepository.findByEmail(sdoEmail)
                .orElseThrow(() -> new IllegalArgumentException("SDO not found"));
        List<String> societyIDs = societyRepository.findAll().stream()
                .filter(s -> sdo.getStaffNumber().equals(s.getSdoStaffNumber()))
                .map(Society::getSocietyID).collect(Collectors.toList());
        return poaRepository.findBySocietyIDIn(societyIDs).stream()
                .map(this::toResponseDTO).collect(Collectors.toList());
    }

    // ── SDO: Review ───────────────────────────────────────────────────────────

    @Transactional
    public POAResponseDTO reviewPOA(String poaID, String action,
                                    String reviewNotes,
                                    List<SDOEventComment> eventComments,
                                    String sdoEmail) {
        var sdo = sdoRepository.findByEmail(sdoEmail)
                .orElseThrow(() -> new IllegalArgumentException("SDO not found"));
        POA poa = poaRepository.findById(poaID)
                .orElseThrow(() -> new IllegalArgumentException("POA not found"));
        validateSDOOwnership(poa.getSocietyID(), sdoEmail);

        if (poa.getStatus() != POAStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Only SUBMITTED POAs can be reviewed. Current: " + poa.getStatus());
        }

        poa.setReviewedByStaffNumber(sdo.getStaffNumber());
        poa.setReviewNotes(reviewNotes);
        poa.setLastUpdatedAt(LocalDateTime.now());

        String sdoName = sdoDisplayName(sdo);
        String societyName = societyRepository.findById(poa.getSocietyID())
                .map(Society::getSocietyName).orElse("your society");

        if ("APPROVE".equalsIgnoreCase(action)) {
            poa.setStatus(POAStatus.APPROVED);
            if (poa.getSubmittedByStudentNumber() != null) {
                studentRepository.findById(poa.getSubmittedByStudentNumber())
                        .ifPresent(student -> emailService.send(
                                EmailType.POA_APPROVED, student.getEmail(),
                                Map.of("societyName", societyName,
                                        "year", String.valueOf(poa.getYear()),
                                        "sdoName", sdoName)));
            }
            log.info("POA {} approved by SDO {}", poaID, sdoEmail);

        } else if ("REQUEST_REVISION".equalsIgnoreCase(action)) {
            poa.setStatus(POAStatus.REVISION_REQUESTED);
            if (poa.getSubmittedByStudentNumber() != null) {
                studentRepository.findById(poa.getSubmittedByStudentNumber())
                        .ifPresent(student -> emailService.send(
                                EmailType.POA_REVISION_REQUESTED, student.getEmail(),
                                Map.of("societyName", societyName,
                                        "reviewNotes", reviewNotes != null ? reviewNotes : "",
                                        "updateLink", baseUrl + "/executive/society/poa/update")));
            }
            if (eventComments != null) {
                for (SDOEventComment c : eventComments) {
                    poaEventRepository.findById(c.poaEventID()).ifPresent(ev -> {
                        ev.setSdoEventComment(c.comment());
                        poaEventRepository.save(ev);
                    });
                }
            }
            log.info("POA {} revision requested by SDO {}", poaID, sdoEmail);
        } else {
            throw new IllegalArgumentException(
                    "Invalid action. Use APPROVE or REQUEST_REVISION.");
        }

        return toResponseDTO(poaRepository.save(poa));
    }

    // ── SDO: Send reminder ────────────────────────────────────────────────────

    @Transactional
    public void sendReminder(String poaID, String sdoEmail) {
        POA poa = poaRepository.findById(poaID)
                .orElseThrow(() -> new IllegalArgumentException("POA not found"));
        validateSDOOwnership(poa.getSocietyID(), sdoEmail);

        if (poa.getStatus() != POAStatus.REVISION_REQUESTED
                && poa.getStatus() != POAStatus.DRAFT) {
            throw new IllegalStateException(
                    "Reminders can only be sent for DRAFT or REVISION_REQUESTED POAs.");
        }
        if (poa.getLastUpdatedAt() != null) {
            long daysSince = java.time.Duration.between(
                    poa.getLastUpdatedAt(), LocalDateTime.now()).toDays();
            if (daysSince < 8) {
                throw new IllegalStateException(
                        "A reminder was sent recently. You can resend after "
                                + (8 - daysSince) + " more day(s).");
            }
        }

        String societyName = societyRepository.findById(poa.getSocietyID())
                .map(Society::getSocietyName).orElse("your society");
        String sdoName = sdoRepository.findByEmail(sdoEmail)
                .map(this::sdoDisplayName).orElse("your SDO");

        notifyExecutives(poa.getSocietyID(), EmailType.POA_REMINDER,
                Map.of("societyName", societyName,
                        "year", String.valueOf(poa.getYear()),
                        "sdoName", sdoName,
                        "reminderReason", poa.getStatus() == POAStatus.REVISION_REQUESTED
                                ? "please update and resubmit your POA."
                                : "please submit your POA for review.",
                        "poaLink", baseUrl + (poa.getStatus() == POAStatus.REVISION_REQUESTED
                                ? "/executive/society/poa/update"
                                : "/executive/society/poa/new")));

        log.info("POA reminder sent for {} by SDO {}", poaID, sdoEmail);
        poa.setLastUpdatedAt(LocalDateTime.now());
        poaRepository.save(poa);
    }

    // ── SDO: Request POA ─────────────────────────────────────────────────────

    public void requestPOA(String societyID, String sdoEmail) {
        validateSDOOwnership(societyID, sdoEmail);
        int year = LocalDate.now().getYear();
        if (poaRepository.findBySocietyIDAndYear(societyID, year).isPresent()) {
            throw new IllegalStateException(
                    "This society already has a POA for " + year + ".");
        }
        String societyName = societyRepository.findById(societyID)
                .map(Society::getSocietyName).orElse("your society");
        String sdoName = sdoRepository.findByEmail(sdoEmail)
                .map(this::sdoDisplayName).orElse("your SDO");

        notifyExecutives(societyID, EmailType.POA_REQUEST_FROM_SDO,
                Map.of("societyName", societyName,
                        "year", String.valueOf(year),
                        "sdoName", sdoName,
                        "poaLink", baseUrl + "/executive/society/poa/new"));
        log.info("POA requested from society {} by SDO {} for year {}",
                societyID, sdoEmail, year);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validateBudgetBalance(List<POAEventDTO> events) {
        for (int i = 0; i < events.size(); i++) {
            POAEventDTO e = events.get(i);
            BigDecimal income = safe(e.getProjectedIncomeFromAccount())
                    .add(safe(e.getProjectedIncomeSponsorship()));
            BigDecimal expenses = safe(e.getExpensePromoMaterial())
                    .add(safe(e.getExpenseDataAirtime()))
                    .add(safe(e.getExpenseGifts()))
                    .add(safe(e.getExpenseOther()));
            if (income.compareTo(expenses) != 0) {
                throw new IllegalArgumentException(
                        "Event " + (i + 1) + " budget does not balance. "
                                + "Income R" + income + " ≠ Expenses R" + expenses + ".");
            }
            if (safe(e.getExpenseOther()).compareTo(BigDecimal.ZERO) > 0
                    && (e.getExpenseOtherSpecification() == null
                    || e.getExpenseOtherSpecification().isBlank())) {
                throw new IllegalArgumentException(
                        "Event " + (i + 1) + ": Specify what 'Other' expenses are for.");
            }
        }
    }

    private void notifyExecutives(String societyID, EmailType type,
                                  Map<String, String> vars) {
        LocalDate today = LocalDate.now();
        executiveRepository.findAll().stream()
                .filter(e -> e.getId().getSocietyID().equals(societyID))
                .filter(e -> e.getTermEndDate() == null
                        || e.getTermEndDate().isAfter(today))
                .forEach(e -> studentRepository
                        .findById(e.getId().getStudentNumber())
                        .ifPresent(s -> emailService.send(type, s.getEmail(), vars)));
    }

    private String resolveInvitingSocietyName(String poaEventID) {
        return poaEventRepository.findById(poaEventID)
                .flatMap(ev -> poaRepository.findById(ev.getPoaID()))
                .flatMap(p -> societyRepository.findById(p.getSocietyID()))
                .map(Society::getSocietyName).orElse("a society");
    }

    private String resolveEventName(String poaEventID) {
        return poaEventRepository.findById(poaEventID)
                .map(ev -> ev.getProgramName() != null
                        ? ev.getProgramName() : "a POA event")
                .orElse("a POA event");
    }

    private BigDecimal safe(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private void validateSDOOwnership(String societyID, String sdoEmail) {
        var sdo = sdoRepository.findByEmail(sdoEmail)
                .orElseThrow(() -> new IllegalArgumentException("SDO not found"));
        societyRepository.findById(societyID).ifPresent(s -> {
            if (!sdo.getStaffNumber().equals(s.getSdoStaffNumber())) {
                throw new IllegalStateException(
                        "You are not the assigned SDO for this society.");
            }
        });
    }

    private POAResponseDTO toResponseDTO(POA poa) {
        List<POAEventResponseDTO> eventDTOs = poaEventRepository
                .findByPoaIDOrderBySortOrderAsc(poa.getPoaID())
                .stream().map(this::toEventResponseDTO).collect(Collectors.toList());
        String societyName = societyRepository.findById(poa.getSocietyID())
                .map(Society::getSocietyName).orElse(poa.getSocietyID());
        return POAResponseDTO.builder()
                .poaID(poa.getPoaID()).societyID(poa.getSocietyID())
                .societyName(societyName)
                .submittedByStudentNumber(poa.getSubmittedByStudentNumber())
                .year(poa.getYear()).status(poa.getStatus().name())
                .submittedDate(poa.getSubmittedDate())
                .reviewedByStaffNumber(poa.getReviewedByStaffNumber())
                .reviewNotes(poa.getReviewNotes())
                .createdAt(poa.getCreatedAt()).lastUpdatedAt(poa.getLastUpdatedAt())
                .events(eventDTOs).build();
    }

    private POAEventResponseDTO toEventResponseDTO(POAEvent e) {
        List<POAEventResponseDTO.CoHostDTO> coHosts = coHostRepository
                .findByPoaEventID(e.getPoaEventID()).stream()
                .map(c -> {
                    String name = societyRepository.findById(c.getInvitedSocietyID())
                            .map(Society::getSocietyName)
                            .orElse(c.getInvitedSocietyID());
                    return POAEventResponseDTO.CoHostDTO.builder()
                            .coHostID(c.getCoHostID())
                            .societyID(c.getInvitedSocietyID())
                            .societyName(name).status(c.getStatus().name()).build();
                }).collect(Collectors.toList());

        return POAEventResponseDTO.builder()
                .poaEventID(e.getPoaEventID())
                .organizationName(e.getOrganizationName()).month(e.getMonth())
                .theme(e.getTheme()).programName(e.getProgramName())
                .eventDate(e.getEventDate()).venue(e.getVenue())
                .attendance(e.getAttendance() != null ? e.getAttendance().name() : null)
                .purpose(e.getPurpose())
                .projectedIncomeFromAccount(e.getProjectedIncomeFromAccount())
                .projectedIncomeSponsorship(e.getProjectedIncomeSponsorship())
                .expensePromoMaterial(e.getExpensePromoMaterial())
                .expenseDataAirtime(e.getExpenseDataAirtime())
                .expenseGifts(e.getExpenseGifts()).expenseOther(e.getExpenseOther())
                .expenseOtherSpecification(e.getExpenseOtherSpecification())
                .sdoEventComment(e.getSdoEventComment()).sortOrder(e.getSortOrder())
                .coHosts(coHosts).build();
    }

    public record SDOEventComment(String poaEventID, String comment) {}

    @lombok.Data @lombok.Builder
    public static class POASocietyCardDTO {
        private String societyID, societyName, acronym, email, campus, poaID, poaStatus;
        private Integer poaYear;
        private LocalDateTime lastUpdatedAt;
        private LocalDate submittedDate;
    }
}