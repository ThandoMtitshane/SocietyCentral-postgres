package com.societycentral.service;

import com.societycentral.dto.response.ExecutiveSocietyProfileDTO;
import com.societycentral.dto.response.SDOSocietyProfileDTO;
import com.societycentral.dto.response.SocietyAnnouncementSummaryDTO;
import com.societycentral.dto.response.SocietyBrowseSummaryResponseDTO;
import com.societycentral.dto.response.SocietyExecutiveSummaryDTO;
import com.societycentral.dto.response.SocietyGalleryMediaResponseDTO;
import com.societycentral.dto.response.SocietyHighlightResponseDTO;
import com.societycentral.dto.response.SocietyMembershipEligibilityResponseDTO;
import com.societycentral.dto.response.StudentEventSummaryDTO;
import com.societycentral.dto.response.StudentSocietyProfileDTO;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.model.Announcement;
import com.societycentral.model.Event;
import com.societycentral.model.Executive;
import com.societycentral.model.ExecutivePosition;
import com.societycentral.model.Society;
import com.societycentral.model.SocietyImageType;
import com.societycentral.model.SocietyMedia;
import com.societycentral.model.RsvpId;
import com.societycentral.model.Student;
import com.societycentral.model.TargetType;
import com.societycentral.model.TaskStatus;
import com.societycentral.model.User;
import com.societycentral.repository.AnnouncementRepository;
import com.societycentral.repository.EventRepository;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.SocietyMemberRepository;
import com.societycentral.repository.SocietyMediaRepository;
import com.societycentral.repository.SocietyRepository;
import com.societycentral.repository.RSVPRepository;
import com.societycentral.repository.StudentRepository;
import com.societycentral.repository.UserProfilePictureRepository;
import com.societycentral.repository.TaskAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Locale;

/**
 * Provides public and role-scoped society browsing profiles.
 */
@Service
@RequiredArgsConstructor
public class SocietyBrowseService {

    private static final int EVENT_PREVIEW_LIMIT = 5;
    private static final int ANNOUNCEMENT_PREVIEW_LIMIT = 3;
    private static final int AT_RISK_MEMBER_THRESHOLD = 50;
    private static final String NOT_ACTIVE_EXECUTIVE_MESSAGE =
            "Authenticated user is not an active executive of this society.";

    private final SocietyRepository societyRepository;
    private final StudentRepository studentRepository;
    private final ExecutiveRepository executiveRepository;
    private final SocietyMemberRepository societyMemberRepository;
    private final SocietyMediaRepository societyMediaRepository;
    private final EventRepository eventRepository;
    private final AnnouncementRepository announcementRepository;
    private final TaskAllocationRepository taskAllocationRepository;
    private final UserProfilePictureRepository userProfilePictureRepository;
    private final MembershipApplicationService membershipApplicationService;
    private final SocietyProfileEditAuthorizationService
            profileEditAuthorizationService;
    private final SocietyHighlightService societyHighlightService;
    private final AnnouncementAudienceResolver announcementAudienceResolver;
    private final Clock clock;
    @Autowired
    private RSVPRepository rsvpRepository;

    /**
     * Returns public summaries of every active society.
     *
     * @return active public society summaries
     */
    @Transactional(readOnly = true)
    public List<SocietyBrowseSummaryResponseDTO> getActiveSocieties() {
        // 1. Load active societies only
        List<Society> societies = societyRepository.findByActiveStatusTrue();

        // 2. Map only browse-card fields
        return societies.stream()
                .sorted(Comparator.comparing(
                        Society::getSocietyName,
                        Comparator.nullsLast(
                                String.CASE_INSENSITIVE_ORDER)))
                .map(this::mapSummary)
                .toList();
    }

    /**
     * Returns an active society profile with the student's membership state.
     *
     * @param societyID selected society identifier
     * @param authenticatedEmail email supplied by the JWT principal
     * @return student-safe society profile
     */
    @Transactional(readOnly = true)
    public StudentSocietyProfileDTO getStudentSocietyProfile(
            String societyID,
            String authenticatedEmail) {

        // 1. Validate the route identifier and authenticated email
        String validatedSocietyID = requireSocietyID(societyID);
        String validatedEmail = requireAuthenticatedEmail(authenticatedEmail);

        // 2. Resolve optional viewer identity, but never require a
        // membership/application record for public visibility.
        Optional<Student> student = studentRepository.findByEmail(validatedEmail);

        // 3. Find the requested active society. Public visibility is not
        // dependent on membership or on an application record.
        Society society = findActiveSociety(validatedSocietyID);

        // 4. Resolve viewer-specific eligibility when the caller maps to a
        // student. Failure to resolve that optional viewer state must not
        // prevent the public profile from being returned.
        SocietyMembershipEligibilityResponseDTO eligibility =
                student
                        .map(currentStudent -> membershipApplicationService
                                .getProfileEligibility(currentStudent, society))
                        .orElse(null);

        // 5. Load public profile sections
        ProfileSections sections = loadProfileSections(
                society,
                student.map(Student::getStudentNumber).orElse(null));

        // 6. Map the student-safe response
        return mapPublicProfile(society, sections, eligibility);
    }

    /**
     * Returns an active public profile without resolving A100 eligibility.
     * This is used for an executive browsing a society they do not manage.
     *
     * @param societyID selected society identifier
     * @return public-only active society profile
     */
    @Transactional(readOnly = true)
    public StudentSocietyProfileDTO getPublicSocietyProfile(
            String societyID) {
        Society society = findActiveSociety(requireSocietyID(societyID));
        ProfileSections sections = loadProfileSections(society);
        return mapPublicProfile(society, sections, null);
    }

    /**
     * Loads the complete public announcement list used by the profile's
     * compact "View all" expansion. Internal society data is never included.
     */
    @Transactional(readOnly = true)
    public List<SocietyAnnouncementSummaryDTO> getSocietyAnnouncements(String societyID, String authenticatedEmail) {
        Society society = findSociety(requireSocietyID(societyID));

        return announcementRepository.findProfileAnnouncements(
                        society.getSocietyID(),
                        announcementAudienceResolver.resolveForEmail(
                                authenticatedEmail, society.getSocietyID()),
                        LocalDateTime.now(clock),
                        Pageable.unpaged())
                .stream()
                .map(this::mapAnnouncement)
                .toList();
    }

    /** Compatibility overload retained for existing internal callers. */
    public List<SocietyAnnouncementSummaryDTO> getSocietyAnnouncements(String societyID) {
        return getSocietyAnnouncements(societyID, null);
    }

    /** Returns one active article without exposing draft highlights. */
    @Transactional(readOnly = true)
    public SocietyHighlightResponseDTO getSocietyHighlightArticle(
            String societyID,
            String highlightID) {
        Society society = findActiveSociety(requireSocietyID(societyID));
        return societyHighlightService.getPublishedArticle(
                society.getSocietyID(),
                highlightID,
                society.getSocietyName());
    }

    /**
     * Returns private profile data to an active executive of the requested
     * society only.
     *
     * @param societyID requested society identifier
     * @param authenticatedEmail email supplied by the JWT principal
     * @return executive profile for the caller's own society
     */
    @Transactional(readOnly = true)
    public ExecutiveSocietyProfileDTO getExecutiveSocietyProfile(
            String societyID,
            String authenticatedEmail) {

        // 1. Validate the caller and route identifier
        String validatedSocietyID = requireSocietyID(societyID);
        String validatedEmail = requireAuthenticatedEmail(authenticatedEmail);

        // 2. Resolve the caller as a student
        Student student = studentRepository.findByEmail(validatedEmail)
                .orElseThrow(() -> new ForbiddenOperationException(
                        NOT_ACTIVE_EXECUTIVE_MESSAGE));

        // 3. Verify an unexpired executive term for this exact society
        LocalDate today = LocalDate.now(clock);
        if (!executiveRepository.existsActiveExecutiveRole(
                student.getStudentNumber(), validatedSocietyID, today)) {
            throw new ForbiddenOperationException(
                    NOT_ACTIVE_EXECUTIVE_MESSAGE);
        }

        // 4. Find the requested active society
        Society society = findActiveSociety(validatedSocietyID);

        // 5. Load approved member and existing oversight metrics
        OversightMetrics metrics = loadOversightMetrics(society, today);

        // 6. Load public profile sections
        ProfileSections sections = loadProfileSections(
                society, student.getStudentNumber());
        boolean canManageProfile = profileEditAuthorizationService
                .canEditSocietyProfile(
                        validatedEmail, validatedSocietyID);
        List<SocietyHighlightResponseDTO> manageableHighlights = canManageProfile
                ? societyHighlightService.getManageableHighlights(
                        society.getSocietyID())
                : null;

        // 7. Map the private own-society response
        return ExecutiveSocietyProfileDTO.builder()
                .societyID(society.getSocietyID())
                .societyName(society.getSocietyName())
                .acronym(society.getAcronym())
                .description(society.getDescription())
                .societyType(enumName(society.getSocietyType()))
                .logoUrl(nonBlank(society.getLogoUrl()))
                .bannerUrl(nonBlank(society.getBannerUrl()))
                .campus(enumName(society.getCampus()))
                .faculty(enumName(society.getFaculty()))
                .school(enumName(society.getSchool()))
                .vision(society.getVision())
                .mission(society.getMission())
                .membershipFee(moneyOrZero(society.getMembershipFee()))
                .contactEmail(society.getEmail())
                .contactPhone(society.getContactNumber())
                .facebookURL(validHttpUrlOrNull(society.getFacebookURL()))
                .instagramURL(validHttpUrlOrNull(society.getInstagramURL()))
                .tiktokURL(validHttpUrlOrNull(society.getTiktokURL()))
                .numberOfMembers(metrics.numberOfMembers())
                .currentBalance(moneyOrZero(society.getCurrentBalance()))
                .annualBudgetAllocation(
                        moneyOrZero(society.getAnnualBudgetAllocation()))
                .activeStatus(society.getActiveStatus())
                .flagged(society.getIsFlagged())
                .atRisk(metrics.atRisk())
                .pendingTasks(metrics.pendingTasks())
                .canManageProfile(canManageProfile)
                .canViewInternalData(true)
                .executiveTermYear(sections.executiveTermYear())
                .executives(sections.executives())
                .upcomingEvents(sections.upcomingEvents())
                .hasMoreUpcomingEvents(
                        sections.hasMoreUpcomingEvents())
                .highlights(sections.highlights())
                .manageableHighlights(manageableHighlights)
                .gallery(sections.gallery())
                .announcements(sections.announcements())
                .hasMoreAnnouncements(sections.hasMoreAnnouncements())
                .build();
    }

    /**
     * Returns oversight data for any requested society, including inactive
     * societies.
     *
     * @param societyID requested society identifier
     * @return SDO oversight profile
     */
    @Transactional(readOnly = true)
    public SDOSocietyProfileDTO getSDOSocietyProfile(String societyID) {
        // 1. Find the society without applying student activity rules
        Society society = findSociety(requireSocietyID(societyID));
        LocalDate today = LocalDate.now(clock);

        // 2. Load approved member and existing oversight metrics
        OversightMetrics metrics = loadOversightMetrics(society, today);

        // 3. Load public profile sections
        ProfileSections sections = loadProfileSections(society);

        // 4. Map the full oversight response
        return SDOSocietyProfileDTO.builder()
                .societyID(society.getSocietyID())
                .societyName(society.getSocietyName())
                .acronym(society.getAcronym())
                .description(society.getDescription())
                .societyType(enumName(society.getSocietyType()))
                .logoUrl(nonBlank(society.getLogoUrl()))
                .bannerUrl(nonBlank(society.getBannerUrl()))
                .campus(enumName(society.getCampus()))
                .faculty(enumName(society.getFaculty()))
                .school(enumName(society.getSchool()))
                .vision(society.getVision())
                .mission(society.getMission())
                .membershipFee(moneyOrZero(society.getMembershipFee()))
                .contactEmail(society.getEmail())
                .contactPhone(society.getContactNumber())
                .facebookURL(validHttpUrlOrNull(society.getFacebookURL()))
                .instagramURL(validHttpUrlOrNull(society.getInstagramURL()))
                .tiktokURL(validHttpUrlOrNull(society.getTiktokURL()))
                .numberOfMembers(metrics.numberOfMembers())
                .annualBudgetAllocation(
                        moneyOrZero(society.getAnnualBudgetAllocation()))
                .currentBalance(moneyOrZero(society.getCurrentBalance()))
                .activeStatus(society.getActiveStatus())
                .flagged(society.getIsFlagged())
                .atRisk(metrics.atRisk())
                .pendingTasks(metrics.pendingTasks())
                .canManageProfile(false)
                .canViewInternalData(true)
                .executiveTermYear(sections.executiveTermYear())
                .executives(sections.executives())
                .upcomingEvents(sections.upcomingEvents())
                .hasMoreUpcomingEvents(
                        sections.hasMoreUpcomingEvents())
                .highlights(sections.highlights())
                .gallery(sections.gallery())
                .announcements(sections.announcements())
                .hasMoreAnnouncements(sections.hasMoreAnnouncements())
                .build();
    }

    /**
     * Checks whether the caller currently represents the requested society.
     *
     * @param authenticatedEmail email supplied by the JWT principal
     * @param societyID requested society identifier
     * @return true only for an unexpired role in that society
     */
    @Transactional(readOnly = true)
    public boolean isActiveExecutiveOfSociety(
            String authenticatedEmail,
            String societyID) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()
                || societyID == null || societyID.isBlank()) {
            return false;
        }

        return studentRepository.findByEmail(authenticatedEmail.trim())
                .map(student -> executiveRepository.existsActiveExecutiveRole(
                        student.getStudentNumber(),
                        societyID.trim(),
                        LocalDate.now(clock)))
                .orElse(false);
    }

    /**
     * Checks whether the authenticated student holds any current executive
     * role. Executive authentication uses ROLE_STUDENT, so persistence is the
     * authoritative source for this distinction.
     */
    @Transactional(readOnly = true)
    public boolean isActiveExecutive(String authenticatedEmail) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            return false;
        }

        LocalDate today = LocalDate.now(clock);
        return studentRepository.findByEmail(authenticatedEmail.trim())
                .map(student -> !executiveRepository
                        .findActiveExecutiveRoles(
                                student.getStudentNumber(), today)
                        .isEmpty())
                .orElse(false);
    }

    private ProfileSections loadProfileSections(Society society) {
        return loadProfileSections(society, null);
    }

    private ProfileSections loadProfileSections(Society society, String studentNumber) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);

        List<Executive> activeExecutives = executiveRepository
                .findActiveExecutivesForSocietyProfile(
                        society.getSocietyID(), today)
                .stream()
                .sorted(Comparator
                        .comparingInt((Executive executive) ->
                                ExecutivePosition.profileOrder(
                                        executive.getPosition()))
                        .thenComparing(
                                executive -> nonBlank(
                                        executive.getPosition()),
                                Comparator.nullsLast(
                                        String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(executive ->
                                executive.getId().getStudentNumber()))
                .toList();

        List<SocietyExecutiveSummaryDTO> executives = activeExecutives
                .stream()
                .map(this::mapExecutive)
                .toList();

        Integer executiveTermYear = activeExecutives.stream()
                .map(Executive::getId)
                .filter(id -> id != null && id.getTermStartDate() != null)
                .map(id -> id.getTermStartDate().getYear())
                .max(Integer::compareTo)
                .orElse(null);

        List<Event> eventPreview = eventRepository
                .findUpcomingPublishedEventsForSocietyProfile(
                        society.getSocietyID(),
                        today,
                        LocalTime.now(clock),
                        PageRequest.of(0, EVENT_PREVIEW_LIMIT + 1));
        eventPreview = eventPreview.stream()
                .filter(event -> event.getRsvpOpenDate() != null
                        && !event.getRsvpOpenDate().isAfter(now))
                .toList();
        boolean hasMoreUpcomingEvents =
                eventPreview.size() > EVENT_PREVIEW_LIMIT;
        List<StudentEventSummaryDTO> upcomingEvents = eventPreview
                .stream()
                .limit(EVENT_PREVIEW_LIMIT)
                .map(event -> mapEvent(event, society, studentNumber))
                .toList();

        List<Announcement> announcementPreview = announcementRepository
                .findProfileAnnouncements(
                                society.getSocietyID(),
                                announcementAudienceResolver.resolveStudentNumber(
                                        studentNumber,
                                        society.getSocietyID()),
                                now,
                                PageRequest.of(
                                        0,
                                        ANNOUNCEMENT_PREVIEW_LIMIT + 1));
        boolean hasMoreAnnouncements =
                announcementPreview.size() > ANNOUNCEMENT_PREVIEW_LIMIT;
        List<SocietyAnnouncementSummaryDTO> announcements =
                announcementPreview.stream()
                .limit(ANNOUNCEMENT_PREVIEW_LIMIT)
                .map(this::mapAnnouncement)
                .toList();

        List<SocietyGalleryMediaResponseDTO> gallery =
                societyMediaRepository
                        .findBySocietyIDAndMediaTypeOrderBySortOrderAscUploadedAtAscMediaIDAsc(
                                society.getSocietyID(),
                                SocietyImageType.GALLERY_IMAGE)
                        .stream()
                        .map(this::mapGalleryMedia)
                        .toList();

        List<SocietyHighlightResponseDTO> highlights =
                societyHighlightService.getPublishedHighlights(
                        society.getSocietyID());

        return new ProfileSections(
                executiveTermYear,
                executives,
                upcomingEvents,
                hasMoreUpcomingEvents,
                highlights,
                gallery,
                announcements,
                hasMoreAnnouncements);
    }

    private OversightMetrics loadOversightMetrics(
            Society society,
            LocalDate today) {
        int numberOfMembers = Math.toIntExact(
                societyMemberRepository.countCurrentMembersBySocietyID(
                        society.getSocietyID(), today));
        int pendingTasks = Math.toIntExact(
                taskAllocationRepository.countPendingTasksForSociety(
                        society.getSocietyID(),
                        TaskStatus.COMPLETE,
                        today));

        return new OversightMetrics(
                numberOfMembers,
                numberOfMembers < AT_RISK_MEMBER_THRESHOLD,
                pendingTasks);
    }

    private SocietyBrowseSummaryResponseDTO mapSummary(Society society) {
        return SocietyBrowseSummaryResponseDTO.builder()
                .societyID(society.getSocietyID())
                .societyName(society.getSocietyName())
                .acronym(society.getAcronym())
                .description(society.getDescription())
                .societyType(enumName(society.getSocietyType()))
                .logoUrl(nonBlank(society.getLogoUrl()))
                .campus(enumName(society.getCampus()))
                .faculty(enumName(society.getFaculty()))
                .school(enumName(society.getSchool()))
                .membershipFee(moneyOrZero(society.getMembershipFee()))
                .build();
    }

    private StudentSocietyProfileDTO mapPublicProfile(
            Society society,
            ProfileSections sections,
            SocietyMembershipEligibilityResponseDTO eligibility) {
        StudentSocietyProfileDTO.StudentSocietyProfileDTOBuilder builder =
                StudentSocietyProfileDTO.builder()
                        .societyName(society.getSocietyName())
                        .acronym(society.getAcronym())
                        .description(society.getDescription())
                        .societyType(enumName(society.getSocietyType()))
                        .logoUrl(nonBlank(society.getLogoUrl()))
                        .bannerUrl(nonBlank(society.getBannerUrl()))
                        .campus(enumName(society.getCampus()))
                        .faculty(enumName(society.getFaculty()))
                        .school(enumName(society.getSchool()))
                        .vision(society.getVision())
                        .mission(society.getMission())
                        .membershipFee(moneyOrZero(
                                society.getMembershipFee()))
                        .contactEmail(nonBlank(society.getEmail()))
                        .contactPhone(nonBlank(society.getContactNumber()))
                        .facebookURL(validHttpUrlOrNull(
                                society.getFacebookURL()))
                        .instagramURL(validHttpUrlOrNull(
                                society.getInstagramURL()))
                        .tiktokURL(validHttpUrlOrNull(
                                society.getTiktokURL()))
                        .canManageProfile(false)
                        .canViewInternalData(false)
                        .executiveTermYear(sections.executiveTermYear())
                        .executives(sections.executives())
                        .upcomingEvents(sections.upcomingEvents())
                        .hasMoreUpcomingEvents(
                                sections.hasMoreUpcomingEvents())
                        .highlights(sections.highlights())
                        .gallery(sections.gallery())
                        .announcements(sections.announcements())
                        .hasMoreAnnouncements(
                                sections.hasMoreAnnouncements());

        if (eligibility != null) {
            builder.membershipState(eligibility.getMembershipState())
                    .applicationsAvailable(
                            eligibility.isApplicationsAvailable())
                    .latestApplicationID(
                            eligibility.getLatestApplicationID())
                    .latestTrackingReference(
                            eligibility.getLatestTrackingReference());
        }

        return builder.build();
    }

    private SocietyExecutiveSummaryDTO mapExecutive(Executive executive) {
        Student student = executive.getStudent();
        User user = student == null ? null : student.getUser();
        String firstName = user == null ? null : nonBlank(user.getFirstName());
        String lastName = user == null ? null : nonBlank(user.getLastName());
        String fullName = nonBlank(
                String.join(" ",
                        firstName == null ? "" : firstName,
                        lastName == null ? "" : lastName));
        java.util.Optional<com.societycentral.model.UserProfilePicture> picture =
                user == null ? java.util.Optional.empty() : userProfilePictureRepository.findById(user.getEmail());

        return SocietyExecutiveSummaryDTO.builder()
                .studentNumber(student == null ? null : student.getStudentNumber())
                .email(user == null ? null : user.getEmail())
                .firstName(firstName)
                .lastName(lastName)
                .fullName(fullName)
                .position(nonBlank(executive.getPosition()))
                .profilePictureURL(null)
                .profilePictureVersion(picture
                        .map(p -> p.getUpdatedAt() == null ? null : p.getUpdatedAt().toString()).orElse(null))
                .hasProfilePicture(picture.isPresent())
                .build();
    }

    private StudentEventSummaryDTO mapEvent(Event event, Society society) {
        return mapEvent(event, society, null);
    }

    private StudentEventSummaryDTO mapEvent(Event event, Society society, String studentNumber) {
        StudentEventSummaryDTO summary = new StudentEventSummaryDTO();
        summary.setEventID(event.getEventID());
        summary.setEventName(event.getEventName());
        summary.setEventDescription(event.getEventDescription());
        summary.setEventDate(event.getEventDate());
        summary.setEventStartTime(
                event.getEventStartTime() == null
                        ? event.getEventTime()
                        : event.getEventStartTime());
        summary.setEventEndTime(event.getEventEndTime());
        summary.setVenueName(event.getEventVenue());
        summary.setCampus(enumName(event.getEventCampus()));
        summary.setAttendingType(enumName(event.getAttendingType()));
        summary.setPrimarySocietyID(society.getSocietyID());
        summary.setPrimarySocietyName(society.getSocietyName());
        summary.setPosterUrl(
                nonBlank(event.getPosterUrl()) == null
                        ? nonBlank(event.getImageUrl())
                        : nonBlank(event.getPosterUrl()));
        summary.setBannerUrl(nonBlank(event.getBannerUrl()));
        summary.setEventLimit(event.getEventLimit());
        summary.setRsvpOpenDate(event.getRsvpOpenDate());
        summary.setRsvpCloseDate(event.getRsvpCloseDate());
        if (studentNumber != null && rsvpRepository.existsById(
                new RsvpId(event.getEventID(), studentNumber))) {
            summary.setStudentRsvpStatus("CONFIRMED");
        }
        return summary;
    }

    private SocietyAnnouncementSummaryDTO mapAnnouncement(
            Announcement announcement) {
        return SocietyAnnouncementSummaryDTO.builder()
                .announcementID(announcement.getAnnouncementID())
                .subject(announcement.getSubject())
                .description(announcement.getDescription())
                .datePosted(announcement.getDatePosted())
                .expireDate(announcement.getExpireDate())
                .build();
    }

    private SocietyGalleryMediaResponseDTO mapGalleryMedia(
            SocietyMedia media) {
        return SocietyGalleryMediaResponseDTO.builder()
                .mediaID(media.getMediaID())
                .mediaUrl(nonBlank(media.getMediaUrl()))
                .caption(nonBlank(media.getCaption()))
                .sortOrder(media.getSortOrder())
                .uploadedAt(media.getUploadedAt())
                .build();
    }

    private Society findActiveSociety(String societyID) {
        Society society = findSociety(societyID);
        if (!Boolean.TRUE.equals(society.getActiveStatus())) {
            throw new ResourceNotFoundException("Society not found.");
        }
        return society;
    }

    private Society findSociety(String societyID) {
        return societyRepository.findById(societyID)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Society not found."));
    }

    private String requireSocietyID(String societyID) {
        if (societyID == null || societyID.isBlank()) {
            throw new IllegalArgumentException("Society ID is required.");
        }
        return societyID.trim();
    }

    private String requireAuthenticatedEmail(String authenticatedEmail) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new BadCredentialsException("Authentication is required.");
        }
        return authenticatedEmail.trim();
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String nonBlank(String value) {
        if (value == null) {
            return null;
        }
        String normalised = value.trim();
        return normalised.isEmpty() ? null : normalised;
    }

    private String validHttpUrlOrNull(String value) {
        String normalised = nonBlank(value);
        if (normalised == null) {
            return null;
        }

        try {
            URI uri = URI.create(normalised);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                return null;
            }
            String lowerScheme = scheme.toLowerCase(Locale.ROOT);
            return lowerScheme.equals("http") || lowerScheme.equals("https")
                    ? normalised
                    : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private BigDecimal moneyOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record ProfileSections(
            Integer executiveTermYear,
            List<SocietyExecutiveSummaryDTO> executives,
            List<StudentEventSummaryDTO> upcomingEvents,
            boolean hasMoreUpcomingEvents,
            List<SocietyHighlightResponseDTO> highlights,
            List<SocietyGalleryMediaResponseDTO> gallery,
            List<SocietyAnnouncementSummaryDTO> announcements,
            boolean hasMoreAnnouncements) {
    }

    private record OversightMetrics(
            int numberOfMembers,
            boolean atRisk,
            int pendingTasks) {
    }
}
