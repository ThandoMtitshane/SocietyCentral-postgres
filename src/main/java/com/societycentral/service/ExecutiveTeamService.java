package com.societycentral.service;

import com.societycentral.dto.response.ExecutiveTaskPerformanceDTO;
import com.societycentral.dto.response.ExecutiveTaskPerformanceDTO.TaskItem;
import com.societycentral.dto.response.ExecutiveTeamMemberDTO;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.model.Executive;
import com.societycentral.model.Student;
import com.societycentral.model.Task;
import com.societycentral.model.TaskStatus;
import com.societycentral.model.User;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.TaskRepository;
import com.societycentral.repository.UserProfilePictureRepository;
import com.societycentral.repository.UserRepository;
import com.societycentral.service.ExecutiveSocietyResolver.ActiveExecutiveSociety;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides the "Executives" team view for an authenticated executive: the
 * current committee of their own society, enriched with contact info,
 * profile-picture flags and task-performance counts. Auth-scoped via
 * {@link ExecutiveSocietyResolver} so no society ID is accepted from the client.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveTeamService {

    private final ExecutiveSocietyResolver executiveSocietyResolver;
    private final ExecutiveRepository executiveRepository;
    private final TaskRepository taskRepository;
    private final UserProfilePictureRepository userProfilePictureRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /** Current executives of the caller's society, with task-performance counts. */
    @Transactional(readOnly = true)
    public List<ExecutiveTeamMemberDTO> getTeamForExecutive(String executiveEmail) {
        ActiveExecutiveSociety context = executiveSocietyResolver.resolve(executiveEmail);
        LocalDate today = LocalDate.now(clock);

        List<Executive> executives = executiveRepository
                .findActiveExecutivesForSocietyProfile(
                        context.society().getSocietyID(), today);

        return executives.stream().map(executive -> {
            Student student = executive.getStudent();
            User user = student == null ? null : student.getUser();
            String studentNumber = student == null ? null : student.getStudentNumber();
            String email = user == null ? null : user.getEmail();
            String firstName = user == null ? null : user.getFirstName();
            String lastName = user == null ? null : user.getLastName();

            long pending = studentNumber == null ? 0
                    : taskRepository.countByAssignedToAndStatus(studentNumber, TaskStatus.PENDING);
            long complete = studentNumber == null ? 0
                    : taskRepository.countByAssignedToAndStatus(studentNumber, TaskStatus.COMPLETE);

            var picture = email == null
                    ? java.util.Optional.<com.societycentral.model.UserProfilePicture>empty()
                    : userProfilePictureRepository.findById(email);

            return ExecutiveTeamMemberDTO.builder()
                    .studentNumber(studentNumber)
                    .firstName(firstName)
                    .lastName(lastName)
                    .fullName(fullName(firstName, lastName, studentNumber))
                    .email(email)
                    .position(executive.getPosition())
                    .hasProfilePicture(picture.isPresent())
                    .profilePictureVersion(picture
                            .map(p -> p.getUpdatedAt() == null ? null : p.getUpdatedAt().toString())
                            .orElse(null))
                    .pendingTaskCount(pending)
                    .completeTaskCount(complete)
                    .totalTaskCount(pending + complete)
                    .build();
        }).toList();
    }

    /**
     * Detailed task performance for one executive. Verifies the target is a
     * current executive of the caller's own society before returning.
     */
    @Transactional(readOnly = true)
    public ExecutiveTaskPerformanceDTO getPerformanceForExecutive(
            String executiveEmail, String targetStudentNumber) {
        ActiveExecutiveSociety context = executiveSocietyResolver.resolve(executiveEmail);
        LocalDate today = LocalDate.now(clock);
        String societyID = context.society().getSocietyID();

        // Confirm the target is a current executive of the same society.
        Executive target = executiveRepository
                .findActiveExecutivesForSocietyProfile(societyID, today).stream()
                .filter(e -> e.getStudent() != null
                        && targetStudentNumber.equals(e.getStudent().getStudentNumber()))
                .findFirst()
                .orElseThrow(() -> new ForbiddenOperationException(
                        "That executive is not part of your society."));

        Student student = target.getStudent();
        if (student == null) {
            throw new ResourceNotFoundException("Executive not found.");
        }
        User user = student.getUser();

        List<Task> tasks = taskRepository
                .findByAssignedToOrderByIssueDateDesc(targetStudentNumber);

        long pending = tasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count();
        long complete = tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETE).count();
        long overdue = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING
                        && t.getDueDate() != null && t.getDueDate().isBefore(today))
                .count();
        long total = tasks.size();
        double completionRate = total == 0 ? 0.0
                : Math.round((double) complete / total * 1000.0) / 10.0;

        Map<String, String> nameCache = new HashMap<>();
        List<TaskItem> items = tasks.stream().map(t -> {
            boolean isOverdue = t.getStatus() == TaskStatus.PENDING
                    && t.getDueDate() != null && t.getDueDate().isBefore(today);
            return TaskItem.builder()
                    .taskID(t.getTaskID())
                    .taskName(t.getTaskName())
                    .taskDescription(t.getTaskDescription())
                    .status(t.getStatus() == null ? null : t.getStatus().name())
                    .issueDate(t.getIssueDate())
                    .dueDate(t.getDueDate())
                    .completionDate(t.getCompletionDate())
                    .overdue(isOverdue)
                    .assignedByName(resolveName(t.getAssignedBy(), nameCache))
                    .build();
        }).toList();

        return ExecutiveTaskPerformanceDTO.builder()
                .studentNumber(targetStudentNumber)
                .fullName(fullName(
                        user == null ? null : user.getFirstName(),
                        user == null ? null : user.getLastName(),
                        targetStudentNumber))
                .position(target.getPosition())
                .pendingTaskCount(pending)
                .completeTaskCount(complete)
                .overdueTaskCount(overdue)
                .totalTaskCount(total)
                .completionRate(completionRate)
                .tasks(items)
                .build();
    }

    private String fullName(String firstName, String lastName, String fallback) {
        String full = ((firstName == null ? "" : firstName) + " "
                + (lastName == null ? "" : lastName)).trim();
        return full.isEmpty() ? (fallback == null ? "Unknown" : fallback) : full;
    }

    private String resolveName(String email, Map<String, String> cache) {
        if (email == null || email.isBlank()) return "System";
        return cache.computeIfAbsent(email, e -> userRepository.findById(e)
                .map(u -> {
                    String full = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                            + (u.getLastName() == null ? "" : u.getLastName())).trim();
                    return full.isEmpty() ? e : full;
                })
                .orElse(e));
    }
}
