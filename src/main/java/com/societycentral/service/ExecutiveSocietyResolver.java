package com.societycentral.service;

import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.model.Executive;
import com.societycentral.model.Society;
import com.societycentral.model.Student;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.SocietyRepository;
import com.societycentral.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Resolves an authenticated executive's current student and society context.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveSocietyResolver {

    private static final String NOT_ACTIVE_EXECUTIVE_MESSAGE =
            "Authenticated user is not an active society executive.";

    private final StudentRepository studentRepository;
    private final ExecutiveRepository executiveRepository;
    private final SocietyRepository societyRepository;
    private final Clock clock;

    /**
     * Resolves the active society role owned by an authenticated student.
     *
     * @param authenticatedEmail email supplied by the JWT principal
     * @return authenticated executive and active society context
     */
    @Transactional(readOnly = true)
    public ActiveExecutiveSociety resolve(String authenticatedEmail) {
        Student student = resolveStudent(authenticatedEmail);

        LocalDate today = LocalDate.now(clock);
        Executive activeRole = executiveRepository.findActiveExecutiveRoles(
                        student.getStudentNumber(), today)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ForbiddenOperationException(
                        NOT_ACTIVE_EXECUTIVE_MESSAGE));

        return resolveContext(student, activeRole);
    }

    /**
     * Resolves the caller's active role for one exact society. This overload
     * avoids using an unrelated first role when a student has more than one
     * current executive record.
     */
    @Transactional(readOnly = true)
    public ActiveExecutiveSociety resolve(
            String authenticatedEmail,
            String societyID) {
        if (societyID == null || societyID.isBlank()) {
            throw new ForbiddenOperationException(
                    NOT_ACTIVE_EXECUTIVE_MESSAGE);
        }

        Student student = resolveStudent(authenticatedEmail);
        LocalDate today = LocalDate.now(clock);
        String requestedSocietyID = societyID.trim();
        Executive activeRole = executiveRepository.findActiveExecutiveRoles(
                        student.getStudentNumber(), today)
                .stream()
                .filter(role -> role.getId().getSocietyID()
                        .equalsIgnoreCase(requestedSocietyID))
                .findFirst()
                .orElseThrow(() -> new ForbiddenOperationException(
                        NOT_ACTIVE_EXECUTIVE_MESSAGE));

        return resolveContext(student, activeRole);
    }

    private Student resolveStudent(String authenticatedEmail) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new ForbiddenOperationException(
                    NOT_ACTIVE_EXECUTIVE_MESSAGE);
        }
        return studentRepository.findByEmail(authenticatedEmail.trim())
                .orElseThrow(() -> new ForbiddenOperationException(
                        NOT_ACTIVE_EXECUTIVE_MESSAGE));
    }

    private ActiveExecutiveSociety resolveContext(
            Student student,
            Executive activeRole) {
        Society society = societyRepository.findById(
                        activeRole.getId().getSocietyID())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Society not found."));

        if (!Boolean.TRUE.equals(society.getActiveStatus())) {
            throw new ForbiddenOperationException(
                    NOT_ACTIVE_EXECUTIVE_MESSAGE);
        }

        return new ActiveExecutiveSociety(student, society, activeRole);
    }

    /**
     * Immutable active executive identity and society ownership context.
     *
     * @param executiveStudent authenticated executive's student record
     * @param society executive's active society
     */
    public record ActiveExecutiveSociety(
            Student executiveStudent,
            Society society,
            Executive executiveRole) {
    }
}
