package com.societycentral.service;

import com.societycentral.model.TaskAllocation;
import com.societycentral.model.TaskAllocationId;
import com.societycentral.repository.TaskAllocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TaskAllocationService {

    private final TaskAllocationRepository taskAllocationRepository;

    @Autowired
    public TaskAllocationService(TaskAllocationRepository taskAllocationRepository) {
        this.taskAllocationRepository = taskAllocationRepository;
    }

    public List<TaskAllocation> findAll() {
        return taskAllocationRepository.findAll();
    }

    public Optional<TaskAllocation> findById(TaskAllocationId id) {
        return taskAllocationRepository.findById(id);
    }

    public List<TaskAllocation> findSocietiesForTask(String taskID) {
        return taskAllocationRepository.findByIdTaskID(taskID);
    }

    public List<TaskAllocation> findTasksForSociety(String societyID) {
        return taskAllocationRepository.findByIdSocietyID(societyID);
    }

    public TaskAllocation allocate(TaskAllocation allocation) {
        // TODO: this should only be called when the corresponding Task has
        // targetType == SOCIETY and assignedTo == null (see TaskService
        // createTask). Consider validating that here, or restrict creation
        // to a single combined method in TaskService that creates both the
        // Task and the TaskAllocation row together.
        return taskAllocationRepository.save(allocation);
    }

    public void deleteById(TaskAllocationId id) {
        taskAllocationRepository.deleteById(id);
    }
}
