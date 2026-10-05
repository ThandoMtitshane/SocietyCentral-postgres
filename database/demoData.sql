-- ============================================================
-- SocietyCentral - Development Seed Data (single consolidated script)
-- ============================================================
-- Run this AFTER SocietyCentral_Schema.sql
--
-- This one file seeds everything needed for a working dev environment:
--   PART A - Academic reference data (campuses, faculties, schools,
--            programmes, programme-campus mappings, residences, accommodation
--            types, off-campus properties). Drives the Registration page.
--   PART B - Dev accounts + sample societies (SDOs, students, executives,
--            societies, memberships).
--
-- Ordering matters: PART A runs first so the reference tables exist before
-- any account data. PART A is idempotent (MERGE / guards) and safe to re-run;
-- PART B uses plain INSERTs, so run it against a fresh/empty account set.
--
-- (Bulk 50-student load is kept separate in Set50Student.sql.)
--
-- Default password for ALL dev accounts: DevPassword123
-- BCrypt hash of "DevPassword123" (cost 10):
-- $2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.
--
-- IMPORTANT: Dev-only data. Never use in production.
-- ============================================================

USE SocietyCentral;
GO

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET XACT_ABORT ON;
GO

-- ============================================================
-- ============================================================
-- PART A - ACADEMIC REFERENCE DATA (Nelson Mandela University)
-- ============================================================
-- Based on NMU's publicly published structure (mandela.ac.za:
-- "Faculties and Schools", faculty prospectuses, School of IT and George
-- Campus / Natural Resource Management pages). NMU has 7 faculties across
-- 7 campuses (6 in Gqeberha, 1 in George). This is a representative
-- catalogue, not the full 500+ programme list; extend as needed.
-- Idempotent (MERGE + guards), safe to re-run.
-- ============================================================

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @year SMALLINT = 2025;

    -- --------------------------------------------------------
    -- Accommodation types. IDs fixed 1..5 (backend maps ID -> code:
    -- 1=ON_CAMPUS, 2=ACCREDITED_OFF_CAMPUS, 3=PRIVATE_OFF_CAMPUS,
    -- 4=HOME, 5=OTHER).
    -- --------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM dbo.AccommodationType)
    BEGIN
        SET IDENTITY_INSERT dbo.AccommodationType ON;
        INSERT INTO dbo.AccommodationType (accommodationTypeID, active) VALUES
            (1, 1), (2, 1), (3, 1), (4, 1), (5, 1);
        SET IDENTITY_INSERT dbo.AccommodationType OFF;
    END;

    -- --------------------------------------------------------
    -- Campuses. Six in Gqeberha, one in George. hasOnCampusResidence marks
    -- campuses with university residences (South, North, Missionvale, George).
    -- --------------------------------------------------------
    ;MERGE dbo.CampusReference AS t
    USING (VALUES
        ('SOUTH_CAMPUS',         'South Campus',          1, 'Gqeberha', 1),
        ('NORTH_CAMPUS',         'North Campus',          1, 'Gqeberha', 1),
        ('SECOND_AVENUE_CAMPUS', 'Second Avenue Campus',  1, 'Gqeberha', 0),
        ('BIRD_STREET_CAMPUS',   'Bird Street Campus',    1, 'Gqeberha', 0),
        ('MISSIONVALE_CAMPUS',   'Missionvale Campus',    1, 'Gqeberha', 1),
        ('OCEAN_SCIENCES_CAMPUS','Ocean Sciences Campus', 1, 'Gqeberha', 0),
        ('GEORGE_CAMPUS',        'George Campus',         1, 'George',   1)
    ) AS s (campusCode, campusName, active, city, hasOnCampusResidence)
       ON t.campusCode = s.campusCode
    WHEN NOT MATCHED BY TARGET THEN
        INSERT (campusCode, campusName, active, city, hasOnCampusResidence)
        VALUES (s.campusCode, s.campusName, s.active, s.city, s.hasOnCampusResidence);

    -- --------------------------------------------------------
    -- Faculties (the 7 NMU faculties).
    -- --------------------------------------------------------
    ;MERGE dbo.Faculty AS t
    USING (VALUES
        ('FAC_BES',  'Business and Economic Sciences',                    @year, 1),
        ('FAC_EDU',  'Education',                                         @year, 1),
        ('FAC_EBET', 'Engineering, the Built Environment and Technology', @year, 1),
        ('FAC_HEA',  'Health Sciences',                                   @year, 1),
        ('FAC_HUM',  'Humanities',                                        @year, 1),
        ('FAC_LAW',  'Law',                                               @year, 1),
        ('FAC_SCI',  'Science',                                           @year, 1)
    ) AS s (facultyCode, facultyName, sourceYear, active)
       ON t.facultyCode = s.facultyCode
    WHEN NOT MATCHED BY TARGET THEN
        INSERT (facultyCode, facultyName, sourceYear, active)
        VALUES (s.facultyCode, s.facultyName, s.sourceYear, s.active);

    -- --------------------------------------------------------
    -- Schools (per faculty, based on NMU's published school structure).
    -- --------------------------------------------------------
    ;MERGE dbo.SchoolReference AS t
    USING (VALUES
        -- Business and Economic Sciences
        ('SCH_BUSSCH', 'Business School',                               'FAC_BES',  @year, 1),
        ('SCH_ACC',    'School of Accounting',                          'FAC_BES',  @year, 1),
        ('SCH_ECO',    'School of Economics, Development and Tourism',  'FAC_BES',  @year, 1),
        ('SCH_MGT',    'School of Management Sciences',                 'FAC_BES',  @year, 1),
        ('SCH_IPHR',   'School of Industrial Psychology and Human Resources', 'FAC_BES', @year, 1),
        -- Education
        ('SCH_EDU',    'School of Education',                           'FAC_EDU',  @year, 1),
        -- Engineering, the Built Environment and Technology
        ('SCH_ENG',    'School of Engineering',                         'FAC_EBET', @year, 1),
        ('SCH_IT',     'School of Information Technology',              'FAC_EBET', @year, 1),
        ('SCH_BEA',    'School of the Built Environment and Architecture', 'FAC_EBET', @year, 1),
        -- Health Sciences
        ('SCH_BLS',    'School of Behavioural and Lifestyle Sciences',  'FAC_HEA',  @year, 1),
        ('SCH_CCMS',   'School of Clinical Care and Medical Sciences',  'FAC_HEA',  @year, 1),
        ('SCH_MED',    'School of Medicine',                            'FAC_HEA',  @year, 1),
        -- Humanities
        ('SCH_VPA',    'School of Visual and Performing Arts',          'FAC_HUM',  @year, 1),
        ('SCH_GSS',    'School of Governmental and Social Sciences',    'FAC_HUM',  @year, 1),
        ('SCH_LMC',    'School of Language, Media and Culture',         'FAC_HUM',  @year, 1),
        -- Law
        ('SCH_LAW',    'School of Law',                                 'FAC_LAW',  @year, 1),
        -- Science
        ('SCH_BIO',    'School of Biological, Earth and Environmental Sciences', 'FAC_SCI', @year, 1),
        ('SCH_MCS',    'School of Mathematical and Computational Sciences',      'FAC_SCI', @year, 1),
        ('SCH_PHY',    'School of Physical Sciences',                   'FAC_SCI',  @year, 1),
        ('SCH_NRM',    'School of Natural Resource Management',         'FAC_SCI',  @year, 1)
    ) AS s (schoolCode, schoolName, facultyCode, sourceYear, active)
       ON t.schoolCode = s.schoolCode
    WHEN NOT MATCHED BY TARGET THEN
        INSERT (schoolCode, schoolName, facultyCode, sourceYear, active)
        VALUES (s.schoolCode, s.schoolName, s.facultyCode, s.sourceYear, s.active);

    -- --------------------------------------------------------
    -- Programmes (representative undergraduate qualifications per faculty).
    -- --------------------------------------------------------
    ;MERGE dbo.ProgrammeReference AS t
    USING (VALUES
        -- Business and Economic Sciences
        ('BCOM_ACC_SCI', 'BCom Accounting Science (Chartered Accountant)', 'Undergraduate', 'FAC_BES', 'SCH_ACC',   @year, 1),
        ('BCOM_ACC',     'BCom Accounting',                                'Undergraduate', 'FAC_BES', 'SCH_ACC',   @year, 1),
        ('BCOM_GEN',     'BCom General',                                   'Undergraduate', 'FAC_BES', 'SCH_MGT',   @year, 1),
        ('BCOM_ECON',    'BCom Economics',                                 'Undergraduate', 'FAC_BES', 'SCH_ECO',   @year, 1),
        ('BCOM_ISCS',    'BCom Information Systems',                       'Undergraduate', 'FAC_BES', 'SCH_MGT',   @year, 1),
        ('BCOM_HRM',     'BCom Human Resource Management',                 'Undergraduate', 'FAC_BES', 'SCH_IPHR',  @year, 1),
        ('DIP_TOUR',     'Diploma in Tourism Management',                  'Undergraduate', 'FAC_BES', 'SCH_ECO',   @year, 1),
        -- Education
        ('BED_FP',       'Bachelor of Education (Foundation Phase Teaching)',    'Undergraduate', 'FAC_EDU', 'SCH_EDU', @year, 1),
        ('BED_IP',       'Bachelor of Education (Intermediate Phase Teaching)',  'Undergraduate', 'FAC_EDU', 'SCH_EDU', @year, 1),
        ('BED_SP_FET',   'Bachelor of Education (Senior & FET Phase Teaching)',  'Undergraduate', 'FAC_EDU', 'SCH_EDU', @year, 1),
        -- Engineering, the Built Environment and Technology
        ('BIT',          'Bachelor of Information Technology',             'Undergraduate', 'FAC_EBET', 'SCH_IT',  @year, 1),
        ('DIP_IT',       'Diploma in Information Technology (Software Development)', 'Undergraduate', 'FAC_EBET', 'SCH_IT', @year, 1),
        ('BENG_MECH',    'Bachelor of Engineering in Mechatronics',        'Undergraduate', 'FAC_EBET', 'SCH_ENG', @year, 1),
        ('BENG_TECH_CIV','Bachelor of Engineering Technology in Civil Engineering',      'Undergraduate', 'FAC_EBET', 'SCH_ENG', @year, 1),
        ('BENG_TECH_ELE','Bachelor of Engineering Technology in Electrical Engineering', 'Undergraduate', 'FAC_EBET', 'SCH_ENG', @year, 1),
        ('BENG_TECH_MEC','Bachelor of Engineering Technology in Mechanical Engineering', 'Undergraduate', 'FAC_EBET', 'SCH_ENG', @year, 1),
        ('BAS_ARCH',     'Bachelor of Architectural Studies',              'Undergraduate', 'FAC_EBET', 'SCH_BEA', @year, 1),
        ('DIP_BUILD',    'Diploma in Building',                            'Undergraduate', 'FAC_EBET', 'SCH_BEA', @year, 1),
        -- Health Sciences
        ('BNURS',        'Bachelor of Nursing',                            'Undergraduate', 'FAC_HEA', 'SCH_CCMS', @year, 1),
        ('BPHARM',       'Bachelor of Pharmacy',                           'Undergraduate', 'FAC_HEA', 'SCH_CCMS', @year, 1),
        ('BHMS',         'Bachelor of Human Movement Science',             'Undergraduate', 'FAC_HEA', 'SCH_BLS',  @year, 1),
        ('MBCHB',        'Bachelor of Medicine and Bachelor of Surgery (MBChB)', 'Undergraduate', 'FAC_HEA', 'SCH_MED', @year, 1),
        -- Humanities
        ('BA_MCC',       'BA Media, Communication and Culture',            'Undergraduate', 'FAC_HUM', 'SCH_LMC',  @year, 1),
        ('BA_GEN',       'Bachelor of Arts (General)',                     'Undergraduate', 'FAC_HUM', 'SCH_GSS',  @year, 1),
        ('BVA',          'Bachelor of Visual Arts',                        'Undergraduate', 'FAC_HUM', 'SCH_VPA',  @year, 1),
        ('BMUS',         'Bachelor of Music',                              'Undergraduate', 'FAC_HUM', 'SCH_VPA',  @year, 1),
        ('BSW',          'Bachelor of Social Work',                        'Undergraduate', 'FAC_HUM', 'SCH_GSS',  @year, 1),
        -- Law
        ('LLB',          'Bachelor of Laws (LLB)',                         'Undergraduate', 'FAC_LAW', 'SCH_LAW',  @year, 1),
        ('BCOM_LAW',     'BCom Law',                                       'Undergraduate', 'FAC_LAW', 'SCH_LAW',  @year, 1),
        -- Science
        ('BSC_CS',       'BSc Computer Science and Information Systems',    'Undergraduate', 'FAC_SCI', 'SCH_MCS',  @year, 1),
        ('BSC_BIO',      'BSc Biological Sciences',                        'Undergraduate', 'FAC_SCI', 'SCH_BIO',  @year, 1),
        ('BSC_CHEM',     'BSc Chemistry',                                  'Undergraduate', 'FAC_SCI', 'SCH_PHY',  @year, 1),
        ('BSC_ENV',      'BSc Environmental Sciences',                     'Undergraduate', 'FAC_SCI', 'SCH_BIO',  @year, 1),
        ('DIP_NATCON',   'Diploma in Nature Conservation',                 'Undergraduate', 'FAC_SCI', 'SCH_NRM',  @year, 1),
        ('DIP_FOR',      'Diploma in Forestry',                            'Undergraduate', 'FAC_SCI', 'SCH_NRM',  @year, 1),
        ('DIP_AGRIC',    'Diploma in Agriculture',                         'Undergraduate', 'FAC_SCI', 'SCH_NRM',  @year, 1),
        ('DIP_GAME',     'Diploma in Game Ranch Management',               'Undergraduate', 'FAC_SCI', 'SCH_NRM',  @year, 1)
    ) AS s (programmeCode, programmeName, qualificationLevel, facultyCode, schoolCode, sourceYear, active)
       ON t.programmeCode = s.programmeCode
    WHEN NOT MATCHED BY TARGET THEN
        INSERT (programmeCode, programmeName, qualificationLevel, facultyCode, schoolCode, sourceYear, active)
        VALUES (s.programmeCode, s.programmeName, s.qualificationLevel, s.facultyCode, s.schoolCode, s.sourceYear, s.active);

    -- --------------------------------------------------------
    -- Programme -> Campus availability. Placement reflects NMU's published
    -- campus offerings: IT at North; Engineering at North; Business across
    -- South / Second Avenue / George; Health at South (Missionvale for some);
    -- Humanities & Law at South; Science at South; Natural Resource
    -- Management diplomas at George.
    -- --------------------------------------------------------
    ;MERGE dbo.ProgrammeCampus AS t
    USING (VALUES
        -- Business and Economic Sciences
        ('BCOM_ACC_SCI', 'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BCOM_ACC',     'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BCOM_ACC',     'GEORGE_CAMPUS',         @year, 'nmu-public'),
        ('BCOM_GEN',     'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BCOM_GEN',     'SECOND_AVENUE_CAMPUS',  @year, 'nmu-public'),
        ('BCOM_GEN',     'GEORGE_CAMPUS',         @year, 'nmu-public'),
        ('BCOM_ECON',    'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BCOM_ISCS',    'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BCOM_HRM',     'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('DIP_TOUR',     'SECOND_AVENUE_CAMPUS',  @year, 'nmu-public'),
        ('DIP_TOUR',     'GEORGE_CAMPUS',         @year, 'nmu-public'),
        -- Education
        ('BED_FP',       'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BED_FP',       'MISSIONVALE_CAMPUS',    @year, 'nmu-public'),
        ('BED_FP',       'GEORGE_CAMPUS',         @year, 'nmu-public'),
        ('BED_IP',       'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BED_IP',       'MISSIONVALE_CAMPUS',    @year, 'nmu-public'),
        ('BED_SP_FET',   'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BED_SP_FET',   'GEORGE_CAMPUS',         @year, 'nmu-public'),
        -- Engineering, the Built Environment and Technology
        ('BIT',          'NORTH_CAMPUS',          @year, 'nmu-public'),
        ('DIP_IT',       'NORTH_CAMPUS',          @year, 'nmu-public'),
        ('BENG_MECH',    'NORTH_CAMPUS',          @year, 'nmu-public'),
        ('BENG_TECH_CIV','NORTH_CAMPUS',          @year, 'nmu-public'),
        ('BENG_TECH_ELE','NORTH_CAMPUS',          @year, 'nmu-public'),
        ('BENG_TECH_MEC','NORTH_CAMPUS',          @year, 'nmu-public'),
        ('BAS_ARCH',     'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('DIP_BUILD',    'NORTH_CAMPUS',          @year, 'nmu-public'),
        -- Health Sciences
        ('BNURS',        'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BPHARM',       'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BHMS',         'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('MBCHB',        'MISSIONVALE_CAMPUS',    @year, 'nmu-public'),
        -- Humanities
        ('BA_MCC',       'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BA_GEN',       'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BVA',          'BIRD_STREET_CAMPUS',    @year, 'nmu-public'),
        ('BMUS',         'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BSW',          'SOUTH_CAMPUS',          @year, 'nmu-public'),
        -- Law
        ('LLB',          'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BCOM_LAW',     'SOUTH_CAMPUS',          @year, 'nmu-public'),
        -- Science
        ('BSC_CS',       'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BSC_BIO',      'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BSC_CHEM',     'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BSC_ENV',      'SOUTH_CAMPUS',          @year, 'nmu-public'),
        ('BSC_ENV',      'GEORGE_CAMPUS',         @year, 'nmu-public'),
        ('DIP_NATCON',   'GEORGE_CAMPUS',         @year, 'nmu-public'),
        ('DIP_FOR',      'GEORGE_CAMPUS',         @year, 'nmu-public'),
        ('DIP_AGRIC',    'GEORGE_CAMPUS',         @year, 'nmu-public'),
        ('DIP_GAME',     'GEORGE_CAMPUS',         @year, 'nmu-public')
    ) AS s (programmeCode, campusCode, sourceYear, evidence)
       ON t.programmeCode = s.programmeCode AND t.campusCode = s.campusCode
    WHEN NOT MATCHED BY TARGET THEN
        INSERT (programmeCode, campusCode, sourceYear, evidence)
        VALUES (s.programmeCode, s.campusCode, s.sourceYear, s.evidence);

    -- --------------------------------------------------------
    -- On-campus residences (representative, per campus with residences).
    -- --------------------------------------------------------
    ;MERGE dbo.ResidenceReference AS t
    USING (VALUES
        (1,  'SOUTH_CAMPUS',       'Melville Residence',        NULL, 1),
        (2,  'SOUTH_CAMPUS',       'Lower Melville Residence',  NULL, 1),
        (3,  'SOUTH_CAMPUS',       'Unitas Residence',          NULL, 1),
        (4,  'SOUTH_CAMPUS',       'Villa Residence',           NULL, 1),
        (5,  'NORTH_CAMPUS',       'Xanadu Residence',          NULL, 1),
        (6,  'NORTH_CAMPUS',       'Sanctor Residence',         NULL, 1),
        (7,  'NORTH_CAMPUS',       'Student Village North',     NULL, 1),
        (8,  'MISSIONVALE_CAMPUS', 'Missionvale Residence',     NULL, 1),
        (9,  'GEORGE_CAMPUS',      'Johnny Clegg Residence',    NULL, 1),
        (10, 'GEORGE_CAMPUS',      'George Campus Residence',   NULL, 1)
    ) AS s (residenceID, campusCode, residenceName, formerName, active)
       ON t.residenceID = s.residenceID
    WHEN NOT MATCHED BY TARGET THEN
        INSERT (residenceID, campusCode, residenceName, formerName, active)
        VALUES (s.residenceID, s.campusCode, s.residenceName, s.formerName, s.active);

    -- --------------------------------------------------------
    -- Accredited off-campus properties. The backend lists properties whose
    -- accreditation year = 2021, so accreditation rows use that year.
    -- --------------------------------------------------------
    ;MERGE dbo.OffCampusProperty AS t
    USING (VALUES
        (1, 'Summerstrand Student Village',   1),
        (2, 'Central Student Living',         1),
        (3, 'South End Student Residence',    1),
        (4, 'George Student Lofts',           1),
        (5, 'Humewood Student Accommodation', 1)
    ) AS s (offCampusPropertyID, propertyName, active)
       ON t.offCampusPropertyID = s.offCampusPropertyID
    WHEN NOT MATCHED BY TARGET THEN
        INSERT (offCampusPropertyID, propertyName, active)
        VALUES (s.offCampusPropertyID, s.propertyName, s.active);

    ;MERGE dbo.OffCampusAccreditation AS t
    USING (VALUES
        (1, CAST(2021 AS SMALLINT), 1),
        (2, CAST(2021 AS SMALLINT), 1),
        (3, CAST(2021 AS SMALLINT), 1),
        (4, CAST(2021 AS SMALLINT), 1),
        (5, CAST(2021 AS SMALLINT), 1)
    ) AS s (offCampusPropertyID, academicYear, accredited)
       ON t.offCampusPropertyID = s.offCampusPropertyID
      AND t.academicYear = s.academicYear
    WHEN NOT MATCHED BY TARGET THEN
        INSERT (offCampusPropertyID, academicYear, accredited)
        VALUES (s.offCampusPropertyID, s.academicYear, s.accredited);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

-- ============================================================
-- ============================================================
-- PART B - DEV ACCOUNTS + SAMPLE SOCIETIES
-- ============================================================
-- 2 developer accounts per role (Student, Executive, SDO) plus 3 sample
-- societies for testing. Uses plain INSERTs; run against a fresh account set.
-- ============================================================

BEGIN TRANSACTION;

-- ============================================================
-- B1. SDO Users (must exist before Societies)
-- ============================================================

INSERT INTO [User] (email, title, firstName, lastName, userType, campus, passwordHash)
VALUES ('mtitshane.tj@outlook.com','Dr','Petra','Michaels','SDO','SOUTH_CAMPUS',
        '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.');

INSERT INTO SDO (staffNumber, email, officeNumber,phoneExtension)
VALUES ('SDO001','mtitshane.tj@outlook.com','0411234001','411');

INSERT INTO [User] (email, title, firstName, lastName, userType, campus, passwordHash)
VALUES ('sdo2@societycentral.com','Mr','James','Nkosi','SDO','NORTH_CAMPUS',
        '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.');

INSERT INTO SDO (staffNumber, email, officeNumber,phoneExtension)
VALUES ('SDO002','sdo2@societycentral.com','0411234002','423');

-- ============================================================
-- B2. Student Users
-- ============================================================

INSERT INTO [User] (email, title, firstName, lastName, userType, campus, passwordHash)
VALUES ('student1@societycentral.com','Ms','Akhona','Dlamini','STUDENT','SOUTH_CAMPUS',
        '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.');

INSERT INTO Student (studentNumber, email, course, level, nationality,
                     residence, school, cellPhoneNumber)
VALUES ('210012001','student1@societycentral.com',
        'BSc Computer Science and Applied Mathematics','3rd Year',
        'South African','On-Campus Residence',
        'SCHOOL_OF_INFORMATION_TECHNOLOGY','0821000001');

INSERT INTO [User] (email, title, firstName, lastName, userType, campus, passwordHash)
VALUES ('student2@societycentral.com','Mr','Luyolo','Mbatha','STUDENT','NORTH_CAMPUS',
        '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.');

INSERT INTO Student (studentNumber, email, course, level, nationality,
                     residence, school, cellPhoneNumber)
VALUES ('210012002','student2@societycentral.com',
        'BCom Accounting','2nd Year',
        'South African','North Campus Residence',
        'SCHOOL_OF_ACCOUNTING','0821000002');

-- ============================================================
-- B3. Executive Users (userType = STUDENT, Executive row gives them EXECUTIVE dashboard)
-- ============================================================

INSERT INTO [User] (email, title, firstName, lastName, userType, campus, passwordHash)
VALUES ('s229878873@mandela.ac.za','Mr','Thabo','Sithole','STUDENT','SOUTH_CAMPUS',
        '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.');

INSERT INTO Student (studentNumber, email, course, level, nationality,
                     residence, school, cellPhoneNumber)
VALUES ('210012003','s229878873@mandela.ac.za',
        'BSc Information Technology','3rd Year',
        'South African','South Campus Residence',
        'SCHOOL_OF_INFORMATION_TECHNOLOGY','0821000003');

INSERT INTO [User] (email, title, firstName, lastName, userType, campus, passwordHash)
VALUES ('exec2@societycentral.com','Ms','Naledi','Mokoena','STUDENT','SOUTH_CAMPUS',
        '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.');

INSERT INTO Student (studentNumber, email, course, level, nationality,
                     residence, school, cellPhoneNumber)
VALUES ('210012004','exec2@societycentral.com',
        'BSc Information Technology','2nd Year',
        'South African','South Campus Residence',
        'SCHOOL_OF_INFORMATION_TECHNOLOGY','0821000004');

-- ============================================================
-- B4. Sample Societies
-- ============================================================

INSERT INTO Society (societyID, societyName, acronym, sdoStaffNumber, numberOfMembers,
                     activeStatus, description, vision, mission, yearEstablished,
                     contactNumber, email, campus, societyType, school, faculty, logoUrl, isFlagged)
VALUES ('SOC001','Computer Society','S1','SDO001',0,1,
        'A technology and innovation society for all NMU students.',
        'To build the next generation of tech leaders at NMU.',
        'Promoting innovation through workshops, hackathons and competitions.',
        2010,'0411230001','society1@societycentral.com','SOUTH_CAMPUS',
        'TECHNOLOGY_AND_INNOVATION','SCHOOL_OF_INFORMATION_TECHNOLOGY',
        'ENGINEERING_BUILT_ENVIRONMENT_AND_TECHNOLOGY','https://images.squarespace-cdn.com/content/v1/65a93a089f862c1d17683127/9bb0b9bc-1c1e-4a9f-8d8a-6d4fbdac1b5a/csociety_logo.png',0);

INSERT INTO Society (societyID, societyName, acronym, sdoStaffNumber, numberOfMembers,
                     activeStatus, description, vision, mission, yearEstablished,
                     contactNumber, email, campus, societyType, school, faculty, logoUrl, isFlagged)
VALUES ('SOC002','Society Two','S2','SDO001',0,1,
        'An academic society supporting students across all faculties.',
        'To foster academic excellence and peer support at NMU.',
        'Supporting students through tutoring, study groups and academic events.',
        2015,'0411230002','society2@societycentral.com','SOUTH_CAMPUS',
        'ACADEMIC','NONE','NONE','https://tse3.mm.bing.net/th/id/OIP.71TzABAWiaBp5LpQ8CqkMgHaHa?r=0&rs=1&pid=ImgDetMain&o=7&rm=3',0);

INSERT INTO Society (societyID, societyName, acronym, sdoStaffNumber, numberOfMembers,
                     activeStatus, description, vision, mission, yearEstablished,
                     contactNumber, email, campus, societyType, school, faculty, logoUrl, isFlagged)
VALUES ('SOC003','ProMaths','S3','SDO002',0,1,
        'A cultural and heritage society celebrating NMU student diversity.',
        'To promote cultural awareness and inclusion across all campuses.',
        'Hosting cultural events, heritage days and intercultural dialogue.',
        2018,'0411230003','society3@societycentral.com','NORTH_CAMPUS',
        'CULTURAL_AND_HERITAGE','NONE','NONE','https://tse2.mm.bing.net/th/id/OIP.BJthBX7vTPB-vjQAP7i46gHaEp?r=0&rs=1&pid=ImgDetMain&o=7&rm=3',0);

-- ============================================================
-- B5. Executive rows (exec1 = President, exec2 = Secretary of SOC001)
-- termEndDate = NULL means currently serving -> dashboardType = EXECUTIVE at login
-- ============================================================

INSERT INTO Executive (studentNumber, societyID, termStartDate, termEndDate, position)
VALUES ('210012003','SOC001','2026-01-01',NULL,'President');

INSERT INTO Executive (studentNumber, societyID, termStartDate, termEndDate, position)
VALUES ('210012004','SOC001','2026-01-01',NULL,'Secretary');

-- ============================================================
-- B6. Society Memberships
-- ============================================================

INSERT INTO SocietyMember (studentNumber, societyID, joinDate)
VALUES ('210012001','SOC001','2026-02-01');

INSERT INTO SocietyMember (studentNumber, societyID, joinDate)
VALUES ('210012001','SOC002','2026-02-01');

INSERT INTO SocietyMember (studentNumber, societyID, joinDate)
VALUES ('210012002','SOC001','2026-02-15');

INSERT INTO SocietyMember (studentNumber, societyID, joinDate)
VALUES ('210012002','SOC002','2026-02-15');

INSERT INTO SocietyMember (studentNumber, societyID, joinDate)
VALUES ('210012003','SOC001','2026-01-01');

INSERT INTO SocietyMember (studentNumber, societyID, joinDate)
VALUES ('210012004','SOC001','2026-01-01');

-- Update denormalised member counts
UPDATE Society SET numberOfMembers = 4 WHERE societyID = 'SOC001';
UPDATE Society SET numberOfMembers = 2 WHERE societyID = 'SOC002';

COMMIT TRANSACTION;
GO

-- Normalise all seeded dev accounts to the shared demo password.
UPDATE [User] SET passwordHash='$2a$10$Rah2fn/pqat/4z2JJSgxYui5eBXGBFnwVjDNP/AQbdQ1JkaTzBnwq';
GO

-- ============================================================
-- Verify inserts (uncomment to run after seeding):
-- ============================================================
-- SELECT * FROM [User];
-- SELECT * FROM Student;
-- SELECT * FROM SDO;
-- SELECT * FROM Society;
-- SELECT * FROM Executive;
-- SELECT * FROM SocietyMember;
-- SELECT * FROM Faculty;
-- SELECT * FROM ProgrammeReference;
