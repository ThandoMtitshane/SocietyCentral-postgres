# A400 Browse Events and C800 Membership Review

This guide uses Postman variables so that no authenticated identity or society
identifier is accepted from the browser as proof of ownership.

## Postman variables

Create these environment variables:

- `baseUrl`: `http://localhost:8080`
- `studentToken`: JWT for an ordinary student
- `executiveToken`: JWT for an active executive
- `sdoToken`: JWT for an SDO
- `applicationID`: a PENDING application owned by the executive's society
- `rejectionApplicationID`: another PENDING application owned by that society
- `otherSocietyApplicationID`: an application owned by another society
- `reviewedApplicationID`: an APPROVED, REJECTED or WITHDRAWN application
- `draftEventID`: a DRAFT event
- `restrictedEventID`: a PUBLISHED MEMBERS event hosted by a society the
  ordinary student does not belong to

All authenticated requests use the header:

```http
Authorization: Bearer {{token}}
```

Replace `{{token}}` with the appropriate environment token for each case.

## A400 requests

### 1. Student sees eligible published events

```http
GET {{baseUrl}}/api/events/visible
Authorization: Bearer {{studentToken}}
```

Expected: `200 OK`. The response contains EVERY_STUDENT events plus
members-only events hosted by the student's active societies. Each card
contains `eventID`, name, description, date/times, venue, campus,
`attendingType`, primary society, poster/banner URLs and `eventLimit`.

### 2. Executive sees all published events

```http
GET {{baseUrl}}/api/events/visible
Authorization: Bearer {{executiveToken}}
```

Expected: `200 OK`. An active executive is resolved from the authenticated
student identity and receives every PUBLISHED event.

### 3. SDO sees all published events

```http
GET {{baseUrl}}/api/events/visible
Authorization: Bearer {{sdoToken}}
```

Expected: `200 OK` with every PUBLISHED event.

### 4. Draft event is excluded

First confirm `draftEventID` does not appear in any browse response. Then call:

```http
GET {{baseUrl}}/api/events/{{draftEventID}}
Authorization: Bearer {{sdoToken}}
```

Expected: `404 Not Found` with `Event not found.`. Detail lookup also enforces
PUBLISHED status.

### 5. Student cannot open an unauthorised members-only event

```http
GET {{baseUrl}}/api/events/{{restrictedEventID}}
Authorization: Bearer {{studentToken}}
```

Expected: `404 Not Found` with `Event not found.`. Returning 404 avoids
revealing an otherwise hidden event.

## C800 requests

### 1. List pending applications

```http
GET {{baseUrl}}/api/executive/membership-applications?status=PENDING&page=0&size=20
Authorization: Bearer {{executiveToken}}
```

Expected: `200 OK`. Only the authenticated executive's society is returned,
oldest application first.

### 2. Search by student name, number or tracking reference

```http
GET {{baseUrl}}/api/executive/membership-applications?status=PENDING&search=akhona&page=0&size=20
Authorization: Bearer {{executiveToken}}
```

Repeat with a student number and tracking reference. Search is
case-insensitive and remains society-scoped.

### 3. Filter reviewed history

```http
GET {{baseUrl}}/api/executive/membership-applications?status=APPROVED&page=0&size=20
Authorization: Bearer {{executiveToken}}
```

Expected: `200 OK`, newest review first. `REJECTED` and `WITHDRAWN` are also
valid status filters.

### 4. Open full application details

```http
GET {{baseUrl}}/api/executive/membership-applications/{{applicationID}}
Authorization: Bearer {{executiveToken}}
```

Expected: `200 OK` with applicant profile, application history and membership
fee. Password and society financial-balance fields are not exposed.

### 5. Approve a pending application

Use a dedicated PENDING test application because this changes persisted data.

```http
POST {{baseUrl}}/api/executive/membership-applications/{{applicationID}}/approve
Authorization: Bearer {{executiveToken}}
```

Expected: `200 OK`, status `APPROVED`, populated `reviewedAt` and
`reviewedBy`. Exactly one SocietyMember and, when the fee is positive, one
MEMBERSHIP_FEE credit are created.

### 6. Reject a pending application with a reason

```http
POST {{baseUrl}}/api/executive/membership-applications/{{rejectionApplicationID}}/reject
Authorization: Bearer {{executiveToken}}
Content-Type: application/json

{
  "rejectionReason": "The society has reached its current intake capacity."
}
```

Expected: `200 OK`, status `REJECTED`, and the trimmed reason is returned and
stored. No SocietyMember or fee transaction is created.

### 7. Reject without a reason

```http
POST {{baseUrl}}/api/executive/membership-applications/{{rejectionApplicationID}}/reject
Authorization: Bearer {{executiveToken}}
Content-Type: application/json

{
  "rejectionReason": ""
}
```

Expected: `400 Bad Request` with `Rejection reason is required.` and no state
change.

### 8. Access another society's application

```http
GET {{baseUrl}}/api/executive/membership-applications/{{otherSocietyApplicationID}}
Authorization: Bearer {{executiveToken}}
```

Also attempt `/approve` or `/reject` for the same ID. Expected: `403
Forbidden`. The API never accepts a society ID from the request.

### 9. Review an already reviewed application

```http
POST {{baseUrl}}/api/executive/membership-applications/{{reviewedApplicationID}}/approve
Authorization: Bearer {{executiveToken}}
```

Repeat with `/reject` and valid JSON. Expected: `409 Conflict` with `Only
pending membership applications may be reviewed.`. This applies to APPROVED,
REJECTED and WITHDRAWN applications.

### 10. Unauthenticated and non-executive requests

Call the list endpoint without `Authorization`; expect `401` or the project's
configured authentication-entry response. Call it using `studentToken` for a
student with no active Executive row; expect `403 Forbidden` with
`Authenticated user is not an active society executive.`.

## SQL Server verification

Run the following against `SocietyCentral` after assigning identifiers from
the Postman responses. These are read-only verification queries.

```sql
USE SocietyCentral;
GO

DECLARE @applicationID VARCHAR(36) = '<approved application UUID>';
DECLARE @rejectionApplicationID VARCHAR(36) = '<rejected application UUID>';

-- 1. Inspect both historical application records.
SELECT applicationID,
       trackingReference,
       studentNumber,
       societyID,
       status,
       applicationDate,
       lastUpdatedAt,
       reviewedAt,
       reviewedBy,
       rejectionReason
FROM dbo.MembershipApplication
WHERE applicationID IN (@applicationID, @rejectionApplicationID);

-- 2. Tracking references must be populated and globally unique.
SELECT trackingReference, COUNT(*) AS duplicateCount
FROM dbo.MembershipApplication
GROUP BY trackingReference
HAVING trackingReference IS NULL OR COUNT(*) > 1;

-- Expected: zero rows.

-- 3. Approval creates exactly one approved-membership row.
SELECT member.studentNumber,
       member.societyID,
       member.joinDate,
       member.expireDate
FROM dbo.SocietyMember AS member
JOIN dbo.MembershipApplication AS application
  ON application.studentNumber = member.studentNumber
 AND application.societyID = member.societyID
WHERE application.applicationID = @applicationID
  AND application.status = 'APPROVED';

-- 4. A positive fee is credited exactly once through FundTransaction.
SELECT tx.transactionID,
       tx.societyID,
       tx.amount,
       tx.direction,
       tx.reason,
       tx.reference,
       tx.balanceBefore,
       tx.balanceAfter
FROM dbo.FundTransaction AS tx
JOIN dbo.MembershipApplication AS application
  ON application.societyID = tx.societyID
 AND application.studentNumber = tx.reference
WHERE application.applicationID = @applicationID
  AND tx.direction = 'CREDIT'
  AND tx.reason = 'MEMBERSHIP_FEE';

-- Expected for a positive membership fee: exactly one row.

-- 5. Rejection stores its reason and creates neither member nor fee row.
SELECT application.status,
       application.rejectionReason,
       application.reviewedAt,
       application.reviewedBy,
       member.studentNumber AS unexpectedMember,
       tx.transactionID AS unexpectedFeeTransaction
FROM dbo.MembershipApplication AS application
LEFT JOIN dbo.SocietyMember AS member
  ON member.studentNumber = application.studentNumber
 AND member.societyID = application.societyID
LEFT JOIN dbo.FundTransaction AS tx
  ON tx.societyID = application.societyID
 AND tx.reference = application.studentNumber
 AND tx.reason = 'MEMBERSHIP_FEE'
WHERE application.applicationID = @rejectionApplicationID;

-- Expected: REJECTED with a reason; both unexpected columns are NULL for a
-- student who did not already have historical membership/fee data.

-- 6. Composite membership keys cannot be duplicated.
SELECT studentNumber, societyID, COUNT(*) AS duplicateCount
FROM dbo.SocietyMember
GROUP BY studentNumber, societyID
HAVING COUNT(*) > 1;

-- Expected: zero rows.

-- 7. Pending queue for one society, oldest first.
DECLARE @societyID VARCHAR(20) = '<executive society ID>';

SELECT applicationID,
       trackingReference,
       studentNumber,
       applicationDate
FROM dbo.MembershipApplication
WHERE societyID = @societyID
  AND status = 'PENDING'
ORDER BY applicationDate ASC, applicationID ASC;

-- 8. Published events visible to one student, in A400 order.
DECLARE @studentNumber VARCHAR(20) = '<student number>';
DECLARE @today DATE = CAST(GETDATE() AS DATE);

SELECT event.eventID,
       event.eventName,
       event.eventDate,
       COALESCE(event.eventStartTime, event.eventTime) AS startTime,
       event.attendingType
FROM dbo.Event AS event
WHERE event.eventStatus = 'PUBLISHED'
  AND (
        event.attendingType = 'EVERY_STUDENT'
        OR EXISTS (
            SELECT 1
            FROM dbo.Hoster AS hoster
            JOIN dbo.SocietyMember AS member
              ON member.societyID = hoster.societyID
            WHERE hoster.eventID = event.eventID
              AND member.studentNumber = @studentNumber
              AND (member.expireDate IS NULL
                   OR member.expireDate >= @today)
        )
  )
ORDER BY event.eventDate ASC,
         COALESCE(event.eventStartTime, event.eventTime) ASC,
         event.eventID ASC;

-- 9. Verify no hidden workflow status can enter the public browse query.
SELECT eventStatus, COUNT(*) AS eventCount
FROM dbo.Event
WHERE eventStatus <> 'PUBLISHED'
GROUP BY eventStatus;
```

The last query inventories hidden events; none of those IDs should occur in
either A400 browse response.

## Verification performed on 3 August 2026

- Focused A400/C800 and application-context suite: 36 tests passed, zero
  failures.
- SQL Server: published-event chronological query passed; executive PENDING
  queue join/order query passed.
- SQL Server schema: MembershipApplication table present; all four required
  indexes present; zero duplicate tracking references; zero duplicate PENDING
  student/society pairs.
- The packaged application started successfully on an isolated port and was
  stopped after the smoke attempt. Its local seed password did not match the
  active executive account, so authenticated Postman-style calls returned 401;
  no approval, rejection, membership or financial data was mutated.
- Complete repository suite: 201 tests executed. The 195 passing tests include
  all A400/C800 tests. Five failures and one error remain in the unrelated
  `EventServiceEventMediaTests` C100 RSVP/media-validation expectations; C100
  was intentionally not changed as part of this task.
