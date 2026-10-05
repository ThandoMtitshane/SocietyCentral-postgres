package com.societycentral.repository;

import com.societycentral.model.TaskAllocation;
import com.societycentral.model.TaskAllocationId;
import com.societycentral.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TaskAllocationRepository extends JpaRepository<TaskAllocation, TaskAllocationId> {
    // ID type = TaskAllocationId (taskID, societyID)

    // All society-wide allocations for a given task
    List<TaskAllocation> findByIdTaskID(String taskID);

    // All society-wide tasks allocated to a given society
    List<TaskAllocation> findByIdSocietyID(String societyID);

    List<TaskAllocation> findByTask_TaskID(String taskTaskID);
    List<TaskAllocation> findByIdSocietyIDIn(List<String> societyIDs);

    /**
     * Counts outstanding tasks using the existing SDO dashboard rule.
     */
    @Query("""
            select count(allocation)
            from TaskAllocation allocation
            join allocation.task task
            where allocation.id.societyID = :societyID
              and task.status <> :completeStatus
              and task.dueDate > :currentDate
            """)
    long countPendingTasksForSociety(
            @Param("societyID") String societyID,
            @Param("completeStatus") TaskStatus completeStatus,
            @Param("currentDate") LocalDate currentDate);
}
