-- ============================================================
-- SocietyCentral — PostgreSQL seed data (deployment copy)
-- ============================================================
-- Run this ONCE against the Render Postgres database AFTER the Spring Boot
-- app has started at least once (Hibernate ddl-auto=update creates the tables
-- on first boot). Hibernate quotes the camelCase identifiers, so every table
-- and column below is double-quoted to match exactly.
--
-- How to run:
--   psql "<your Render External Database URL>" -f database/postgres_seed.sql
--
-- Idempotent: uses ON CONFLICT DO NOTHING so it is safe to re-run.
-- Shared demo password for every seeded account: see the UPDATE at the end.
-- ============================================================

BEGIN;

-- ── Reference data: campuses (used by registration dropdowns) ────────────────
INSERT INTO "CampusReference" ("campusCode","campusName","active","city","hasOnCampusResidence") VALUES
    ('SOUTH_CAMPUS',         'South Campus',          true, 'Gqeberha', true),
    ('NORTH_CAMPUS',         'North Campus',          true, 'Gqeberha', true),
    ('SECOND_AVENUE_CAMPUS', 'Second Avenue Campus',  true, 'Gqeberha', false),
    ('BIRD_STREET_CAMPUS',   'Bird Street Campus',    true, 'Gqeberha', false),
    ('MISSIONVALE_CAMPUS',   'Missionvale Campus',    true, 'Gqeberha', true),
    ('OCEAN_SCIENCES_CAMPUS','Ocean Sciences Campus', true, 'Gqeberha', false),
    ('GEORGE_CAMPUS',        'George Campus',         true, 'George',   true)
ON CONFLICT ("campusCode") DO NOTHING;

-- ── Reference data: faculties ────────────────────────────────────────────────
INSERT INTO "Faculty" ("facultyCode","facultyName","sourceYear","active") VALUES
    ('FAC_BES',  'Business and Economic Sciences',                    2025, true),
    ('FAC_EDU',  'Education',                                         2025, true),
    ('FAC_EBET', 'Engineering, the Built Environment and Technology', 2025, true),
    ('FAC_HEA',  'Health Sciences',                                   2025, true),
    ('FAC_HUM',  'Humanities',                                        2025, true),
    ('FAC_LAW',  'Law',                                               2025, true),
    ('FAC_SCI',  'Science',                                           2025, true)
ON CONFLICT ("facultyCode") DO NOTHING;

-- ── Reference data: accommodation types (fixed IDs 1..5) ─────────────────────
INSERT INTO "AccommodationType" ("accommodationTypeID","active") VALUES
    (1,true),(2,true),(3,true),(4,true),(5,true)
ON CONFLICT ("accommodationTypeID") DO NOTHING;

-- ============================================================
-- Dev accounts (2 per role) + 3 sample societies
-- ============================================================

-- SDO users (must exist before societies reference them)
INSERT INTO "User" ("email","title","firstName","lastName","userType","campus","passwordHash") VALUES
    ('mtitshane.tj@outlook.com','Dr','Petra','Michaels','SDO','SOUTH_CAMPUS','$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.'),
    ('sdo2@societycentral.com','Mr','James','Nkosi','SDO','NORTH_CAMPUS','$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.')
ON CONFLICT ("email") DO NOTHING;

INSERT INTO "SDO" ("staffNumber","email","officeNumber","phoneExtension") VALUES
    ('SDO001','mtitshane.tj@outlook.com','0411234001','411'),
    ('SDO002','sdo2@societycentral.com','0411234002','423')
ON CONFLICT ("staffNumber") DO NOTHING;

-- Student users
INSERT INTO "User" ("email","title","firstName","lastName","userType","campus","passwordHash") VALUES
    ('student1@societycentral.com','Ms','Akhona','Dlamini','STUDENT','SOUTH_CAMPUS','$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.'),
    ('student2@societycentral.com','Mr','Luyolo','Mbatha','STUDENT','NORTH_CAMPUS','$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.'),
    ('s229878873@mandela.ac.za','Mr','Thabo','Sithole','STUDENT','SOUTH_CAMPUS','$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.'),
    ('exec2@societycentral.com','Ms','Naledi','Mokoena','STUDENT','SOUTH_CAMPUS','$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.')
ON CONFLICT ("email") DO NOTHING;

INSERT INTO "Student" ("studentNumber","email","course","level","nationality","residence","school","cellPhoneNumber") VALUES
    ('210012001','student1@societycentral.com','BSc Computer Science and Applied Mathematics','3rd Year','South African','On-Campus Residence','SCHOOL_OF_INFORMATION_TECHNOLOGY','0821000001'),
    ('210012002','student2@societycentral.com','BCom Accounting','2nd Year','South African','North Campus Residence','SCHOOL_OF_ACCOUNTING','0821000002'),
    ('210012003','s229878873@mandela.ac.za','BSc Information Technology','3rd Year','South African','South Campus Residence','SCHOOL_OF_INFORMATION_TECHNOLOGY','0821000003'),
    ('210012004','exec2@societycentral.com','BSc Information Technology','2nd Year','South African','South Campus Residence','SCHOOL_OF_INFORMATION_TECHNOLOGY','0821000004')
ON CONFLICT ("studentNumber") DO NOTHING;

-- Sample societies
INSERT INTO "Society" ("societyID","societyName","acronym","sdoStaffNumber","numberOfMembers","activeStatus","description","vision","mission","yearEstablished","contactNumber","email","campus","societyType","school","faculty","logoUrl","isFlagged") VALUES
    ('SOC001','Computer Society','S1','SDO001',4,true,
        'A technology and innovation society for all NMU students.',
        'To build the next generation of tech leaders at NMU.',
        'Promoting innovation through workshops, hackathons and competitions.',
        2010,'0411230001','society1@societycentral.com','SOUTH_CAMPUS',
        'TECHNOLOGY_AND_INNOVATION','SCHOOL_OF_INFORMATION_TECHNOLOGY',
        'ENGINEERING_BUILT_ENVIRONMENT_AND_TECHNOLOGY',
        'https://images.squarespace-cdn.com/content/v1/65a93a089f862c1d17683127/9bb0b9bc-1c1e-4a9f-8d8a-6d4fbdac1b5a/csociety_logo.png',false),
    ('SOC002','Society Two','S2','SDO001',2,true,
        'An academic society supporting students across all faculties.',
        'To foster academic excellence and peer support at NMU.',
        'Supporting students through tutoring, study groups and academic events.',
        2015,'0411230002','society2@societycentral.com','SOUTH_CAMPUS',
        'ACADEMIC','NONE','NONE',
        'https://tse3.mm.bing.net/th/id/OIP.71TzABAWiaBp5LpQ8CqkMgHaHa?r=0&rs=1&pid=ImgDetMain&o=7&rm=3',false),
    ('SOC003','ProMaths','S3','SDO002',0,true,
        'A cultural and heritage society celebrating NMU student diversity.',
        'To promote cultural awareness and inclusion across all campuses.',
        'Hosting cultural events, heritage days and intercultural dialogue.',
        2018,'0411230003','society3@societycentral.com','NORTH_CAMPUS',
        'CULTURAL_AND_HERITAGE','NONE','NONE',
        'https://tse2.mm.bing.net/th/id/OIP.BJthBX7vTPB-vjQAP7i46gHaEp?r=0&rs=1&pid=ImgDetMain&o=7&rm=3',false)
ON CONFLICT ("societyID") DO NOTHING;

-- Executive rows (termEndDate NULL = currently serving -> EXECUTIVE dashboard)
INSERT INTO "Executive" ("studentNumber","societyID","termStartDate","termEndDate","position") VALUES
    ('210012003','SOC001',DATE '2026-01-01',NULL,'President'),
    ('210012004','SOC001',DATE '2026-01-01',NULL,'Secretary')
ON CONFLICT DO NOTHING;

-- Society memberships
INSERT INTO "SocietyMember" ("studentNumber","societyID","joinDate") VALUES
    ('210012001','SOC001',DATE '2026-02-01'),
    ('210012001','SOC002',DATE '2026-02-01'),
    ('210012002','SOC001',DATE '2026-02-15'),
    ('210012002','SOC002',DATE '2026-02-15'),
    ('210012003','SOC001',DATE '2026-01-01'),
    ('210012004','SOC001',DATE '2026-01-01')
ON CONFLICT DO NOTHING;

-- Normalise all seeded dev accounts to the shared demo password.
UPDATE "User" SET "passwordHash" = '$2a$10$Rah2fn/pqat/4z2JJSgxYui5eBXGBFnwVjDNP/AQbdQ1JkaTzBnwq';

COMMIT;

-- Login with any seeded email above and the shared demo password.
