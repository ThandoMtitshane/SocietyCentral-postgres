package com.societycentral.repository;

import com.societycentral.model.Executive;
import com.societycentral.model.Task;
import com.societycentral.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {
    // ID type = String (taskID)

    // Individual tasks assigned to a specific student
    List<Task> findByAssignedTo(String studentNumber);

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByAssignedBy(String assignedByEmail);
    List<Task> findByAssignedToIn(List<String> studentNumbers);

    // Performance counts per executive (used by the Executives page)
    long countByAssignedToAndStatus(String studentNumber, TaskStatus status);

    // A student's individual tasks, newest first (for the performance detail view)
    List<Task> findByAssignedToOrderByIssueDateDesc(String studentNumber);


    @Modifying(flushAutomatically = true)
    @Query("update Task t set t.assignedBy = :newEmail where t.assignedBy = :oldEmail")
    int reassignAssignedByEmail(@Param("oldEmail") String oldEmail,
                                @Param("newEmail") String newEmail);
}
