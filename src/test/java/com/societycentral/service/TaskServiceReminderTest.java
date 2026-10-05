package com.societycentral.service;

import com.societycentral.model.*;
import com.societycentral.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TaskService#sendTaskReminders()}.
 *
 * Pure Mockito test - no Spring context is started, since the method under
 * test has no Spring-specific behaviour (no @Transactional, no autowiring
 * quirks) worth booting a context for. Path matches Maven/Gradle convention:
 * src/test/java mirrors src/main/java package-for-package.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceReminderTest {

    @Mock private TaskRepository taskRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private ExecutiveRepository executiveRepository;
    @Mock private SDORepository sdoRepository;
    @Mock private SocietyRepository societyRepository;
    @Mock private EmailService emailService;
    @Mock private TaskAllocationRepository taskAllocationRepository;

    private TaskService taskService;

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    @BeforeEach
    void setUp() {
        taskService = new TaskService(
                taskRepository,
                studentRepository,
                sdoRepository,
                societyRepository,
                executiveRepository,
                emailService,
                taskAllocationRepository,
                notificationRepository
        );
    }

    @Test
    void individualTask_dueInOneDay_sendsReminderAndCreatesNotification() {
        LocalDate today = LocalDate.now(SAST);

        Task task = new Task();
        task.setTaskID("TASK001");
        task.setTaskName("Demo Task");
        task.setTaskDescription("Test description");
        task.setStatus(TaskStatus.PENDING);
        task.setTargetType(TaskTargetType.INDIVIDUAL);
        task.setAssignedTo("s229878873");
        task.setAssignedBy("sdo@nmu.ac.za");
        task.setIssueDate(today);
        task.setDueDate(today.plusDays(1)); // matches reminder window

        Student assignee = new Student();
        assignee.setStudentNumber("s229878873");
        assignee.setEmail("student@mandela.ac.za");

        when(taskRepository.findByStatus(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(studentRepository.findById("s229878873")).thenReturn(Optional.of(assignee));
        when(notificationRepository.count()).thenReturn(0L);

        taskService.sendTaskReminders();

        // Verify email sent to the correct student
        verify(emailService, times(1))
                .send(eq(EmailType.TASK_REMINDER), eq("student@mandela.ac.za"), anyMap());

        // Verify a Notification row was created and saved
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals("student@mandela.ac.za", saved.getRecipientEmail());
        assertEquals(NotificationType.TASK_REMINDER, saved.getNotifType());
        assertEquals("TASK001", saved.getRelatedID());
        assertFalse(saved.getIsRead());
    }

    @Test
    void individualTask_dueInSixDays_doesNotSendReminder() {
        LocalDate today = LocalDate.now(SAST);

        Task task = new Task();
        task.setTaskID("TASK002");
        task.setStatus(TaskStatus.PENDING);
        task.setTargetType(TaskTargetType.INDIVIDUAL);
        task.setAssignedTo("s229878873");
        task.setDueDate(today.plusDays(6)); // NOT in {1,3,7}

        when(taskRepository.findByStatus(TaskStatus.PENDING)).thenReturn(List.of(task));

        taskService.sendTaskReminders();

        verifyNoInteractions(emailService);
        verifyNoInteractions(notificationRepository);
        // studentRepository shouldn't even be queried since the day check short-circuits first
        verifyNoInteractions(studentRepository);
    }

    @Test
    void individualTask_withNullDueDate_isSkipped() {
        Task task = new Task();
        task.setTaskID("TASK003");
        task.setStatus(TaskStatus.PENDING);
        task.setTargetType(TaskTargetType.INDIVIDUAL);
        task.setAssignedTo("s229878873");
        task.setDueDate(null);

        when(taskRepository.findByStatus(TaskStatus.PENDING)).thenReturn(List.of(task));

        taskService.sendTaskReminders();

        verifyNoInteractions(emailService);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void societyTask_dueInThreeDays_sendsReminderToAllCurrentExecutives() {
        LocalDate today = LocalDate.now(SAST);

        Task task = new Task();
        task.setTaskID("TASK004");
        task.setTaskName("Society Wide Task");
        task.setStatus(TaskStatus.PENDING);
        task.setTargetType(TaskTargetType.SOCIETY);
        task.setAssignedTo(null);
        task.setAssignedBy("sdo@nmu.ac.za");
        task.setDueDate(today.plusDays(3));

        TaskAllocation allocation = new TaskAllocation();
        Society society = new Society();
        society.setSocietyID("SOC001");
        allocation.setSociety(society);

        Student execStudent1 = new Student();
        execStudent1.setStudentNumber("s111");
        execStudent1.setEmail("exec1@mandela.ac.za");

        Student execStudent2 = new Student();
        execStudent2.setStudentNumber("s222");
        execStudent2.setEmail("exec2@mandela.ac.za");

        Executive exec1 = new Executive();
        exec1.setStudent(execStudent1);
        Executive exec2 = new Executive();
        exec2.setStudent(execStudent2);

        when(taskRepository.findByStatus(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(taskAllocationRepository.findByTask_TaskID("TASK004")).thenReturn(List.of(allocation));
        when(executiveRepository.findCurrentExecutivesBySociety(eq("SOC001"), any(LocalDate.class)))
                .thenReturn(List.of(exec1, exec2));
        when(notificationRepository.count()).thenReturn(0L, 1L); // two saves in sequence

        taskService.sendTaskReminders();

        verify(emailService).send(eq(EmailType.TASK_REMINDER), eq("exec1@mandela.ac.za"), anyMap());
        verify(emailService).send(eq(EmailType.TASK_REMINDER), eq("exec2@mandela.ac.za"), anyMap());
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    void noPendingTasks_sendsNoReminders() {
        // findByStatus(PENDING) feeds the loop, so completed/other-status
        // tasks are excluded at the repository query level - verifying
        // that contract explicitly here.
        when(taskRepository.findByStatus(TaskStatus.PENDING)).thenReturn(List.of());

        taskService.sendTaskReminders();

        verify(taskRepository).findByStatus(TaskStatus.PENDING);
        verifyNoInteractions(emailService);
        verifyNoInteractions(notificationRepository);
    }
}