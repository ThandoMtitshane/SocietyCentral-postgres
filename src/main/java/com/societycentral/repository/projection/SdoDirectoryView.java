package com.societycentral.repository.projection;

/**
 * One row of the SDO directory search: an SDO identified by staff number and
 * name (name lives on the linked User).
 */
public interface SdoDirectoryView {
    String getStaffNumber();
    String getEmail();
    String getFirstName();
    String getLastName();
}
