package com.societycentral.dto.response;

public final class AcademicReferenceResponses {
    private AcademicReferenceResponses() {}
    public record Faculty(String facultyCode, String facultyName) {}
    public record Campus(String campusCode, String campusName, String city, boolean hasOnCampusResidence) {}
    public record School(String schoolCode, String schoolName) {}
    public record Programme(String programmeCode, String programmeName, String qualificationLevel,
                            Faculty faculty, School school, java.util.List<Campus> campuses) {}
    public record AccommodationType(Integer id, String code, String name) {}
    public record Residence(Integer id, String residenceName, String formerName, String campusCode) {}
    public record Property(Integer id, String propertyName, String campusCode) {}
}
