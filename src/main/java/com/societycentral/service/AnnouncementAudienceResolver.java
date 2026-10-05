package com.societycentral.service;

import com.societycentral.model.Student;
import com.societycentral.model.TargetType;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.SocietyMemberRepository;
import com.societycentral.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Resolves recipient announcement audiences for one viewer and one society. */
@Service
@RequiredArgsConstructor
public class AnnouncementAudienceResolver {

    private final StudentRepository studentRepository;
    private final ExecutiveRepository executiveRepository;
    private final SocietyMemberRepository societyMemberRepository;
    private final Clock clock;

    public List<TargetType> resolveForEmail(String email, String societyID) {
        if (email == null || email.isBlank()) {
            return List.of(TargetType.STUDENTS);
        }
        return studentRepository.findByEmail(email.trim())
                .map(student -> resolve(student, societyID))
                .orElse(List.of(TargetType.STUDENTS));
    }

    public List<TargetType> resolve(Student student, String societyID) {
        return resolveStudentNumber(
                student == null ? null : student.getStudentNumber(), societyID);
    }

    public List<TargetType> resolveStudentNumber(
            String studentNumber, String societyID) {
        if (studentNumber == null || studentNumber.isBlank()
                || societyID == null || societyID.isBlank()) {
            return List.of(TargetType.STUDENTS);
        }

        LocalDate today = LocalDate.now(clock);
        if (executiveRepository.existsActiveExecutiveRole(
                studentNumber, societyID, today)) {
            return List.of(
                    TargetType.STUDENTS,
                    TargetType.MEMBERS,
                    TargetType.EXECUTIVES);
        }
        if (societyMemberRepository.existsActiveMembership(
                studentNumber, societyID, today)) {
            return List.of(TargetType.STUDENTS, TargetType.MEMBERS);
        }
        return List.of(TargetType.STUDENTS);
    }
}
