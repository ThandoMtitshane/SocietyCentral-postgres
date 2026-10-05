package com.societycentral.repository.projection;

import com.societycentral.model.Campus;
import com.societycentral.model.MembershipApplicationStatus;

import java.time.LocalDateTime;

/**
 * One-query projection for the executive membership-application queue.
 */
public interface ExecutiveMembershipApplicationListProjection {

    /** @return application identifier */
    String getApplicationID();

    /** @return public tracking reference */
    String getTrackingReference();

    /** @return current workflow status */
    MembershipApplicationStatus getStatus();

    /** @return original submission timestamp */
    LocalDateTime getApplicationDate();

    /** @return latest workflow update timestamp */
    LocalDateTime getLastUpdatedAt();

    /** @return applicant student number */
    String getStudentNumber();

    /** @return applicant first name */
    String getStudentFirstName();

    /** @return applicant last name */
    String getStudentLastName();

    /** @return applicant course */
    String getCourse();

    /** @return applicant academic level */
    String getLevel();

    /** @return applicant campus */
    Campus getCampus();

    /** @return applicant motivation */
    String getMotivation();

    /** @return owning society identifier */
    String getSocietyID();

    /** @return owning society name */
    String getSocietyName();
}
