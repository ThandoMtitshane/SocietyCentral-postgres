package com.societycentral.model;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@Embeddable
public class TaskAllocationId implements Serializable {

    private String taskID;
    private String societyID;

    public TaskAllocationId() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskAllocationId)) return false;
        TaskAllocationId that = (TaskAllocationId) o;
        return Objects.equals(taskID, that.taskID)
                && Objects.equals(societyID, that.societyID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskID, societyID);
    }
}
