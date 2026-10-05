package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "TaskAllocation")
public class TaskAllocation {

    @EmbeddedId
    private TaskAllocationId id;

    @ManyToOne
    @MapsId("taskID")
    @JoinColumn(name = "taskID")
    private Task task;

    @ManyToOne
    @MapsId("societyID")
    @JoinColumn(name = "societyID")
    private Society society;

}
