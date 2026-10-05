USE SocietyCentral;
GO

BEGIN TRANSACTION;

DECLARE @i INT = 4;
DECLARE @studentNumber INT = 210012005;

WHILE @i <= 53
BEGIN

    DECLARE @email VARCHAR(100);
    DECLARE @studentNo VARCHAR(20);
    DECLARE @phone VARCHAR(20);

    SET @email = CONCAT('student', @i, '@societycentral.ac.za');
    SET @studentNo = CAST(@studentNumber AS VARCHAR(20));
    SET @phone = CONCAT('08210000', RIGHT('00' + CAST(@i AS VARCHAR(2)),2));


    -- Insert User
INSERT INTO [User]
(
    email,
    title,
    firstName,
    lastName,
    userType,
    campus,
    passwordHash
)
VALUES
    (
    @email,
    CASE WHEN @i % 2 = 0 THEN 'Mr' ELSE 'Ms' END,
    CONCAT('Student', @i),
    'Member',
    'STUDENT',
    'SOUTH_CAMPUS',
    '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.'
    );


-- Insert Student
INSERT INTO Student
(
    studentNumber,
    email,
    course,
    level,
    nationality,
    residence,
    school,
    cellPhoneNumber
)
VALUES
    (
        @studentNo,
        @email,
        'BSc Computer Science and Applied Mathematics',
        '2nd Year',
        'South African',
        'On-Campus Residence',
        'SCHOOL_OF_INFORMATION_TECHNOLOGY',
        @phone
    );


-- Add to Computer Society
INSERT INTO SocietyMember
(
    studentNumber,
    societyID,
    joinDate
)
VALUES
    (
        @studentNo,
        'SOC001',
        '2024-02-01'
    );


-- Add to ProMaths
INSERT INTO SocietyMember
(
    studentNumber,
    societyID,
    joinDate
)
VALUES
    (
        @studentNo,
        'SOC003',
        '2024-02-01'
    );


SET @i = @i + 1;
    SET @studentNumber = @studentNumber + 1;

END;


-- Update member counts
UPDATE Society
SET numberOfMembers = numberOfMembers + 50
WHERE societyID = 'SOC001';


UPDATE Society
SET numberOfMembers = numberOfMembers + 50
WHERE societyID = 'SOC003';


COMMIT TRANSACTION;
GO