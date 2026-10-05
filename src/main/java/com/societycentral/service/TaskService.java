package com.societycentral.service;

import com.societycentral.model.*;
import com.societycentral.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;

@Service
public class TaskService {


    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository; // ← new

    /*
     * Repositories used to validate task assignment
     * authorisation rules.
     */
    private final StudentRepository studentRepository;
    private final ExecutiveRepository executiveRepository;
    private final SDORepository sdoRepository;
    private final SocietyRepository societyRepository;
    private final EmailService emailService;
    private final TaskAllocationRepository taskAllocationRepository; // ← new

    @Autowired
    public TaskService(TaskRepository taskRepository,
                       StudentRepository studentRepository,
                       SDORepository sdoRepository,
                       SocietyRepository societyRepository,
                       ExecutiveRepository executiveRepository,
                       EmailService emailService,
                       TaskAllocationRepository taskAllocationRepository,
                       NotificationRepository notificationRepository) { // ← new param
        this.taskRepository = taskRepository;
        this.studentRepository = studentRepository;
        this.sdoRepository = sdoRepository;
        this.societyRepository = societyRepository;
        this.executiveRepository = executiveRepository;
        this.emailService = emailService;
        this.taskAllocationRepository = taskAllocationRepository;
        this.notificationRepository = notificationRepository; // ← new
    }

    public List<Task> findAll() {
        return taskRepository.findAll();
    }

    public Optional<Task> findById(String taskID) {
        return taskRepository.findById(taskID);
    }

    public List<Task> findByAssignedTo(String studentNumber) {
        return taskRepository.findByAssignedTo(studentNumber);
    }

    public List<Task> findByStatus(TaskStatus status) {
        return taskRepository.findByStatus(status);
    }

    public List<Task> findByAssignedBy(String assignedByEmail) {
        return taskRepository.findByAssignedBy(assignedByEmail);
    }

    public Task createTask(Task task, String societyID) {
        /*
         * ===============================================================
         * Generate Task ID
         * ===============================================================
         *
         * Business Rule:
         * Every newly created task must have a unique identifier before
         * it is stored in the database.
         *
         * ID Format:
         * TASK001
         * TASK002
         * TASK003
         * ===============================================================
         */
        long count = taskRepository.count() + 1;

        task.setTaskID(
                String.format("TASK%03d", count)
        );


        /*
         * ===============================================================
         * BUSINESS RULE: Task Assignment Authorisation
         * ===============================================================
         *
         * Only authorised users may assign tasks.
         *
         * Authorised Roles
         * ----------------
         * • Executive
         * • Student Development Officer (SDO)
         *
         * Executive Rules
         * ---------------
         * • May assign tasks only to Executives belonging to the
         *   Executive's own society.
         *
         * SDO Rules
         * ---------
         * • May assign tasks only to Executives belonging to
         *   societies they supervise.
         *
         * The implementation of these rules begins below.
         * ===============================================================
         */

        // BUSINESS RULE: targetType is "never both, always exactly one" -
        // INDIVIDUAL -> assignedTo is set, no TaskAllocation row.
        // SOCIETY    -> assignedTo is null, a TaskAllocation row links this
        //               task to the society (created via
        //               TaskAllocationService, not here).
        if (task.getTargetType() == TaskTargetType.INDIVIDUAL && task.getAssignedTo() == null) {
            throw new IllegalArgumentException("INDIVIDUAL tasks must have assignedTo set");
        }
        if (task.getTargetType() == TaskTargetType.SOCIETY && task.getAssignedTo() != null) {
            throw new IllegalArgumentException("SOCIETY tasks must NOT have assignedTo set");
        }

        /*
         * ===============================================================
         * Determine whether the assigner is an Executive.
         * ===============================================================
         *
         * Business Rule:
         * The authenticated user's email (assignedBy) is first resolved
         * to a Student record.
         *
         * If the Student currently serves as an Executive, the
         * Executive-specific assignment rules will be applied.
         *
         * If no Executive record is found, the assigner may still be an
         * SDO. That validation will be implemented in the next step.
         * ===============================================================
         */
        Student assigner = studentRepository
                .findByEmail(task.getAssignedBy())
                .orElse(null);

        if (assigner != null) {

            List<Executive> executives =
                    executiveRepository.findByIdStudentNumber(
                            assigner.getStudentNumber());

            if (!executives.isEmpty()) {

                /*
                 * ===========================================================
                 * BUSINESS RULE:
                 * Executive → Executive Assignment
                 * ===========================================================
                 *
                 * An Executive may assign tasks only to another registered
                 * Executive belonging to the same society.
                 *
                 * Validation Steps:
                 * 1. Verify the assignee exists.
                 * 2. Verify the assignee is a registered Executive.
                 * 3. Verify both Executives belong to the same society.
                 * ===========================================================
                 */

                /*
                 * Step 1:
                 * Resolve the assignee using the supplied student number.
                 */
                Student assignee = studentRepository
                        .findById(task.getAssignedTo())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Assigned student does not exist."));

                /*
                 * Step 2:
                 * Verify the assignee is a registered Executive.
                 */
                List<Executive> assignedExecutives =
                        executiveRepository.findByIdStudentNumber(
                                assignee.getStudentNumber());

                if (assignedExecutives.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Tasks may only be assigned to registered Executives.");
                }

                /*
                 * Step 3:
                 * Verify both Executives belong to the same society.
                 */
                Executive assigningExecutive = executives.get(0);
                Executive assignedExecutive = assignedExecutives.get(0);

                if (!assigningExecutive.getSociety().getSocietyID()
                        .equals(assignedExecutive.getSociety().getSocietyID())) {

                    throw new IllegalArgumentException(
                            "Executive may assign tasks only within their own society.");
                }

            }
            else {

                /*
                 * ===========================================================
                 * BUSINESS RULE:
                 * Student Development Officer (SDO) Assignment
                 * ===========================================================
                 *
                 * The assigner is not an Executive.
                 *
                 * The system must now determine whether the assigner is an
                 * SDO.
                 *
                 * An SDO may assign tasks only to Executives belonging to
                 * societies they supervise.
                 * ===========================================================
                 */

                Optional<SDO> optionalSdo =
                        sdoRepository.findByEmail(task.getAssignedBy());

                if (optionalSdo.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Only Executives and SDOs may assign tasks.");
                }

                /*
                 * Resolve the assignee.
                 */
                Student assignee = studentRepository
                        .findById(task.getAssignedTo())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Assigned student does not exist."));

                /*
                 * Verify that the assignee is an Executive.
                 */
                List<Executive> assignedExecutives =
                        executiveRepository.findByIdStudentNumber(
                                assignee.getStudentNumber());

                if (assignedExecutives.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Tasks may only be assigned to registered Executives.");
                }

                Executive assignedExecutive =
                        assignedExecutives.get(0);

                /*
                 * Retrieve all societies supervised by the SDO.
                 */
                List<Society> supervisedSocieties =
                        societyRepository.findBySdoStaffNumber(
                                optionalSdo.get().getStaffNumber());

                /*
                 * Verify that the Executive belongs to one of the
                 * supervised societies.
                 */
                boolean authorised = supervisedSocieties.stream()
                        .anyMatch(society ->
                                society.getSocietyID().equals(
                                        assignedExecutive.getSociety().getSocietyID()));

                if (!authorised) {
                    throw new IllegalArgumentException(
                            "SDO may assign tasks only within supervised societies.");
                }
            }

        }

        task.setStatus(TaskStatus.PENDING);
        task.setIssueDate(LocalDate.now());
        Task savedTask = taskRepository.save(task);

        if (savedTask.getTargetType() == TaskTargetType.SOCIETY) {
            if (societyID == null || societyID.isBlank()) {
                throw new IllegalArgumentException("SOCIETY tasks require a societyID.");
            }

            Society society = societyRepository.findById(societyID)
                    .orElseThrow(() -> new IllegalArgumentException("Society not found."));

            TaskAllocation allocation = new TaskAllocation();
            TaskAllocationId allocationId = new TaskAllocationId();
            allocationId.setTaskID(savedTask.getTaskID());
            allocationId.setSocietyID(societyID);
            allocation.setId(allocationId);
            allocation.setTask(savedTask);
            allocation.setSociety(society);
            taskAllocationRepository.save(allocation);

            // Notify every current executive of the society immediately
            LocalDate today = LocalDate.now();
            List<Executive> currentExecutives =
                    executiveRepository.findCurrentExecutivesBySociety(societyID, today);

            for (Executive exec : currentExecutives) {
                Student execStudent = exec.getStudent();
                if (execStudent != null && execStudent.getEmail() != null) {
                    createTaskAssignedNotification(execStudent.getEmail(), savedTask);
                }
            }
        } else {
            // INDIVIDUAL task - notify the assignee immediately
            createTaskAssignedNotification(savedTask.getAssignedTo() != null
                    ? studentRepository.findById(savedTask.getAssignedTo())
                    .map(Student::getEmail).orElse(null)
                    : null, savedTask);
        }

        return savedTask;
    }

    /**
     * BUSINESS RULE: "Sending Task reminders to every person who has not completed their task"
     *
     * Runs every day at 17:00 South African time (adjust cron below if 18:50 was intentional).
     * For every PENDING task, checks how many whole days remain until dueDate
     * and sends a TASK_REMINDER email + creates a Notification row if that gap
     * is exactly 1, 3, or 7 days.
     *
     * Because dueDate has no time component and this job runs once a day,
     * each task only ever presents one integer "days until due" value per
     * run — so checking membership in {1, 3, 7} is sufficient; a task due
     * in 6 days and 12 hours simply doesn't exist as a distinct state here.
     *
     * - INDIVIDUAL tasks: reminder goes to the assigned student.
     * - SOCIETY tasks: reminder goes to every current Executive of that
     *   society (termEndDate is null or hasn't passed yet).
     */
    @Scheduled(cron = "30 11 19 * * *", zone = "Africa/Johannesburg")
    public void sendTaskReminders() {
        System.out.println("Started");
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        Set<Long> reminderDays = Set.of(1L, 3L, 7L);

        List<Task> pendingTasks = taskRepository.findByStatus(TaskStatus.PENDING);

        for (Task task : pendingTasks) {

            if (task.getDueDate() == null) {
                continue;
            }

            long daysUntilDue = ChronoUnit.DAYS.between(today, task.getDueDate());

            if (!reminderDays.contains(daysUntilDue)) {
                continue;
            }

            Map<String, String> vars = Map.of(
                    "taskTitle", task.getTaskName(),
                    "taskDescription", task.getTaskDescription() != null
                            ? task.getTaskDescription() : "",
                    "dueDate", task.getDueDate().toString(),
                    "issueDate", task.getIssueDate() != null
                            ? task.getIssueDate().toString() : "",
                    "issuedBy", task.getAssignedBy()
            );

            if (task.getTargetType() == TaskTargetType.SOCIETY) {

                List<TaskAllocation> allocations =
                        taskAllocationRepository.findByTask_TaskID(task.getTaskID());

                for (TaskAllocation allocation : allocations) {

                    String societyID = allocation.getSociety().getSocietyID();

                    List<Executive> currentExecutives =
                            executiveRepository.findCurrentExecutivesBySociety(societyID, today);

                    for (Executive exec : currentExecutives) {

                        Student execStudent = exec.getStudent();
                        if (execStudent == null || execStudent.getEmail() == null) {
                            continue;
                        }

                        emailService.send(EmailType.TASK_REMINDER, execStudent.getEmail(), vars);
                        createTaskReminderNotification(execStudent.getEmail(), task);
                    }
                }

                continue;
            }

            // INDIVIDUAL branch
            if (task.getTargetType() != TaskTargetType.INDIVIDUAL || task.getAssignedTo() == null) {
                continue;
            }

            Student assignee = studentRepository
                    .findById(task.getAssignedTo())
                    .orElse(null);

            if (assignee == null || assignee.getEmail() == null) {
                continue;
            }

            emailService.send(EmailType.TASK_REMINDER, assignee.getEmail(), vars);
            createTaskReminderNotification(assignee.getEmail(), task);
        }
    }

    public Task markComplete(String taskID) {
        // BUSINESS RULE: "A task remains active until it is marked as
        // complete."
        Task task = taskRepository.findById(taskID)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskID));
        task.setStatus(TaskStatus.COMPLETE);
        task.setCompletionDate(LocalDate.now());
        return taskRepository.save(task);
    }



    public Task save(Task task) {
        return taskRepository.save(task);
    }

    public void deleteById(String taskID) {
        taskRepository.deleteById(taskID);
    }
    /**
     * Creates and saves a Notification row for a task reminder.
     * ID format follows the same pattern as Task IDs: NOTIF001, NOTIF002...
     */

    private void createTaskReminderNotification(String recipientEmail, Task task) {

        Notification notification = new Notification();
        notification.setNotificationID(generateNotificationID());
        notification.setRecipientEmail(recipientEmail);
        notification.setTitle("Task Reminder: " + task.getTaskName());
        notification.setMessage("Your task \"" + task.getTaskName()
                + "\" is due on " + task.getDueDate() + ".");
        notification.setNotifType(NotificationType.TASK_REMINDER);
        notification.setRelatedID(task.getTaskID());
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(notification);
    }

    /**
     * Creates and saves a Notification row immediately when a task
     * is first assigned (separate from the day-before reminder job).
     */
    private void createTaskAssignedNotification(String recipientEmail, Task task) {
        if (recipientEmail == null) return;

        Notification notification = new Notification();
        notification.setNotificationID(generateNotificationID());
        notification.setRecipientEmail(recipientEmail);
        notification.setTitle("New Task Assigned: " + task.getTaskName());
        notification.setMessage("You have been assigned a new task: \"" + task.getTaskName()
                + "\", due on " + task.getDueDate() + ".");
        notification.setNotifType(NotificationType.TASK_ASSIGNED);
        notification.setRelatedID(task.getTaskID());
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(notification);
    }

    /**
     * B400 - Respond to Task
     *
     * Updates the status of a task assigned to the authenticated executive,
     * optionally with a comment. If marked COMPLETE, reminders stop
     * automatically since sendTaskReminders() only processes PENDING tasks.
     * Notifies the task creator of the status change.
     *
     * @param taskID task being updated
     * @param status new status (PENDING or COMPLETE)
     * @param comment optional comment from the assignee
     * @param email authenticated user's email (must be the assignee)
     * @return the updated task
     */
    public Task updateTaskStatus(String taskID, TaskStatus status, String comment, String email) {

        Task task = taskRepository.findById(taskID)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskID));

        // Verify the authenticated user is actually the assignee
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));

        boolean isAssignee = task.getTargetType() == TaskTargetType.INDIVIDUAL
                && student.getStudentNumber().equals(task.getAssignedTo());

        if (!isAssignee) {
            throw new IllegalArgumentException("You are not authorised to update this task.");
        }

        task.setStatus(status);
        if (comment != null && !comment.isBlank()) {
            task.setComment(comment);
        }
        if (status == TaskStatus.COMPLETE) {
            task.setCompletionDate(LocalDate.now());
        }

        Task savedTask = taskRepository.save(task);

        // Notify the task creator of the status update
        createTaskStatusUpdateNotification(savedTask.getAssignedBy(), savedTask, student);

        return savedTask;
    }

    /**
     * Notifies the task creator (assignedBy) that the assignee has
     * updated the task's status.
     */
    private void createTaskStatusUpdateNotification(String creatorEmail, Task task, Student updatedBy) {
        if (creatorEmail == null) return;

        Notification notification = new Notification();
        notification.setNotificationID(generateNotificationID());
        notification.setRecipientEmail(creatorEmail);
        notification.setTitle("Task Updated: " + task.getTaskName());
        notification.setMessage(updatedBy.getUser().getFirstName() + " " + updatedBy.getUser().getLastName()
                + " marked \"" + task.getTaskName() + "\" as " + task.getStatus() + ".");
        notification.setNotifType(NotificationType.TASK_ASSIGNED);
        notification.setRelatedID(task.getTaskID());
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(notification);
    }

    private String generateNotificationID() {
        return "NTF" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 17).toUpperCase(Locale.ROOT);
    }

    /**
     * Returns tasks assigned to the student matching the given email.
     */
    public List<Task> findByAssignedToEmail(String email) {
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));

        List<Task> individualTasks = taskRepository.findByAssignedTo(student.getStudentNumber());

        List<Executive> activeExecutiveRoles = executiveRepository
                .findActiveExecutiveRoles(student.getStudentNumber(), LocalDate.now());

        if (activeExecutiveRoles.isEmpty()) {
            return individualTasks;
        }

        String societyID = activeExecutiveRoles.get(0).getId().getSocietyID();

        List<Task> societyTasks = taskAllocationRepository
                .findByIdSocietyID(societyID)
                .stream()
                .map(TaskAllocation::getTask)
                .toList();

        List<Task> combined = new java.util.ArrayList<>(individualTasks);
        combined.addAll(societyTasks);

        return combined;
    }

    /**
     * B400 - View Tasks (SDO)
     *
     * Returns all tasks (individual and society-wide) across every
     * society supervised by the given SDO. Read-only overview -
     * SDO does not respond to or update these tasks.
     *
     * @param email authenticated SDO's email
     * @return all tasks for supervised societies
     */
    public List<Task> findTasksForSupervisedSocieties(String email) {

        SDO sdo = sdoRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("SDO not found."));

        List<String> societyIDs = societyRepository
                .findBySdoStaffNumber(sdo.getStaffNumber())
                .stream()
                .map(Society::getSocietyID)
                .toList();

        if (societyIDs.isEmpty()) {
            return List.of();
        }

        // Individual tasks assigned to current executives of these societies
        List<String> executiveStudentNumbers = societyIDs.stream()
                .flatMap(id -> executiveRepository
                        .findCurrentExecutivesBySociety(id, LocalDate.now())
                        .stream())
                .map(exec -> exec.getId().getStudentNumber())
                .distinct()
                .toList();

        List<Task> individualTasks = executiveStudentNumbers.isEmpty()
                ? List.of()
                : taskRepository.findByAssignedToIn(executiveStudentNumbers);

        // Society-wide tasks allocated to any of these societies
        List<Task> societyTasks = taskAllocationRepository
                .findByIdSocietyIDIn(societyIDs)
                .stream()
                .map(TaskAllocation::getTask)
                .toList();

        List<Task> combined = new java.util.ArrayList<>(individualTasks);
        combined.addAll(societyTasks);

        return combined;
    }
}
