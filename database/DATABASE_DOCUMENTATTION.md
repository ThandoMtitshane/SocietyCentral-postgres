 # SocietyCentral — Database Documentation

> **Engine:** Microsoft SQL Server 17
> **Database:** `SocietyCentral`
> **Last updated:** September 2026

---

## 1. Overview

~40 tables across eight domains. Enums are stored as VARCHAR (the enum **name string**, not an integer). The schema is defined in `SocietyCentral_Schema.sql` in 25 numbered sections.

| Domain | Tables |
|---|---|
| Users & Identity | `User`, `Student`, `SDO`, `PasswordResetToken` |
| Academic / NMU reference data | `CampusReference`, `Faculty`, `SchoolReference`, `ProgrammeReference`, `ProgrammeCampus`, `ResidenceReference`, `AccommodationType`, `OffCampusProperty`, `OffCampusAccreditation`, `Venue` |
| Societies | `Society`, `SocietyMedia`, `SocietyHighlight`, `Executive`, `SocietyMember`, `MembershipApplication` |
| Events | `Event`, `Hoster`, `EventCoHostInvitation`, `RSVP`, `EventOutcome`, `EventFeedback`, `ExecutiveEventReport` |
| Operations | `Task`, `TaskAllocation`, `Announcement`, `Notification` |
| Messaging | `Conversation`, `ConversationParticipant`, `ConversationSDOParticipant`, `Message`, `MessageMention` |
| Financial | `BudgetRequest`, `FundTransaction`, `BankAccount`, `AuditLog` |
| POA | `POA`, `POAEvent`, `POAEventCoHost` |

> The academic reference tables mirror **NMU** catalogue data (campuses, faculties, schools, programmes, residences, venues). They are seeded reference data — application entities point at these codes rather than free-text.

---

## 2. Setup

Run in this order (all scripts live in `/database`):

```
1. Drop_societycentral.sql       force-drops the DB even with active connections
2. SocietyCentral_Schema.sql     creates all ~40 tables (25 sections)
3. demoData.sql                  PART A: academic/NMU reference data
                                 PART B: dev accounts + sample societies/events
4. Set50Student.sql              bulk-loads 50 demo students (kept separate)
```

`demoData.sql` is the single consolidated seed file — all sample data was merged into it, **except** `Set50Student.sql`, which stays on its own so the 50-student bulk load can be run (or skipped) independently.

`spring.jpa.hibernate.ddl-auto=validate` means the app refuses to start if the entities and DB schema disagree. Hibernate never creates or alters tables — always re-run these scripts after any schema change and mirror the change in the entities.

---

## 3. ER Summary

```
User (email PK)
 ├── Student (studentNumber PK, email FK)
 │    ├── Executive (studentNumber+societyID+termStartDate PK)
 │    ├── SocietyMember (studentNumber+societyID PK)
 │    ├── MembershipApplication (applicationID PK)
 │    └── RSVP (eventID+studentNumber PK)
 └── SDO (staffNumber PK, email FK)
      └── Society (sdoStaffNumber FK → SDO)

PasswordResetToken (token UUID PK, email FK → User)

Academic reference (NMU catalogues, referenced by code):
 CampusReference ← ProgrammeCampus → ProgrammeReference
 Faculty ← SchoolReference ; CampusReference ← ResidenceReference
 OffCampusProperty ← OffCampusAccreditation ; Venue

Society → SocietyMedia / SocietyHighlight (cover image → SocietyMedia)
Society → Hoster → Event
Hoster (eventID+societyID PK, isPrimary BIT)
Event → EventCoHostInvitation (mirrors POAEventCoHost)
BudgetRequest / EventOutcome (FK → Hoster composite)
Event → EventFeedback ; Hoster → ExecutiveEventReport

Messaging:
 Conversation (conversationID UUID PK)
  ├── ConversationParticipant (studentNumber)      -- students/executives
  ├── ConversationSDOParticipant (sdoStaffNumber)  -- SDOs
  └── Message (messageID UUID PK) → MessageMention

POA (poaID UUID PK, societyID FK, UNIQUE societyID+year)
 └── POAEvent (poaEventID UUID PK, poaID FK)
      └── POAEventCoHost (coHostID UUID PK, poaEventID FK ON DELETE CASCADE)

FundTransaction (transactionID PK, societyID FK → Society)
BankAccount (societyID FK → Society) ; AuditLog (staffNumber)
```

> §4 below documents the core tables in detail. The newer tables — academic reference catalogues, `SocietyMedia`/`SocietyHighlight`, `MembershipApplication`, `Venue`, `EventCoHostInvitation`, `ExecutiveEventReport`, the messaging set, `BankAccount`, and `AuditLog` — follow the same conventions (VARCHAR(36) UUID PKs for newer aggregates, enum-name VARCHAR columns, composite FKs into `Hoster`/`Society`). See `SocietyCentral_Schema.sql` for their exact column definitions.

---

## 4. Table Reference

### `[User]`
> Bracketed,  USER is a reserved keyword in SQL Server.

| Column | Type | Notes |
|---|---|---|
| `email` | VARCHAR(100) PK | Natural login key |
| `title` | VARCHAR(10) | Mr, Ms, Dr |
| `firstName` | VARCHAR(50) NOT NULL | Stored here for ALL roles including SDO |
| `lastName` | VARCHAR(50) NOT NULL | Stored here for ALL roles including SDO |
| `userType` | VARCHAR(20) NOT NULL | STUDENT or SDO |
| `campus` | VARCHAR(30) | Campus enum |
| `passwordHash` | VARCHAR(255) NOT NULL | BCrypt |
| `profilePictureURL` | VARCHAR(255) | nullable |

---

### `Student`

| Column | Type | Notes |
|---|---|---|
| `studentNumber` | VARCHAR(20) PK | |
| `email` | VARCHAR(100) FK→User | |
| `course` | VARCHAR(100) | |
| `level` | VARCHAR(20) | e.g. 3rd Year |
| `nationality` | VARCHAR(50) | |
| `residence` | VARCHAR(100) | |
| `school` | VARCHAR(60) | School enum. **Faculty DERIVED from this,  not stored.** |
| `cellPhoneNumber` | VARCHAR(20) | |

---

### `SDO`

| Column | Type | Notes |
|---|---|---|
| `staffNumber` | VARCHAR(20) PK | |
| `email` | VARCHAR(100) FK→User UNIQUE | |
| `officeNumber` | VARCHAR(20) | |
| `phoneExtension` | VARCHAR(10) | |

> **SDO has no firstName/lastName.** Those live in `User`.
> To get SDO's name: `SELECT u.firstName, u.lastName FROM [User] u WHERE u.email = sdo.email`
> In Java: `userRepository.findById(sdo.getEmail()).map(u -> u.getFirstName() + " " + u.getLastName())`

---

### `PasswordResetToken`

| Column | Type | Notes |
|---|---|---|
| `token` | VARCHAR(36) PK | UUID,  one-time use |
| `email` | VARCHAR(100) FK→User | |
| `expiresAt` | DATETIME2 | 1 hour from creation |
| `used` | BIT DEFAULT 0 | Set to 1 after successful reset,  prevents replay |

---

### `Society`

| Column | Type | Notes |
|---|---|---|
| `societyID` | VARCHAR(20) PK | |
| `societyName` | VARCHAR(100) NOT NULL | |
| `acronym` | VARCHAR(10) | |
| `sdoStaffNumber` | VARCHAR(20) FK→SDO NOT NULL | |
| `numberOfMembers` | INT DEFAULT 0 | Denormalised,  updated on join/leave |
| `activeStatus` | BIT DEFAULT 0 | |
| `isFlagged` | BIT DEFAULT 0 | Set by SDO |
| `description` | VARCHAR(500) | |
| `vision` | VARCHAR(500) | |
| `mission` | VARCHAR(500) | |
| `yearEstablished` | INT | |
| `contactNumber` | VARCHAR(20) | |
| `email` | VARCHAR(100) | Society contact,  NOT a User FK |
| `logoUrl` | VARCHAR(255) | |
| `rating` | FLOAT | |
| `membershipFee` | DECIMAL(10,2) DEFAULT 0 | Auto-added to currentBalance when member joins |
| `annualBudgetAllocation` | DECIMAL(10,2) DEFAULT 0 | **SDO-only field** |
| `currentBalance` | DECIMAL(10,2) DEFAULT 0 | **Via FundTransactionService only,  never set directly** |
| `campus` | VARCHAR(30) | Campus enum |
| `societyType` | VARCHAR(40) | SocietyType enum |
| `school` | VARCHAR(60) DEFAULT 'NONE' | Custom setter auto-fills faculty |
| `faculty` | VARCHAR(60) DEFAULT 'NONE' | Faculty enum |
| `tiktokURL`, `facebookURL`, `instagramURL` | VARCHAR(200) | Social links |

---

### `Executive`
Composite PK: `(studentNumber, societyID, termStartDate)`

| Column | Notes |
|---|---|
| `termEndDate` DATE | NULL = currently serving |
| `position` VARCHAR(50) | e.g. President, Secretary, Treasurer |

Active: `termEndDate IS NULL OR termEndDate > GETDATE()`

---

### `SocietyMember`
Composite PK: `(studentNumber, societyID)`
Fields: `joinDate`, `membershipStatus` (ACTIVE, PENDING)

---

### `Event`
PK: `eventID`. Status: `PROPOSED → APPROVED → PUBLISHED`.
Fields: `eventName`, `eventDate`, `eventTime`, `eventVenue`, `eventCampus`,
`eventDescription`, `eventStatus`, `rsvpOpenDate`, `rsvpCloseDate`,
`eventLimit`, `attendingType`, `overallRating`, `imageUrl`

---

### `Hoster`
Composite PK: `(eventID, societyID)`. `isPrimary BIT`,  exactly one row per event = 1.
Enables co-hosting (an event belongs to multiple societies).

---

### `RSVP`
Composite PK: `(eventID, studentNumber)`.
Key fields: `qrCodeTicket VARCHAR(100) UNIQUE`, `scannedStatus BIT DEFAULT 0`, `scannedAt DATETIME2`

---

### `EventOutcome`
Composite PK: `(eventID, societyID)` → FK → Hoster. One per hosting society per event.

---

### `EventFeedback`
PK: `feedbackID`. Eligibility (scannedStatus=1) enforced at app layer,  not by DB constraint.

---

### `Task`
PK: `taskID`. `targetType`: INDIVIDUAL (assignedTo FK→Student) or SOCIETY (via TaskAllocation, assignedTo NULL).
Status: PENDING, IN_PROGRESS, COMPLETE, CANCELLED

---

### `TaskAllocation`
Composite PK: `(taskID, societyID)`,  only for SOCIETY tasks.

---

### `Announcement`
PK: `announcementID`. `targetType`: ALL_STUDENTS, SOCIETY_MEMBERS, EXECUTIVES, SDO.

---

### `Notification`
PK: `notificationID`. `recipientEmail FK→User`. `isRead BIT DEFAULT 0`. In-app only.

---

### `BudgetRequest`

| Column | Notes |
|---|---|
| `budgetRequestID` VARCHAR(20) PK | |
| `eventID` + `societyID` | Composite FK → Hoster(eventID, societyID) |
| `requestingStudentNumber` FK→Student | Who submitted |
| `amount` DECIMAL(10,2) NOT NULL | |
| `type` VARCHAR(30) | BudgetRequestType enum |
| `typeSpecification` VARCHAR(100) | Required when type=OTHER |
| `status` VARCHAR(30) DEFAULT 'PENDING' | BudgetRequestStatus enum |
| `reviewedByStaffNumber` FK→SDO | nullable |
| `reviewNotes` VARCHAR(500) | |
| `poaID` VARCHAR(20) | Reserved,  no FK constraint yet |

---

### `FundTransaction`

| Column | Notes |
|---|---|
| `transactionID` VARCHAR(20) PK | |
| `societyID` FK→Society | |
| `amount` DECIMAL(10,2) NOT NULL | Always positive |
| `direction` VARCHAR(10) | CREDIT (+) or DEBIT (-) |
| `reason` VARCHAR(30) | FundTransactionReason enum |
| `description` VARCHAR(500) | Human-readable explanation |
| `reference` VARCHAR(100) | Context: studentNumber / budgetRequestID / etc. |
| `balanceBefore`, `balanceAfter` DECIMAL(10,2) | Immutable audit snapshots |
| `transactionDate` DATETIME2 NOT NULL | |
| `createdBy` VARCHAR(100) | email or 'SYSTEM' |

---

### `POA`

| Column | Notes |
|---|---|
| `poaID` VARCHAR(36) PK | UUID |
| `societyID` VARCHAR(20) FK→Society NOT NULL | |
| `submittedByStudentNumber` VARCHAR(20) FK→Student | Last person to save/submit |
| `year` INT NOT NULL | |
| `status` VARCHAR(30) DEFAULT 'DRAFT' | POAStatus enum |
| `submittedDate` DATE | Set when status→SUBMITTED |
| `reviewedByStaffNumber` VARCHAR(20) FK→SDO | |
| `reviewNotes` VARCHAR(1000) | SDO overall comments or revision notes |
| `createdAt` DATETIME2 NOT NULL | |
| `lastUpdatedAt` DATETIME2 | Reset on every save + after reminder sent |

**UNIQUE CONSTRAINT: `UK_POA_society_year (societyID, year)`**,  one POA per society per year.

---

### `POAEvent`

| Column | Notes |
|---|---|
| `poaEventID` VARCHAR(36) PK | UUID |
| `poaID` VARCHAR(36) FK→POA | |
| `organizationName` VARCHAR(200) | e.g. "CS Society x Law Society" |
| `month` VARCHAR(20) | "March", "TBC", etc. |
| `theme` VARCHAR(200) | |
| `programName` VARCHAR(100) | Event/programme name |
| `eventDate` VARCHAR(30) | VARCHAR,  accepts "TBC" |
| `venue` VARCHAR(100) | |
| `attendance` VARCHAR(20) | POAAttendance enum |
| `purpose` VARCHAR(1000) | |
| `projectedIncomeFromAccount` DECIMAL(10,2) DEFAULT 0 | |
| `projectedIncomeSponsorship` DECIMAL(10,2) DEFAULT 0 | |
| `expensePromoMaterial` DECIMAL(10,2) DEFAULT 0 | |
| `expenseDataAirtime` DECIMAL(10,2) DEFAULT 0 | |
| `expenseGifts` DECIMAL(10,2) DEFAULT 0 | |
| `expenseOther` DECIMAL(10,2) DEFAULT 0 | |
| `expenseOtherSpecification` VARCHAR(200) | Required when expenseOther > 0 |
| `sdoEventComment` VARCHAR(500) | SDO per-event revision comment |
| `sortOrder` INT DEFAULT 0 | Preserves exec ordering |

**Budget rule (app-layer enforced on SUBMIT):**
`(fromAccount + sponsorship) == (promoMaterial + dataAirtime + gifts + other)`

---

### `POAEventCoHost`

| Column | Notes |
|---|---|
| `coHostID` VARCHAR(36) PK | UUID |
| `poaEventID` VARCHAR(36) FK→POAEvent | **ON DELETE CASCADE** |
| `invitedSocietyID` VARCHAR(20) FK→Society | |
| `status` VARCHAR(20) DEFAULT 'PENDING' | POACoHostStatus enum |
| `respondedAt` DATETIME2 | |

**ON DELETE CASCADE:** Deleting a `POAEvent` automatically removes all its co-host rows. The service also calls `coHostRepository.deleteByPoaEventID()` explicitly before deleting events, for extra clarity.

---

## 5. Enum Columns

| Column | Table | Valid values |
|---|---|---|
| `userType` | User | STUDENT, SDO |
| `campus` | User, Society, Event | SOUTH_CAMPUS, NORTH_CAMPUS, SECOND_AVENUE_CAMPUS, BIRD_STREET_CAMPUS, MISSIONVALE_CAMPUS, OCEAN_SCIENCES_CAMPUS, GEORGE_CAMPUS |
| `school` | Student, Society | 31 values,  see School.java. Each carries `getFaculty()`. |
| `faculty` | Society | BUSINESS_AND_ECONOMIC_SCIENCES, EDUCATION, ENGINEERING_BUILT_ENVIRONMENT_AND_TECHNOLOGY, HEALTH_SCIENCES, HUMANITIES, LAW, SCIENCE, NONE |
| `societyType` | Society | ACADEMIC, RELIGIOUS_AND_FAITH_BASED, CULTURAL_AND_HERITAGE, COMMUNITY_ENGAGEMENT_AND_VOLUNTEER, POLITICAL_AND_LEADERSHIP, PROFESSIONAL_DEVELOPMENT, ENTREPRENEURSHIP_AND_BUSINESS, ARTS_AND_CREATIVE, ENVIRONMENTAL_AND_SUSTAINABILITY, HEALTH_AND_WELLNESS, SPORTS_AND_RECREATION, TECHNOLOGY_AND_INNOVATION, SPECIAL_INTEREST, RESIDENCE_BASED, INTERNATIONAL_STUDENT, OTHER |
| `eventStatus` | Event | PROPOSED, APPROVED, PUBLISHED |
| `attendingType` | Event, RSVP | MEMBERS, EVERY_STUDENT |
| `targetType` | Announcement | ALL_STUDENTS, SOCIETY_MEMBERS, EXECUTIVES, SDO |
| `targetType` | Task | INDIVIDUAL, SOCIETY |
| `status` | Task | PENDING, IN_PROGRESS, COMPLETE, CANCELLED |
| `type` | BudgetRequest | MEALS, PROMO_MATERIAL, TRANSPORTATION, VENUE_AND_EQUIPMENT, OTHER |
| `status` | BudgetRequest | PENDING, APPROVED, CONDITIONALLY_APPROVED, REJECTED |
| `direction` | FundTransaction | CREDIT, DEBIT |
| `reason` | FundTransaction | ANNUAL_ALLOCATION, MEMBERSHIP_FEE, BUDGET_APPROVED, MANUAL_ADJUSTMENT, OTHER |
| `status` | POA | DRAFT, SUBMITTED, APPROVED, REVISION_REQUESTED |
| `attendance` | POAEvent | MEMBERS_ONLY, EVERY_STUDENT |
| `status` | POAEventCoHost | PENDING, ACCEPTED, DECLINED |

---

## 6. Key Design Decisions

### Faculty not stored on Student
Always derived from `school.getFaculty()`. Storing separately would risk disagreement.

### SDO name in User, not SDO
`SDO` entity has no name fields,  only `staffNumber`, `email`, `officeNumber`, `phoneExtension`. Always join through `User` to get a name.

### Hoster junction (not Event.societyID)
One event can be co-hosted by multiple societies. A direct FK on Event only supports one host. `Hoster(eventID, societyID)` with `isPrimary BIT` supports multiple hosts.

### BudgetRequest FK → Hoster composite
Ensures a budget request can only exist for a valid (event, society) co-hosting pair. A society cannot request budget for an event they are not hosting.

### POA UUID PKs
`POA`, `POAEvent`, `POAEventCoHost` use `VARCHAR(36)` UUID PKs generated in Java via `UUID.randomUUID().toString()`. Reasons: no count query needed, no race conditions under concurrent inserts, safe for 1000+ POAs.

### POAEvent smart sync (no delete-all-recreate)
`POAService.syncEvents()` diffs incoming events against DB rows:
- Matched by `poaEventID` → UPDATE in place (no FK violation)
- New → INSERT
- Removed → `deleteByPoaEventID()` (clears co-hosts), then `deleteById(event)`

`ON DELETE CASCADE` on `FK_CoHost_POAEvent` is an extra DB-level safety net.

### PasswordResetToken always returns 200
The forgot-password endpoint never reveals whether an email exists. Prevents user enumeration attacks. The token is generated and email sent only if the email is found,  but the HTTP response is always identical.

---

## 7. Development Seed Data

Seeded by `demoData.sql` (PART A = NMU academic reference data, PART B = dev accounts + sample societies/events). `Set50Student.sql` adds 50 extra demo students separately.

Default password for all seeded accounts: **`DevPassword123`**

| Email | Role | Dashboard |
|---|---|---|
| `student1@societycentral.com` | Student | Student Dashboard |
| `student2@societycentral.com` | Student | Student Dashboard |
| `s229878873@mandela.ac.za` | Student (exec,  President of SOC001) | Executive Dashboard |
| `exec2@societycentral.com` | Student (exec,  Secretary of SOC001) | Executive Dashboard |
| `mtitshane.tj@outlook.com` | SDO | SDO Dashboard |
| `sdo2@societycentral.com` | SDO | SDO Dashboard |

**Societies:**
- `SOC001`,  Society One (TECHNOLOGY_AND_INNOVATION),  assigned to sdo1
- `SOC002`,  Society Two (ACADEMIC),  assigned to sdo1
- `SOC003`,  Society Three (CULTURAL_AND_HERITAGE),  assigned to sdo2

exec1 (President) and exec2 (Secretary) are both active executives of SOC001.