package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
@Entity
@Table(name = "UserProfilePicture")
public class UserProfilePicture {
    @Id
    @Column(name = "userEmail", length = 100, nullable = false)
    private String userEmail;

    @Column(name = "imageData", columnDefinition = "bytea", nullable = false)
    private byte[] imageData;

    @Column(name = "contentType", length = 100, nullable = false)
    private String contentType;

    @Column(name = "originalFileName", length = 255)
    private String originalFileName;

    @Column(name = "fileSize", nullable = false)
    private long fileSize;

    @Column(name = "updatedAt", nullable = false)
    private LocalDateTime updatedAt;
}
