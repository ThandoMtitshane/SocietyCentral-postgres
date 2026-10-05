/*
  Documentation of the SSMS-applied academic reference migration.
  This project uses spring.jpa.hibernate.ddl-auto=validate; this file is
  intentionally not executed by Hibernate and must not recreate the database.
  Applied tables: Faculty, CampusReference, SchoolReference,
  ProgrammeReference, ProgrammeCampus.
  Applied columns: Student.programmeCode, Society.facultyCode,
  Society.schoolCode. Reference data source year: 2021.
*/
