Event access fix — 13 September 2026

Implemented and automated verification passed. Live acceptance remains unverified:
no browser provider/session is connected; attempting to open the local route
returned `Browser is not available: iab`. No claim is made that the user's live
EVT011 record or running frontend/backend has been verified or restarted.

Request trace and root cause

1. `/executive/events/EVT011` previously selected `pages/executive/EventDetailsPage.jsx`.
2. Its `loadEvent()` calls `eventApi.js:getExecutiveEvent("EVT011")`.
3. Axios sends `GET http://localhost:8080/api/executive/events/EVT011` (the current
   frontend `.env` base URL), with `Content-Type: application/json` and
   `Authorization: Bearer <sc_token>` from localStorage.
4. `JwtAuthFilter` resolves the authenticated user; `SecurityConfig` requires
   authentication. Executives retain `ROLE_STUDENT`.
5. `ExecutiveEventController.getEvent()` calls `EventService.getExecutiveEvent()`.
6. `EventService.requireHostedEvent()` rejects an existing event without a Hoster
   relationship to the executive's resolved society, throwing
   `ForbiddenOperationException("The event does not belong to your society.")`.
7. `GlobalExceptionHandler.handleForbiddenOperation()` maps that exception to 403.

This is a management API being used for a browsing request. The current working
source already contained a redirect after that 403; its presence was preserved.
Whether the screenshot used an older bundle cannot be established without the
live browser. The exact management 403 was reproduced with the real HTTP stack
and isolated mocked fixtures.

Changes and preserved rules

- A route selector now checks the existing paginated, society-scoped executive
  event list. Exact event IDs in that server result select the existing management
  page, including co-hosts and later pages. Other events select the existing shared
  attendee page at the same `/executive/events/:eventID` URL. No routes/endpoints
  were added; unrelated detail requests use `GET /api/events/{eventID}`.
- The shared attendee page already supports executive RSVP actions and current
  RSVP status. Its obsolete comments were corrected; the page was not duplicated.
- `EventViewService` and its persistence-backed executive browsing logic were
  left unchanged. Published events available through existing browsing queries
  remain visible across societies. Existing RSVP-opening visibility conditions
  also remain unchanged; unpublished events are not exposed.
- `confirmRsvp()` had no attendance-type membership validation. It now delegates
  that responsibility to `EventAttendanceEligibility`, which permits EVERY_STUDENT
  or, for MEMBERS, current membership in an actual hosting society. It uses
  `SocietyMemberRepository.existsActiveMembership()` and the existing clock.
  Executive status never grants membership. This attendee validation applies to
  authenticated and invitation-token flows.
- The authenticated primary-host executive exclusion and its exact message remain.
  ExecutiveRepository is now a required constructor dependency rather than an
  optional field that could silently bypass that check. RSVP dates, duplicates,
  locking, capacity, ticket generation and check-in logic were not changed.
- Existing co-host semantics are preserved: normal membership eligibility accepts
  any hosting society; the executive RSVP exclusion checks PRIMARY host only.
  No supplied source/specification required broadening that exclusion.
- No management guard was weakened or modified. No cross-society edit, cancel,
  report, private guest-list or check-in authority was granted. Existing edit,
  cancel and private-list ownership failures use IllegalStateException (HTTP 409);
  management details use ForbiddenOperationException (HTTP 403).
- No application database records, JWT format, workflows or other business rules
  were changed. Isolated tests use H2 and mocked persistence/outbound effects.

Files changed by this task (other pre-existing working-tree changes retained)

Backend, relative to SocietyCentral_Back-end:

- `src/main/java/com/societycentral/service/EventAttendanceEligibility.java` — new attendee policy.
- `src/main/java/com/societycentral/service/RSVPService.java` — required dependencies and eligibility delegation.
- `src/test/java/com/societycentral/service/EventViewServiceTests.java` — two host-society cases.
- `src/test/java/com/societycentral/service/ExecutiveAttendeeRsvpTests.java` — 13 RSVP regression cases.
- `src/test/java/com/societycentral/service/CrossSocietyEventManagementTests.java` — three management denials.
- `src/test/java/com/societycentral/ExecutiveEventAccessHttpTests.java` — three real HTTP/startup cases.
- `docs/Executive_Event_Attendee_Access_Fix.md` — this report.

Frontend, relative to SocietyCentral/societycentral_front-end:

- `src/App.jsx` — select the new executive details route component.
- `src/pages/executive/ExecutiveEventDetailsRoute.jsx` — select management/attendee presentation.
- `src/pages/events/EventDetailsPage.jsx` — correct executive RSVP comments.
- `src/tests/component_tests/ExecutiveEventAccess.test.jsx` — three route/action regression cases.
- `package.json` — include the new tests in `npm test`.

Generated staging files and logs are under backend `target/event-access-fix/`;
normal build output was generated under backend `target/` and frontend `dist/`.

Verification

- 42 focused backend tests passed: ExecutiveAttendeeRsvpTests,
  CrossSocietyEventManagementTests, EventViewServiceTests, EventViewControllerTests,
  EventVisibilityRepositoryTests. This includes the requested normal-student
  EVERY_STUDENT/MEMBERS visibility and unpublished-event repository checks.
- Three additional full-application HTTP tests passed: the same STUDENT JWT
  representing a SOC001 executive receives browsing 200, management-details 403,
  and eligible RSVP 201 for a SOC010 EVT011 fixture.
- Java 25 compilation passed. Full Spring Boot/Tomcat startup passed on an isolated
  random port (56326 in this run), with H2, mocked fixtures, and mocked schedulers,
  initializer and outbound delivery. This is not a production startup claim.
- All three new frontend tests passed: executive route browsing request/JWT/RSVP
  navigation/no management request; existing RSVP ticket status; co-host management
  on a later page. Existing event-view API tests also passed.
- Frontend production build passed with existing mixed-import/chunk-size warnings.

Commands (installed Maven 3.9.16 was used directly with JAVA_HOME set to Java 25):

```text
mvn -o -Dtest=ExecutiveAttendeeRsvpTests,CrossSocietyEventManagementTests,EventViewServiceTests,EventViewControllerTests,EventVisibilityRepositoryTests test
mvn -o -Dtest=ExecutiveEventAccessHttpTests test
node --import tsx --import ./src/tests/registerCssModuleLoader.js --test --test-concurrency=1 src/tests/component_tests/ExecutiveEventAccess.test.jsx src/tests/component_tests/EventViewPages.test.jsx src/api/eventViewService.test.js
npm run build
```

Pre-existing unrelated failures observed in broader selected suites

- ExecutiveEventManagementServiceTests: six failures/errors concerning draft edit
  schedule changes, venue conflict/capacity mocks and rejected revisions. The
  relevant EventService implementation and these tests were not changed by this
  task. Its cross-society draft-update denial test passed.
- EventViewPages.test.jsx: four failures concerning existing time/description
  presentation and retry/browse expectations. Those tests do not import the new
  executive route, and the shared page's changes in this task are comments only.
- The initial Maven wrapper could not initialize in the sandbox; direct installed
  Maven required escalation to read Java's security configuration. Subsequent
  compilation and focused runs succeeded. No full repository-suite pass is claimed.

Remaining acceptance step: open the actual signed-in executive session at
`/executive/events/EVT011`, verify the real record returns browsing 200 and renders,
and inspect attendee actions without performing a real RSVP/database mutation.
