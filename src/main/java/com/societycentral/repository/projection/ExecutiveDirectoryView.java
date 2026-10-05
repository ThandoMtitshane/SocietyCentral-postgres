package com.societycentral.repository.projection;

/**
 * One row of the executive directory search: an active executive identified by
 * name, position and society, used when starting a direct conversation.
 */
public interface ExecutiveDirectoryView {
    String getStudentNumber();
    String getEmail();
    String getFirstName();
    String getLastName();
    String getPosition();
    String getSocietyID();
    String getSocietyName();
}
