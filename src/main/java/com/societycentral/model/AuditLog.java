package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity representing an audit record within SocietyCentral.
 *
 * Purpose:
 * Records important changes made to entities in the system.
 *
 * Every audit record represents a single field that has changed.
 *
 * Examples:
 * ---------------------------------------------------------
 * Entity: User
 * Field: Email
 * Old Value: john.smith@nmu.ac.za
 * New Value: john.williams@nmu.ac.za
 *
 * Entity: SDO
 * Field: Office Number
 * Old Value: B212
 * New Value: B310
 * ---------------------------------------------------------
 *
 * The AuditLog provides:
 *
 * • Accountability
 * • Traceability
 * • Historical record keeping
 *
 * The audit trail allows the University to determine:
 *
 * • What changed
 * • Who changed it
 * • When it changed
 * • Which record was affected
 */
@Getter
@Setter
@Entity
@Table(name = "AuditLog")
public class AuditLog {

    /**
     * Unique identifier for each audit record.
     *
     * SQL Server automatically generates this value.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auditID")
    private Integer auditID;

    /**
     * Permanent identifier of the employee whose
     * information was modified.
     *
     * This references the SDO table.
     */
    @Column(name = "staffNumber", nullable = false, length = 20)
    private String staffNumber;

    /**
     * Name of the entity that was modified.
     *
     * Examples:
     * User
     * SDO
     * Society
     * Event
     */
    @Column(name = "entityName", nullable = false, length = 50)
    private String entityName;

    /**
     * Operation performed.
     *
     * Examples:
     * CREATE
     * UPDATE
     * DELETE
     */
    @Column(name = "operation", nullable = false, length = 20)
    private String operation;

    /**
     * Name of the field that changed.
     *
     * Examples:
     * Email
     * Campus
     * Office Number
     */
    @Column(name = "fieldChanged", nullable = false, length = 50)
    private String fieldChanged;

    /**
     * Previous value before the update.
     */
    @Column(name = "oldValue", length = 255)
    private String oldValue;

    /**
     * New value after the update.
     */
    @Column(name = "newValue", length = 255)
    private String newValue;

    /**
     * Email address of the Administrator
     * who performed the update.
     */
    @Column(name = "changedBy", nullable = false, length = 100)
    private String changedBy;

    /**
     * Date and time when the update occurred.
     */
    @Column(name = "changedDate", nullable = false)
    private LocalDateTime changedDate;

    /**
     * Optional explanation describing why
     * the information was changed.
     */
    @Column(name = "reason", length = 255)
    private String reason;

}