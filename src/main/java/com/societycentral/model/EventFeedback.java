package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "EventFeedback")
@Data
public class EventFeedback {

    @Id
    @Column(name = "feedbackID", length = 20)
    private String feedbackID;

    @Column(name = "studentNumber", length = 20, nullable = false)
    private String studentNumber;

    @ManyToOne
    @JoinColumn(name = "studentNumber", referencedColumnName = "studentNumber", insertable = false, updatable = false)
    private Student student;

    @Column(name = "eventID", length = 20, nullable = false)
    private String eventID;

    @ManyToOne
    @JoinColumn(name = "eventID", referencedColumnName = "eventID", insertable = false, updatable = false)
    private Event event;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "organizationRating")
    private Integer organizationRating;

    @Column(name = "venueRating")
    private Integer venueRating;

    @Column(name = "contentRating")
    private Integer contentRating;

    @Column(name = "wouldRecommend")
    private Boolean wouldRecommend;

    @Column(name = "highlights", length = 500)
    private String highlights;

    @Column(name = "improvements", length = 500)
    private String improvements;

    @Column(name = "submittedAt")
    private LocalDateTime submittedAt;
}
