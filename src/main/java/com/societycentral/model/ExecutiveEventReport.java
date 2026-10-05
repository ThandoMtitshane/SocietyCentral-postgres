package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ExecutiveEventReport")
@Getter
@Setter
@NoArgsConstructor
public class ExecutiveEventReport {

    @Id
    @Column(name = "reportID", length = 36)
    private String reportID;

    @Column(name = "eventID", length = 20, nullable = false)
    private String eventID;

    @Column(name = "societyID", length = 20, nullable = false)
    private String societyID;

    @Column(name = "studentNumber", length = 20, nullable = false)
    private String studentNumber;

    @Column(name = "expectations", length = 1000)
    private String expectations;

    @Column(name = "expectationsMet", length = 20)
    private String expectationsMet;

    @Column(name = "successAssessment", length = 20)
    private String successAssessment;

    @Column(name = "successReason", length = 1000)
    private String successReason;

    @Column(name = "improvements", length = 1000)
    private String improvements;

    @Column(name = "advice", length = 1000)
    private String advice;

    @Column(name = "attendeeCount")
    private Integer attendeeCount;

    @Column(name = "overallRating")
    private Integer overallRating;

    @Column(name = "additionalNotes", length = 1000)
    private String additionalNotes;

    @Column(name = "submittedAt", nullable = false)
    private LocalDateTime submittedAt;

    public static String newID() {
        return UUID.randomUUID().toString();
    }
}
