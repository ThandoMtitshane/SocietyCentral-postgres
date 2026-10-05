package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "Student")
public class Student {

    @Id
    @Column(name = "studentNumber", length = 20)
    private String studentNumber;

    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    @ManyToOne
    @JoinColumn(name = "email", referencedColumnName = "email", insertable = false, updatable = false)
    private User user;

    @Column(name = "course", length = 100)
    private String course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "programmeCode")
    private ProgrammeReference programme;

    @Column(name = "level", length = 10)
    private String level;

    @Column(name = "nationality", length = 50)
    private String nationality;

    @Column(name = "studyCity", length = 120)
    private String studyCity;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 30)
    private Gender gender;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "accommodationTypeID")
    private AccommodationType accommodationType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "residenceID")
    private ResidenceReference residenceReference;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "offCampusPropertyID")
    private OffCampusProperty offCampusProperty;
    @Column(name = "otherAccommodationName", length = 220)
    private String otherAccommodationName;

    @Column(name = "residence", length = 100)
    private String residence;

    @Enumerated(EnumType.STRING)
    @Column(name = "school", length = 60)
    private School school;

    @Column(name = "cellPhoneNumber", length = 10)
    private String cellPhoneNumber;

    /**
     * Derived from {@link #school} - Student no longer stores faculty
     * separately (see schema design discussion: faculty is always
     * implied by school for students, unlike societies which can be
     * scoped at either level independently).
     */
    public Faculty getFaculty() {
        return school != null ? school.getFaculty() : Faculty.NONE;
    }

}
