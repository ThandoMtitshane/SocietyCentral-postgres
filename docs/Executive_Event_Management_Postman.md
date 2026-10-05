# Executive Event Management Postman Flow

Base URL used below: `http://localhost:8080`.

Create these Postman environment variables:

- `baseUrl` = `http://localhost:8080`
- `token` = empty
- `societyID` = empty
- `eventID` = empty
- `posterUrl` = URL returned by the poster upload
- `bannerUrl` = URL returned by the banner upload

All responses use SocietyCentral's `ApiResponse` envelope.

## 1. Log in as an executive

```http
POST {{baseUrl}}/api/auth/login
Content-Type: application/json

{
  "email": "executive@nmu.ac.za",
  "password": "<executive-password>"
}
```

Store the JWT from `data.token`:

```javascript
pm.environment.set("token", pm.response.json().data.token);
```

Get the executive dashboard once and store its authoritative active society:

```http
GET {{baseUrl}}/api/executive/dashboard
Authorization: Bearer {{token}}
```

```javascript
pm.environment.set("societyID", pm.response.json().data.societyID);
```

The event list/detail/update endpoints do not accept a society ID. They resolve
it from the authenticated executive. The create path retains `societyID` for
compatibility, and the service verifies it against the executive record.

## 2. List the current society's events

```http
GET {{baseUrl}}/api/executive/events?page=0&size=20
Authorization: Bearer {{token}}
```

Optional examples:

```http
GET {{baseUrl}}/api/executive/events?status=DRAFT&page=0&size=20
GET {{baseUrl}}/api/executive/events?status=REJECTED
GET {{baseUrl}}/api/executive/events?search=auditorium
```

Supported statuses are `DRAFT`, `PROPOSED`, `APPROVED`, `REJECTED`,
`PUBLISHED`, `CANCELLED`, and `COMPLETED`. `PROPOSED` is the persisted value
the frontend may label "Pending Approval" or "Under Review".

## 3. Upload the poster and banner

Use `multipart/form-data` for both requests.

```http
POST {{baseUrl}}/api/executive/events/media?imageType=POSTER
Authorization: Bearer {{token}}
Content-Type: multipart/form-data

file: <1080x1350 JPEG, PNG or WebP>
```

```http
POST {{baseUrl}}/api/executive/events/media?imageType=BANNER
Authorization: Bearer {{token}}
Content-Type: multipart/form-data

file: <1500x500 JPEG, PNG or WebP>
```

Save each response's `data.fileUrl` as `posterUrl` or `bannerUrl`.

## 4. Save a new event as DRAFT

Use a future date and a venue code returned by the existing availability
endpoint. Venue name, type, campus and capacity are resolved again by the
backend.

```http
GET {{baseUrl}}/api/executive/venues/available?campus=SOUTH_CAMPUS&eventDate=2030-08-10&startTime=17:00&endTime=19:00
Authorization: Bearer {{token}}
```

```http
POST {{baseUrl}}/api/executive/societies/{{societyID}}/events
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "eventName": "Society Welcome Evening",
  "eventDescription": "An introduction and networking event for new members.",
  "eventDate": "2030-08-10",
  "eventStartTime": "17:00:00",
  "eventEndTime": "19:00:00",
  "eventCampus": "SOUTH_CAMPUS",
  "venueCode": "SC001",
  "eventLimit": 150,
  "attendingType": "MEMBERS",
  "rsvpOpenDate": "2030-07-20T08:00:00",
  "rsvpCloseDate": "2030-08-09T17:00:00",
  "posterUrl": "{{posterUrl}}",
  "bannerUrl": "{{bannerUrl}}"
}
```

Store the returned identifier:

```javascript
const body = pm.response.json();
pm.expect(body.data.eventStatus).to.eql("DRAFT");
pm.environment.set("eventID", body.data.eventID);
```

## 5. Confirm the saved draft appears

```http
GET {{baseUrl}}/api/executive/events?status=DRAFT
Authorization: Bearer {{token}}
```

The response is ordered by `updatedAt` descending.

## 6. Retrieve complete details

```http
GET {{baseUrl}}/api/executive/events/{{eventID}}
Authorization: Bearer {{token}}
```

The response includes edit/preview fields, authoritative venue details,
society details, timestamps, real `eventStatus`, and `rejectionReason` when
present. Verify `venueCode`, `venueName`, `venueType`, `venueCapacity`, and
`campus` are populated directly from the backend.

## 7. Update the same draft

`PUT` is a full editable-form update. It changes the existing row and does not
create another Event or Hoster.

Before saving, repeat the availability lookup with the draft identifier. The
current draft's reservation is ignored, but every other blocking event still
causes the venue to be omitted:

```http
GET {{baseUrl}}/api/executive/venues/available?campus=SOUTH_CAMPUS&eventDate=2030-08-10&startTime=17:30&endTime=19:30&excludeEventID={{eventID}}
Authorization: Bearer {{token}}
```

Verify the draft's current `venueCode` remains in the response when no other
event overlaps.

```http
PUT {{baseUrl}}/api/executive/events/{{eventID}}
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "eventName": "Updated Society Welcome Evening",
  "eventDescription": "Updated programme and networking details.",
  "eventDate": "2030-08-10",
  "eventStartTime": "17:30:00",
  "eventEndTime": "19:30:00",
  "eventCampus": "SOUTH_CAMPUS",
  "venueCode": "SC001",
  "eventLimit": 140,
  "attendingType": "MEMBERS",
  "rsvpOpenDate": "2030-07-20T08:00:00",
  "rsvpCloseDate": "2030-08-09T17:00:00",
  "posterUrl": "{{posterUrl}}",
  "bannerUrl": "{{bannerUrl}}"
}
```

Verify `data.eventID` is unchanged, `createdAt` is unchanged, and `updatedAt`
has advanced.

For a `REJECTED` event, the same request preserves `rejectionReason` and moves
the same row to `DRAFT`. Submission remains a separate action.

## 8. Log out, log in again, and retrieve the same draft

```http
POST {{baseUrl}}/api/auth/logout
Authorization: Bearer {{token}}
```

Repeat step 1 to get a new token, then:

```http
GET {{baseUrl}}/api/executive/events/{{eventID}}
Authorization: Bearer {{token}}
```

Verify the updated values are still present. Event persistence is independent
of the stateless JWT session.

## 9. Submit the completed draft

```http
POST {{baseUrl}}/api/executive/events/{{eventID}}/submit
Authorization: Bearer {{token}}
```

Expected result:

```json
{
  "type": "SUCCESS",
  "message": "Event submitted for approval successfully.",
  "data": {
    "eventID": "{{eventID}}",
    "eventStatus": "PROPOSED"
  }
}
```

Submission revalidates required fields, dates/times, RSVP chronology, both
media URLs, the database venue/campus/capacity, and venue overlap before
changing the status.

## 10. Verify the pending filter

```http
GET {{baseUrl}}/api/executive/events?status=PROPOSED
Authorization: Bearer {{token}}
```

Verify the submitted event appears.

## 11. Verify a PROPOSED event cannot be edited or resubmitted

Repeat the `PUT` request from step 7. Expected HTTP `409`:

```json
{
  "type": "ERROR",
  "message": "Only draft or rejected events may be edited.",
  "data": null
}
```

Repeat the submit request. Expected HTTP `409`:

```json
{
  "type": "ERROR",
  "message": "Only events in DRAFT status may be submitted for approval.",
  "data": null
}
```
