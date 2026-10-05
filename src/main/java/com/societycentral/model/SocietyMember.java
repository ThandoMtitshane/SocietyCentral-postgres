package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@Entity
@Table(name = "SocietyMember")
public class SocietyMember {

    @EmbeddedId
    private SocietyMemberId id;

    @ManyToOne
    @MapsId("studentNumber")
    @JoinColumn(name = "studentNumber")
    private Student student;

    @ManyToOne
    @MapsId("societyID")
    @JoinColumn(name = "societyID")
    private Society society;

    @Column(name = "joinDate")
    private LocalDate joinDate;

    @Column(name ="expireDate")
    private LocalDate expireDate;
}
