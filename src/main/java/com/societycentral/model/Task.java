package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@Entity
@Table(name = "Task")
public class Task {

    @Id
    @Column(name = "taskID", length = 20)
    private String taskID;

    @Column(name = "taskName", length = 100, nullable = false)
    private String taskName;

    @Column(name = "taskDescription", length = 500)
    private String taskDescription;

    @Column(name = "issueDate")
    private LocalDate issueDate;

    @Column(name = "dueDate")
    private LocalDate dueDate;

    @Column(name = "completionDate")
    private LocalDate completionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private TaskStatus status; // 'PENDING','COMPLETE'


    @Column(name = "assignedBy", length = 100, nullable = false)
    private String assignedBy;

    @ManyToOne
    @JoinColumn(name = "assignedBy", referencedColumnName = "email", insertable = false, updatable = false)
    private User assignedByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "targetType", length = 20, nullable = false)
    private TaskTargetType targetType; // 'INDIVIDUAL' or 'SOCIETY'

    @Column(name = "assignedTo", length = 20)
    private String assignedTo; // studentNumber, null if society-wide

    @ManyToOne
    @JoinColumn(name = "assignedTo", referencedColumnName = "studentNumber", insertable = false, updatable = false)
    private Student assignedToStudent;

    @Column(name = "comment", length = 500)
    private String comment;

}
