package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "Executive")
public class Executive {

    @EmbeddedId
    private ExecutiveId id;

    @ManyToOne
    @MapsId("studentNumber")
    @JoinColumn(name = "studentNumber")
    private Student student;

    @ManyToOne
    @MapsId("societyID")
    @JoinColumn(name = "societyID")
    private Society society;

    @Column(name = "termEndDate")
    private LocalDate termEndDate;

    @Column(name = "position", length = 50)
    private String position;

    public Executive() {
    }

}
