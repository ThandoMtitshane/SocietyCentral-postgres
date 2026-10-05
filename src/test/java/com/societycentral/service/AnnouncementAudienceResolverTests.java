package com.societycentral.service;

import com.societycentral.model.Student;
import com.societycentral.model.TargetType;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.SocietyMemberRepository;
import com.societycentral.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementAudienceResolverTests {

    private static final LocalDate TODAY = LocalDate.of(2030, 8, 11);
    private static final String SOCIETY_ID = "SOC001";
    private static final String STUDENT_NUMBER = "220000001";
    private static final String EMAIL = "student@nmu.ac.za";

    @Mock private StudentRepository studentRepository;
    @Mock private ExecutiveRepository executiveRepository;
    @Mock private SocietyMemberRepository societyMemberRepository;

    private AnnouncementAudienceResolver resolver;
    private Student student;

    @BeforeEach
    void setUp() {
        resolver = new AnnouncementAudienceResolver(
                studentRepository,
                executiveRepository,
                societyMemberRepository,
                Clock.fixed(Instant.parse("2030-08-11T08:30:00Z"), ZoneOffset.UTC));
        student = new Student();
        student.setStudentNumber(STUDENT_NUMBER);
        student.setEmail(EMAIL);
    }

    @Test
    void nonMemberSeesStudentsOnly() {
        assertEquals(List.of(TargetType.STUDENTS),
                resolver.resolve(student, SOCIETY_ID));
    }

    @Test
    void currentMemberSeesStudentsAndMembers() {
        when(societyMemberRepository.existsActiveMembership(
                STUDENT_NUMBER, SOCIETY_ID, TODAY)).thenReturn(true);

        assertEquals(List.of(TargetType.STUDENTS, TargetType.MEMBERS),
                resolver.resolve(student, SOCIETY_ID));
    }

    @Test
    void currentExecutiveSeesAllSupportedTargetsWithoutMembership() {
        when(executiveRepository.existsActiveExecutiveRole(
                STUDENT_NUMBER, SOCIETY_ID, TODAY)).thenReturn(true);

        assertEquals(List.of(
                        TargetType.STUDENTS,
                        TargetType.MEMBERS,
                        TargetType.EXECUTIVES),
                resolver.resolve(student, SOCIETY_ID));
    }

    @Test
    void executiveOfAnotherSocietyGetsNoElevatedAccess() {
        assertEquals(List.of(TargetType.STUDENTS),
                resolver.resolve(student, SOCIETY_ID));
        verify(executiveRepository).existsActiveExecutiveRole(
                STUDENT_NUMBER, SOCIETY_ID, TODAY);
    }

    @Test
    void emailResolutionUsesTheSameSocietySpecificRule() {
        when(studentRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(societyMemberRepository.existsActiveMembership(
                STUDENT_NUMBER, SOCIETY_ID, TODAY)).thenReturn(true);

        assertEquals(resolver.resolve(student, SOCIETY_ID),
                resolver.resolveForEmail(EMAIL, SOCIETY_ID));
    }
}
