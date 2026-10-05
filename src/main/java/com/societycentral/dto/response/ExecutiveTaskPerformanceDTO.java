package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Detailed task performance for one executive, used by the "View Performance"
 * expansion on the Executives page.
 */
@Data
@Builder
public class ExecutiveTaskPerformanceDTO {

    private String studentNumber;
    private String fullName;
    private String position;

    private long pendingTaskCount;
    private long completeTaskCount;
    private long overdueTaskCount;   // pending and past due date
    private long totalTaskCount;
    private double completionRate;   // % complete of total (0 when no tasks)

    private List<TaskItem> tasks;    // this executive's individual tasks, newest first

    @Data
    @Builder
    public static class TaskItem {
        private String taskID;
        private String taskName;
        private String taskDescription;
        private String status;         // PENDING or COMPLETE
        private LocalDate issueDate;
        private LocalDate dueDate;
        private LocalDate completionDate;
        private boolean overdue;
        private String assignedByName;  // resolved from assignedBy email
    }
}
