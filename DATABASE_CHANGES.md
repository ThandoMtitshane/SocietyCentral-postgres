# Academic reference data

Applied in SQL Server on 2026-08-24 from the supplied Nelson Mandela University Undergraduate Guide 2021.

The existing database contains 7 faculties, 7 campuses, 18 schools, 153 programmes and 33 explicit programme-campus mappings. The tables are `Faculty`, `CampusReference`, `SchoolReference`, `ProgrammeReference`, and `ProgrammeCampus`.

Student gender is now represented by `Student.gender` using the `Gender` enum. Execute `database/Add_Student_Gender.sql` in SSMS before starting the updated backend; until then `ddl-auto=validate` will correctly reject the new mapping.

`Student.programmeCode`, `Society.facultyCode`, and `Society.schoolCode` are foreign-key additions. Legacy `Student.course`, `Student.school`, `User.campus`, `Society.campus`, `Society.faculty`, and `Society.school` remain intentionally retained for compatibility and migration.

`Student.programme` is authoritative when present; faculty and school are derived from it. `spring.jpa.hibernate.ddl-auto=validate` requires the schema to exist before startup. The SQL reference file under `src/main/resources/db` documents the applied change and is not an automatic schema creator.
