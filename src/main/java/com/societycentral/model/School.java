package com.societycentral.model;

import lombok.Getter;

/**
 * Schools, departments, and clusters across NMU's 7 faculties, unified
 * into a single enum. Each constant carries its parent {@link Faculty}
 * via {@link #getFaculty()}.
 * <p>
 * Education and Law are organised into "departments" rather than
 * "schools", and Science into "clusters" - these are represented here
 * using the same School type for consistency, with naming suffixes
 * dropped (e.g. "Mathematical & Computational Science Cluster" becomes
 * MATHEMATICAL_AND_COMPUTATIONAL_SCIENCE).
 * <p>
 * NONE represents a society/student not scoped to any specific school
 * (its faculty is therefore also NONE - see {@link #getFaculty()}).
 */
@Getter
public enum School {

    // ---- Faculty of Business and Economic Sciences ----
    BUSINESS_SCHOOL(Faculty.BUSINESS_AND_ECONOMIC_SCIENCES),
    SCHOOL_FOR_INDUSTRIAL_PSYCHOLOGY_AND_HUMAN_RESOURCES(Faculty.BUSINESS_AND_ECONOMIC_SCIENCES),
    SCHOOL_OF_ACCOUNTING(Faculty.BUSINESS_AND_ECONOMIC_SCIENCES),
    SCHOOL_OF_ECONOMICS_DEVELOPMENT_AND_TOURISM(Faculty.BUSINESS_AND_ECONOMIC_SCIENCES),
    SCHOOL_OF_MANAGEMENT_SCIENCES(Faculty.BUSINESS_AND_ECONOMIC_SCIENCES),

    // ---- Faculty of Education (departments) ----
    PRIMARY_SCHOOL_EDUCATION_FOUNDATION_PHASE(Faculty.EDUCATION),
    PRIMARY_SCHOOL_EDUCATION_INTERMEDIATE_PHASE(Faculty.EDUCATION),
    SECONDARY_SCHOOL_EDUCATION(Faculty.EDUCATION),
    POST_SCHOOLING(Faculty.EDUCATION),
    POST_GRADUATE_EDUCATION(Faculty.EDUCATION),

    // ---- Faculty of Engineering, the Built Environment and Technology ----
    SCHOOL_OF_ARCHITECTURE(Faculty.ENGINEERING_BUILT_ENVIRONMENT_AND_TECHNOLOGY),
    SCHOOL_OF_ENGINEERING(Faculty.ENGINEERING_BUILT_ENVIRONMENT_AND_TECHNOLOGY),
    SCHOOL_OF_INFORMATION_TECHNOLOGY(Faculty.ENGINEERING_BUILT_ENVIRONMENT_AND_TECHNOLOGY),
    SCHOOL_OF_THE_BUILT_ENVIRONMENT_AND_CIVIL_ENGINEERING(Faculty.ENGINEERING_BUILT_ENVIRONMENT_AND_TECHNOLOGY),

    // ---- Faculty of Health Sciences ----
    SCHOOL_OF_BEHAVIOURAL_AND_LIFESTYLE_SCIENCES(Faculty.HEALTH_SCIENCES),
    SCHOOL_OF_CLINICAL_CARE_AND_MEDICAL_SCIENCES(Faculty.HEALTH_SCIENCES),
    SCHOOL_OF_MEDICINE(Faculty.HEALTH_SCIENCES),

    // ---- Faculty of Humanities ----
    SCHOOL_OF_GOVERNMENTAL_AND_SOCIAL_SCIENCES(Faculty.HUMANITIES),
    SCHOOL_OF_LANGUAGE_MEDIA_AND_COMMUNICATION(Faculty.HUMANITIES),
    SCHOOL_OF_VISUAL_AND_PERFORMING_ARTS(Faculty.HUMANITIES),

    // ---- Faculty of Law (departments) ----
    PUBLIC_LAW(Faculty.LAW),
    MERCANTILE_LAW(Faculty.LAW),
    PRIVATE_LAW(Faculty.LAW),
    CRIMINAL_AND_PROCEDURAL_LAW(Faculty.LAW),

    // ---- Faculty of Science (clusters) ----
    LIFE_EARTH_ENVIRONMENTAL_AND_AGRICULTURAL_SCIENCE(Faculty.SCIENCE),
    MATHEMATICAL_AND_COMPUTATIONAL_SCIENCE(Faculty.SCIENCE),
    PHYSICAL_SCIENCES(Faculty.SCIENCE),
    NATURAL_RESOURCE_SCIENCE_AND_MANAGEMENT(Faculty.SCIENCE),
    BIOSCIENCES_AND_BIOTECHNOLOGY(Faculty.SCIENCE),
    X_STREAM(Faculty.SCIENCE),

    // ---- Not scoped to a specific school ----
    NONE(Faculty.NONE);

    /**
     * -- GETTER --
     *
     * @return the parent Faculty this school belongs to (NONE if this
     *         School is NONE).
     */
    private final Faculty faculty;

    School(Faculty faculty) {
        this.faculty = faculty;
    }

}
