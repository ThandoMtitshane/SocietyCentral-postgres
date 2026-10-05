package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "SDO")
public class SDO {

    @Id
    @Column(name = "staffNumber", length = 20)
    private String staffNumber;

    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    @ManyToOne
    @JoinColumn(name = "email", referencedColumnName = "email", insertable = false, updatable = false)
    private User user;

    @Column(name = "officeNumber", length = 6)
    private String officeNumber;

    @Column(name = "phoneExtension", length = 10)
    private String phoneExtension;

}

