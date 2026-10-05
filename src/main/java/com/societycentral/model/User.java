package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "[User]")
public class User {

    @Id
    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "title", length = 10)
    private String title;

    @Column(name = "firstName", length = 50, nullable = false)
    private String firstName;

    @Column(name = "lastName", length = 50, nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "userType", length = 20, nullable = false)
    private UserType userType; // 'STUDENT', 'SDO'

    @Enumerated(EnumType.STRING)
    @Column(name = "campus", length = 30)
    private Campus campus;

    @Column(name = "passwordHash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "profilePictureURL", length = 255)
    private String profilePictureURL;

    @Column(name = "emailVerified", nullable = false)
    private boolean emailVerified = true;

    @Column(name = "passwordTemporary", nullable = false)
    private boolean passwordTemporary = false;

}
