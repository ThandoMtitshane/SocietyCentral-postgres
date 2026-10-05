package com.societycentral.service;

import com.societycentral.model.Announcement;
import com.societycentral.model.Executive;
import com.societycentral.model.ExecutiveId;
import com.societycentral.model.Society;
import com.societycentral.model.Student;
import com.societycentral.repository.AnnouncementRepository;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.SDORepository;
import com.societycentral.repository.SocietyMemberRepository;
import com.societycentral.repository.SocietyRepository;
import com.societycentral.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTests {

    private static final String EMAIL = "executive@nmu.ac.za";

    @Mock private SDORepository sdoRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private ExecutiveRepository executiveRepository;
    @Mock private AnnouncementRepository announcementRepository;
    @Mock private SocietyRepository societyRepository;
    @Mock private SocietyMemberRepository societyMemberRepository;
    @Mock private NotificationService notificationService;
    @Mock private AnnouncementAudienceResolver announcementAudienceResolver;

    private AnnouncementService service;
    private Student student;
    private Society ownSociety;
    private Executive ownRole;

    @BeforeEach
    void setUp() {
        service = new AnnouncementService(
                announcementRepository,
                societyRepository,
                studentRepository,
                executiveRepository,
                sdoRepository,
                societyMemberRepository,
                notificationService,
                announcementAudienceResolver);
        student = new Student();
        student.setStudentNumber("220000001");
        student.setEmail(EMAIL);
        ownSociety = society("SOC001");
        ownRole = executive(student, ownSociety);
    }

    @Test
    void removedAnnouncementRemainsInExecutiveManagementHistory() {
        Announcement removed = announcement("ANN001", ownSociety, "author@nmu.ac.za");
        removed.setRemoved(true);
        removed.setRemovedAt(LocalDateTime.now().minusDays(1));
        when(studentRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(executiveRepository.findActiveExecutiveRoles(
                student.getStudentNumber(), LocalDate.now())).thenReturn(List.of(ownRole));
        when(sdoRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(announcementRepository.findAll()).thenReturn(List.of(removed));

        assertEquals(List.of(removed), service.findForManager(EMAIL));
    }

    @Test
    void executiveCannotManageAnotherSocietysAnnouncementEvenIfTheyWereTheSender() {
        Announcement other = announcement("ANN010", society("SOC010"), EMAIL);
        when(announcementRepository.findById("ANN010")).thenReturn(Optional.of(other));
        when(studentRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(executiveRepository.findActiveExecutiveRoles(
                student.getStudentNumber(), LocalDate.now())).thenReturn(List.of(ownRole));

        assertThrows(AccessDeniedException.class,
                () -> service.remove("ANN010", EMAIL));
    }

    private Society society(String societyID) {
        Society society = new Society();
        society.setSocietyID(societyID);
        return society;
    }

    private Executive executive(Student executiveStudent, Society society) {
        Executive executive = new Executive();
        executive.setId(new ExecutiveId(
                executiveStudent.getStudentNumber(),
                society.getSocietyID(),
                LocalDate.now().minusMonths(1)));
        executive.setStudent(executiveStudent);
        executive.setSociety(society);
        executive.setPosition("President");
        return executive;
    }

    private Announcement announcement(
            String announcementID, Society society, String sentBy) {
        Announcement announcement = new Announcement();
        announcement.setAnnouncementID(announcementID);
        announcement.setSociety(society);
        announcement.setSentBy(sentBy);
        announcement.setDatePosted(LocalDateTime.now().minusDays(1));
        return announcement;
    }
}
