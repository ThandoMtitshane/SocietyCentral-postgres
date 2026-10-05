# SocietyCentral — Backend Documentation

> **Stack:** Java 25, Spring Boot 4.1.0, Hibernate/JPA, SQL Server 17, Maven
> **Architecture:** Modular monolith — one Spring Boot app, feature-organised packages
> **Dev URL:** http://localhost:8080
> **Base package:** `com.societycentral`
> **Last updated:** September 2026

---

## 1. Project Structure

The backend is a single Spring Boot application (a modular monolith). It exposes ~34 `@RestController` classes over ~70 services, ~88 model files (entities + enums), and maps to ~40 SQL Server tables. Layout under `src/main/java/com/societycentral/`:

```
com/societycentral/
├── config/       SecurityConfig, CorsConfig, WebSocketConfig (STOMP), Resend/email config
├── controller/   34 @RestControllers (see §7)
├── dto/
│   ├── request/  *RequestDTO — inbound payloads
│   └── response/ ApiResponse envelope + *DTO / *View projections
├── exception/    GlobalExceptionHandler
├── model/        entities + enums (see §4)
├── repository/   Spring Data JPA repositories (one per aggregate)
├── security/     UserPrincipal, CustomUserDetailsService, JwtUtil, JwtAuthFilter
└── service/      domain services incl. EmailService, FundTransactionService,
                  MessagingService, NotificationService, ExecutiveSocietyResolver
```

Resources: `src/main/resources/application.properties` (see §13). Config beans wire CORS, JWT security, and the STOMP WebSocket broker.

---

## 2. Security & Authentication

JWT stateless auth. Every endpoint except `/api/auth/**` (and a few explicitly public read endpoints) requires `Authorization: Bearer <token>`. `JwtAuthFilter` validates the token and populates a `UserPrincipal` on the security context.

### Roles vs. dashboard type
Spring Security only ever assigns **`ROLE_STUDENT`** or **`ROLE_SDO`** — there is *no* `ROLE_EXECUTIVE`. Executives are students who hold an active `Executive` row, so at the Spring level they carry `ROLE_STUDENT`. Executive-scoped authorization is enforced **at the service layer** via `ExecutiveSocietyResolver`, which resolves the caller's active executive society/position and rejects access to societies they don't lead. Treat `dashboardType` as a UI-routing hint, not a security boundary.

### Login flow
```
POST /api/auth/login
→ authenticates credentials
→ resolves dashboardType  (SDO | EXECUTIVE | STUDENT)
→ resolves executivePosition (e.g. "President"; null for non-execs)
→ returns AuthResponse with JWT + dashboardType + executivePosition
```

### Dashboard type priority
1. `userType == SDO` → SDO
2. Has an active Executive row (termEndDate IS NULL or > today) → EXECUTIVE
3. Otherwise → STUDENT

### AuthResponse fields
`token`, `email`, `firstName`, `lastName`, `userType`, `dashboardType`,
`executivePosition`, `studentNumber`, `faculty`, `school`, `campus`,
`course`, `profilePictureURL`

---

## 3. API Response Envelope

Every response uses one envelope:

```json
{ "type": "SUCCESS|WARNING|ERROR", "message": "...", "data": {...} }
```

`ResponseType` = SUCCESS | WARNING | ERROR. The frontend maps `type` + `message` straight to a toast, so messages should be user-facing.

`GlobalExceptionHandler` mapping: `IllegalArgumentException` → 400,
`IllegalStateException` → 409, `BadCredentialsException`/auth → 401,
`AccessDeniedException` → 403, any other `Exception` → 500. All errors return the same envelope with `type: ERROR`.

---

## 3a. Real-time (WebSocket / STOMP)

`WebSocketConfig` registers a STOMP endpoint at **`/ws`** (SockJS fallback) and a simple in-memory broker. The JWT is read from the STOMP CONNECT frame headers, so socket connections are authenticated the same as REST.

User-scoped destinations the server pushes to:
- **`/user/queue/messages`** — new / edited / deleted direct messages
- **`/user/queue/conversations`** — inbox summary updates
- **`/user/queue/notifications`** — lightweight "something changed" signal (client then re-fetches `/api/notifications`)

`MessagingService` and `NotificationService` publish to these via `SimpMessagingTemplate.convertAndSendToUser(...)`.

---

## 4. Entities & Enums

### Critical naming rules

**`User`**,  `firstName` and `lastName` live HERE for ALL roles including SDO.
Never call `sdo.getFirstName()`,  it doesn't exist. Always:
```java
userRepository.findById(sdo.getEmail())
    .map(u -> u.getFirstName() + " " + u.getLastName())
    .orElse("your SDO");
```
`POAService.sdoDisplayName(sdo)` is a helper that does exactly this.

**`Student`**,  faculty is DERIVED from `school.getFaculty()`. Never stored.

**`SDO`**,  PK: `staffNumber`. FK: `email → User`. Fields: `officeNumber`, `phoneExtension` only.

**`Society`**,  `school` has custom setter that auto-fills `faculty`. Use `@Setter(AccessLevel.NONE)`. Financial fields: `membershipFee`, `annualBudgetAllocation` (SDO-only), `currentBalance` (via `FundTransactionService` only,  never set directly).

**`PasswordResetToken`**,  UUID PK, `email FK→User`, `expiresAt`, `used BIT`. One-time use. Expires 1 hour after creation.

**`POA`**,  UUID PK. Unique constraint `(societyID, year)`. Status flow: `DRAFT → SUBMITTED → APPROVED` or `REVISION_REQUESTED → SUBMITTED`.

**`POAEvent`**,  UUID PK. `eventDate` is VARCHAR (accepts "TBC"). Budget constraint enforced at SUBMIT: `totalIncome == totalExpenses`.

**`POAEventCoHost`**,  UUID PK. FK→POAEvent `ON DELETE CASCADE`.

### Enums (model package)
Identity/academic: `UserType`, `Campus`, `Faculty`, `School`, `SocietyType` — the academic reference values (Campus/Faculty/School/course) mirror **NMU** reference data and are seeded, not hand-typed.
Events: `EventStatus`, `AttendingType`.
Tasks: `TaskStatus`, `TaskTargetType`.
Finance: `BudgetRequestType`, `BudgetRequestStatus`, `FundTransactionDirection`, `FundTransactionReason`.
POA: `POAStatus`, `POAAttendance`, `POACoHostStatus`.
Membership: membership application status enum.
Comms: `EmailType` (~30 values, see §10), `NotificationType` (15 values), `TargetType`/recipient targeting, `ResponseType`.
Messaging: conversation/participant type + message status enums.

All enums persist as their **name string** in VARCHAR columns (not integers).

---

## 5. Repository Layer

Spring Data JPA — one repository per aggregate (~40 in total), all extending `JpaRepository`. They range from simple `findById` lookups (User by email, Student/SDO) to role-aware, scoped queries that back messaging and dashboards, e.g.:

- **Identity:** `UserRepository.findById(email)`, `StudentRepository`, `SDORepository`, `PasswordResetTokenRepository`
- **Societies/exec:** `SocietyRepository`, `ExecutiveRepository`, `SocietyMemberRepository` — resolve active executives and society membership
- **Events:** event, hoster, RSVP, outcome, feedback, and event-proposal repositories
- **Finance:** `BudgetRequestRepository`, `FundTransactionRepository`, bank-account repository
- **POA:** `POARepository` (`findBySocietyIDAndYear`, `findBySocietyIDIn`), `POAEventRepository`, `POAEventCoHostRepository`
- **Comms:** notification, announcement, conversation / participant / message repositories with directory-search queries (scope exec directory to society IDs, find supervising SDOs, search SDOs by name)

---

## 6. Service Layer

~70 services carry the business logic; controllers stay thin. Notable gateways and orchestrators:

| Service | Responsibility |
|---|---|
| `AuthService` | register, login, forgot/reset password |
| `EmailService` | **Single gateway** for all outbound email via the Resend SDK — never call Resend directly. Failures are logged and swallowed. |
| `FundTransactionService` | **Single gateway** for ALL `currentBalance` changes; records an immutable `FundTransaction` per change. |
| `NotificationService` | Persists notifications and pushes the `/user/queue/notifications` signal. |
| `MessagingService` | Conversations/messages, role-aware directory search, start-direct-conversation, STOMP fan-out to `/user/queue/messages` + `/conversations`. |
| `ExecutiveSocietyResolver` | Resolves the caller's active executive society/position; the authorization boundary for all executive-scoped actions. |
| `SocietyManagementService` | Society CRUD (individual + bulk CSV); calls `FundTransactionService`. |
| `EventProposalService` / event services | DRAFT → PROPOSED → APPROVED/REJECTED event lifecycle, media, RSVP, reports, feedback. |
| `POAService` | Full POA lifecycle (see §9). |
| `ExecutiveDashboardService` / `SDODashboardService` / `StudentDashboardService` | Per-role dashboard aggregation. |

### POA co-host sync rules
`syncCoHosts()` is called on every save/submit (both Create and Update POA):
- **ADD** new co-hosts → create `POAEventCoHost` (PENDING) + email executives of invited society
- **KEEP** existing PENDING/ACCEPTED still in list → untouched
- **REMOVE** PENDING/ACCEPTED no longer in list → delete + email notification
- **DECLINED** are ignored (can be re-invited)

---

## 7. Endpoints

The API spans 34 controllers. Rather than list every path, here is the controller surface grouped by area — each controller owns the routes under its base path.

### Public / auth
- `AuthController` — `/api/auth/*`: `register` (201, no auto-login), `login`, `forgot-password` (always 200, prevents enumeration), `reset-password`, email verification.
- `AcademicReferenceController` — `/api/reference/*`: NMU campus/faculty/school/course reference data for dropdowns.

### Profile & identity
- `CurrentProfileController` — `/api/profile`
- `PublicExecutiveProfileController` — `/api/users/{studentNumber}/profile`
- `UserProfilePictureController` — profile image upload/serve

### Student
- `StudentController`, `StudentDashboardController` — `/api/student/*`, `/api/student/dashboard`
- `MembershipApplicationController` — student-side society membership applications

### Societies (shared / SDO)
- `SocietyBrowseController` — `/api/societies` (public/active browse)
- `SocietyManagementController` — `/api/sdo/societies/**` (full CRUD + bulk CSV)

### Executive `/api/executive/*`
- `ExecutiveController`, `ExecutiveDashboardController` — account + dashboard (`currentBalance`, `pendingBudgetRequestCount`)
- `ExecutiveSocietyProfileController` — society profile + logo
- `ExecutiveMemberController`, `ExecutiveTeamController` — members / exec team
- `ExecutiveMembershipApplicationController` — review membership applications
- `ExecutiveEventController`, `EventMediaController`, `EventProposalController` — event CRUD, media (POSTER/BANNER), proposal workflow
- `ExecutiveVenueController` — venue management
- `POAController` — `/api/executive/poa/*`: `view` (read-only, all statuses), `current` (edit flows), save DRAFT or SUBMIT
- `BudgetRequestController`, `BankAccountController` — finance

### SDO `/api/sdo/*`
- `SDOController`, `SDODashboardController`
- POA (via `POAController` SDO routes): `/poa/overview`, `/poa?societyID=`, `/poa/{id}/review` (APPROVE / REQUEST_REVISION), `/poa/{id}/remind` (8-day cooldown), `/poa/society/{id}/request`
- Event oversight: `EventReportController`, `EventFeedbackController` + proposal review
- `AdminController` — SDO/admin registration & management

### Events (read side, shared)
- `EventViewController` — `/api/events/visible`, `/api/events/{id}` (backend owns per-actor visibility)
- `RSVPController` — RSVP confirmations (RSVP endpoint is public)

### Comms
- `AnnouncementController` — `/api/*/announcements`
- `NotificationController` — `/api/notifications` (list, unread count, mark read / mark all)
- `MessagingController` — conversations & messages REST (list, get, send, edit, delete, accept/reject request, start direct/SDO conversation); realtime fan-out over STOMP
- `TaskController` — task assignment / viewing

> Executive routes carry `ROLE_STUDENT` at the Spring level; per-society authorization is enforced in the service layer via `ExecutiveSocietyResolver` (see §2).

---

## 8. Financial Model

`currentBalance` changes ONLY via `FundTransactionService.recordTransaction()`.
Every change is recorded in `FundTransaction` (immutable audit trail).

| Trigger | Direction | Reason |
|---|---|---|
| Society registered (allocation > 0) | CREDIT | ANNUAL_ALLOCATION |
| SDO updates allocation (immediate) | CREDIT/DEBIT | ANNUAL_ALLOCATION |
| New member joins | CREDIT | MEMBERSHIP_FEE |
| BudgetRequest approved | DEBIT | BUDGET_APPROVED |

Display: `+R 1 000,00` (CREDIT), `-R 500,00` (DEBIT).

---

## 9. POA Model

### Status flow
```
DRAFT ──► SUBMITTED ──► APPROVED
                    └──► REVISION_REQUESTED ──► SUBMITTED
```

### Smart event sync (no delete-all-recreate)
`syncEvents()` diffs incoming vs DB events:
- Match by `poaEventID` → UPDATE in place
- New → INSERT
- Removed → `deleteByPoaEventID()` (removes co-hosts first), then `deleteById()`

This avoids `FK_CoHost_POAEvent` violations. `ON DELETE CASCADE` is a DB safety net.

### 8-day reminder cooldown
`sendReminder()` checks `poa.lastUpdatedAt`. Throws if < 8 days. Resets clock after sending.

### UUID PKs
`POA`, `POAEvent`, `POAEventCoHost` use `UUID.randomUUID().toString()` (VARCHAR(36)). No race conditions. Safe for 1000+ POAs.

---

## 10. Email Service

Provider: **Resend** (`resend.com`),  free tier 3 000 emails/month.

```java
// Usage in any service:
emailService.send(EmailType.POA_APPROVED, recipientEmail, Map.of(
    "societyName", "CS Society",
    "year",        "2026",
    "sdoName",     "Dr Petra Michaels"
));
```

Failures are caught, logged, and swallowed,  never crash the main flow.

### EmailType enum (~30 values, grouped)
- **Auth:** `FORGOT_PASSWORD`, email verification
- **POA:** `POA_SUBMITTED_TO_SDO`, `POA_APPROVED`, `POA_REVISION_REQUESTED`, `POA_REMINDER`, `POA_REQUEST_FROM_SDO`, `POA_COHOST_INVITE`, `POA_COHOST_ACCEPTED`, `POA_COHOST_DECLINED`
- **Membership:** `MEMBERSHIP_APPROVED`, `MEMBERSHIP_REJECTED`
- **Events:** `EVENT_PROPOSAL_SUBMITTED`, `EVENT_APPROVED`, `EVENT_REJECTED`, `RSVP_CONFIRMATION`, feedback request
- **Finance:** `BUDGET_REQUEST_SUBMITTED`, `BUDGET_REQUEST_APPROVED`, `BUDGET_REQUEST_REJECTED`
- **Ops/comms:** `TASK_ASSIGNED`, `ANNOUNCEMENT`, messaging-related notifications

`NotificationType` (in-app, 15 values) covers the same domains and drives the `/user/queue/notifications` signal + the `/api/notifications` feed.

---

## 11. Password Reset Flow

```
POST /forgot-password { email }
→ delete existing tokens for email
→ create PasswordResetToken (UUID, 1 hour)
→ email {app.base-url}/reset-password?token={UUID}
→ always return 200

POST /reset-password { token, newPassword }
→ find token (unused + not expired)
→ BCrypt new password onto User
→ mark token.used = true
→ return 200
```

---

## 12. Lombok Usage

- Entities: `@Getter @Setter @NoArgsConstructor`
- `Society.school`: `@Setter(AccessLevel.NONE)`,  custom setter auto-fills faculty
- DTOs: `@Data`
- Response DTOs with builder: `@Data @Builder`
- Services: `@RequiredArgsConstructor @Slf4j`

---

## 13. application.properties

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=SocietyCentral
spring.datasource.username=...
spring.datasource.password=...
spring.jpa.hibernate.ddl-auto=validate

jwt.secret=<min 32 chars>
jwt.expiration-ms=86400000

resend.api.key=re_xxxxxxxxx
resend.from.address=onboarding@resend.dev
resend.from.name=SocietyCentral

app.base-url=http://localhost:5173
```

**`spring.jpa.hibernate.ddl-auto=validate`** — Hibernate never creates or alters tables; it only validates that the entities match the DB. The app refuses to start if they disagree, so every schema change must be applied via the SQL scripts (see the database docs) and mirrored in the entities.

> ⚠️ **Security note:** the current `application.properties` contains real credentials (DB password, `jwt.secret`, `resend.api.key`) and is tracked in the repo. This should be moved to environment variables / an untracked local file and the committed secrets rotated. Do not paste real secret values into documentation or commits.

# Development Journal

## Use Case A101 – Register Student Development Officer

**Date:** 18–19 July 2026

---

# Objective

The objective of this use case was to implement the complete Student Development Officer (SDO) registration workflow within the Society Central administration module. This functionality enables an administrator to register a new Student Development Officer by capturing their personal and employment details, reviewing the captured information before submission, and creating the account in the system.

---

# Implementation Overview

The Register Student Development Officer use case was implemented across both the frontend and backend of the application.

The implementation followed a multi-step registration workflow:

1. Administrator completes the registration form.
2. System validates all required information.
3. Administrator reviews the captured information.
4. Administrator confirms the registration.
5. System stores the new Student Development Officer in the database.
6. A registration success page is displayed.

---

# Frontend Development

The frontend implementation introduced a dedicated administrative registration workflow.

The following components and pages were created or modified:

- Admin Layout
- Admin Dashboard
- Register Student Development Officer Page
- Student Development Officer Registration Form
- Review Registration Page
- Registration Complete Page
- Review Card Component
- Summary Card Component
- User Management Components
- Student Development Officer API Service

The implemented workflow is illustrated below:

```
Administrator
      │
      ▼
Register Student Development Officer
      │
      ▼
Review Registration
      │
      ▼
Confirm Registration
      │
      ▼
Registration Complete
```

The registration form captures the following information:

- Title
- First Name
- Last Name
- Email Address
- Password
- Staff Number
- Campus
- Office Number
- Phone Extension

---

# Backend Integration

The frontend was integrated with the existing Spring Boot REST API.

The endpoint used for registration is:

```
POST /api/auth/register-sdo
```

The backend performs the following operations:

- Validates duplicate email addresses.
- Validates duplicate staff numbers.
- Encrypts the user's password before storage.
- Creates the User record.
- Creates the Student Development Officer record.
- Commits both records within a single database transaction.

---

# Database Testing

Testing confirmed that:

- Student Development Officer records are successfully created.
- Passwords are encrypted before being stored.
- Duplicate email validation functions correctly.
- Duplicate staff number validation functions correctly.
- Data is successfully persisted to the SQL Server database.

During testing, the database was reset by removing existing Student Development Officer test data together with related audit log records to allow repeatable registration tests.

---

# Challenges Encountered

## 1. Campus Enumeration

The frontend initially submitted human-readable campus names while the backend expected enumeration values.

Example:

Incorrect:

```
North Campus
```

Correct:

```
NORTH_CAMPUS
```

The Select component was updated to submit the correct enumeration values expected by the backend.

---

## 2. API Endpoint Mismatch

During integration testing, registration requests failed because the frontend was calling an incorrect endpoint.

Initially, the frontend attempted to call:

```
/auth/register-sdo
```

instead of:

```
/api/auth/register-sdo
```

Updating the API service to use the correct endpoint resolved the issue and allowed successful communication between the React frontend and Spring Boot backend.

---

## 3. Foreign Key Constraint During Database Testing

While removing test data from SQL Server, deletion of Student Development Officer records failed because Audit Log records referenced the Student Development Officer through a foreign key constraint.

The issue was resolved by deleting the related Audit Log records before deleting the Student Development Officer records.

---

# Testing Performed

The following tests were completed successfully:

### Frontend Testing

- Registration form loads successfully.
- Required field validation functions correctly.
- Review page displays the entered information accurately.
- Registration completion page is displayed after successful registration.

### Backend Testing

- Endpoint tested successfully using Postman.
- Endpoint tested successfully through the React frontend.
- SQL Server database verified successful record creation.
- Duplicate email validation verified.
- Duplicate staff number validation verified.

---

# Outcome

Use Case A101 – Register Student Development Officer was successfully completed.

The Society Central system now supports complete end-to-end registration of Student Development Officers through the administrative interface. The implementation integrates the React frontend, Spring Boot backend, and SQL Server database into a fully functional registration workflow.

---

# Git Commit

```
feat(A101): complete Register Student Development Officer use case
```

---

**Status:** Completed

**Next Use Case:** A102 – Update Student Development Officer


# Additional Features
### Event Media

#### Overview

Event media refers to the digital assets associated with an event that provide visual representation and promotional information. In the current implementation of SocietyCentral, event media consists of a single **event poster** that serves as the primary promotional image for the event.

The Event entity stores only a reference to the uploaded poster through the `posterUrl` field rather than storing the image file directly in the database.

Example:

```java
private String posterUrl;
```

The actual image is stored separately (for example, within an uploads directory or external storage), while SQL Server stores only the URL or file path to the image.

This approach reduces database storage requirements, improves query performance, simplifies backups, and separates media management from application data.

---

#### Purpose

The event poster serves several purposes within SocietyCentral:

- Provides a professional visual representation of the event.
- Assists the Student Development Officer (SDO) during the approval process.
- Improves the appearance of published events.
- Supports event promotion and marketing.
- Helps students quickly identify events when browsing the platform.

---

#### Validation

Event media validation depends on the current stage of the event lifecycle.

##### Draft Events (C100)

When an executive creates or edits an event draft:

- The event poster is optional.
- A draft may be saved without any event media.
- Executives may upload or replace the poster at any time before submission.

This allows executives to begin planning an event before promotional material has been designed.

##### Event Proposal Submission (C200)

Before an event can transition from **DRAFT** to **PROPOSED**, the backend validates that the required event media has been provided.

The validation is performed by:

```java
validateRequiredEventMedia(...)
```

Conceptually, the validation ensures:

```java
if (request.getPosterUrl() == null || request.getPosterUrl().isBlank()) {
    throw new IllegalArgumentException(
        "An event poster is required before submission."
    );
}
```

If validation fails:

- the submission request is rejected;
- the event remains in the **DRAFT** state;
- the executive must upload the required poster before attempting submission again.

---

#### Business Rules

| Rule ID | Business Rule |
|----------|---------------|
| EM01 | Event media is optional while an event is in the DRAFT state. |
| EM02 | An event poster is required before an event can be submitted for approval. |
| EM03 | The `posterUrl` field may not be null or blank during submission. |
| EM04 | Only authorised executives of the hosting society may upload or replace event media. |
| EM05 | Missing required media prevents the event from transitioning from DRAFT to PROPOSED. |
| EM06 | Published events display the event poster as the primary promotional image. |
| EM07 | The database stores a reference to the poster rather than the image itself. |

---

#### Future Enhancements

The current implementation supports a single event poster. Future versions of SocietyCentral may introduce a dedicated **EventMedia** entity to support multiple media assets for each event.

Possible media types include:

- Event Posters
- Banner Images
- Gallery Images
- Sponsor Logos
- Speaker Images
- Promotional Videos
- Event Programmes (PDF)

A future implementation would establish a one-to-many relationship between **Event** and **EventMedia**, allowing multiple media files to be associated with a single event while maintaining a scalable and extensible architecture.