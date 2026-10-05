-- ============================================================
-- SocietyCentral Database Schema
-- SQL Server
-- ============================================================

CREATE DATABASE SocietyCentral;
GO

USE SocietyCentral;
GO

SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET QUOTED_IDENTIFIER ON;
GO

-- ============================================================
-- 1. USER (supertype)
-- ============================================================
CREATE TABLE [User] (
    email             VARCHAR(100) PRIMARY KEY,
    title             VARCHAR(10),
    firstName         VARCHAR(50)  NOT NULL,
    lastName          VARCHAR(50)  NOT NULL,
    userType          VARCHAR(20)  NOT NULL,   -- 'STUDENT', 'SDO'
    campus            VARCHAR(30),             -- Campus enum (e.g. 'SOUTH_CAMPUS')
    passwordHash      VARCHAR(255) NOT NULL,
    profilePictureURL VARCHAR(255),
    emailVerified     BIT NOT NULL CONSTRAINT DF_User_EmailVerified DEFAULT 1,
    passwordTemporary BIT NOT NULL CONSTRAINT DF_User_PasswordTemporary DEFAULT 0
);
GO

-- ============================================================
-- 2. SDO (subtype of User)
-- ============================================================
CREATE TABLE SDO (
    staffNumber     VARCHAR(20) PRIMARY KEY,
    email           VARCHAR(100) NOT NULL UNIQUE,
    officeNumber    VARCHAR(15),
    phoneExtension  VARCHAR(10),
    FOREIGN KEY (email) REFERENCES [User](email)
);
GO

-- ============================================================
-- 2A. PHASE 5 REFERENCE DATA TABLES
--     Academic hierarchy used by Student registration.
--     Must be created before Student because Student references them.
-- ============================================================

-- AccommodationType (auto-generated PK)
CREATE TABLE AccommodationType (
    accommodationTypeID INT IDENTITY(1,1) PRIMARY KEY,
    active              BIT NOT NULL
);
GO

-- CampusReference (authoritative campus catalogue)
CREATE TABLE CampusReference (
    campusCode            VARCHAR(40)  PRIMARY KEY,
    campusName            VARCHAR(120) NOT NULL UNIQUE,
    active                BIT          NOT NULL,
    city                  VARCHAR(120),
    hasOnCampusResidence  BIT          NOT NULL
);
GO

-- Faculty (academic faculty reference)
CREATE TABLE Faculty (
    facultyCode  VARCHAR(20)  PRIMARY KEY,
    facultyName  VARCHAR(200) NOT NULL UNIQUE,
    sourceYear   SMALLINT     NOT NULL,
    active       BIT          NOT NULL
);
GO

-- SchoolReference (academic school, belongs to a faculty)
CREATE TABLE SchoolReference (
    schoolCode   VARCHAR(60)  PRIMARY KEY,
    schoolName   VARCHAR(220) NOT NULL,
    facultyCode  VARCHAR(20)  NOT NULL,
    sourceYear   SMALLINT     NOT NULL,
    active       BIT          NOT NULL,
    CONSTRAINT FK_SchoolReference_Faculty
        FOREIGN KEY (facultyCode) REFERENCES Faculty(facultyCode)
);
GO

-- ProgrammeReference (degree / qualification)
CREATE TABLE ProgrammeReference (
    programmeCode       VARCHAR(120) PRIMARY KEY,
    programmeName       VARCHAR(300) NOT NULL UNIQUE,
    qualificationLevel  VARCHAR(50)  NOT NULL,
    facultyCode         VARCHAR(20)  NOT NULL,
    schoolCode          VARCHAR(60)  NULL,
    sourceYear          SMALLINT     NOT NULL,
    active              BIT          NOT NULL,
    CONSTRAINT FK_ProgrammeReference_Faculty
        FOREIGN KEY (facultyCode) REFERENCES Faculty(facultyCode),
    CONSTRAINT FK_ProgrammeReference_School
        FOREIGN KEY (schoolCode) REFERENCES SchoolReference(schoolCode)
);
GO

-- ProgrammeCampus (which programmes are offered on which campuses)
CREATE TABLE ProgrammeCampus (
    programmeCode  VARCHAR(120) NOT NULL,
    campusCode     VARCHAR(40)  NOT NULL,
    sourceYear     SMALLINT     NOT NULL,
    evidence       VARCHAR(200) NOT NULL,
    PRIMARY KEY (programmeCode, campusCode),
    CONSTRAINT FK_ProgrammeCampus_Programme
        FOREIGN KEY (programmeCode) REFERENCES ProgrammeReference(programmeCode),
    CONSTRAINT FK_ProgrammeCampus_Campus
        FOREIGN KEY (campusCode) REFERENCES CampusReference(campusCode)
);
GO

-- ResidenceReference (on-campus residences per campus)
CREATE TABLE ResidenceReference (
    residenceID    INT          PRIMARY KEY,
    campusCode     VARCHAR(40)  NOT NULL,
    residenceName  VARCHAR(200) NOT NULL,
    formerName     VARCHAR(200),
    active         BIT          NOT NULL
);
GO

-- OffCampusProperty (accredited off-campus accommodation)
CREATE TABLE OffCampusProperty (
    offCampusPropertyID  INT          PRIMARY KEY,
    propertyName         VARCHAR(220) NOT NULL,
    active               BIT          NOT NULL
);
GO

-- OffCampusAccreditation (per-property per-year accreditation status)
CREATE TABLE OffCampusAccreditation (
    offCampusPropertyID  INT      NOT NULL,
    academicYear         SMALLINT NOT NULL,
    accredited           BIT      NOT NULL,
    PRIMARY KEY (offCampusPropertyID, academicYear),
    CONSTRAINT FK_OffCampusAccreditation_Property
        FOREIGN KEY (offCampusPropertyID) REFERENCES OffCampusProperty(offCampusPropertyID)
);
GO

-- ============================================================
-- 3. STUDENT (subtype of User)
-- ============================================================
CREATE TABLE Student (
    studentNumber           VARCHAR(20)  PRIMARY KEY,
    email                   VARCHAR(100) NOT NULL UNIQUE,
    course                  VARCHAR(100),
    level                   VARCHAR(10),
    nationality             VARCHAR(50),
    studyCity               VARCHAR(120),
    gender                  VARCHAR(30),            -- Gender enum
    residence               VARCHAR(100),           -- legacy free-text
    school                  VARCHAR(60),            -- School enum (faculty derived from school)
    cellPhoneNumber         VARCHAR(10),
    otherAccommodationName  VARCHAR(220),
    programmeCode           VARCHAR(120),
    accommodationTypeID     INT,
    residenceID             INT,
    offCampusPropertyID     INT,
    FOREIGN KEY (email) REFERENCES [User](email),
    CONSTRAINT FK_Student_Programme
        FOREIGN KEY (programmeCode) REFERENCES ProgrammeReference(programmeCode),
    CONSTRAINT FK_Student_AccommodationType
        FOREIGN KEY (accommodationTypeID) REFERENCES AccommodationType(accommodationTypeID),
    CONSTRAINT FK_Student_Residence
        FOREIGN KEY (residenceID) REFERENCES ResidenceReference(residenceID),
    CONSTRAINT FK_Student_OffCampusProperty
        FOREIGN KEY (offCampusPropertyID) REFERENCES OffCampusProperty(offCampusPropertyID)
);
GO

-- ============================================================
-- 4. SOCIETY
-- ============================================================
CREATE TABLE Society (
    societyID              VARCHAR(20)    PRIMARY KEY,
    societyName            VARCHAR(100)   NOT NULL,
    numberOfMembers        INT            DEFAULT 0,
    sdoStaffNumber         VARCHAR(20)    NOT NULL,
    activeStatus           BIT            DEFAULT 0,
    description            VARCHAR(2500),
    campus                 VARCHAR(30),            -- Campus enum, primary campus only
    email                  VARCHAR(100),
    tiktokURL              VARCHAR(200),
    facebookURL            VARCHAR(200),
    instagramURL           VARCHAR(200),
    rating                 FLOAT,
    school                 VARCHAR(60)    DEFAULT 'NONE',   -- School enum
    faculty                VARCHAR(60)    DEFAULT 'NONE',   -- Faculty enum
    societyType            VARCHAR(40),            -- SocietyType enum
    logoUrl                VARCHAR(255),
    bannerUrl              VARCHAR(500),
    isFlagged              BIT            DEFAULT 0,
    acronym                VARCHAR(10),
    vision                 VARCHAR(500),
    mission                VARCHAR(500),
    yearEstablished        INT,
    flaggedDate            DATETIME2      DEFAULT(NULL),
    contactNumber          VARCHAR(10),
    membershipFee          DECIMAL(10,2)  DEFAULT 0.00,
    annualBudgetAllocation DECIMAL(10,2)  DEFAULT 0.00,
    currentBalance         DECIMAL(10,2)  DEFAULT 0.00,
    FOREIGN KEY (sdoStaffNumber) REFERENCES SDO(staffNumber)
);
GO

-- ============================================================
-- 4A. SOCIETY MEDIA
--     Repeatable gallery slides and highlight cover images.
--     Logo and banner remain on Society.
-- ============================================================
CREATE TABLE SocietyMedia (
    mediaID     VARCHAR(36)  NOT NULL,
    societyID   VARCHAR(20)  NOT NULL,
    mediaUrl    VARCHAR(500) NOT NULL,
    mediaType   VARCHAR(30)  NOT NULL,
    caption     VARCHAR(200),
    sortOrder   INT          NOT NULL,
    uploadedAt  DATETIME2(7) NOT NULL,
    uploadedBy  VARCHAR(100) NOT NULL,
    CONSTRAINT PK_SocietyMedia
        PRIMARY KEY (mediaID),
    CONSTRAINT FK_SocietyMedia_Society
        FOREIGN KEY (societyID)
            REFERENCES Society(societyID),
    CONSTRAINT CK_SocietyMedia_Type
        CHECK (mediaType IN ('GALLERY_IMAGE', 'HIGHLIGHT_COVER')),
    CONSTRAINT CK_SocietyMedia_SortOrder
        CHECK (sortOrder >= 0)
);
GO

CREATE INDEX IX_SocietyMedia_Society_Type_Order
    ON SocietyMedia(
        societyID,
        mediaType,
        sortOrder,
        uploadedAt,
        mediaID
    );
GO

-- ============================================================
-- 4B. SOCIETY HIGHLIGHT
--     Article-backed highlights for society profile pages.
--     Each highlight references a cover image in SocietyMedia.
-- ============================================================
CREATE TABLE SocietyHighlight (
    highlightID   VARCHAR(36)   NOT NULL,
    societyID     VARCHAR(20)   NOT NULL,
    headline      VARCHAR(MAX)  NOT NULL,
    caption       VARCHAR(MAX)  NOT NULL,
    article       VARCHAR(MAX)  NOT NULL,
    coverMediaID  VARCHAR(36)   NOT NULL,
    category      VARCHAR(80)   NULL,
    sortOrder     INT           NOT NULL,
    publishedAt   DATETIME2(7)  NULL,
    activeStatus  BIT           NOT NULL DEFAULT 1,
    createdBy     VARCHAR(100)  NOT NULL,
    createdAt     DATETIME2(7)  NOT NULL,
    updatedAt     DATETIME2(7)  NOT NULL,
    CONSTRAINT PK_SocietyHighlight
        PRIMARY KEY (highlightID),
    CONSTRAINT FK_SocietyHighlight_Society
        FOREIGN KEY (societyID)
            REFERENCES Society(societyID),
    CONSTRAINT FK_SocietyHighlight_CoverMedia
        FOREIGN KEY (coverMediaID)
            REFERENCES SocietyMedia(mediaID),
    CONSTRAINT UQ_SocietyHighlight_CoverMedia
        UNIQUE (coverMediaID),
    CONSTRAINT CK_SocietyHighlight_SortOrder
        CHECK (sortOrder >= 0)
);
GO

CREATE INDEX IX_SocietyHighlight_PublicOrder
    ON SocietyHighlight(
        societyID,
        activeStatus,
        sortOrder,
        publishedAt,
        highlightID
    );
GO

-- ============================================================
-- 5. EXECUTIVE (composite key: studentNumber + societyID + termStartDate)
-- ============================================================
CREATE TABLE Executive (
    studentNumber  VARCHAR(20) NOT NULL,
    societyID      VARCHAR(20) NOT NULL,
    termStartDate  DATE NOT NULL,
    termEndDate    DATE,
    position       VARCHAR(50),
    PRIMARY KEY (studentNumber, societyID, termStartDate),
    FOREIGN KEY (studentNumber) REFERENCES Student(studentNumber),
    FOREIGN KEY (societyID) REFERENCES Society(societyID)
);
GO

-- ============================================================
-- 6. SOCIETY MEMBER (many-to-many: Student <-> Society)
-- ============================================================
CREATE TABLE SocietyMember (
    studentNumber VARCHAR(20) NOT NULL,
    societyID     VARCHAR(20) NOT NULL,
    joinDate      DATE DEFAULT GETDATE(),
    expireDate    DATE DEFAULT (
        DATEFROMPARTS(YEAR(GETDATE()), 12, 31)
    ),
    PRIMARY KEY (studentNumber, societyID),
    FOREIGN KEY (studentNumber) REFERENCES Student(studentNumber),
    FOREIGN KEY (societyID) REFERENCES Society(societyID)
);
GO

-- ============================================================
-- 6A. MEMBERSHIP APPLICATION
--     Pending workflow record created before approved membership.
-- ============================================================
CREATE TABLE MembershipApplication (
    applicationID      VARCHAR(36)  NOT NULL,
    trackingReference  VARCHAR(20)  NOT NULL,
    studentNumber      VARCHAR(20)  NOT NULL,
    societyID          VARCHAR(20)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    motivation         VARCHAR(500) NOT NULL,
    applicationDate    DATETIME2(7) NOT NULL,
    lastUpdatedAt      DATETIME2(7) NOT NULL,
    reviewedAt         DATETIME2(7),
    reviewedBy         VARCHAR(20),
    rejectionReason    VARCHAR(500),
    withdrawnAt        DATETIME2(7),
    CONSTRAINT PK_MembershipApplication
        PRIMARY KEY (applicationID),
    CONSTRAINT UQ_MembershipApplication_TrackingReference
        UNIQUE (trackingReference),
    CONSTRAINT CK_MembershipApplication_Status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT FK_MembershipApplication_Student
        FOREIGN KEY (studentNumber) REFERENCES Student(studentNumber),
    CONSTRAINT FK_MembershipApplication_Society
        FOREIGN KEY (societyID) REFERENCES Society(societyID),
    CONSTRAINT FK_MembershipApplication_Reviewer
        FOREIGN KEY (reviewedBy) REFERENCES Student(studentNumber)
);
GO

CREATE INDEX IX_MembershipApplication_Student_Society_Status
    ON MembershipApplication(studentNumber, societyID, status);
GO

CREATE UNIQUE INDEX UX_MembershipApplication_Pending
    ON MembershipApplication(studentNumber, societyID)
    WHERE status = 'PENDING';
GO

CREATE INDEX IX_MembershipApplication_Society_Status_ApplicationDate
    ON MembershipApplication(
        societyID,
        status,
        applicationDate,
        applicationID
    );
GO

CREATE INDEX IX_MembershipApplication_Society_Status_LastUpdated
    ON MembershipApplication(
        societyID,
        status,
        lastUpdatedAt DESC,
        applicationID
    );
GO

-- ============================================================
-- 7. VENUE (authoritative campus venue catalogue)
-- ============================================================
CREATE TABLE Venue (
    venueCode VARCHAR(10) PRIMARY KEY,
    campus    VARCHAR(50) NOT NULL,
    venueName VARCHAR(150) NOT NULL,
    venueType VARCHAR(100) NOT NULL,
    capacity  INT NOT NULL,
    active    BIT NOT NULL
        CONSTRAINT DF_Venue_Active DEFAULT 1,
    CONSTRAINT CK_Venue_Capacity_Positive
        CHECK (capacity > 0)
);
GO

CREATE INDEX IX_Venue_Campus_Active
    ON Venue(campus, active);
GO

-- ============================================================
-- 8. EVENT (societyID removed - now linked via Hoster)
-- ============================================================
CREATE TABLE Event (
    eventID          VARCHAR(20) PRIMARY KEY,
    eventName        VARCHAR(100) NOT NULL,
    eventDate        DATE,
    eventStartTime   TIME,
    eventEndTime     TIME,
    venueCode        VARCHAR(10),
    -- Legacy compatibility columns:
    eventTime        TIME,
    advertisementVersion  INT,
    eventVenue       VARCHAR(100),
    eventCampus      VARCHAR(30),        -- Campus enum
    eventDescription VARCHAR(1000),
    eventStatus      VARCHAR(20),        -- DRAFT, PROPOSED, APPROVED, REJECTED, PUBLISHED, CANCELLED, COMPLETED
    rsvpOpenDate     DATETIME,
    rsvpCloseDate    DATETIME,
    eventLimit       INT,
    attendingType    VARCHAR(20),        -- 'MEMBERS' or 'EVERY_STUDENT'
    overallRating    FLOAT,
    imageUrl         VARCHAR(255),       -- legacy single-image URL
    posterUrl        VARCHAR(500) NULL,
    bannerUrl        VARCHAR(500) NULL,
    rejectionReason  VARCHAR(1000) NULL,
    -- Proposal workflow columns:
    submittedBy      VARCHAR(20) NULL,          -- studentNumber of executive who submitted
    poaEventID       VARCHAR(36) NULL,          -- links to POA event used to create this proposal
    -- Budget (inline, mirrors POAEvent):
    budgetIncomeFromAccount      DECIMAL(10,2) DEFAULT 0.00,
    budgetIncomeSponsorship      DECIMAL(10,2) DEFAULT 0.00,
    budgetExpensePromoMaterial    DECIMAL(10,2) DEFAULT 0.00,
    budgetExpenseDataAirtime      DECIMAL(10,2) DEFAULT 0.00,
    budgetExpenseGifts            DECIMAL(10,2) DEFAULT 0.00,
    budgetExpenseVenue            DECIMAL(10,2) DEFAULT 0.00,
    budgetExpenseOther            DECIMAL(10,2) DEFAULT 0.00,
    budgetExpenseOtherSpecification VARCHAR(200) NULL,
    -- SDO review:
    sdoReviewNotes         VARCHAR(1000) NULL,
    reviewedByStaffNumber  VARCHAR(20) NULL,
    reviewedAt             DATETIME2 NULL,
    publishedAt            DATETIME2 NULL,
    -- Feedback scheduling:
    feedbackEmailSentAt    DATETIME2 NULL,
    advanceNoticeSentAt    DATETIME2 NULL,
    rsvpOpenNoticeSentAt   DATETIME2 NULL,
    -- Audit:
    createdAt        DATETIME2 NOT NULL
        CONSTRAINT DF_Event_CreatedAt DEFAULT SYSDATETIME(),
    updatedAt        DATETIME2 NOT NULL
        CONSTRAINT DF_Event_UpdatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Event_Venue
        FOREIGN KEY (venueCode) REFERENCES Venue(venueCode),
    CONSTRAINT FK_Event_SubmittedBy
        FOREIGN KEY (submittedBy) REFERENCES Student(studentNumber),
    CONSTRAINT FK_Event_ReviewedBy
        FOREIGN KEY (reviewedByStaffNumber) REFERENCES SDO(staffNumber)
);
GO

CREATE INDEX IX_Event_VenueAvailability
    ON Event(venueCode, eventDate, eventStartTime, eventEndTime, eventStatus);
GO

CREATE INDEX IX_Event_Status_UpdatedAt
    ON Event(eventStatus, updatedAt DESC);
GO

CREATE INDEX IX_Event_UpdatedAt
    ON Event(updatedAt DESC);
GO

-- ============================================================
-- 9. HOSTER (many-to-many: Event <-> Society, with primary host flag)
-- ============================================================
CREATE TABLE Hoster (
    eventID    VARCHAR(20) NOT NULL,
    societyID  VARCHAR(20) NOT NULL,
    isPrimary  BIT DEFAULT 0,
    PRIMARY KEY (eventID, societyID),
    FOREIGN KEY (eventID) REFERENCES Event(eventID),
    FOREIGN KEY (societyID) REFERENCES Society(societyID)
);
GO

CREATE INDEX IX_Hoster_Society_Event
    ON Hoster(societyID, eventID)
    INCLUDE (isPrimary);
GO

-- ============================================================
-- 9A. EVENT CO-HOST INVITATION
--     Collaboration requests sent to other societies when submitting
--     an event proposal. Mirrors POAEventCoHost pattern.
-- ============================================================
CREATE TABLE EventCoHostInvitation (
    invitationID             VARCHAR(36)  NOT NULL
        CONSTRAINT PK_EventCoHostInvitation PRIMARY KEY,
    eventID                  VARCHAR(20)  NOT NULL,
    invitedSocietyID         VARCHAR(20)  NOT NULL,
    invitedByStudentNumber   VARCHAR(20)  NOT NULL,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    respondedAt              DATETIME2    NULL,
    respondedByStudentNumber VARCHAR(20)  NULL,
    createdAt                DATETIME2    NOT NULL DEFAULT SYSDATETIME(),

    CONSTRAINT FK_EventCoHost_Event
        FOREIGN KEY (eventID) REFERENCES Event(eventID),
    CONSTRAINT FK_EventCoHost_Society
        FOREIGN KEY (invitedSocietyID) REFERENCES Society(societyID),
    CONSTRAINT FK_EventCoHost_InvitedBy
        FOREIGN KEY (invitedByStudentNumber) REFERENCES Student(studentNumber),
    CONSTRAINT CK_EventCoHost_Status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED'))
);
GO

CREATE INDEX IX_EventCoHost_Event
    ON EventCoHostInvitation(eventID, status);
GO

CREATE INDEX IX_EventCoHost_Society
    ON EventCoHostInvitation(invitedSocietyID, status);
GO

CREATE UNIQUE INDEX UX_EventCoHost_EventSociety
    ON EventCoHostInvitation(eventID, invitedSocietyID)
    WHERE status <> 'DECLINED';
GO

-- ============================================================
-- 10. RSVP (composite key: eventID + studentNumber)
-- ============================================================
CREATE TABLE RSVP (
    eventID         VARCHAR(20)  NOT NULL,
    studentNumber   VARCHAR(20)  NOT NULL,
    QRcodeTicket    VARCHAR(100) UNIQUE,
    scannedStatus   BIT          DEFAULT 0,
    scannedAt       DATETIME     NULL,
    rsvpCreatedAt   DATETIME2    NULL,
    checkInMethod   VARCHAR(20)  NULL,
    checkedInBy     VARCHAR(100) NULL,
    PRIMARY KEY (eventID, studentNumber),
    FOREIGN KEY (eventID) REFERENCES Event(eventID),
    FOREIGN KEY (studentNumber) REFERENCES Student(studentNumber)
);
GO

-- ============================================================
-- 11. EVENT OUTCOME (one per event+society, must match a Hoster row)
--     termStartDate identifies WHICH term the submitting executive
--     was serving in, completing the composite FK to Executive.
-- ============================================================
CREATE TABLE EventOutcome (
    eventID        VARCHAR(20)   NOT NULL,
    societyID      VARCHAR(20)   NOT NULL,
    studentNumber  VARCHAR(20)   NOT NULL,
    termStartDate  DATE          NOT NULL,
    description    VARCHAR(1000),
    suggestion     VARCHAR(1000),
    outcome        VARCHAR(500),
    PRIMARY KEY (eventID, societyID),
    FOREIGN KEY (eventID, societyID) REFERENCES Hoster(eventID, societyID),
    FOREIGN KEY (studentNumber, societyID, termStartDate)
        REFERENCES Executive(studentNumber, societyID, termStartDate)
);
GO

-- ============================================================
-- 12. EVENT FEEDBACK
--     Business rule: only students who attended (scannedStatus = 1)
--     may submit feedback. Enforced at the application layer.
-- ============================================================
CREATE TABLE EventFeedback (
    feedbackID          VARCHAR(20) PRIMARY KEY,
    studentNumber       VARCHAR(20) NOT NULL,
    eventID             VARCHAR(20) NOT NULL,
    rating              INT,
    description         VARCHAR(500),
    organizationRating  INT,
    venueRating         INT,
    contentRating       INT,
    wouldRecommend      BIT,
    highlights          VARCHAR(500),
    improvements        VARCHAR(500),
    submittedAt         DATETIME2,
    FOREIGN KEY (studentNumber) REFERENCES Student(studentNumber),
    FOREIGN KEY (eventID) REFERENCES Event(eventID)
);
GO

-- ============================================================
-- 12A. EXECUTIVE EVENT REPORT
--      Post-event report submitted by the hosting executive.
--      One report per (event, society) pair.
-- ============================================================
CREATE TABLE ExecutiveEventReport (
    reportID            VARCHAR(36)   NOT NULL
        CONSTRAINT PK_ExecutiveEventReport PRIMARY KEY,
    eventID             VARCHAR(20)   NOT NULL,
    societyID           VARCHAR(20)   NOT NULL,
    studentNumber       VARCHAR(20)   NOT NULL,
    expectations        VARCHAR(1000),
    expectationsMet     VARCHAR(20),       -- 'YES', 'PARTIALLY', 'NO'
    successAssessment   VARCHAR(20),       -- 'SUCCESS', 'PARTIAL', 'FAILURE'
    successReason       VARCHAR(1000),
    improvements        VARCHAR(1000),
    advice              VARCHAR(1000),
    attendeeCount       INT,
    overallRating       INT,
    additionalNotes     VARCHAR(1000),
    submittedAt         DATETIME2     NOT NULL DEFAULT SYSDATETIME(),

    CONSTRAINT FK_ExecReport_Event
        FOREIGN KEY (eventID) REFERENCES Event(eventID),
    CONSTRAINT FK_ExecReport_Society
        FOREIGN KEY (societyID) REFERENCES Society(societyID),
    CONSTRAINT FK_ExecReport_Student
        FOREIGN KEY (studentNumber) REFERENCES Student(studentNumber)
);
GO

CREATE UNIQUE INDEX UX_ExecReport_EventSociety
    ON ExecutiveEventReport(eventID, societyID);
GO

-- ============================================================
-- 13. TASK
--     targetType: 'INDIVIDUAL' or 'SOCIETY'
--     assignedTo is NULL when targetType = 'SOCIETY'
--     (society-wide assignment handled via TaskAllocation)
-- ============================================================
CREATE TABLE Task (
    taskID           VARCHAR(20) PRIMARY KEY,
    taskName         VARCHAR(100) NOT NULL,
    taskDescription  VARCHAR(500),
    issueDate        DATE DEFAULT GETDATE(),
    dueDate          DATE,
    completionDate   DATE NULL,
    status           VARCHAR(20),          -- e.g. 'PENDING','COMPLETE'
    assignedBy       VARCHAR(100) NOT NULL,
    targetType       VARCHAR(20) NOT NULL,  -- 'INDIVIDUAL' or 'SOCIETY'
    assignedTo       VARCHAR(20) NULL,      -- studentNumber, null if society-wide
    comment          VARCHAR(500),
    FOREIGN KEY (assignedBy) REFERENCES [User](email),
    FOREIGN KEY (assignedTo) REFERENCES Student(studentNumber)
);
GO

-- ============================================================
-- 14. TASK ALLOCATION (society-wide tasks)
-- ============================================================
CREATE TABLE TaskAllocation (
    taskID     VARCHAR(20) NOT NULL,
    societyID  VARCHAR(20) NOT NULL,
    PRIMARY KEY (taskID, societyID),
    FOREIGN KEY (taskID) REFERENCES Task(taskID),
    FOREIGN KEY (societyID) REFERENCES Society(societyID)
);
GO

-- ============================================================
-- 15. ANNOUNCEMENT
-- ============================================================
CREATE TABLE Announcement (
    announcementID  VARCHAR(20) PRIMARY KEY,
    targetType      VARCHAR(20),        -- enum: 'STUDENTS','MEMBERS','EXECUTIVES'
    societyID       VARCHAR(20) NULL,
    sentBy          VARCHAR(100) NOT NULL,
    subject         VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    datePosted      DATETIME DEFAULT GETDATE(),
    publishAt       DATETIME NULL,
    expireDate      DATETIME,
    isRemoved       BIT NOT NULL CONSTRAINT DF_Announcement_isRemoved DEFAULT 0,
    removedAt       DATETIME2 NULL,
    removedBy       VARCHAR(100) NULL,
    FOREIGN KEY (sentBy) REFERENCES [User](email),
    CONSTRAINT FK_Announcement_Society
        FOREIGN KEY (societyID) REFERENCES Society(societyID),
    CONSTRAINT FK_Announcement_RemovedBy
        FOREIGN KEY (removedBy) REFERENCES [User](email)
);
GO

CREATE INDEX IX_Announcement_Visibility
    ON Announcement(publishAt, expireDate, societyID, targetType);
GO

-- ============================================================
-- 16. NOTIFICATION
--     In-app notification feed. Actual delivery (email/push) is
--     handled at the application/service layer - this table only
--     tracks what should be shown to the user and read status.
-- ============================================================
CREATE TABLE Notification (
    notificationID  VARCHAR(20) PRIMARY KEY,
    recipientEmail  VARCHAR(100) NOT NULL,
    title           VARCHAR(100),
    message         VARCHAR(500),
    notifType       VARCHAR(50),        -- NotificationType enum
    relatedID       VARCHAR(100),       -- generic reference, including UUID-based application IDs
    isRead          BIT DEFAULT 0,
    createdAt       DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (recipientEmail) REFERENCES [User](email)
);
GO

-- ============================================================
-- 16A. INSTITUTIONAL MESSAGING
--      Executive groups, executive direct-message requests, and lazy SDO
--      society-leadership channels. Membership rows are ended with leftAt so
--      historical messages remain available without retaining active access.
-- ============================================================
CREATE TABLE Conversation (
    conversationID           VARCHAR(36)  NOT NULL,
    type                     VARCHAR(20)  NOT NULL,
    societyID                VARCHAR(20),
    title                    VARCHAR(150),
    status                   VARCHAR(20)  NOT NULL,
    contactReason            VARCHAR(500),
    initiatedByStudentNumber VARCHAR(20),
    createdAt                DATETIME2(7) NOT NULL,
    lastMessageAt            DATETIME2(7),
    CONSTRAINT PK_Conversation
        PRIMARY KEY (conversationID),
    CONSTRAINT CK_Conversation_Type
        CHECK (type IN ('SOCIETY_GROUP', 'DIRECT', 'SDO_SOCIETY')),
    CONSTRAINT CK_Conversation_Status
        CHECK (status IN ('ACTIVE', 'PENDING', 'REJECTED')),
    CONSTRAINT CK_Conversation_SocietyContext
        CHECK (
            (type = 'DIRECT' AND societyID IS NULL)
            OR (type IN ('SOCIETY_GROUP', 'SDO_SOCIETY')
                AND societyID IS NOT NULL)
        ),
    CONSTRAINT FK_Conversation_Society
        FOREIGN KEY (societyID) REFERENCES Society(societyID),
    CONSTRAINT FK_Conversation_Initiator
        FOREIGN KEY (initiatedByStudentNumber)
            REFERENCES Student(studentNumber)
);
GO

CREATE UNIQUE INDEX UX_Conversation_SocietyGroup
    ON Conversation(societyID)
    WHERE type = 'SOCIETY_GROUP';
GO

CREATE UNIQUE INDEX UX_Conversation_SdoSociety
    ON Conversation(societyID)
    WHERE type = 'SDO_SOCIETY';
GO

CREATE INDEX IX_Conversation_LastMessageAt
    ON Conversation(lastMessageAt DESC, conversationID);
GO

CREATE TABLE ConversationParticipant (
    conversationID VARCHAR(36) NOT NULL,
    studentNumber  VARCHAR(20) NOT NULL,
    role           VARCHAR(20) NOT NULL,
    lastReadAt     DATETIME2(7),
    joinedAt       DATETIME2(7) NOT NULL,
    leftAt         DATETIME2(7),
    CONSTRAINT PK_ConversationParticipant
        PRIMARY KEY (conversationID, studentNumber),
    CONSTRAINT CK_ConversationParticipant_Role
        CHECK (role IN ('MEMBER', 'ADMIN')),
    CONSTRAINT FK_ConversationParticipant_Conversation
        FOREIGN KEY (conversationID)
            REFERENCES Conversation(conversationID) ON DELETE CASCADE,
    CONSTRAINT FK_ConversationParticipant_Student
        FOREIGN KEY (studentNumber) REFERENCES Student(studentNumber)
);
GO

CREATE INDEX IX_ConversationParticipant_Student_Active
    ON ConversationParticipant(studentNumber, conversationID)
    INCLUDE (lastReadAt)
    WHERE leftAt IS NULL;
GO

CREATE TABLE ConversationSDOParticipant (
    conversationID VARCHAR(36) NOT NULL,
    sdoStaffNumber VARCHAR(20) NOT NULL,
    role           VARCHAR(20) NOT NULL,
    lastReadAt     DATETIME2(7),
    joinedAt       DATETIME2(7) NOT NULL,
    leftAt         DATETIME2(7),
    CONSTRAINT PK_ConversationSDOParticipant
        PRIMARY KEY (conversationID, sdoStaffNumber),
    CONSTRAINT CK_ConversationSDOParticipant_Role
        CHECK (role = 'SDO'),
    CONSTRAINT FK_ConversationSDOParticipant_Conversation
        FOREIGN KEY (conversationID)
            REFERENCES Conversation(conversationID) ON DELETE CASCADE,
    CONSTRAINT FK_ConversationSDOParticipant_SDO
        FOREIGN KEY (sdoStaffNumber) REFERENCES SDO(staffNumber)
);
GO

CREATE INDEX IX_ConversationSDOParticipant_Sdo_Active
    ON ConversationSDOParticipant(sdoStaffNumber, conversationID)
    INCLUDE (lastReadAt)
    WHERE leftAt IS NULL;
GO

CREATE TABLE Message (
    messageID              VARCHAR(36)   NOT NULL,
    conversationID         VARCHAR(36)   NOT NULL,
    senderStudentNumber    VARCHAR(20),
    senderSdoStaffNumber   VARCHAR(20),
    body                   VARCHAR(2000) NOT NULL,
    replyToMessageID       VARCHAR(36),
    status                 VARCHAR(20)   NOT NULL,
    createdAt              DATETIME2(7)  NOT NULL,
    editedAt               DATETIME2(7),
    CONSTRAINT PK_Message
        PRIMARY KEY (messageID),
    CONSTRAINT CK_Message_Status
        CHECK (status IN ('VISIBLE', 'DELETED')),
    CONSTRAINT CK_Message_OneSender
        CHECK (
            (senderStudentNumber IS NOT NULL AND senderSdoStaffNumber IS NULL)
            OR (senderStudentNumber IS NULL AND senderSdoStaffNumber IS NOT NULL)
        ),
    CONSTRAINT FK_Message_Conversation
        FOREIGN KEY (conversationID)
            REFERENCES Conversation(conversationID) ON DELETE CASCADE,
    CONSTRAINT FK_Message_StudentSender
        FOREIGN KEY (senderStudentNumber) REFERENCES Student(studentNumber),
    CONSTRAINT FK_Message_SdoSender
        FOREIGN KEY (senderSdoStaffNumber) REFERENCES SDO(staffNumber),
    CONSTRAINT FK_Message_ReplyTo
        FOREIGN KEY (replyToMessageID) REFERENCES Message(messageID)
);
GO

CREATE INDEX IX_Message_Conversation_CreatedAt
    ON Message(conversationID, createdAt DESC, messageID);
GO

CREATE TABLE MessageMention (
    mentionID           VARCHAR(36) NOT NULL,
    messageID           VARCHAR(36) NOT NULL,
    mentionType         VARCHAR(20) NOT NULL,
    targetStudentNumber VARCHAR(20),
    targetEventID       VARCHAR(20),
    CONSTRAINT PK_MessageMention
        PRIMARY KEY (mentionID),
    CONSTRAINT CK_MessageMention_Type
        CHECK (mentionType IN ('USER', 'EVENT')),
    CONSTRAINT CK_MessageMention_Target
        CHECK (
            (mentionType = 'USER'
                AND targetStudentNumber IS NOT NULL
                AND targetEventID IS NULL)
            OR (mentionType = 'EVENT'
                AND targetStudentNumber IS NULL
                AND targetEventID IS NOT NULL)
        ),
    CONSTRAINT FK_MessageMention_Message
        FOREIGN KEY (messageID)
            REFERENCES Message(messageID) ON DELETE CASCADE,
    CONSTRAINT FK_MessageMention_Student
        FOREIGN KEY (targetStudentNumber) REFERENCES Student(studentNumber),
    CONSTRAINT FK_MessageMention_Event
        FOREIGN KEY (targetEventID) REFERENCES Event(eventID)
);
GO

CREATE INDEX IX_MessageMention_Message
    ON MessageMention(messageID);
GO

-- ============================================================
-- 17. BUDGET REQUEST
--     One row = one line item for one (event, society) pair.
--     Composite FK -> Hoster(eventID, societyID).
-- ============================================================
CREATE TABLE BudgetRequest (
    budgetRequestID         VARCHAR(20)    PRIMARY KEY,
    eventID                 VARCHAR(20)    NOT NULL,
    societyID               VARCHAR(20)    NOT NULL,
    requestingStudentNumber VARCHAR(20)    NOT NULL,
    name                    VARCHAR(100)   NOT NULL,
    description             VARCHAR(500),
    amount                  DECIMAL(10,2)  NOT NULL,
    type                    VARCHAR(30)    NOT NULL,        -- BudgetRequestType enum
    typeSpecification       VARCHAR(100),                   -- required when type = OTHER
    status                  VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    requestDate             DATE,
    lastUpdatedDate         DATE,
    reviewedByStaffNumber   VARCHAR(20),
    reviewNotes             VARCHAR(500),
    poaID                   VARCHAR(20),                    -- reserved for future POA FK
    payoutStatus            VARCHAR(20)    NOT NULL         -- BudgetRequestPayoutStatus enum
        CONSTRAINT DF_BudgetRequest_PayoutStatus DEFAULT 'NOT_PROCESSED',
    processedAt             DATETIME2,                      -- set when the SDO releases funds (B300)

    CONSTRAINT CK_BudgetRequest_PayoutStatus
        CHECK (payoutStatus IN ('NOT_PROCESSED', 'PROCESSED')),
    CONSTRAINT FK_BudgetRequest_Hoster
        FOREIGN KEY (eventID, societyID)
            REFERENCES Hoster(eventID, societyID),
    CONSTRAINT FK_BudgetRequest_Student
        FOREIGN KEY (requestingStudentNumber)
            REFERENCES Student(studentNumber),
    CONSTRAINT FK_BudgetRequest_SDO
        FOREIGN KEY (reviewedByStaffNumber)
            REFERENCES SDO(staffNumber)
);
GO

-- ============================================================
-- 18. FUND TRANSACTION
--     Immutable audit trail of every currentBalance movement.
--     direction: CREDIT (+) or DEBIT (-)
-- ============================================================
CREATE TABLE FundTransaction (
    transactionID       VARCHAR(20)    PRIMARY KEY,
    societyID           VARCHAR(20)    NOT NULL,
    amount              DECIMAL(10,2)  NOT NULL,
    direction           VARCHAR(10)    NOT NULL,        -- CREDIT or DEBIT
    reason              VARCHAR(30)    NOT NULL,        -- FundTransactionReason enum
    description         VARCHAR(500),
    reference           VARCHAR(100),
    balanceBefore       DECIMAL(10,2),
    balanceAfter        DECIMAL(10,2),
    transactionDate     DATETIME2      NOT NULL,
    createdBy           VARCHAR(100),                   -- email or 'SYSTEM'

    CONSTRAINT FK_FundTransaction_Society
        FOREIGN KEY (societyID)
            REFERENCES Society(societyID)
);
GO

-- ============================================================
-- 19. AUDIT LOG
--     Records important changes made within the SocietyCentral system.
--     Each record represents one field that was changed.
-- ============================================================
CREATE TABLE AuditLog (
    auditID       INT IDENTITY(1,1) PRIMARY KEY,
    staffNumber   VARCHAR(20)  NOT NULL,
    entityName    VARCHAR(50)  NOT NULL,
    operation     VARCHAR(20)  NOT NULL,
    fieldChanged  VARCHAR(50)  NOT NULL,
    oldValue      VARCHAR(255),
    newValue      VARCHAR(255),
    changedBy     VARCHAR(100) NOT NULL,
    changedDate   DATETIME     NOT NULL DEFAULT GETDATE(),
    reason        VARCHAR(255),
    CONSTRAINT FK_AuditLog_SDO
        FOREIGN KEY (staffNumber)
            REFERENCES SDO(staffNumber)
);
GO

-- ============================================================
-- 20. POA (Plan of Action)
--     One per society per year. Unique constraint enforced.
-- ============================================================
CREATE TABLE POA (
    poaID                       VARCHAR(36)   PRIMARY KEY,  -- UUID
    societyID                   VARCHAR(20)   NOT NULL,
    submittedByStudentNumber    VARCHAR(20),
    year                        INT           NOT NULL,
    status                      VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    submittedDate               DATE,
    reviewedByStaffNumber       VARCHAR(20),
    reviewNotes                 VARCHAR(1000),
    createdAt                   DATETIME2     NOT NULL,
    lastUpdatedAt               DATETIME2,

    CONSTRAINT FK_POA_Society
        FOREIGN KEY (societyID) REFERENCES Society(societyID),
    CONSTRAINT FK_POA_Student
        FOREIGN KEY (submittedByStudentNumber) REFERENCES Student(studentNumber),
    CONSTRAINT FK_POA_SDO
        FOREIGN KEY (reviewedByStaffNumber) REFERENCES SDO(staffNumber),
    CONSTRAINT UK_POA_society_year
        UNIQUE (societyID, year)
);
GO

-- ============================================================
-- 21. POA EVENT
--     Budget rule: totalIncome must equal totalExpenses (enforced in app layer).
-- ============================================================
CREATE TABLE POAEvent (
    poaEventID                  VARCHAR(36)   PRIMARY KEY,  -- UUID
    poaID                       VARCHAR(36)   NOT NULL,
    organizationName            VARCHAR(200),
    month                       VARCHAR(20),
    theme                       VARCHAR(200),
    programName                 VARCHAR(100),
    eventDate                   VARCHAR(30),               -- accepts "TBC"
    venue                       VARCHAR(100),
    attendance                  VARCHAR(20),
    purpose                     VARCHAR(1000),
    projectedIncomeFromAccount  DECIMAL(10,2) DEFAULT 0,
    projectedIncomeSponsorship  DECIMAL(10,2) DEFAULT 0,
    expensePromoMaterial        DECIMAL(10,2) DEFAULT 0,
    expenseDataAirtime          DECIMAL(10,2) DEFAULT 0,
    expenseGifts                DECIMAL(10,2) DEFAULT 0,
    expenseOther                DECIMAL(10,2) DEFAULT 0,
    expenseOtherSpecification   VARCHAR(200),
    sdoEventComment             VARCHAR(500),
    sortOrder                   INT           DEFAULT 0,

    CONSTRAINT FK_POAEvent_POA
        FOREIGN KEY (poaID) REFERENCES POA(poaID)
);
GO

-- ============================================================
-- 22. POA EVENT CO-HOST
--     ON DELETE CASCADE so deleting a POAEvent auto-removes its co-hosts.
-- ============================================================
CREATE TABLE POAEventCoHost (
    coHostID            VARCHAR(36)   PRIMARY KEY,  -- UUID
    poaEventID          VARCHAR(36)   NOT NULL,
    invitedSocietyID    VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    respondedAt         DATETIME2,

    CONSTRAINT FK_CoHost_POAEvent
        FOREIGN KEY (poaEventID) REFERENCES POAEvent(poaEventID)
            ON DELETE CASCADE,
    CONSTRAINT FK_CoHost_Society
        FOREIGN KEY (invitedSocietyID) REFERENCES Society(societyID)
);
GO

-- ============================================================
-- 23. PASSWORD RESET TOKEN
--     One-time token for password reset. Expires after 1 hour.
-- ============================================================
CREATE TABLE PasswordResetToken (
    token       VARCHAR(36)   PRIMARY KEY,   -- UUID
    email       VARCHAR(100)  NOT NULL,
    expiresAt   DATETIME2     NOT NULL,
    used        BIT           NOT NULL DEFAULT 0,

    CONSTRAINT FK_PasswordResetToken_User
        FOREIGN KEY (email) REFERENCES [User](email)
);
GO

-- ============================================================
-- 24. USER PROFILE PICTURE
--     Database-backed authenticated profile picture storage.
--     Stores the binary image data directly.
-- ============================================================
CREATE TABLE UserProfilePicture (
    userEmail        VARCHAR(100)  NOT NULL
        CONSTRAINT PK_UserProfilePicture PRIMARY KEY,
    imageData        VARBINARY(MAX) NOT NULL,
    contentType      VARCHAR(100)  NOT NULL,
    originalFileName VARCHAR(255)  NULL,
    fileSize         BIGINT        NOT NULL,
    updatedAt        DATETIME2     NOT NULL,
    CONSTRAINT FK_UserProfilePicture_User
        FOREIGN KEY (userEmail) REFERENCES [User](email)
            ON DELETE CASCADE
);
GO

-- ============================================================
-- 25. BANK ACCOUNT
--     A society's banking details for fund payouts (B300).
--     Society 1 ── 0..1 BankAccount (UNIQUE societyID).
--     verificationStatus is managed by the SDO before any payout.
--     proofOfAccountDocument stores the uploaded proof binary.
-- ============================================================
CREATE TABLE BankAccount (
    bankAccountID          VARCHAR(36)    NOT NULL
        CONSTRAINT PK_BankAccount PRIMARY KEY,     -- UUID
    societyID              VARCHAR(20)    NOT NULL,
    bankName               VARCHAR(100)   NOT NULL,
    accountHolderName      VARCHAR(150)   NOT NULL,
    accountNumber          VARCHAR(30)    NOT NULL,
    branchCode             VARCHAR(20)    NOT NULL,
    accountType            VARCHAR(30)    NOT NULL,   -- CHEQUE, SAVINGS, TRANSMISSION
    verificationStatus     VARCHAR(20)    NOT NULL    -- BankAccountVerificationStatus enum
        CONSTRAINT DF_BankAccount_VerificationStatus DEFAULT 'UNVERIFIED',
    verifiedBy             VARCHAR(20),               -- SDO staffNumber who verified
    verifiedAt             DATETIME2,
    lastUpdatedBy          VARCHAR(100),              -- email of last editor
    createdAt              DATETIME2      NOT NULL,
    updatedAt              DATETIME2      NOT NULL,
    proofOfAccountDocument VARBINARY(MAX),

    CONSTRAINT UQ_BankAccount_Society
        UNIQUE (societyID),
    CONSTRAINT CK_BankAccount_VerificationStatus
        CHECK (verificationStatus IN ('UNVERIFIED', 'VERIFIED', 'REJECTED')),
    CONSTRAINT FK_BankAccount_Society
        FOREIGN KEY (societyID) REFERENCES Society(societyID)
);
GO

-- Supports the SDO's "accounts awaiting verification" view.
CREATE INDEX IX_BankAccount_VerificationStatus
    ON BankAccount(verificationStatus);
GO
