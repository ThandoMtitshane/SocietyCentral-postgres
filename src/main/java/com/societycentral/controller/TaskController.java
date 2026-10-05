package com.societycentral.controller;

import com.societycentral.dto.request.TaskRequestDTO;
import com.societycentral.dto.request.TaskUpdateRequestDTO;
import com.societycentral.dto.response.TaskResponseDTO;
import com.societycentral.model.Task;
import com.societycentral.service.TaskService;
import com.societycentral.dto.response.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    @Autowired
    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponseDTO>> assignTask(
            @RequestBody TaskRequestDTO request,
            Authentication authentication) {

        /*
         * Build the Task entity from the incoming request.
         * System-generated fields (ID, issue date, status)
         * are populated by the service layer.
         */
        Task task = new Task();

        task.setTaskName(request.getTaskName());
        task.setTaskDescription(request.getTaskDescription());
        task.setDueDate(request.getDueDate());
        task.setTargetType(request.getTargetType());
        task.setAssignedTo(request.getAssignedTo());
        task.setComment(request.getComment());

        /*
         * The authenticated Executive or SDO becomes the assigner.
         */
        task.setAssignedBy(authentication.getName());

        /*
         * Delegate business logic to the service layer.
         */
        Task savedTask = taskService.createTask(task, request.getSocietyID());

        /*
         * Convert the saved entity into a response DTO.
         */
        TaskResponseDTO response = TaskResponseDTO.builder()
                .taskID(savedTask.getTaskID())
                .taskName(savedTask.getTaskName())
                .taskDescription(savedTask.getTaskDescription())
                .issueDate(savedTask.getIssueDate())
                .dueDate(savedTask.getDueDate())
                .completionDate(savedTask.getCompletionDate())
                .status(savedTask.getStatus())
                .assignedBy(savedTask.getAssignedBy())
                .targetType(savedTask.getTargetType())
                .assignedTo(savedTask.getAssignedTo())
                .comment(savedTask.getComment())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Task assigned successfully.",
                        response));
    }


    /**
     * B400 - Respond to Task
     *
     * Returns all tasks assigned to the authenticated executive.
     */
    @GetMapping("/my-tasks")
    public ResponseEntity<ApiResponse<List<TaskResponseDTO>>> getMyTasks(
            Authentication authentication) {

        // Resolve the authenticated user's studentNumber via their tasks
        List<Task> tasks = taskService.findByAssignedToEmail(authentication.getName());

        List<TaskResponseDTO> response = tasks.stream()
                .map(task -> TaskResponseDTO.builder()
                        .taskID(task.getTaskID())
                        .taskName(task.getTaskName())
                        .taskDescription(task.getTaskDescription())
                        .issueDate(task.getIssueDate())
                        .dueDate(task.getDueDate())
                        .completionDate(task.getCompletionDate())
                        .status(task.getStatus())
                        .assignedBy(task.getAssignedBy())
                        .targetType(task.getTargetType())
                        .assignedTo(task.getAssignedTo())
                        .comment(task.getComment())
                        .build())
                .toList();

        return ResponseEntity.ok(
                ApiResponse.success("Tasks retrieved successfully.", response));
    }

    /**
     * B400 - Respond to Task
     *
     * Updates the status of a task assigned to the authenticated executive.
     */
    @PatchMapping("/{taskID}/status")
    public ResponseEntity<ApiResponse<TaskResponseDTO>> updateTaskStatus(
            @PathVariable String taskID,
            @RequestBody TaskUpdateRequestDTO request,
            Authentication authentication) {

        Task updatedTask = taskService.updateTaskStatus(
                taskID, request.getStatus(), request.getComment(), authentication.getName());

        TaskResponseDTO response = TaskResponseDTO.builder()
                .taskID(updatedTask.getTaskID())
                .taskName(updatedTask.getTaskName())
                .taskDescription(updatedTask.getTaskDescription())
                .issueDate(updatedTask.getIssueDate())
                .dueDate(updatedTask.getDueDate())
                .completionDate(updatedTask.getCompletionDate())
                .status(updatedTask.getStatus())
                .assignedBy(updatedTask.getAssignedBy())
                .targetType(updatedTask.getTargetType())
                .assignedTo(updatedTask.getAssignedTo())
                .comment(updatedTask.getComment())
                .build();

        return ResponseEntity.ok(
                ApiResponse.success("Task status updated successfully.", response));
    }

    /**
     * B400 - View Tasks (SDO)
     *
     * Returns all tasks across societies the authenticated SDO supervises.
     * Read-only - includes assignee info but no status-update action.
     */
    @GetMapping("/supervised")
    public ResponseEntity<ApiResponse<List<TaskResponseDTO>>> getSupervisedTasks(
            Authentication authentication) {

        List<Task> tasks = taskService.findTasksForSupervisedSocieties(authentication.getName());

        List<TaskResponseDTO> response = tasks.stream()
                .map(task -> TaskResponseDTO.builder()
                        .taskID(task.getTaskID())
                        .taskName(task.getTaskName())
                        .taskDescription(task.getTaskDescription())
                        .issueDate(task.getIssueDate())
                        .dueDate(task.getDueDate())
                        .completionDate(task.getCompletionDate())
                        .status(task.getStatus())
                        .assignedBy(task.getAssignedBy())
                        .targetType(task.getTargetType())
                        .assignedTo(task.getAssignedTo())
                        .comment(task.getComment())
                        .build())
                .toList();

        return ResponseEntity.ok(
                ApiResponse.success("Supervised tasks retrieved successfully.", response));
    }
}
