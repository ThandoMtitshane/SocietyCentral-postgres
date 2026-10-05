package com.societycentral.service;

import com.societycentral.model.*;
import com.societycentral.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AnnouncementService {


    private final SDORepository sdoRepository;
    private final StudentRepository studentRepository;
    private final ExecutiveRepository executiveRepository;
    private final AnnouncementRepository announcementRepository;
    private final SocietyRepository societyRepository;
    private final SocietyMemberRepository societyMemberRepository;
    private final NotificationService notificationService;
    private final AnnouncementAudienceResolver announcementAudienceResolver;

    @Autowired
    public AnnouncementService(AnnouncementRepository announcementRepository,
                               SocietyRepository societyRepository,
                               StudentRepository studentRepository,
                               ExecutiveRepository executiveRepository,
                               SDORepository sdoRepository,
                               SocietyMemberRepository societyMemberRepository,
                               NotificationService notificationService,
                               AnnouncementAudienceResolver announcementAudienceResolver) {
        this.announcementRepository = announcementRepository;
        this.societyRepository = societyRepository;
        this.studentRepository = studentRepository;
        this.executiveRepository = executiveRepository;
        this.sdoRepository = sdoRepository;
        this.societyMemberRepository = societyMemberRepository;
        this.notificationService = notificationService;
        this.announcementAudienceResolver = announcementAudienceResolver;
    }

    public List<Announcement> findAll() {
        return announcementRepository.findAll();
    }

    public List<Announcement> findActive() {
        // BUSINESS RULE: "Each announcement must include ... an expiry
        // date/removal date." Active = not yet expired.
        return announcementRepository.findByExpireDateGreaterThanEqual(LocalDateTime.now());
    }

    public Optional<Announcement> findById(String announcementID) {
        return announcementRepository.findById(announcementID);
    }

    public List<Announcement> findForViewer(String email) {
        List<Announcement> active = announcementRepository.findVisible(LocalDateTime.now());
        Student student = studentRepository.findByEmail(email).orElse(null);
        Optional<SDO> sdo = sdoRepository.findByEmail(email);
        Set<String> sdoSocieties = sdo.map(value -> societyRepository.findBySdoStaffNumber(value.getStaffNumber()).stream()
                .map(Society::getSocietyID).collect(Collectors.toSet())).orElse(Set.of());
        return active.stream().filter(a -> {
            String societyID = a.getSociety() == null ? null : a.getSociety().getSocietyID();
            if (sdo.isPresent() && (societyID == null || sdoSocieties.contains(societyID))) return true;
            return student != null && announcementAudienceResolver
                    .resolve(student, societyID)
                    .contains(a.getTargetType());
        }).sorted((a, b) -> java.util.Comparator.nullsLast(LocalDateTime::compareTo).reversed()
                .compare(a.getDatePosted(), b.getDatePosted())).toList();
    }

    public List<Announcement> findForManager(String email) {
        LocalDateTime now = LocalDateTime.now();
        Student student = studentRepository.findByEmail(email).orElse(null);
        Set<String> executiveSocieties = activeExecutiveSocietyIds(student);
        Optional<SDO> sdo = sdoRepository.findByEmail(email);
        Set<String> supervised = sdo.map(value -> societyRepository.findBySdoStaffNumber(value.getStaffNumber()).stream()
                .map(Society::getSocietyID).collect(Collectors.toSet())).orElse(Set.of());
        return announcementRepository.findAll().stream().filter(a -> {
            String societyID = a.getSociety() == null ? null : a.getSociety().getSocietyID();
            boolean executiveScope = societyID != null && executiveSocieties.contains(societyID);
            boolean sdoScope = sdo.isPresent() && (societyID == null ? a.getSentBy().equals(email) : supervised.contains(societyID));
            return executiveScope || sdoScope;
        }).sorted(java.util.Comparator.comparing(Announcement::getDatePosted, java.util.Comparator.nullsLast(LocalDateTime::compareTo)).reversed()).toList();
    }

    public boolean canView(String email, Announcement announcement) {
        return findForViewer(email).stream().anyMatch(a -> a.getAnnouncementID().equals(announcement.getAnnouncementID()));
    }

    public boolean canManage(String email, Announcement announcement) {
        return findForManager(email).stream().anyMatch(a -> a.getAnnouncementID().equals(announcement.getAnnouncementID()));
    }

    public boolean canEditOrRemove(String email, Announcement announcement) {
        return !announcement.isRemoved() && isAuthorisedManager(announcement, email);
    }

    public List<Announcement> findByTargetType(TargetType targetType) {
        return announcementRepository.findByTargetType(targetType);
    }

    public List<Announcement> findBySender(String sentByEmail) {
        return announcementRepository.findBySentBy(sentByEmail);
    }

    public Announcement create(Announcement announcement) {

        LocalDateTime now = LocalDateTime.now();
        if (announcement.getPublishAt() == null) announcement.setPublishAt(now);
        if (announcement.getPublishAt().isBefore(now)) throw new IllegalArgumentException("Publish date and time cannot be in the past.");
        if (announcement.getExpireDate() == null || !announcement.getExpireDate().isAfter(announcement.getPublishAt())) throw new IllegalArgumentException("Expiry must be after the publish date and time.");
        if (announcement.getExpireDate().isAfter(announcement.getPublishAt().plusDays(30))) throw new IllegalArgumentException("Announcements cannot remain active for more than 30 days.");
        if (announcement.getDatePosted() == null) announcement.setDatePosted(now);
        if (announcement.getExpireDate() == null || !announcement.getExpireDate().isAfter(now)) {
            throw new IllegalArgumentException("Announcement expiry must be a future date and time.");
        }

        long count = announcementRepository.count() + 1;

        announcement.setAnnouncementID(
                String.format("ANN%03d", count)
        );

        // TODO: BUSINESS RULE - "Only authorised users may create and
        // distribute announcements." Verify announcement.getSentBy()
        // corresponds to a User permitted to post (SDO or Executive,
        // depending on targetType - confirm scope with business rules).
        // Resolve the target society if one was supplied.
        if (announcement.getSociety() != null) {

            Society society = societyRepository.findById(
                    announcement.getSociety().getSocietyID()
            ).orElseThrow(() ->
                    new IllegalArgumentException("Target society does not exist."));

            announcement.setSociety(society);
        }
        // Determine whether the sender is an Executive.
        Student student = studentRepository.findByEmail(
                announcement.getSentBy()).orElse(null);

        if (student != null) {

            List<Executive> executives = activeExecutiveRoles(student);

            // Executive announcements always belong to the
            // executive's own society.
            if (!executives.isEmpty()) {

                if (executives.size() > 1) {
                    throw new IllegalArgumentException("Multiple active Executive societies require an explicit management context.");
                }

                announcement.setSociety(
                        executives.get(0).getSociety());

            } else {

                // Sender is not an Executive.
                // Check whether the sender is an SDO.
                Optional<SDO> optionalSdo =
                        sdoRepository.findByEmail(announcement.getSentBy());

                if (optionalSdo.isPresent()) {

                    // Null means all societies supervised by the SDO.
                    if (announcement.getSociety() != null) {

                        List<Society> supervisedSocieties =
                                societyRepository.findBySdoStaffNumber(
                                        optionalSdo.get().getStaffNumber());

                        boolean authorised = supervisedSocieties.stream()
                                .anyMatch(society ->
                                        society.getSocietyID().equals(
                                                announcement.getSociety().getSocietyID()));

                        if (!authorised) {
                            throw new IllegalArgumentException(
                                    "SDO is not authorised to send announcements to this society.");
                        }
                    }

                } else {

                    throw new IllegalArgumentException(
                            "Only Executives and SDOs may create announcements.");
                }

            }

        } else {

            // Sender is not a Student.
            // Check whether the sender is an SDO.
            Optional<SDO> optionalSdo =
                    sdoRepository.findByEmail(announcement.getSentBy());

            if (optionalSdo.isPresent()) {

                // Null means all societies supervised by the SDO.
                if (announcement.getSociety() != null) {

                    List<Society> supervisedSocieties =
                            societyRepository.findBySdoStaffNumber(
                                    optionalSdo.get().getStaffNumber());

                    boolean authorised = supervisedSocieties.stream()
                            .anyMatch(society ->
                                    society.getSocietyID().equals(
                                            announcement.getSociety().getSocietyID()));

                    if (!authorised) {
                        throw new IllegalArgumentException(
                                "SDO is not authorised to send announcements to this society.");
                    }
                }

            } else {

                throw new IllegalArgumentException(
                        "Only Executives and SDOs may create announcements.");
            }

        }
        announcement.setDatePosted(LocalDateTime.now());

        // TODO: once created, this is also where a fan-out to
        // NotificationService could happen - creating Notification rows
        // for every user in the targetType group (STUDENTS/MEMBERS/
        // EXECUTIVES). Consider whether that happens synchronously here or
        // via an async/event-driven approach for performance with large
        // recipient groups.
        Announcement saved = announcementRepository.save(announcement);
        if (!saved.getPublishAt().isAfter(now)) notifyEligibleRecipients(saved);
        return saved;
    }

    @org.springframework.transaction.annotation.Transactional
    public void notifyEligibleRecipients(Announcement announcement) {
        if (announcement.isRemoved() || announcement.getPublishAt() == null || announcement.getExpireDate() == null) return;
        LocalDateTime now = LocalDateTime.now();
        if (announcement.getPublishAt().isAfter(now) || !announcement.getExpireDate().isAfter(now)) return;

        String societyID = announcement.getSociety() == null ? null : announcement.getSociety().getSocietyID();
        java.util.Set<String> societyIDs = societyID == null
                ? (sdoRepository.findByEmail(announcement.getSentBy()).map(sdo -> societyRepository.findBySdoStaffNumber(sdo.getStaffNumber()).stream().map(Society::getSocietyID).collect(Collectors.toSet())).orElse(Set.of()))
                : Set.of(societyID);
        String societyName = announcement.getSociety() == null ? null : announcement.getSociety().getSocietyName();
        Set<String> recipients;
        if (announcement.getTargetType() == TargetType.STUDENTS) {
            recipients = studentRepository.findAll().stream().map(Student::getEmail).collect(Collectors.toSet());
        } else if (announcement.getTargetType() == TargetType.MEMBERS) {
            recipients = societyIDs.stream().flatMap(id -> societyMemberRepository.findCurrentMembersBySocietyID(id, java.time.LocalDate.now()).stream()).map(member -> member.getStudent().getEmail()).collect(Collectors.toSet());
        } else {
            recipients = societyIDs.stream().flatMap(id -> executiveRepository.findCurrentExecutivesBySociety(id, java.time.LocalDate.now()).stream()).map(executive -> executive.getStudent().getEmail()).collect(Collectors.toSet());
        }
        recipients.forEach(email -> notificationService.createAnnouncementNotificationIfAbsent(email, societyName, announcement.getSubject(), announcement.getAnnouncementID()));
    }

    public Announcement update(String id, com.societycentral.dto.request.AnnouncementRequestDTO request, String email) {
        Announcement existing = announcementRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Announcement not found."));
        authorizeManager(existing, email);
        if (existing.isRemoved()) throw new IllegalArgumentException("Removed announcements are read-only.");
        LocalDateTime now = LocalDateTime.now();
        if (existing.getExpireDate() != null && !existing.getExpireDate().isAfter(now)) throw new IllegalArgumentException("Expired announcements are read-only.");
        LocalDateTime publish = existing.getPublishAt();
        if (publish == null) publish = existing.getDatePosted();
        if (publish.isAfter(now) && request.getPublishAt() != null) publish = request.getPublishAt();
        if (!publish.isAfter(now) && request.getPublishAt() != null && !request.getPublishAt().equals(existing.getPublishAt())) throw new IllegalArgumentException("The publish time of an active announcement cannot be changed.");
        boolean active = !publish.isAfter(now);
        if (!request.getExpireDate().isAfter(now) || !request.getExpireDate().isAfter(publish) || request.getExpireDate().isAfter(publish.plusDays(30))) throw new IllegalArgumentException("Expiry must be after publish, now, and within 30 days.");
        existing.setSubject(request.getSubject().trim()); existing.setDescription(request.getDescription().trim()); existing.setTargetType(request.getTargetType()); existing.setPublishAt(publish); existing.setExpireDate(request.getExpireDate());
        return announcementRepository.save(existing);
    }

    public Announcement remove(String id, String email) {
        Announcement announcement = announcementRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Announcement not found."));
        authorizeManager(announcement, email);
        if (!announcement.isRemoved()) {
            announcement.setRemoved(true);
            announcement.setRemovedAt(LocalDateTime.now());
            announcement.setRemovedBy(email);
            return announcementRepository.save(announcement);
        }
        return announcement;
    }

    private void authorizeManager(Announcement announcement, String email) {
        if (isAuthorisedManager(announcement, email)) return;
        throw new org.springframework.security.access.AccessDeniedException("You are not authorised to manage this announcement.");
    }

    private boolean isAuthorisedManager(Announcement announcement, String email) {
        if (announcement.getSociety() == null) {
            return email.equalsIgnoreCase(announcement.getSentBy())
                    && sdoRepository.findByEmail(email).isPresent();
        }
        String societyID = announcement.getSociety().getSocietyID();
        Student student = studentRepository.findByEmail(email).orElse(null);
        if (student != null) {
            return executiveRepository.findActiveExecutiveRoles(
                            student.getStudentNumber(), java.time.LocalDate.now())
                    .stream()
                    .filter(e -> e.getSociety().getSocietyID().equals(societyID))
                    .anyMatch(e -> email.equalsIgnoreCase(announcement.getSentBy())
                            || e.getPosition() != null
                            && e.getPosition().trim().equalsIgnoreCase("Secretary"));
        }
        return email.equalsIgnoreCase(announcement.getSentBy())
                && sdoRepository.findByEmail(email)
                .map(sdo -> societyRepository
                        .findBySdoStaffNumber(sdo.getStaffNumber()).stream()
                        .anyMatch(society -> society.getSocietyID().equals(societyID)))
                .orElse(false);
    }

    /**
     * Executive management is intentionally single-society. The current data
     * model permits historical and potentially overlapping appointments, so a
     * caller must not silently select an arbitrary row as its authority.
     */
    private Set<String> activeExecutiveSocietyIds(Student student) {
        List<Executive> roles = activeExecutiveRoles(student);
        if (roles.size() > 1) {
            throw new IllegalStateException("Multiple active Executive societies require an explicit management context.");
        }
        return roles.isEmpty() ? Set.of() : Set.of(roles.get(0).getId().getSocietyID());
    }

    private List<Executive> activeExecutiveRoles(Student student) {
        return student == null ? List.of() : executiveRepository.findActiveExecutiveRoles(
                student.getStudentNumber(), java.time.LocalDate.now());
    }
}
