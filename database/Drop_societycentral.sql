-- ============================================================
-- Drop SocietyCentral database
-- Run this BEFORE re-running SocietyCentral_Schema.sql, since the
-- schema has changed (Student.faculty dropped, Society/User/Event
-- campus/school/faculty columns resized for enums, Society.societyType
-- added).
--
-- WARNING: This permanently deletes the database and ALL data in it.
-- Only run this if you have no data you need to keep.
-- ============================================================

-- Switch to a different database first - SQL Server won't let you drop
-- the database you're currently connected to.
USE master;
GO

-- Forcefully disconnect any active connections to SocietyCentral
-- (e.g. if your Spring Boot app or SSMS still has a connection open)
ALTER DATABASE SocietyCentral SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
GO

DROP DATABASE SocietyCentral;
GO