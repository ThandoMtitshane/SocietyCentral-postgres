package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "Notification")
public class Notification {

    @Id
    @Column(name = "notificationID", length = 20)
    private String notificationID;

    @Column(name = "recipientEmail", length = 100, nullable = false)
    private String recipientEmail;

    @ManyToOne
    @JoinColumn(name = "recipientEmail", referencedColumnName = "email", insertable = false, updatable = false)
    private User recipient;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "message", length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "notifType", length = 50)
    private NotificationType notifType;

    @Column(name = "relatedID", length = 20)
    private String relatedID; // e.g. taskID or eventID this notification refers to

    @Column(name = "isRead")
    private Boolean isRead = false;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;


}
