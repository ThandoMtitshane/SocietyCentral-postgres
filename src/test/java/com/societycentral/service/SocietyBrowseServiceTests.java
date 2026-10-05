package com.societycentral.service; // Declares that this test class belongs to the Society Central service test package.

import com.societycentral.dto.response.ExecutiveSocietyProfileDTO; // Imports the executive-specific society profile response used in executive profile tests.
import com.societycentral.dto.response.MembershipState; // Imports the membership-state enum used when testing student membership eligibility.
import com.societycentral.dto.response.SDOSocietyProfileDTO; // Imports the SDO-specific society profile response used in oversight tests.
import com.societycentral.dto.response.SocietyHighlightResponseDTO; // Imports the DTO used when testing published and manageable society highlights.
import com.societycentral.dto.response.SocietyMembershipEligibilityResponseDTO; // Imports the DTO used to represent a student's membership eligibility for a society.
import com.societycentral.dto.response.StudentSocietyProfileDTO; // Imports the public/student society profile DTO returned by the browse service.
import com.societycentral.exception.ForbiddenOperationException; // Imports the exception expected when an executive tries to access another society's private profile.
import com.societycentral.exception.ResourceNotFoundException; // Imports the exception expected when a student/public viewer requests an inactive society.
import com.societycentral.model.Announcement; // Imports the Announcement entity used to build test announcement fixtures.
import com.societycentral.model.Event; // Imports the Event entity used to build upcoming-event fixtures.
import com.societycentral.model.EventStatus; // Imports the event-status enum used to mark fixture events as published.
import com.societycentral.model.Executive; // Imports the Executive entity used to build committee fixtures.
import com.societycentral.model.ExecutiveId; // Imports the composite Executive identifier used by executive fixtures.
import com.societycentral.model.Society; // Imports the Society entity used throughout the test fixtures.
import com.societycentral.model.SocietyImageType; // Imports the society-media type enum used to create gallery fixture data.
import com.societycentral.model.SocietyMedia; // Imports the SocietyMedia entity used to create gallery fixture data.
import com.societycentral.model.Student; // Imports the Student entity used to represent the authenticated student/executive.
import com.societycentral.model.TargetType; // Imports the announcement target enum used by society-profile announcement queries.
import com.societycentral.model.TaskStatus; // Imports the task-status enum used when calculating pending society tasks.
import com.societycentral.model.User; // Imports the User entity linked to Student and used for names and email.
import com.societycentral.model.UserProfilePicture; // Imports the database-backed profile-picture entity used by the new executive-picture architecture.
import com.societycentral.repository.AnnouncementRepository; // Imports the mocked announcement repository used by the service.
import com.societycentral.repository.EventRepository; // Imports the mocked event repository used by the service.
import com.societycentral.repository.ExecutiveRepository; // Imports the mocked executive repository used by the service.
import com.societycentral.repository.RSVPRepository;
import com.societycentral.repository.SocietyMemberRepository; // Imports the mocked membership repository used for member-count calculations.
import com.societycentral.repository.SocietyMediaRepository; // Imports the mocked society-media repository used for gallery retrieval.
import com.societycentral.repository.SocietyRepository; // Imports the mocked society repository used to retrieve societies.
import com.societycentral.repository.StudentRepository; // Imports the mocked student repository used to resolve viewers.
import com.societycentral.repository.TaskAllocationRepository; // Imports the mocked task repository used to calculate outstanding tasks.
import com.societycentral.repository.UserProfilePictureRepository; // Imports the repository that now represents the authoritative profile-picture source.
import org.junit.jupiter.api.BeforeEach; // Imports the lifecycle annotation used to rebuild the service before each test.
import org.junit.jupiter.api.Test; // Imports the JUnit annotation used to mark test methods.
import org.junit.jupiter.api.extension.ExtendWith; // Imports the extension annotation needed to activate Mockito integration.
import org.mockito.Mock; // Imports the annotation used to create Mockito mocks automatically.
import org.mockito.junit.jupiter.MockitoExtension; // Imports the Mockito JUnit extension used by this test class.
import org.springframework.data.domain.Pageable; // Imports Pageable because the service repository queries use pageable parameters.
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal; // Imports BigDecimal because Society financial values are represented as decimal values.
import java.time.Clock; // Imports Clock so the service can be tested using a predictable fixed current time.
import java.time.Instant; // Imports Instant to construct the fixed test clock.
import java.time.LocalDate; // Imports LocalDate for executive terms, event dates and society membership dates.
import java.time.LocalDateTime; // Imports LocalDateTime for announcement, gallery and profile-picture timestamps.
import java.time.LocalTime; // Imports LocalTime because upcoming-event repository queries include current time.
import java.time.ZoneOffset; // Imports ZoneOffset to make the fixed test clock deterministic.
import java.util.List; // Imports List for repository fixtures and result assertions.
import java.util.Optional; // Imports Optional because repositories return optional values for Student and UserProfilePicture.

import static org.junit.jupiter.api.Assertions.assertEquals; // Imports equality assertions used throughout the tests.
import static org.junit.jupiter.api.Assertions.assertFalse; // Imports false assertions used for boolean response fields.
import static org.junit.jupiter.api.Assertions.assertNull; // Imports null assertions used when optional response values should be absent.
import static org.junit.jupiter.api.Assertions.assertThrows; // Imports exception assertions used for access-control and inactive-society tests.
import static org.junit.jupiter.api.Assertions.assertTrue; // Imports true assertions used for permissions, picture state and oversight flags.
import static org.mockito.ArgumentMatchers.any; // Imports the generic Mockito argument matcher.
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq; // Imports the equality Mockito argument matcher.
import static org.mockito.Mockito.never; // Imports the Mockito verification mode used to prove methods were not called.
import static org.mockito.Mockito.verify; // Imports Mockito's method-call verification helper.
import static org.mockito.Mockito.when; // Imports Mockito's stubbing helper.

@ExtendWith(MockitoExtension.class) // Enables Mockito annotations and mock injection for every test in this class.
class SocietyBrowseServiceTests { // Declares the test suite for SocietyBrowseService.

    private static final String SOCIETY_ID = "SOC001"; // Defines one reusable society identifier for the test fixtures.
    private static final String STUDENT_EMAIL = "student@nmu.ac.za"; // Defines the authenticated student's email used throughout the tests.
    private static final LocalDate TODAY = LocalDate.of(2030, 8, 11); // Defines a fixed business date so executive-term and upcoming-event logic remain deterministic.
    private static final Clock FIXED_CLOCK = Clock.fixed( // Creates a fixed clock so calls to LocalDate.now and LocalDateTime.now are predictable in tests.
            Instant.parse("2030-08-11T08:30:00Z"), // Defines the exact instant that represents "now" during the tests.
            ZoneOffset.UTC); // Defines UTC as the timezone for the fixed test clock.

    @Mock // Tells Mockito to create a fake SocietyRepository for isolated service testing.
    private SocietyRepository societyRepository; // Stores the mocked society repository dependency.

    @Mock // Tells Mockito to create a fake StudentRepository for isolated service testing.
    private StudentRepository studentRepository; // Stores the mocked student repository dependency.

    @Mock // Tells Mockito to create a fake ExecutiveRepository for isolated service testing.
    private ExecutiveRepository executiveRepository; // Stores the mocked executive repository dependency.

    @Mock // Tells Mockito to create a fake SocietyMemberRepository for member-count tests.
    private SocietyMemberRepository societyMemberRepository; // Stores the mocked membership repository dependency.

    @Mock
    private RSVPRepository rsvpRepository;

    @Mock // Tells Mockito to create a fake SocietyMediaRepository for gallery tests.
    private SocietyMediaRepository societyMediaRepository; // Stores the mocked society-media repository dependency.

    @Mock // Tells Mockito to create a fake EventRepository for upcoming-event tests.
    private EventRepository eventRepository; // Stores the mocked event repository dependency.

    @Mock // Tells Mockito to create a fake AnnouncementRepository for society-announcement tests.
    private AnnouncementRepository announcementRepository; // Stores the mocked announcement repository dependency.

    @Mock // Tells Mockito to create a fake TaskAllocationRepository for oversight-metric tests.
    private TaskAllocationRepository taskAllocationRepository; // Stores the mocked task repository dependency.

    @Mock // Tells Mockito to create the new database-backed user profile-picture repository required by SocietyBrowseService.
    private UserProfilePictureRepository userProfilePictureRepository; // Stores the mocked authoritative user profile-picture repository.

    @Mock // Tells Mockito to create a fake MembershipApplicationService for membership-eligibility tests.
    private MembershipApplicationService membershipApplicationService; // Stores the mocked membership application service.

    @Mock // Tells Mockito to create a fake profile-edit authorisation service.
    private SocietyProfileEditAuthorizationService profileEditAuthorizationService; // Stores the mocked society-profile authorisation dependency.

    @Mock // Tells Mockito to create a fake SocietyHighlightService for highlight tests.
    private SocietyHighlightService societyHighlightService; // Stores the mocked highlight service dependency.

    private SocietyBrowseService service; // Holds the concrete service instance tested by each method.

    @BeforeEach // Runs this method before every test so each test gets a clean service instance.
    void setUp() { // Creates SocietyBrowseService using the mocks instead of real repositories.
        service = new SocietyBrowseService( // Constructs the service using the same dependency order generated by @RequiredArgsConstructor.
                societyRepository, // Supplies the mocked society repository.
                studentRepository, // Supplies the mocked student repository.
                executiveRepository, // Supplies the mocked executive repository.
                societyMemberRepository, // Supplies the mocked membership repository.
                societyMediaRepository, // Supplies the mocked media repository.
                eventRepository, // Supplies the mocked event repository.
                announcementRepository, // Supplies the mocked announcement repository.
                taskAllocationRepository, // Supplies the mocked task-allocation repository.
                userProfilePictureRepository, // Supplies the new mocked database-backed profile-picture repository.
                membershipApplicationService, // Supplies the mocked membership-application service.
                profileEditAuthorizationService, // Supplies the mocked profile-edit authorisation service.
                societyHighlightService, // Supplies the mocked society-highlight service.
                new AnnouncementAudienceResolver(
                        studentRepository,
                        executiveRepository,
                        societyMemberRepository,
                        FIXED_CLOCK),
                FIXED_CLOCK); // Supplies the deterministic test clock.
        ReflectionTestUtils.setField(service, "rsvpRepository", rsvpRepository);
    } // Ends the common test setup.

    @Test // Marks this method as a JUnit test.
    void activeBrowseSummariesNeverLoadMemberCounts() { // Verifies that public browse cards do not run unnecessary member-count queries.
        Society society = society(true); // Creates an active society fixture.
        society.setNumberOfMembers(999); // Sets a stored member number to prove the browse mapping does not query/recalculate membership counts.
        when(societyRepository.findByActiveStatusTrue()) // Stubs the repository call used to load active societies.
                .thenReturn(List.of(society)); // Returns only the active test society.

        var result = service.getActiveSocieties(); // Calls the service method being tested.

        assertEquals(1, result.size()); // Verifies exactly one society summary was produced.
        assertEquals(SOCIETY_ID, result.getFirst().getSocietyID()); // Verifies the society identifier was mapped correctly.
        assertEquals("/media/societies/logos/society-logo.png", // Defines the expected persisted society-logo media path.
                result.getFirst().getLogoUrl()); // Verifies the society logo URL was mapped correctly.
        verify(societyMemberRepository, never()) // Begins verification that no membership-count query was performed.
                .countCurrentMembersBySocietyID(any(), any()); // Confirms the expensive member-count repository method was never called.
    } // Ends the browse-summary performance test.

    @Test // Marks this method as a JUnit test.
    void studentProfileMapsPublicSectionsAndNormalisesBlankSocialLinks() { // Verifies the public Student society profile maps its visible sections correctly.
        Society society = society(true); // Creates an active society fixture.
        society.setFacebookURL(" https://facebook.com/computing "); // Sets a valid Facebook URL with surrounding whitespace to test trimming.
        society.setInstagramURL("   "); // Sets a blank Instagram URL to verify it becomes null.
        society.setTiktokURL("@computing"); // Sets an invalid non-HTTP TikTok value to verify it is rejected.
        Student student = student(); // Creates the authenticated Student fixture.
        SocietyMembershipEligibilityResponseDTO eligibility = // Begins construction of the mocked membership eligibility response.
                SocietyMembershipEligibilityResponseDTO.builder() // Uses the DTO builder to create predictable eligibility state.
                        .membershipState(MembershipState.PENDING) // Marks the Student as having a pending membership application.
                        .applicationsAvailable(false) // Indicates another application cannot currently be submitted.
                        .latestApplicationID("APP001") // Provides the current application identifier.
                        .latestTrackingReference("MEM-TRACK") // Provides the current application tracking reference.
                        .build(); // Completes the eligibility DTO.
        Executive executive = executive(student, society); // Creates one active executive fixture for the society.
        Event event = event(); // Creates one upcoming published event fixture.
        Announcement announcement = announcement(); // Creates one active society announcement fixture.
        SocietyMedia galleryMedia = galleryMedia(); // Creates one society-gallery media fixture.

        when(studentRepository.findByEmail(STUDENT_EMAIL)) // Stubs viewer resolution by authenticated email.
                .thenReturn(Optional.of(student)); // Returns the Student fixture.
        when(societyRepository.findById(SOCIETY_ID)) // Stubs society lookup by ID.
                .thenReturn(Optional.of(society)); // Returns the active society fixture.
        when(membershipApplicationService.getProfileEligibility( // Stubs membership eligibility calculation.
                student, society)).thenReturn(eligibility); // Returns the predefined pending eligibility state.
        when(executiveRepository.findActiveExecutivesForSocietyProfile( // Stubs active executive retrieval for the society profile.
                SOCIETY_ID, TODAY)).thenReturn(List.of(executive)); // Returns the single President fixture.
        when(eventRepository.findUpcomingPublishedEventsForSocietyProfile( // Stubs the upcoming-event preview query.
                eq(SOCIETY_ID), // Requires the expected society identifier.
                eq(TODAY), // Requires the expected fixed business date.
                any(LocalTime.class), // Accepts the current time calculated from the fixed clock.
                any(Pageable.class))) // Accepts the preview paging definition.
                .thenReturn(List.of(event)); // Returns the one event fixture.
        when(announcementRepository.findProfileAnnouncements( // Stubs the public society-announcement preview query.
                eq(SOCIETY_ID), // Requires the expected society identifier.
                anyCollection(), // Accepts the audience resolved for the current viewer.
                any(LocalDateTime.class), // Accepts the service's current timestamp.
                any(Pageable.class))) // Accepts the preview paging definition.
                .thenReturn(List.of(announcement)); // Returns the one announcement fixture.
        when(societyMediaRepository // Begins stubbing the gallery-media lookup.
                .findBySocietyIDAndMediaTypeOrderBySortOrderAscUploadedAtAscMediaIDAsc( // Matches the exact ordered gallery repository method used by the service.
                        SOCIETY_ID, // Requires the expected society identifier.
                        SocietyImageType.GALLERY_IMAGE)) // Requires gallery-image media only.
                .thenReturn(List.of(galleryMedia)); // Returns the one gallery item fixture.
        StudentSocietyProfileDTO result = // Declares the service response being asserted.
                service.getStudentSocietyProfile( // Calls the Student-facing society profile method.
                        SOCIETY_ID, STUDENT_EMAIL); // Supplies the society identifier and authenticated Student email.

        assertEquals("https://facebook.com/computing", // Defines the expected trimmed Facebook URL.
                result.getFacebookURL()); // Verifies valid social URLs are trimmed and retained.
        assertNull(result.getInstagramURL()); // Verifies blank social links are normalised to null.
        assertNull(result.getTiktokURL()); // Verifies invalid non-HTTP social values are excluded.
        assertEquals(MembershipState.PENDING, result.getMembershipState()); // Verifies membership eligibility is included for the Student viewer.
        assertEquals("Alex Smith", // Defines the expected executive full name from the linked User fixture.
                result.getExecutives().getFirst().getFullName()); // Verifies executive name mapping works.
        assertEquals("220000001", // Defines the expected executive Student Number used by public-profile navigation.
                result.getExecutives().getFirst().getStudentNumber()); // Verifies the public executive DTO now contains a stable Student identifier.
        assertFalse(result.getExecutives().getFirst().isHasProfilePicture()); // Verifies the executive reports no database-backed picture for this fixture.
        assertEquals("EVT001", // Defines the expected event identifier.
                result.getUpcomingEvents().getFirst().getEventID()); // Verifies upcoming event data is included.
        assertEquals("ANN001", // Defines the expected announcement identifier.
                result.getAnnouncements().getFirst().getAnnouncementID()); // Verifies society announcements are included.
        assertEquals("MEDIA001", // Defines the expected gallery-media identifier.
                result.getGallery().getFirst().getMediaID()); // Verifies society-gallery media is included.
        assertEquals("/media/societies/logos/society-logo.png", // Defines the expected society logo path.
                result.getLogoUrl()); // Verifies the society logo is mapped.
        assertEquals("/media/societies/banners/society-banner.png", // Defines the expected society banner path.
                result.getBannerUrl()); // Verifies the society banner is mapped.
        assertEquals(2030, result.getExecutiveTermYear()); // Verifies the active executive term year is derived from the executive record.
        assertFalse(result.isCanManageProfile()); // Verifies a normal Student cannot manage the society profile.
        assertFalse(result.isCanViewInternalData()); // Verifies a normal Student cannot access private executive/SDO data.

        verify(societyMemberRepository, never()) // Begins verification that the public Student profile does not query internal member counts.
                .countCurrentMembersBySocietyID(any(), any()); // Confirms member-count lookup was not called.
        verify(taskAllocationRepository, never()) // Begins verification that the Student profile does not query internal pending-task metrics.
                .countPendingTasksForSociety(any(), any(), any()); // Confirms task oversight logic was not invoked.
    } // Ends the Student public-profile mapping test.

    @Test // Marks this method as a JUnit test.
    void executiveProfilePictureMetadataComesFromUserProfilePictureTable() { // Verifies executive picture metadata uses UserProfilePicture rather than User.profilePictureURL.
        Society society = society(true); // Creates an active society fixture.
        Student student = student(); // Creates the executive's Student/User fixture.
        Executive executive = executive(student, society); // Creates an active executive fixture linked to the Student.
        UserProfilePicture picture = profilePicture(STUDENT_EMAIL); // Creates a database-backed picture metadata fixture.

        when(studentRepository.findByEmail(STUDENT_EMAIL)) // Stubs viewer resolution.
                .thenReturn(Optional.of(student)); // Returns the Student fixture.
        when(societyRepository.findById(SOCIETY_ID)) // Stubs society lookup.
                .thenReturn(Optional.of(society)); // Returns the active society.
        when(membershipApplicationService.getProfileEligibility( // Stubs membership eligibility because the Student profile service calculates it.
                student, society)) // Supplies the exact fixture Student and Society.
                .thenReturn(SocietyMembershipEligibilityResponseDTO.builder() // Creates a neutral eligibility response.
                        .membershipState(MembershipState.ELIGIBLE) // Uses a valid neutral state; picture assertions do not depend on it.
                        .applicationsAvailable(true) // Allows normal profile rendering without affecting picture assertions.
                        .build()); // Completes the eligibility response.
        when(executiveRepository.findActiveExecutivesForSocietyProfile( // Stubs active executive retrieval.
                SOCIETY_ID, TODAY)) // Requires the expected society and fixed current date.
                .thenReturn(List.of(executive)); // Returns the executive whose picture metadata is under test.
        when(eventRepository.findUpcomingPublishedEventsForSocietyProfile( // Stubs the event preview query required while building the profile.
                eq(SOCIETY_ID), // Requires the expected society identifier.
                eq(TODAY), // Requires the fixed test date.
                any(LocalTime.class), // Accepts current fixed-clock time.
                any(Pageable.class))) // Accepts paging.
                .thenReturn(List.of()); // Returns no events because they are irrelevant to this picture test.
        when(announcementRepository.findProfileAnnouncements( // Stubs the announcement preview query.
                eq(SOCIETY_ID), // Requires the expected society ID.
                anyCollection(), // Accepts the audience resolved for the current viewer.
                any(LocalDateTime.class), // Accepts current time.
                any(Pageable.class))) // Accepts paging.
                .thenReturn(List.of()); // Returns no announcements because they are irrelevant here.
        when(societyMediaRepository // Begins stubbing the gallery-media lookup required during profile construction.
                .findBySocietyIDAndMediaTypeOrderBySortOrderAscUploadedAtAscMediaIDAsc( // Matches the service's ordered gallery query.
                        SOCIETY_ID, // Requires the expected society identifier.
                        SocietyImageType.GALLERY_IMAGE)) // Requires gallery media.
                .thenReturn(List.of()); // Returns no gallery media because it is irrelevant here.
        when(userProfilePictureRepository.findById(STUDENT_EMAIL)) // Stubs retrieval of the picture metadata used for cache-version generation.
                .thenReturn(Optional.of(picture)); // Returns the UserProfilePicture fixture.

        StudentSocietyProfileDTO result = service.getStudentSocietyProfile( // Calls the Student-facing society profile path that exposes the executive committee.
                SOCIETY_ID, STUDENT_EMAIL); // Supplies the target society and authenticated viewer email.

        assertEquals("220000001", // Defines the stable Student Number expected for profile navigation.
                result.getExecutives().getFirst().getStudentNumber()); // Verifies the executive DTO contains the Student Number.
        assertEquals(STUDENT_EMAIL, // Defines the User email expected from the linked User entity.
                result.getExecutives().getFirst().getEmail()); // Verifies the executive DTO retains the expected identity metadata.
        assertTrue(result.getExecutives().getFirst().isHasProfilePicture()); // Verifies the DTO reports that a database-backed profile picture exists.
        assertEquals("2030-08-11T08:30", // Defines the expected serialised picture update timestamp used as the picture version.
                result.getExecutives().getFirst().getProfilePictureVersion()); // Verifies picture version comes from UserProfilePicture.updatedAt.
        assertNull(result.getExecutives().getFirst().getProfilePictureURL()); // Verifies the legacy User.profilePictureURL is no longer exposed as the authoritative executive image source.
    } // Ends the database-backed executive-picture metadata test.

    @Test // Marks this method as a JUnit test.
    void executiveWithoutDatabasePictureUsesPictureFallbackMetadata() { // Verifies an executive without UserProfilePicture is reported as having no persisted picture.
        Society society = society(true); // Creates an active society fixture.
        Student student = student(); // Creates the executive Student/User fixture.
        Executive executive = executive(student, society); // Creates an active executive fixture.

        when(studentRepository.findByEmail(STUDENT_EMAIL)) // Stubs Student viewer resolution.
                .thenReturn(Optional.of(student)); // Returns the fixture Student.
        when(societyRepository.findById(SOCIETY_ID)) // Stubs society lookup.
                .thenReturn(Optional.of(society)); // Returns the active society.
        when(membershipApplicationService.getProfileEligibility( // Stubs the eligibility lookup required by Student profile rendering.
                student, society)) // Supplies the fixture Student and Society.
                .thenReturn(SocietyMembershipEligibilityResponseDTO.builder() // Creates a neutral eligibility response.
                        .membershipState(MembershipState.ELIGIBLE) // Uses a valid neutral state for picture assertions.
                        .applicationsAvailable(true) // Allows profile rendering.
                        .build()); // Completes the eligibility response.
        when(executiveRepository.findActiveExecutivesForSocietyProfile( // Stubs active executive retrieval.
                SOCIETY_ID, TODAY)) // Requires the expected society and date.
                .thenReturn(List.of(executive)); // Returns the one executive.
        when(eventRepository.findUpcomingPublishedEventsForSocietyProfile( // Stubs event preview lookup.
                eq(SOCIETY_ID), // Requires society ID.
                eq(TODAY), // Requires fixed current date.
                any(LocalTime.class), // Accepts fixed current time.
                any(Pageable.class))) // Accepts paging.
                .thenReturn(List.of()); // Returns no events.
        when(announcementRepository.findProfileAnnouncements( // Stubs announcement preview lookup.
                eq(SOCIETY_ID), // Requires society ID.
                anyCollection(), // Accepts the audience resolved for the current viewer.
                any(LocalDateTime.class), // Accepts current timestamp.
                any(Pageable.class))) // Accepts paging.
                .thenReturn(List.of()); // Returns no announcements.
        when(societyMediaRepository // Begins stubbing the gallery-media query.
                .findBySocietyIDAndMediaTypeOrderBySortOrderAscUploadedAtAscMediaIDAsc( // Matches the service's gallery query.
                        SOCIETY_ID, // Requires society ID.
                        SocietyImageType.GALLERY_IMAGE)) // Requires gallery-image media.
                .thenReturn(List.of()); // Returns no gallery media.
        when(userProfilePictureRepository.findById(STUDENT_EMAIL)) // Stubs the current service's version metadata lookup.
                .thenReturn(Optional.empty()); // Returns no picture record.

        StudentSocietyProfileDTO result = service.getStudentSocietyProfile( // Calls the public Student profile mapper.
                SOCIETY_ID, STUDENT_EMAIL); // Supplies society and authenticated viewer.

        assertFalse(result.getExecutives().getFirst().isHasProfilePicture()); // Verifies the DTO correctly reports no picture.
        assertNull(result.getExecutives().getFirst().getProfilePictureVersion()); // Verifies there is no cache/version value when no picture record exists.
        assertNull(result.getExecutives().getFirst().getProfilePictureURL()); // Verifies no legacy image URL is used as fallback.
    } // Ends the no-picture metadata test.

    @Test // Marks this method as a JUnit test.
    void publicCommitteeUsesOfficialPositionOrderWithLegacyRolesLast() { // Verifies committee members are sorted according to recognised executive roles before legacy roles.
        Society society = society(true); // Creates an active society fixture.
        when(societyRepository.findById(SOCIETY_ID)) // Stubs society lookup for public profile rendering.
                .thenReturn(Optional.of(society)); // Returns the test society.
        when(executiveRepository.findActiveExecutivesForSocietyProfile( // Stubs the active executive committee query.
                SOCIETY_ID, TODAY)).thenReturn(List.of( // Returns executives deliberately supplied out of official order.
                executive("220000007", "Legacy Chair", society), // Adds an unrecognised legacy role that should appear last.
                executive("220000006", "PRO", society), // Adds the PRO role.
                executive("220000004", "Deputy Secretary", society), // Adds Deputy Secretary.
                executive("220000005", "Treasurer", society), // Adds Treasurer.
                executive("220000002", "Deputy-President", society), // Adds Deputy President.
                executive("220000003", "SECRETARY", society), // Adds Secretary using uppercase formatting.
                executive("220000001", "President", society))); // Adds President.
        when(eventRepository.findUpcomingPublishedEventsForSocietyProfile( // Stubs event preview loading.
                eq(SOCIETY_ID), // Requires society ID.
                eq(TODAY), // Requires fixed date.
                any(LocalTime.class), // Accepts current time.
                any(Pageable.class))) // Accepts paging.
                .thenReturn(List.of()); // Returns no events.
        when(announcementRepository.findProfileAnnouncements( // Stubs announcement preview loading.
                eq(SOCIETY_ID), // Requires society ID.
                anyCollection(), // Accepts the audience resolved for the current viewer.
                any(LocalDateTime.class), // Accepts current timestamp.
                any(Pageable.class))) // Accepts paging.
                .thenReturn(List.of()); // Returns no announcements.
        when(societyMediaRepository // Begins stubbing the gallery-media query.
                .findBySocietyIDAndMediaTypeOrderBySortOrderAscUploadedAtAscMediaIDAsc( // Matches the service's gallery query.
                        SOCIETY_ID, // Requires society ID.
                        SocietyImageType.GALLERY_IMAGE)) // Requires gallery media.
                .thenReturn(List.of()); // Returns no gallery entries.

        StudentSocietyProfileDTO result = // Declares the public society profile result.
                service.getPublicSocietyProfile(SOCIETY_ID); // Loads the public profile without Student membership-state resolution.

        assertEquals( // Compares the mapped committee order against the approved role order.
                List.of( // Defines the expected ordered list of executive positions.
                        "President", // Expects President first.
                        "Deputy-President", // Expects Deputy President second.
                        "SECRETARY", // Expects Secretary third regardless of casing.
                        "Deputy Secretary", // Expects Deputy Secretary fourth.
                        "Treasurer", // Expects Treasurer fifth.
                        "PRO", // Expects PRO sixth.
                        "Legacy Chair"), // Expects the unrecognised legacy role last.
                result.getExecutives().stream() // Starts mapping the returned executive DTO list.
                        .map(executive -> executive.getPosition()) // Extracts only each executive's position.
                        .toList()); // Collects the positions into a List for comparison.
    } // Ends the executive ordering test.

    @Test // Marks this method as a JUnit test.
    void publicExecutiveFallbackDoesNotResolveStudentMembershipState() { // Verifies public profile access does not attempt Student membership eligibility.
        Society society = society(true); // Creates an active society fixture.
        when(societyRepository.findById(SOCIETY_ID)) // Stubs public society lookup.
                .thenReturn(Optional.of(society)); // Returns the test society.
        stubEmptySections(society); // Stubs all optional public profile sections as empty.

        StudentSocietyProfileDTO result = // Declares the resulting public profile response.
                service.getPublicSocietyProfile(SOCIETY_ID); // Calls public profile retrieval without authenticated Student context.

        assertEquals("Computing Society", result.getSocietyName()); // Verifies the society profile still loads.
        assertNull(result.getMembershipState()); // Verifies no Student membership state is attached to public fallback viewing.
        assertFalse(result.isApplicationsAvailable()); // Verifies no Student application capability is inferred.
        verify(studentRepository, never()).findByEmail(any()); // Verifies no Student viewer resolution occurs.
        verify(membershipApplicationService, never()) // Begins verification that membership eligibility was not queried.
                .getProfileEligibility(any(Student.class), any(Society.class)); // Confirms the eligibility service is not used.
        verify(societyMemberRepository, never()) // Begins verification that public viewing does not expose internal membership counts.
                .countCurrentMembersBySocietyID(any(), any()); // Confirms member-count lookup was not called.
    } // Ends the public-fallback isolation test.

    @Test // Marks this method as a JUnit test.
    void studentsAndPublicExecutiveFallbackCannotInspectInactiveSocieties() { // Verifies inactive societies remain hidden from Student and public viewers.
        Society society = society(false); // Creates an inactive society fixture.
        Student student = student(); // Creates an authenticated Student fixture.
        when(studentRepository.findByEmail(STUDENT_EMAIL)) // Stubs Student resolution.
                .thenReturn(Optional.of(student)); // Returns the Student fixture.
        when(societyRepository.findById(SOCIETY_ID)) // Stubs society lookup.
                .thenReturn(Optional.of(society)); // Returns the inactive society.

        assertThrows(ResourceNotFoundException.class, // Expects inactive Student profile access to be treated as unavailable.
                () -> service.getStudentSocietyProfile( // Invokes the Student profile method inside the exception assertion.
                        SOCIETY_ID, STUDENT_EMAIL)); // Supplies society ID and authenticated email.
        assertThrows(ResourceNotFoundException.class, // Expects public fallback access to the inactive society to be unavailable.
                () -> service.getPublicSocietyProfile(SOCIETY_ID)); // Invokes public profile access.

        verify(membershipApplicationService, never()) // Begins verification that eligibility is not calculated after society rejection.
                .getProfileEligibility(any(Student.class), any(Society.class)); // Confirms eligibility was not queried.
        verify(societyMemberRepository, never()) // Begins verification that internal member counts were not queried.
                .countCurrentMembersBySocietyID(any(), any()); // Confirms no member-count work occurs for inaccessible societies.
    } // Ends the inactive society visibility test.

    @Test // Marks this method as a JUnit test.
    void executiveProfileUsesCurrentApprovedMemberCount() { // Verifies an active executive receives current private oversight metrics for their own society.
        Society society = society(true); // Creates an active society fixture.
        Student student = student(); // Creates the authenticated executive's Student fixture.
        SocietyHighlightResponseDTO publishedHighlight = // Begins construction of a published society highlight.
                SocietyHighlightResponseDTO.builder() // Uses the highlight DTO builder.
                        .highlightID("HIGHLIGHT-PUBLISHED") // Assigns a stable published highlight identifier.
                        .headline("Published story") // Assigns a public headline.
                        .activeStatus(true) // Marks the highlight as published/active.
                        .build(); // Completes the published highlight DTO.
        SocietyHighlightResponseDTO draftHighlight = // Begins construction of a private draft highlight.
                SocietyHighlightResponseDTO.builder() // Uses the highlight DTO builder.
                        .highlightID("HIGHLIGHT-DRAFT") // Assigns a stable draft highlight identifier.
                        .headline("Draft story") // Assigns the draft headline.
                        .article("Manager-only draft article") // Supplies draft-only content for the private executive view.
                        .activeStatus(false) // Marks the highlight as unpublished.
                        .build(); // Completes the draft highlight DTO.
        when(studentRepository.findByEmail(STUDENT_EMAIL)) // Stubs resolution of the authenticated executive's Student record.
                .thenReturn(Optional.of(student)); // Returns the Student fixture.
        when(executiveRepository.existsActiveExecutiveRole( // Stubs active executive-role verification.
                student.getStudentNumber(), SOCIETY_ID, TODAY)) // Requires Student Number, society ID and current date.
                .thenReturn(true); // Confirms the viewer is an active executive of this society.
        when(societyRepository.findById(SOCIETY_ID)) // Stubs society lookup.
                .thenReturn(Optional.of(society)); // Returns the active society fixture.
        when(societyMemberRepository.countCurrentMembersBySocietyID( // Stubs the current approved member-count query.
                SOCIETY_ID, TODAY)).thenReturn(64L); // Returns 64 active/current members.
        when(taskAllocationRepository.countPendingTasksForSociety( // Stubs the pending-task oversight query.
                SOCIETY_ID, TaskStatus.COMPLETE, TODAY)).thenReturn(2L); // Returns two tasks not considered complete.
        when(profileEditAuthorizationService.canEditSocietyProfile( // Stubs the profile-management authorisation check.
                STUDENT_EMAIL, SOCIETY_ID)).thenReturn(true); // Allows this executive to manage the society profile.
        stubEmptySections(society); // Stubs all remaining public profile sections as empty.
        when(societyHighlightService.getPublishedHighlights(SOCIETY_ID))
                .thenReturn(List.of(publishedHighlight));
        when(societyHighlightService.getManageableHighlights(SOCIETY_ID))
                .thenReturn(List.of(publishedHighlight, draftHighlight));

        ExecutiveSocietyProfileDTO result = // Declares the executive profile result.
                service.getExecutiveSocietyProfile( // Calls the private own-society profile service.
                        SOCIETY_ID, STUDENT_EMAIL); // Supplies society ID and authenticated executive email.

        assertEquals(64, result.getNumberOfMembers()); // Verifies the current approved member count is used.
        assertEquals(2, result.getPendingTasks()); // Verifies current pending task count is used.
        assertFalse(result.getAtRisk()); // Verifies 64 members is above the configured at-risk threshold.
        assertEquals(new BigDecimal("5000.00"), // Defines the expected current society balance.
                result.getCurrentBalance()); // Verifies the financial value is mapped correctly.
        assertTrue(result.isCanManageProfile()); // Verifies the executive receives profile-management permission.
        assertEquals(List.of(publishedHighlight), result.getHighlights()); // Verifies public highlights remain present.
        assertEquals( // Begins comparison of the executive-manageable highlights collection.
                List.of(publishedHighlight, draftHighlight), // Defines the expected published + draft list.
                result.getManageableHighlights()); // Verifies the executive can inspect draft highlights.
    } // Ends the private executive society-profile metrics test.

    @Test // Marks this method as a JUnit test.
    void executiveCannotRequestAnotherSocietysPrivateProfile() { // Verifies executives cannot access private management data belonging to another society.
        Student student = student(); // Creates the authenticated executive Student fixture.
        when(studentRepository.findByEmail(STUDENT_EMAIL)) // Stubs Student resolution by authenticated email.
                .thenReturn(Optional.of(student)); // Returns the Student fixture.
        when(executiveRepository.existsActiveExecutiveRole( // Stubs the executive-role check for another society.
                student.getStudentNumber(), "SOC002", TODAY)) // Requires the target other society identifier.
                .thenReturn(false); // Indicates the viewer has no active executive role there.

        assertThrows( // Begins assertion that the service rejects the private cross-society request.
                ForbiddenOperationException.class, // Defines the expected domain authorisation exception.
                () -> service.getExecutiveSocietyProfile( // Invokes the restricted profile method.
                        "SOC002", STUDENT_EMAIL)); // Supplies another society's identifier and the authenticated viewer.

        verify(societyRepository, never()).findById("SOC002"); // Verifies the service rejects access before retrieving private Society data.
        verify(societyMemberRepository, never()) // Begins verification that private oversight calculations were not performed.
                .countCurrentMembersBySocietyID(any(), any()); // Confirms member counts were not queried.
    } // Ends the cross-society executive authorisation test.

    @Test // Marks this method as a JUnit test.
    void sdoCanInspectInactiveSociety() { // Verifies an SDO oversight profile may inspect a society even when it is inactive.
        Society society = society(false); // Creates an inactive society fixture.
        when(societyRepository.findById(SOCIETY_ID)) // Stubs direct society lookup.
                .thenReturn(Optional.of(society)); // Returns the inactive society fixture.
        when(societyMemberRepository.countCurrentMembersBySocietyID( // Stubs current-member count calculation.
                SOCIETY_ID, TODAY)).thenReturn(12L); // Returns twelve current members.
        when(taskAllocationRepository.countPendingTasksForSociety( // Stubs pending-task calculation.
                SOCIETY_ID, TaskStatus.COMPLETE, TODAY)).thenReturn(4L); // Returns four incomplete/pending tasks.
        stubEmptySections(society); // Stubs the remaining public profile sections as empty.

        SDOSocietyProfileDTO result = // Declares the SDO profile response.
                service.getSDOSocietyProfile(SOCIETY_ID); // Calls the SDO oversight society profile method.

        assertFalse(result.getActiveStatus()); // Verifies the inactive state is exposed to SDO oversight.
        assertEquals(12, result.getNumberOfMembers()); // Verifies current member count is exposed.
        assertTrue(result.getAtRisk()); // Verifies the society is flagged at-risk because membership is below the threshold.
        assertEquals(4, result.getPendingTasks()); // Verifies pending-task count is exposed.
    } // Ends the SDO inactive-society oversight test.

    @Test // Marks this method as a JUnit test.
    void allSocietyAnnouncementsRemainPublicAndSocietyScoped() { // Verifies the public society announcement list remains scoped to the requested society.
        Society society = society(false); // Creates a society fixture whose active state does not prevent this direct announcement lookup.
        Announcement first = announcement(); // Creates the first announcement fixture.
        Announcement second = announcement(); // Creates a second announcement based on the standard fixture.
        second.setAnnouncementID("ANN002"); // Changes the second announcement's identifier so the two records are distinct.
        second.setSubject("Venue update"); // Changes the second announcement's subject to verify ordering/content is retained.
        when(societyRepository.findById(SOCIETY_ID)) // Stubs the direct society lookup.
                .thenReturn(Optional.of(society)); // Returns the society fixture.
        when(announcementRepository.findProfileAnnouncements( // Stubs the complete profile-announcement query.
                eq(SOCIETY_ID), // Requires the target society identifier.
                anyCollection(), // Accepts the audience resolved for the current viewer.
                eq(LocalDateTime.now(FIXED_CLOCK)), // Requires the exact current fixed-clock timestamp.
                any(Pageable.class))) // Accepts the unpaged Pageable supplied by the service.
                .thenReturn(List.of(first, second)); // Returns both announcements.

        var result = service.getSocietyAnnouncements(SOCIETY_ID); // Calls the full society-announcement listing method.

        assertEquals(2, result.size()); // Verifies both announcements are returned.
        assertEquals("ANN001", result.getFirst().getAnnouncementID()); // Verifies the first announcement ID is preserved.
        assertEquals("Venue update", result.getLast().getSubject()); // Verifies the second announcement subject is preserved.
        verify(announcementRepository).findProfileAnnouncements( // Verifies the repository was called with the expected society scope.
                eq(SOCIETY_ID), // Verifies the target society ID.
                anyCollection(), // Verifies an explicit allowed-target collection is used.
                eq(LocalDateTime.now(FIXED_CLOCK)), // Verifies active-announcement filtering uses the fixed current time.
                any(Pageable.class)); // Accepts the unpaged pageable parameter.
    } // Ends the full public announcement listing test.

    @Test
    void previewAndFullListUseTheSameAccessibleAudienceAndHasMoreCount() {
        Society society = society(true);
        Student student = student();
        when(societyRepository.findById(SOCIETY_ID)).thenReturn(Optional.of(society));
        when(studentRepository.findByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(membershipApplicationService.getProfileEligibility(student, society))
                .thenReturn(SocietyMembershipEligibilityResponseDTO.builder()
                        .membershipState(MembershipState.ELIGIBLE)
                        .build());
        when(executiveRepository.findActiveExecutivesForSocietyProfile(
                SOCIETY_ID, TODAY)).thenReturn(List.of());
        when(eventRepository.findUpcomingPublishedEventsForSocietyProfile(
                eq(SOCIETY_ID), eq(TODAY), any(LocalTime.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(societyMediaRepository
                .findBySocietyIDAndMediaTypeOrderBySortOrderAscUploadedAtAscMediaIDAsc(
                        SOCIETY_ID, SocietyImageType.GALLERY_IMAGE))
                .thenReturn(List.of());
        when(societyHighlightService.getPublishedHighlights(SOCIETY_ID))
                .thenReturn(List.of());
        List<Announcement> accessible = java.util.stream.IntStream.rangeClosed(1, 4)
                .mapToObj(index -> {
                    Announcement item = announcement();
                    item.setAnnouncementID("ANN00" + index);
                    return item;
                }).toList();
        when(announcementRepository.findProfileAnnouncements(
                eq(SOCIETY_ID),
                eq(List.of(TargetType.STUDENTS)),
                eq(LocalDateTime.now(FIXED_CLOCK)),
                any(Pageable.class))).thenReturn(accessible);

        StudentSocietyProfileDTO preview = service.getStudentSocietyProfile(
                SOCIETY_ID, STUDENT_EMAIL);
        var fullList = service.getSocietyAnnouncements(SOCIETY_ID, STUDENT_EMAIL);

        assertEquals(3, preview.getAnnouncements().size());
        assertTrue(preview.isHasMoreAnnouncements());
        assertEquals(4, fullList.size());
    }

    private void stubEmptySections(Society society) { // Provides common empty stubs for tests that do not care about public profile sections.
        when(executiveRepository.findActiveExecutivesForSocietyProfile( // Stubs the active executive query.
                SOCIETY_ID, TODAY)).thenReturn(List.of()); // Returns no executives.
        when(eventRepository.findUpcomingPublishedEventsForSocietyProfile( // Stubs upcoming event preview retrieval.
                eq(SOCIETY_ID), // Requires the expected society identifier.
                eq(TODAY), // Requires the fixed current date.
                any(LocalTime.class), // Accepts current fixed time.
                any(Pageable.class))) // Accepts paging.
                .thenReturn(List.of()); // Returns no events.
        when(announcementRepository.findProfileAnnouncements( // Stubs profile-announcement preview retrieval.
                eq(SOCIETY_ID), // Requires the expected society identifier.
                anyCollection(), // Accepts the audience resolved for the current viewer.
                any(LocalDateTime.class), // Accepts the current timestamp.
                any(Pageable.class))) // Accepts paging.
                .thenReturn(List.of()); // Returns no announcements.
        when(societyMediaRepository // Begins stubbing the gallery-media lookup used by ProfileSections.
                .findBySocietyIDAndMediaTypeOrderBySortOrderAscUploadedAtAscMediaIDAsc( // Matches the ordered gallery query.
                        SOCIETY_ID, // Requires the test society identifier.
                        SocietyImageType.GALLERY_IMAGE)) // Requires only gallery-image media.
                .thenReturn(List.of()); // Returns no gallery media.
        when(societyHighlightService.getPublishedHighlights(SOCIETY_ID)) // Stubs published highlight retrieval required by the profile sections.
                .thenReturn(List.of()); // Returns no published highlights.
    } // Ends the shared empty-section stub helper.

    private Society society(boolean active) { // Creates a reusable Society entity fixture with predictable public and oversight data.
        Society society = new Society(); // Instantiates a fresh Society entity.
        society.setSocietyID(SOCIETY_ID); // Assigns the standard test society identifier.
        society.setSocietyName("Computing Society"); // Assigns the society's display name.
        society.setSdoStaffNumber("SDO001"); // Assigns the supervising SDO identifier used by announcement queries.
        society.setActiveStatus(active); // Sets active/inactive status according to the individual test.
        society.setMembershipFee(new BigDecimal("75.00")); // Sets a predictable membership fee.
        society.setCurrentBalance(new BigDecimal("5000.00")); // Sets a predictable current balance.
        society.setAnnualBudgetAllocation(new BigDecimal("10000.00")); // Sets a predictable annual budget allocation.
        society.setLogoUrl( // Begins setting the society's persisted logo media path.
                "/media/societies/logos/society-logo.png"); // Supplies the logo path used in mapping assertions.
        society.setBannerUrl( // Begins setting the society's persisted banner media path.
                "/media/societies/banners/society-banner.png"); // Supplies the banner path used in mapping assertions.
        return society; // Returns the completed Society fixture.
    } // Ends the Society fixture helper.

    private Student student() { // Creates a reusable Student entity with a linked User that matches the database-backed profile-picture architecture.
        User user = new User(); // Instantiates the linked User entity.
        user.setEmail(STUDENT_EMAIL); // Sets User.email because UserProfilePicture is keyed using the user's email address.
        user.setFirstName("Alex"); // Sets the executive/student's first name used in public committee display.
        user.setLastName("Smith"); // Sets the executive/student's last name used in public committee display.

        Student student = new Student(); // Instantiates the Student entity linked to the User.
        student.setStudentNumber("220000001"); // Assigns the stable Student Number used for executive identity and public-profile navigation.
        student.setEmail(STUDENT_EMAIL); // Sets the Student's email foreign-key/identity field.
        student.setUser(user); // Connects the Student fixture to its User fixture.
        return student; // Returns the completed Student fixture.
    } // Ends the Student fixture helper.

    private Executive executive(Student student, Society society) { // Creates an active Executive fixture linking a Student to a Society.
        Executive executive = new Executive(); // Instantiates the Executive entity.
        executive.setId(new ExecutiveId( // Creates the composite Executive ID.
                student.getStudentNumber(), // Uses the Student Number as the first identifier component.
                society.getSocietyID(), // Uses the Society ID as the second identifier component.
                TODAY.minusMonths(1))); // Uses a term start date one month before the fixed current date so the role is active.
        executive.setStudent(student); // Links the Executive entity to the Student.
        executive.setSociety(society); // Links the Executive entity to the Society.
        executive.setPosition("President"); // Assigns a default recognised executive position.
        return executive; // Returns the completed Executive fixture.
    } // Ends the Executive fixture helper using an existing Student.

    private Executive executive( // Declares an overload used to create executives with specific Student Numbers and positions.
                                 String studentNumber, // Receives the desired Student Number.
                                 String position, // Receives the desired executive position.
                                 Society society) { // Receives the society to which the executive belongs.
        Student student = student(); // Creates the base Student/User fixture.
        student.setStudentNumber(studentNumber); // Replaces the base Student Number with the requested one.
        Executive executive = executive(student, society); // Creates an Executive using the updated Student fixture.
        executive.setPosition(position); // Replaces the default President position with the requested position.
        return executive; // Returns the customised Executive fixture.
    } // Ends the customised Executive fixture helper.

    private UserProfilePicture profilePicture(String email) { // Creates metadata for a stored database-backed user profile picture.
        UserProfilePicture picture = new UserProfilePicture(); // Instantiates the UserProfilePicture entity.
        picture.setUserEmail(email); // Assigns the User email that acts as the profile-picture primary key.
        picture.setImageData(new byte[]{1, 2, 3}); // Supplies minimal image bytes so the entity represents a valid stored picture fixture.
        picture.setContentType("image/jpeg"); // Defines a realistic image MIME type.
        picture.setOriginalFileName("profile.jpg"); // Provides a representative original file name.
        picture.setFileSize(3L); // Records the byte size matching the small fixture image-data array.
        picture.setUpdatedAt(LocalDateTime.of(2030, 8, 11, 8, 30)); // Sets a deterministic timestamp used by profilePictureVersion assertions.
        return picture; // Returns the completed database-backed picture fixture.
    } // Ends the UserProfilePicture fixture helper.

    private Event event() { // Creates a reusable upcoming published Event fixture.
        Event event = new Event(); // Instantiates a fresh Event entity.
        event.setEventID("EVT001"); // Assigns the standard event identifier.
        event.setEventName("Welcome Evening"); // Assigns a readable event name.
        event.setEventStatus(EventStatus.PUBLISHED); // Marks the event as published so it qualifies for public preview.
        event.setEventDate(TODAY.plusDays(2)); // Places the event two days after the fixed current date so it is upcoming.
        event.setRsvpOpenDate(LocalDateTime.now(FIXED_CLOCK).minusDays(1));
        return event; // Returns the completed Event fixture.
    } // Ends the Event fixture helper.

    private Announcement announcement() { // Creates a reusable active society Announcement fixture.
        Announcement announcement = new Announcement(); // Instantiates a new Announcement entity.
        announcement.setAnnouncementID("ANN001"); // Assigns the standard announcement identifier.
        announcement.setSubject("Applications open"); // Assigns a public announcement subject.
        announcement.setDescription("Join us this semester."); // Assigns the announcement body content.
        announcement.setDatePosted(LocalDateTime.now(FIXED_CLOCK)); // Sets a deterministic posting timestamp using the fixed test clock.
        return announcement; // Returns the completed Announcement fixture.
    } // Ends the Announcement fixture helper.

    private SocietyMedia galleryMedia() { // Creates a reusable Society gallery-media fixture.
        SocietyMedia media = new SocietyMedia(); // Instantiates a new SocietyMedia entity.
        media.setMediaID("MEDIA001"); // Assigns a stable media identifier.
        media.setSocietyID(SOCIETY_ID); // Links the media to the standard test society.
        media.setMediaType(SocietyImageType.GALLERY_IMAGE); // Marks the media as a Society gallery image.
        media.setMediaUrl("/media/societies/gallery/highlight.png"); // Assigns a predictable saved media path.
        media.setCaption("Outreach day"); // Assigns a representative gallery caption.
        media.setSortOrder(0); // Places this media item first in gallery ordering.
        media.setUploadedAt(LocalDateTime.now(FIXED_CLOCK)); // Assigns a deterministic upload timestamp.
        media.setUploadedBy("president@nmu.ac.za"); // Records a representative executive uploader email.
        return media; // Returns the completed SocietyMedia fixture.
    } // Ends the Society gallery-media fixture helper.
} // Ends the SocietyBrowseServiceTests class.
