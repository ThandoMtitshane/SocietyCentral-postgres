package com.societycentral.service;

import com.societycentral.dto.request.RegisterExecutiveRequestDTO;
import com.societycentral.dto.request.UpdateExecutiveRequestDTO;
import com.societycentral.dto.response.ExecutiveResponseDTO;
import com.societycentral.model.*;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.SDORepository;
import com.societycentral.repository.SocietyRepository;
import com.societycentral.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExecutiveService {

    private final ExecutiveRepository executiveRepository;
    private final StudentRepository studentRepository;
    private final SocietyRepository societyRepository;
    private final SDORepository sdoRepository;
    private final EmailService emailService;

    @Autowired
    public ExecutiveService(ExecutiveRepository executiveRepository,
                            StudentRepository studentRepository,
                            SocietyRepository societyRepository,
                            SDORepository sdoRepository,
                            EmailService emailService) {
        this.executiveRepository = executiveRepository;
        this.studentRepository = studentRepository;
        this.societyRepository = societyRepository;
        this.sdoRepository = sdoRepository;
        this.emailService = emailService;
    }

    public List<Executive> findAll() {
        return executiveRepository.findAll();
    }

    public Optional<Executive> findById(ExecutiveId id) {
        return executiveRepository.findById(id);
    }

    public List<Executive> findByStudentNumber(String studentNumber) {
        // Returns ALL terms (past and present) for this student.
        return executiveRepository.findByIdStudentNumber(studentNumber);
    }

    public List<Executive> findBySocietyID(String societyID) {
        return executiveRepository.findByIdSocietyID(societyID);
    }

    /**
     * Register a single executive (B901)
     */
    // In registerExecutive() method
    @Transactional
    public ExecutiveResponseDTO registerExecutive(RegisterExecutiveRequestDTO request, String requestingSdoEmail) {
        // Verify SDO exists
        SDO sdo = sdoRepository.findByEmail(requestingSdoEmail)
                .orElseThrow(() -> new IllegalArgumentException("Only SDOs can register executives"));

        // Verify student exists
        Student student = studentRepository.findById(request.getStudentNumber())
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + request.getStudentNumber()));

        // Verify society exists
        Society society = societyRepository.findById(request.getSocietyID())
                .orElseThrow(() -> new IllegalArgumentException("Society not found: " + request.getSocietyID()));

        // BUSINESS RULE: SDO must supervise this society
        if (!society.getSdoStaffNumber().equals(sdo.getStaffNumber())) {
            throw new IllegalArgumentException("You do not supervise this society");
        }

        // BUSINESS RULE: A society must have a max of 20 executives
        long currentExecutives = executiveRepository
                .findCurrentExecutivesBySociety(request.getSocietyID(), LocalDate.now())
                .size();

        if (currentExecutives >= 20) {
            throw new IllegalArgumentException("Society already has 20 executives (maximum)");
        }

        assertKeyPortfolioAvailable(request.getSocietyID(), request.getPosition(),
                request.getTermStartDate(), request.getTermEndDate(), null);

        // BUSINESS RULE: A student cannot be an executive of 2 societies at the same time
        List<Executive> existingExecutives = executiveRepository
                .findByIdStudentNumber(request.getStudentNumber());

        for (Executive existing : existingExecutives) {
            if (existing.getTermEndDate() == null ||
                    existing.getTermEndDate().isAfter(LocalDate.now())) {
                // Student is currently serving as an executive in another society
                if (!existing.getId().getSocietyID().equals(request.getSocietyID())) {
                    Society existingSociety = societyRepository.findById(existing.getId().getSocietyID())
                            .orElse(null);
                    throw new IllegalArgumentException(
                            "Student is already an active executive of " +
                                    (existingSociety != null ? existingSociety.getSocietyName() : "another society")
                    );
                }
            }
        }

        // Check if executive already exists with same studentNumber, societyID, and termStartDate
        ExecutiveId id = new ExecutiveId(
                request.getStudentNumber(),
                request.getSocietyID(),
                request.getTermStartDate()
        );

        if (executiveRepository.existsById(id)) {
            throw new IllegalArgumentException("Executive already registered for this term");
        }

        // Create new executive
        Executive executive = new Executive();
        executive.setId(id);
        executive.setStudent(student);
        executive.setSociety(society);
        executive.setPosition(request.getPosition());
        executive.setTermEndDate(request.getTermEndDate());

        Executive saved = executiveRepository.save(executive);

        // Send email to executive
        sendExecutiveRegistrationEmail(student, society, saved);

        return convertToDTO(saved);
    }

    /**
     * Bulk register executives (B901)
     */
    // In bulkRegisterExecutives() method
    @Transactional
    public List<ExecutiveResponseDTO> bulkRegisterExecutives(List<RegisterExecutiveRequestDTO> requests, String requestingSdoEmail) {
        List<ExecutiveResponseDTO> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Verify SDO exists
        SDO sdo = sdoRepository.findByEmail(requestingSdoEmail)
                .orElseThrow(() -> new IllegalArgumentException("Only SDOs can register executives"));

        // Get all societies the SDO supervises
        List<Society> supervisedSocieties = societyRepository.findBySdoStaffNumber(sdo.getStaffNumber());
        List<String> supervisedSocietyIDs = supervisedSocieties.stream()
                .map(Society::getSocietyID)
                .collect(Collectors.toList());

        for (RegisterExecutiveRequestDTO request : requests) {
            try {
                // Verify student exists
                Student student = studentRepository.findById(request.getStudentNumber())
                        .orElseThrow(() -> new IllegalArgumentException("Student not found: " + request.getStudentNumber()));

                // Verify society is supervised by this SDO
                if (!supervisedSocietyIDs.contains(request.getSocietyID())) {
                    throw new IllegalArgumentException("You do not supervise society: " + request.getSocietyID());
                }

                Society society = societyRepository.findById(request.getSocietyID())
                        .orElseThrow(() -> new IllegalArgumentException("Society not found: " + request.getSocietyID()));

                assertKeyPortfolioAvailable(request.getSocietyID(), request.getPosition(),
                        request.getTermStartDate(), request.getTermEndDate(), null);

                // Check if executive already exists with same studentNumber, societyID, and termStartDate
                ExecutiveId id = new ExecutiveId(
                        request.getStudentNumber(),
                        request.getSocietyID(),
                        request.getTermStartDate()
                );

                if (executiveRepository.existsById(id)) {
                    throw new IllegalArgumentException("Executive already registered for this term: " + request.getStudentNumber());
                }

                // Create executive
                Executive executive = new Executive();
                executive.setId(id);
                executive.setStudent(student);
                executive.setSociety(society);
                executive.setPosition(request.getPosition());
                executive.setTermEndDate(request.getTermEndDate());

                Executive saved = executiveRepository.save(executive);
                results.add(convertToDTO(saved));

                // Send email
                sendExecutiveRegistrationEmail(student, society, saved);

            } catch (Exception e) {
                errors.add("Failed to register " + request.getStudentNumber() + ": " + e.getMessage());
            }
        }

        if (!errors.isEmpty() && results.isEmpty()) {
            throw new IllegalArgumentException("All registrations failed: " + String.join("; ", errors));
        }

        return results;
    }

    @Transactional
    public Executive updateExecutive(Executive executive, String requestingSdoEmail) {
        // Verify SDO
        SDO sdo = sdoRepository.findByEmail(requestingSdoEmail)
                .orElseThrow(() -> new IllegalArgumentException("Only SDOs can update executives"));

        // Verify existing executive exists
        Executive existing = executiveRepository.findById(executive.getId())
                .orElseThrow(() -> new IllegalArgumentException("Executive not found"));

        // Verify SDO supervises this society
        Society society = societyRepository.findById(executive.getId().getSocietyID())
                .orElseThrow(() -> new IllegalArgumentException("Society not found"));

        if (!society.getSdoStaffNumber().equals(sdo.getStaffNumber())) {
            throw new IllegalArgumentException("You do not supervise this society");
        }

        assertKeyPortfolioAvailable(
                executive.getId().getSocietyID(), executive.getPosition(),
                executive.getId().getTermStartDate(), executive.getTermEndDate(),
                existing.getId());

        // Update fields
        existing.setPosition(executive.getPosition());
        existing.setTermEndDate(executive.getTermEndDate());

        return executiveRepository.save(existing);
    }

    /*
    public Executive updateExecutive(Executive executive, String requestingSdoEmail) {
        // TODO: same authorisation + constraint checks as appointExecutive
        // apply to updates (e.g. setting/changing termEndDate, position).
        return executiveRepository.save(executive);
    }

     */

    public void deleteById(ExecutiveId id) {
        executiveRepository.deleteById(id);
    }

    /** Prevents overlapping President or Secretary appointments in one society. */
    private void assertKeyPortfolioAvailable(
            String societyID,
            String requestedPosition,
            LocalDate requestedStart,
            LocalDate requestedEnd,
            ExecutiveId excludedId) {
        ExecutivePosition requested = ExecutivePosition.fromStoredValue(requestedPosition);
        if (requested != ExecutivePosition.PRESIDENT
                && requested != ExecutivePosition.SECRETARY) {
            return;
        }
        boolean occupied = executiveRepository.findByIdSocietyID(societyID).stream()
                .filter(existing -> excludedId == null || !excludedId.equals(existing.getId()))
                .filter(existing -> ExecutivePosition.fromStoredValue(existing.getPosition()) == requested)
                .anyMatch(existing -> termsOverlap(
                        requestedStart, requestedEnd,
                        existing.getId().getTermStartDate(), existing.getTermEndDate()));
        if (occupied) {
            String label = requested == ExecutivePosition.PRESIDENT ? "President" : "Secretary";
            throw new IllegalArgumentException(
                    "Society already has a " + label + " for the requested term.");
        }
    }

    private boolean termsOverlap(
            LocalDate leftStart, LocalDate leftEnd,
            LocalDate rightStart, LocalDate rightEnd) {
        if (leftStart == null || rightStart == null) {
            throw new IllegalArgumentException("Executive term start date is required.");
        }
        return (rightEnd == null || !leftStart.isAfter(rightEnd))
                && (leftEnd == null || !rightStart.isAfter(leftEnd));
    }

    // TODO: helper method "isCurrentExecutiveOf(studentNumber, societyID)"
    // will be useful for EventOutcomeService (verifying the submitter is a
    // genuine executive of the hosting society) and EventFeedbackService-
    // adjacent checks. Add once the "currently serving" definition
    // (termEndDate == null vs termEndDate >= today) is confirmed.

    /**
     * Send registration confirmation email to executive
     */
    private void sendExecutiveRegistrationEmail(Student student, Society society, Executive executive) {
        try {
            if (student.getEmail() == null) return;

            Map<String, String> vars = new HashMap<>();
            vars.put("studentNumber", student.getStudentNumber());
            vars.put("societyName", society.getSocietyName());
            vars.put("position", executive.getPosition());
            vars.put("loginLink", "http://localhost:8080/login");

            emailService.send(EmailType.TASK_ASSIGNED, student.getEmail(), vars);
            // You can create a new EmailType.EXECUTIVE_REGISTRATION if needed
        } catch (Exception e) {
            // Log error but don't fail the registration
            System.err.println("Failed to send executive registration email: " + e.getMessage());
        }
    }

    /**
     * Convert Executive to DTO
     */
    private ExecutiveResponseDTO convertToDTO(Executive executive) {
        ExecutiveResponseDTO dto = new ExecutiveResponseDTO();
        dto.setStudentNumber(executive.getId().getStudentNumber());
        dto.setSocietyID(executive.getId().getSocietyID());
        dto.setTermStartDate(executive.getId().getTermStartDate());  // From composite key
        dto.setPosition(executive.getPosition());
        dto.setTermEndDate(executive.getTermEndDate());

        if (executive.getStudent() != null) {
            if (executive.getStudent().getUser() != null) {
                dto.setFullName(executive.getStudent().getUser().getFirstName() + " " +
                        executive.getStudent().getUser().getLastName());
            }
            dto.setEmail(executive.getStudent().getEmail());
        }

        if (executive.getSociety() != null) {
            dto.setSocietyName(executive.getSociety().getSocietyName());
        }

        return dto;
    }

    /**
     * Update a single executive
     */
    @Transactional
    public ExecutiveResponseDTO updateExecutive(UpdateExecutiveRequestDTO request, String requestingSdoEmail) {
        // Verify SDO exists
        SDO sdo = sdoRepository.findByEmail(requestingSdoEmail)
                .orElseThrow(() -> new IllegalArgumentException("Only SDOs can update executives"));

        // Verify SDO supervises this society
        Society society = societyRepository.findById(request.getSocietyID())
                .orElseThrow(() -> new IllegalArgumentException("Society not found"));

        if (!society.getSdoStaffNumber().equals(sdo.getStaffNumber())) {
            throw new IllegalArgumentException("You do not supervise this society");
        }

        // Find the executive - use the ID from request
        ExecutiveId id = new ExecutiveId(
                request.getStudentNumber(),
                request.getSocietyID(),
                request.getTermStartDate()
        );

        Executive executive = executiveRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Executive not found"));

        String nextPosition = request.getPosition() == null || request.getPosition().isBlank()
                ? executive.getPosition() : request.getPosition().trim();
        assertKeyPortfolioAvailable(request.getSocietyID(), nextPosition,
                request.getTermStartDate(), request.getTermEndDate(), id);

        // Update fields
        if (request.getPosition() != null && !request.getPosition().trim().isEmpty()) {
            executive.setPosition(request.getPosition().trim());
        }

        executive.setTermEndDate(request.getTermEndDate());

        Executive saved = executiveRepository.save(executive);
        return convertToDTO(saved);
    }

    /**
     * Bulk update executives
     */
    @Transactional
    public List<ExecutiveResponseDTO> bulkUpdateExecutives(List<UpdateExecutiveRequestDTO> requests, String requestingSdoEmail) {
        List<ExecutiveResponseDTO> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Verify SDO exists
        SDO sdo = sdoRepository.findByEmail(requestingSdoEmail)
                .orElseThrow(() -> new IllegalArgumentException("Only SDOs can update executives"));

        // Get all societies the SDO supervises
        List<Society> supervisedSocieties = societyRepository.findBySdoStaffNumber(sdo.getStaffNumber());
        List<String> supervisedSocietyIDs = supervisedSocieties.stream()
                .map(Society::getSocietyID)
                .collect(Collectors.toList());

        for (UpdateExecutiveRequestDTO request : requests) {
            try {
                // Verify society is supervised by this SDO
                if (!supervisedSocietyIDs.contains(request.getSocietyID())) {
                    throw new IllegalArgumentException("You do not supervise society: " + request.getSocietyID());
                }

                // Find the executive
                ExecutiveId id = new ExecutiveId(
                        request.getStudentNumber(),
                        request.getSocietyID(),
                        request.getTermStartDate()
                );

                Executive executive = executiveRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Executive not found: " + request.getStudentNumber()));

                String nextPosition = request.getPosition() == null || request.getPosition().isBlank()
                        ? executive.getPosition() : request.getPosition().trim();
                LocalDate nextEnd = request.getTermEndDate() == null
                        ? executive.getTermEndDate() : request.getTermEndDate();
                assertKeyPortfolioAvailable(request.getSocietyID(), nextPosition,
                        request.getTermStartDate(), nextEnd, id);

                // Update fields
                if (request.getPosition() != null && !request.getPosition().trim().isEmpty()) {
                    executive.setPosition(request.getPosition().trim());
                }

                if (request.getTermEndDate() != null) {
                    executive.setTermEndDate(request.getTermEndDate());
                }

                Executive saved = executiveRepository.save(executive);
                results.add(convertToDTO(saved));

            } catch (Exception e) {
                errors.add("Failed to update " + request.getStudentNumber() + ": " + e.getMessage());
            }
        }

        if (!errors.isEmpty() && results.isEmpty()) {
            throw new IllegalArgumentException("All updates failed: " + String.join("; ", errors));
        }

        return results;
    }
}
