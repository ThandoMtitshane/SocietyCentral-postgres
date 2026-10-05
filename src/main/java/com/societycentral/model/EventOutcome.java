package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Table(name = "EventOutcome")
@Data
public class EventOutcome {

    @EmbeddedId
    private EventOutcomeId id;

    /**
     * FK to Hoster(eventID, societyID) - shares the same two columns as
     * this entity's own primary key, so it is mapped read-only here.
     * Hoster row is looked up via the id fields (eventID, societyID).
     */
    @ManyToOne
    @JoinColumns({
            @JoinColumn(name = "eventID", referencedColumnName = "eventID", insertable = false, updatable = false),
            @JoinColumn(name = "societyID", referencedColumnName = "societyID", insertable = false, updatable = false)
    })
    private Hoster hoster;

    @Column(name = "studentNumber", length = 20, nullable = false)
    private String studentNumber;

    @Column(name = "termStartDate", nullable = false)
    private LocalDate termStartDate;

    /**
     * FK to Executive(studentNumber, societyID, termStartDate).
     * societyID comes from this entity's own id (EventOutcomeId.societyID),
     * studentNumber and termStartDate are stored directly on this entity.
     */
    @ManyToOne
    @JoinColumns({
            @JoinColumn(name = "studentNumber", referencedColumnName = "studentNumber", insertable = false, updatable = false),
            @JoinColumn(name = "societyID", referencedColumnName = "societyID", insertable = false, updatable = false),
            @JoinColumn(name = "termStartDate", referencedColumnName = "termStartDate", insertable = false, updatable = false)
    })
    private Executive executive;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "suggestion", length = 1000)
    private String suggestion;

    @Column(name = "outcome", length = 500)
    private String outcome;

}
