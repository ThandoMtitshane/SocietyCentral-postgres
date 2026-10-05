package com.societycentral.service;

import com.societycentral.dto.request.EditMessageRequestDTO;
import com.societycentral.dto.request.MentionRequestDTO;
import com.societycentral.dto.request.SendMessageRequestDTO;
import com.societycentral.dto.request.StartDirectConversationRequestDTO;
import com.societycentral.dto.response.*;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import com.societycentral.repository.projection.ExecutiveDirectoryView;
import com.societycentral.repository.projection.SdoDirectoryView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Business rules for Society Central's institutional messaging feature.
 * Active executives may use society groups and executive direct messages.
 * SDO users and current Presidents/Secretaries share a lazily-created society
 * leadership channel. Access is enforced here so REST calls cannot bypass UI
 * controls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingService {

    static final Duration EDIT_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_PREVIEW_LENGTH = 120;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConversationSdoParticipantRepository sdoParticipantRepository;
    private final MessageRepository messageRepository;
    private final MessageMentionRepository mentionRepository;
    private final ExecutiveRepository executiveRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final SDORepository sdoRepository;
    private final SocietyRepository societyRepository;
    private final HosterRepository hosterRepository;
    private final EventRepository eventRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    private final Map<String, String> studentNameCache = new ConcurrentHashMap<>();
    private final Map<String, String> sdoNameCache = new ConcurrentHashMap<>();

    private record ActorContext(
            String email,
            Student student,
            SDO sdo,
            List<Executive> roles,
            Set<String> societyIDs) {

        boolean isExecutive() {
            return student != null;
        }

        boolean isSdo() {
            return sdo != null;
        }

        String studentNumber() {
            return student == null ? null : student.getStudentNumber();
        }

        String sdoStaffNumber() {
            return sdo == null ? null : sdo.getStaffNumber();
        }

        boolean sent(Message message) {
            return isExecutive()
                    ? Objects.equals(studentNumber(), message.getSenderStudentNumber())
                    : Objects.equals(sdoStaffNumber(), message.getSenderSdoStaffNumber());
        }
    }

    // ================================================================
    // Authenticated actor context
    // ================================================================

    private ActorContext requireActor(String authenticatedEmail) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new BadCredentialsException("Authentication is required.");
        }
        String email = authenticatedEmail.trim();
        LocalDate today = LocalDate.now(clock);

        Student student = studentRepository.findByEmail(email).orElse(null);
        if (student != null) {
            List<Executive> roles = executiveRepository.findActiveExecutiveRoles(
                    student.getStudentNumber(), today);
            if (!roles.isEmpty()) {
                Set<String> societyIDs = roles.stream()
                        .map(role -> role.getId().getSocietyID())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                return new ActorContext(
                        email, student, null, List.copyOf(roles), societyIDs);
            }
        }

        SDO sdo = sdoRepository.findByEmail(email).orElse(null);
        if (sdo != null) {
            Set<String> societyIDs = societyRepository
                    .findBySdoStaffNumber(sdo.getStaffNumber()).stream()
                    .map(Society::getSocietyID)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return new ActorContext(
                    email, null, sdo, List.of(), societyIDs);
        }

        throw new ForbiddenOperationException(
                "Messaging is available to active society executives and SDO users only.");
    }

    private ActorContext requireExecutive(String authenticatedEmail) {
        ActorContext actor = requireActor(authenticatedEmail);
        if (!actor.isExecutive()) {
            throw new ForbiddenOperationException(
                    "This messaging action is available to active executives only.");
        }
        return actor;
    }

    private ActorContext requireSdo(String authenticatedEmail) {
        ActorContext actor = requireActor(authenticatedEmail);
        if (!actor.isSdo()) {
            throw new ForbiddenOperationException(
                    "Only an authorised SDO user can start this conversation.");
        }
        return actor;
    }

    // ================================================================
    // Inbox
    // ================================================================

    /**
     * Returns current conversations. Executive society groups are lazily
     * ensured; existing SDO channels are synchronized without creating empty
     * channels for every society.
     */
    @Transactional
    public List<ConversationSummaryView> getInbox(String authenticatedEmail) {
        ActorContext actor = requireActor(authenticatedEmail);
        if (actor.isExecutive()) {
            actor.societyIDs().forEach(this::ensureSocietyGroup);
        }
        synchronizeExistingSdoChannels(actor.societyIDs());

        List<String> conversationIDs = actor.isExecutive()
                ? conversationRepository.findActiveConversationIdsForStudent(
                        actor.studentNumber())
                : conversationRepository.findActiveConversationIdsForSdo(
                        actor.sdoStaffNumber());

        List<ConversationSummaryView> inbox = new ArrayList<>();
        for (String conversationID : conversationIDs) {
            conversationRepository.findById(conversationID)
                    .map(conversation -> toSummary(conversation, actor))
                    .ifPresent(inbox::add);
        }
        return inbox;
    }

    private void synchronizeExistingSdoChannels(Set<String> societyIDs) {
        for (String societyID : societyIDs) {
            conversationRepository.findByTypeAndSocietyID(
                            ConversationType.SDO_SOCIETY, societyID)
                    .ifPresent(this::synchronizeSdoSocietyParticipants);
        }
    }

    private ConversationSummaryView toSummary(
            Conversation conversation, ActorContext actor) {
        LocalDateTime lastReadAt = lastReadAt(conversation, actor);
        Message last = messageRepository
                .findFirstByConversationIDOrderByCreatedAtDesc(
                        conversation.getConversationID())
                .orElse(null);
        long unread = actor.isExecutive()
                ? messageRepository.countUnread(
                        conversation.getConversationID(),
                        actor.studentNumber(), lastReadAt)
                : messageRepository.countUnreadForSdo(
                        conversation.getConversationID(),
                        actor.sdoStaffNumber(), lastReadAt);
        boolean incomingRequest = conversation.getType() == ConversationType.DIRECT
                && conversation.getStatus() == ConversationStatus.PENDING
                && !Objects.equals(actor.studentNumber(),
                        conversation.getInitiatedByStudentNumber());

        return ConversationSummaryView.builder()
                .conversationID(conversation.getConversationID())
                .type(conversation.getType())
                .status(conversation.getStatus())
                .title(displayTitle(conversation, actor))
                .subtitle(displaySubtitle(conversation, actor))
                .societyID(conversation.getSocietyID())
                .lastMessagePreview(last == null ? null
                        : preview(last.getStatus() == MessageStatus.DELETED
                                ? "This message was deleted" : last.getBody()))
                .lastMessageSenderName(last == null ? null : senderName(last))
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(unread)
                .contactReason(conversation.getContactReason())
                .incomingRequest(incomingRequest)
                .build();
    }

    private LocalDateTime lastReadAt(
            Conversation conversation, ActorContext actor) {
        if (actor.isExecutive()) {
            return participantRepository
                    .findByIdConversationIDAndIdStudentNumber(
                            conversation.getConversationID(), actor.studentNumber())
                    .map(ConversationParticipant::getLastReadAt)
                    .orElse(null);
        }
        return sdoParticipantRepository
                .findByIdConversationIDAndIdSdoStaffNumber(
                        conversation.getConversationID(), actor.sdoStaffNumber())
                .map(ConversationSdoParticipant::getLastReadAt)
                .orElse(null);
    }

    // ================================================================
    // Society executive group membership
    // ================================================================

    /**
     * Ensures the society executive group mirrors current appointments.
     * Former participants retain history but receive a leftAt timestamp.
     */
    @Transactional
    public Conversation ensureSocietyGroup(String societyID) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        Conversation group = conversationRepository
                .findByTypeAndSocietyID(ConversationType.SOCIETY_GROUP, societyID)
                .orElseGet(() -> {
                    Conversation created = new Conversation();
                    created.setConversationID(UUID.randomUUID().toString());
                    created.setType(ConversationType.SOCIETY_GROUP);
                    created.setSocietyID(societyID);
                    created.setStatus(ConversationStatus.ACTIVE);
                    created.setTitle(societyGroupTitle(societyID));
                    created.setCreatedAt(now);
                    return conversationRepository.save(created);
                });

        Set<String> currentStudents = executiveRepository
                .findCurrentExecutivesBySociety(societyID, today).stream()
                .map(executive -> executive.getId().getStudentNumber())
                .collect(Collectors.toSet());
        synchronizeExecutiveParticipants(group, currentStudents, now);
        return group;
    }

    private void synchronizeExecutiveParticipants(
            Conversation conversation,
            Set<String> currentStudents,
            LocalDateTime now) {
        List<ConversationParticipant> existing = participantRepository
                .findByIdConversationID(conversation.getConversationID());
        Map<String, ConversationParticipant> byStudent = existing.stream()
                .collect(Collectors.toMap(
                        participant -> participant.getId().getStudentNumber(),
                        participant -> participant,
                        (first, ignored) -> first));

        for (String studentNumber : currentStudents) {
            ConversationParticipant participant = byStudent.get(studentNumber);
            if (participant == null) {
                addExecutiveParticipant(conversation, studentNumber, now);
            } else if (participant.getLeftAt() != null) {
                participant.setLeftAt(null);
                participant.setJoinedAt(now);
                participantRepository.save(participant);
            }
        }
        for (ConversationParticipant participant : existing) {
            if (!currentStudents.contains(participant.getId().getStudentNumber())
                    && participant.getLeftAt() == null) {
                participant.setLeftAt(now);
                participantRepository.save(participant);
            }
        }
    }

    private ConversationParticipant addExecutiveParticipant(
            Conversation conversation,
            String studentNumber,
            LocalDateTime now) {
        ConversationParticipant participant = new ConversationParticipant();
        participant.setId(new ConversationParticipantId(
                conversation.getConversationID(), studentNumber));
        participant.setConversation(conversation);
        participant.setStudent(studentRepository.findById(studentNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Executive student record was not found.")));
        participant.setRole("MEMBER");
        participant.setJoinedAt(now);
        return participantRepository.save(participant);
    }

    // ================================================================
    // SDO society leadership membership
    // ================================================================

    /**
     * Sends the first or a later message in a supervised society's
     * institutional channel. The first message creates the channel lazily.
     */
    @Transactional
    public ConversationDetailView startSdoSocietyConversation(
            String authenticatedEmail,
            String societyID,
            SendMessageRequestDTO request) {
        ActorContext actor = requireSdo(authenticatedEmail);
        Society society = requireSociety(societyID);
        if (!Objects.equals(society.getSdoStaffNumber(), actor.sdoStaffNumber())) {
            throw new ForbiddenOperationException(
                    "You may only message leadership of societies assigned to you.");
        }
        if (!Boolean.TRUE.equals(society.getActiveStatus())) {
            throw new IllegalStateException(
                    "Messaging is unavailable for an inactive society.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Conversation conversation = conversationRepository
                .findByTypeAndSocietyID(ConversationType.SDO_SOCIETY, societyID)
                .orElseGet(() -> {
                    Conversation created = new Conversation();
                    created.setConversationID(UUID.randomUUID().toString());
                    created.setType(ConversationType.SDO_SOCIETY);
                    created.setSocietyID(societyID);
                    created.setTitle(society.getSocietyName() + " Leadership");
                    created.setStatus(ConversationStatus.ACTIVE);
                    created.setCreatedAt(now);
                    return conversationRepository.save(created);
                });
        synchronizeSdoSocietyParticipants(conversation);
        requireParticipant(conversation, actor);
        requireActiveConversation(conversation);

        Message message = persistMessage(
                conversation, actor, request.getBody(),
                request.getReplyToMessageID(), request.getMentions());
        afterMessagePersisted(conversation, actor, message);
        return getConversation(
                authenticatedEmail, conversation.getConversationID(), 0, 30);
    }

    private void synchronizeSdoSocietyParticipants(Conversation conversation) {
        if (conversation.getType() != ConversationType.SDO_SOCIETY) return;
        Society society = requireSociety(conversation.getSocietyID());
        LocalDateTime now = LocalDateTime.now(clock);
        Set<String> leaders = executiveRepository
                .findCurrentExecutivesBySociety(
                        society.getSocietyID(), LocalDate.now(clock)).stream()
                .filter(executive -> {
                    ExecutivePosition position = ExecutivePosition
                            .fromStoredValue(executive.getPosition());
                    return position == ExecutivePosition.PRESIDENT
                            || position == ExecutivePosition.SECRETARY;
                })
                .map(executive -> executive.getId().getStudentNumber())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        synchronizeExecutiveParticipants(conversation, leaders, now);

        String assignedStaff = society.getSdoStaffNumber();
        SDO assignedSdo = sdoRepository.findById(assignedStaff)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "The society's assigned SDO was not found."));
        List<ConversationSdoParticipant> existing = sdoParticipantRepository
                .findByIdConversationID(conversation.getConversationID());
        ConversationSdoParticipant assigned = existing.stream()
                .filter(participant -> Objects.equals(
                        participant.getId().getSdoStaffNumber(), assignedStaff))
                .findFirst().orElse(null);
        if (assigned == null) {
            addSdoParticipant(conversation, assignedSdo, now);
        } else if (assigned.getLeftAt() != null) {
            assigned.setLeftAt(null);
            assigned.setJoinedAt(now);
            sdoParticipantRepository.save(assigned);
        }
        for (ConversationSdoParticipant participant : existing) {
            if (!Objects.equals(
                    participant.getId().getSdoStaffNumber(), assignedStaff)
                    && participant.getLeftAt() == null) {
                participant.setLeftAt(now);
                sdoParticipantRepository.save(participant);
            }
        }
    }

    private ConversationSdoParticipant addSdoParticipant(
            Conversation conversation, SDO sdo, LocalDateTime now) {
        ConversationSdoParticipant participant =
                new ConversationSdoParticipant();
        participant.setId(new ConversationSdoParticipantId(
                conversation.getConversationID(), sdo.getStaffNumber()));
        participant.setConversation(conversation);
        participant.setSdo(sdo);
        participant.setRole("SDO");
        participant.setJoinedAt(now);
        return sdoParticipantRepository.save(participant);
    }

    // ================================================================
    // Display helpers
    // ================================================================

    private String displayName(String studentNumber) {
        if (studentNumber == null) return "Unknown";
        return studentNameCache.computeIfAbsent(studentNumber, number ->
                studentRepository.findById(number)
                        .map(Student::getEmail)
                        .flatMap(userRepository::findById)
                        .map(this::userName)
                        .orElse("Unknown"));
    }

    private String displaySdoName(String staffNumber) {
        if (staffNumber == null) return "Student Development Office";
        return sdoNameCache.computeIfAbsent(staffNumber, number ->
                sdoRepository.findById(number)
                        .map(SDO::getEmail)
                        .flatMap(userRepository::findById)
                        .map(this::userName)
                        .orElse("Student Development Office"));
    }

    private String userName(User user) {
        return (safe(user.getFirstName()) + " " + safe(user.getLastName())).trim();
    }

    private String senderName(Message message) {
        return message.getSenderStudentNumber() != null
                ? displayName(message.getSenderStudentNumber())
                : displaySdoName(message.getSenderSdoStaffNumber());
    }

    private String senderEmail(Message message) {
        return message.getSenderStudentNumber() != null
                ? emailOfStudent(message.getSenderStudentNumber())
                : emailOfSdo(message.getSenderSdoStaffNumber());
    }

    private String societyGroupTitle(String societyID) {
        return societyRepository.findById(societyID)
                .map(society -> society.getSocietyName() + " Executives")
                .orElse(societyID + " Executives");
    }

    private String displayTitle(
            Conversation conversation, ActorContext actor) {
        if (conversation.getType() == ConversationType.SOCIETY_GROUP) {
            return conversation.getTitle() != null
                    ? conversation.getTitle()
                    : societyGroupTitle(conversation.getSocietyID());
        }
        if (conversation.getType() == ConversationType.SDO_SOCIETY) {
            return actor.isSdo()
                    ? societyRepository.findById(conversation.getSocietyID())
                            .map(society -> society.getSocietyName() + " Leadership")
                            .orElse("Society Leadership")
                    : "Student Development Office";
        }
        // DIRECT: name the other participant, which may be a student (exec) or
        // an SDO. Prefer whichever participant is not the viewer.
        String otherStudent = participantRepository
                .findByIdConversationID(conversation.getConversationID()).stream()
                .map(participant -> participant.getId().getStudentNumber())
                .filter(number -> !Objects.equals(number, actor.studentNumber()))
                .findFirst().orElse(null);
        if (otherStudent != null) {
            return displayName(otherStudent);
        }
        String otherSdo = sdoParticipantRepository
                .findByIdConversationID(conversation.getConversationID()).stream()
                .map(participant -> participant.getId().getSdoStaffNumber())
                .filter(number -> !Objects.equals(number, actor.sdoStaffNumber()))
                .findFirst().orElse(null);
        if (otherSdo != null) {
            return displaySdoName(otherSdo);
        }
        return "Direct message";
    }

    private String displaySubtitle(
            Conversation conversation, ActorContext actor) {
        if (conversation.getType() == ConversationType.SOCIETY_GROUP) {
            long count = participantRepository
                    .findActiveByConversationID(conversation.getConversationID())
                    .size();
            return count + (count == 1
                    ? " current executive" : " current executives");
        }
        if (conversation.getType() == ConversationType.SDO_SOCIETY) {
            if (actor.isSdo()) return "President and Secretary";
            return societyRepository.findById(conversation.getSocietyID())
                    .map(Society::getSdo)
                    .map(SDO::getUser)
                    .map(User::getCampus)
                    .map(campus -> "SDO · " + campus)
                    .orElse("SDO");
        }
        String otherStudent = participantRepository
                .findByIdConversationID(conversation.getConversationID()).stream()
                .map(participant -> participant.getId().getStudentNumber())
                .filter(number -> !Objects.equals(number, actor.studentNumber()))
                .findFirst().orElse(null);
        if (otherStudent != null) {
            return executiveContext(otherStudent);
        }
        boolean hasOtherSdo = sdoParticipantRepository
                .findByIdConversationID(conversation.getConversationID()).stream()
                .anyMatch(participant -> !Objects.equals(
                        participant.getId().getSdoStaffNumber(),
                        actor.sdoStaffNumber()));
        return hasOtherSdo ? "Student Development Officer" : null;
    }

    private String executiveContext(String studentNumber) {
        Executive role = executiveRepository.findActiveExecutiveRoles(
                        studentNumber, LocalDate.now(clock)).stream()
                .findFirst().orElse(null);
        if (role == null) return null;
        String societyName = role.getSociety() == null
                ? role.getId().getSocietyID()
                : role.getSociety().getSocietyName();
        return safe(role.getPosition()) + " · " + societyName;
    }

    private String preview(String body) {
        if (body == null) return null;
        String trimmed = body.strip();
        return trimmed.length() <= MAX_PREVIEW_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_PREVIEW_LENGTH - 1) + "…";
    }

    // ================================================================
    // Conversation detail and history
    // ================================================================

    @Transactional
    public ConversationDetailView getConversation(
            String authenticatedEmail, String conversationID,
            int page, int size) {
        ActorContext actor = requireActor(authenticatedEmail);
        Conversation conversation = requireConversation(conversationID);
        synchronizeIfInstitutional(conversation);
        requireParticipant(conversation, actor);

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        var pageResult = messageRepository
                .findByConversationIDOrderByCreatedAtDesc(
                        conversationID, PageRequest.of(safePage, safeSize));
        List<Message> messages = pageResult.getContent();
        Map<String, List<MentionView>> mentionsByMessage = loadMentions(messages);
        LocalDateTime now = LocalDateTime.now(clock);
        List<MessageView> messageViews = messages.stream()
                .map(message -> toMessageView(
                        message, actor, now,
                        mentionsByMessage.getOrDefault(
                                message.getMessageID(), List.of())))
                .toList();
        boolean incomingRequest = conversation.getType() == ConversationType.DIRECT
                && conversation.getStatus() == ConversationStatus.PENDING
                && !Objects.equals(actor.studentNumber(),
                        conversation.getInitiatedByStudentNumber());
        markConversationRead(conversation, actor, now);
        publishConversationUpdate(conversation, actor);

        return ConversationDetailView.builder()
                .conversationID(conversation.getConversationID())
                .type(conversation.getType())
                .status(conversation.getStatus())
                .title(displayTitle(conversation, actor))
                .subtitle(displaySubtitle(conversation, actor))
                .societyID(conversation.getSocietyID())
                .contactReason(conversation.getContactReason())
                .incomingRequest(incomingRequest)
                .canSend(conversation.getStatus() == ConversationStatus.ACTIVE)
                .participants(loadParticipantViews(conversation))
                .messages(messageViews)
                .page(safePage)
                .size(safeSize)
                .totalMessages(pageResult.getTotalElements())
                .hasMore(pageResult.hasNext())
                .build();
    }

    private void synchronizeIfInstitutional(Conversation conversation) {
        if (conversation.getType() == ConversationType.SDO_SOCIETY) {
            synchronizeSdoSocietyParticipants(conversation);
        }
    }

    private List<ConversationParticipantView> loadParticipantViews(
            Conversation conversation) {
        List<ConversationParticipantView> views = new ArrayList<>();
        for (ConversationParticipant participant : participantRepository
                .findActiveByConversationID(conversation.getConversationID())) {
            String studentNumber = participant.getId().getStudentNumber();
            Student student = studentRepository.findById(studentNumber).orElse(null);
            Executive role = activeRoleForConversation(
                    studentNumber, conversation.getSocietyID());
            views.add(ConversationParticipantView.builder()
                    .participantType("EXECUTIVE")
                    .studentNumber(studentNumber)
                    .email(student == null ? null : student.getEmail())
                    .name(displayName(studentNumber))
                    .position(role == null ? null : role.getPosition())
                    .societyID(role == null ? null : role.getId().getSocietyID())
                    .societyName(role == null || role.getSociety() == null
                            ? null : role.getSociety().getSocietyName())
                    .build());
        }
        for (ConversationSdoParticipant participant : sdoParticipantRepository
                .findActiveByConversationID(conversation.getConversationID())) {
            String staffNumber = participant.getId().getSdoStaffNumber();
            SDO sdo = sdoRepository.findById(staffNumber).orElse(null);
            User user = sdo == null ? null
                    : userRepository.findById(sdo.getEmail()).orElse(null);
            views.add(ConversationParticipantView.builder()
                    .participantType("SDO")
                    .sdoStaffNumber(staffNumber)
                    .email(sdo == null ? null : sdo.getEmail())
                    .name(displaySdoName(staffNumber))
                    .position("Student Development Office")
                    .campus(user == null || user.getCampus() == null
                            ? null : user.getCampus().toString())
                    .build());
        }
        return views;
    }

    private Executive activeRoleForConversation(
            String studentNumber, String societyID) {
        List<Executive> roles = executiveRepository.findActiveExecutiveRoles(
                studentNumber, LocalDate.now(clock));
        if (societyID != null) {
            return roles.stream()
                    .filter(role -> Objects.equals(
                            societyID, role.getId().getSocietyID()))
                    .findFirst().orElse(null);
        }
        return roles.stream().findFirst().orElse(null);
    }

    private Map<String, List<MentionView>> loadMentions(List<Message> messages) {
        if (messages.isEmpty()) return Map.of();
        List<String> ids = messages.stream().map(Message::getMessageID).toList();
        Map<String, List<MentionView>> result = new LinkedHashMap<>();
        for (MessageMention mention : mentionRepository.findByMessageIDIn(ids)) {
            result.computeIfAbsent(
                    mention.getMessageID(), ignored -> new ArrayList<>())
                    .add(toMentionView(mention));
        }
        return result;
    }

    private MentionView toMentionView(MessageMention mention) {
        String label = mention.getMentionType() == MentionType.USER
                ? displayName(mention.getTargetStudentNumber())
                : eventRepository.findById(mention.getTargetEventID())
                        .map(Event::getEventName).orElse("event");
        return MentionView.builder()
                .mentionType(mention.getMentionType())
                .targetStudentNumber(mention.getTargetStudentNumber())
                .targetEventID(mention.getTargetEventID())
                .label(label)
                .build();
    }

    private MessageView toMessageView(
            Message message, ActorContext viewer,
            LocalDateTime now, List<MentionView> mentions) {
        boolean mine = viewer.sent(message);
        boolean withinWindow = !now.isAfter(
                message.getCreatedAt().plus(EDIT_WINDOW));
        boolean visible = message.getStatus() == MessageStatus.VISIBLE;
        String replyPreview = null;
        String replySender = null;
        if (message.getReplyToMessageID() != null) {
            Message parent = messageRepository
                    .findById(message.getReplyToMessageID()).orElse(null);
            if (parent != null) {
                replySender = senderName(parent);
                replyPreview = parent.getStatus() == MessageStatus.DELETED
                        ? "This message was deleted" : preview(parent.getBody());
            }
        }
        boolean sdoSender = message.getSenderSdoStaffNumber() != null;
        return MessageView.builder()
                .messageID(message.getMessageID())
                .conversationID(message.getConversationID())
                .senderStudentNumber(message.getSenderStudentNumber())
                .senderSdoStaffNumber(message.getSenderSdoStaffNumber())
                .senderEmail(senderEmail(message))
                .senderType(sdoSender ? "SDO" : "EXECUTIVE")
                .senderName(senderName(message))
                .senderContext(sdoSender ? "Student Development Office" : null)
                .body(visible ? message.getBody() : null)
                .status(message.getStatus())
                .createdAt(message.getCreatedAt())
                .editedAt(message.getEditedAt())
                .edited(message.getEditedAt() != null)
                .replyToMessageID(message.getReplyToMessageID())
                .replyToSenderName(replySender)
                .replyToPreview(replyPreview)
                .mentions(mentions)
                .mine(mine)
                .canEdit(mine && visible && withinWindow)
                .canDelete(mine && visible && withinWindow)
                .build();
    }

    // ================================================================
    // Sending and structured mentions
    // ================================================================

    @Transactional
    public MessageView sendMessage(
            String authenticatedEmail, String conversationID,
            SendMessageRequestDTO request) {
        ActorContext actor = requireActor(authenticatedEmail);
        Conversation conversation = requireConversation(conversationID);
        synchronizeIfInstitutional(conversation);
        requireParticipant(conversation, actor);
        requireActiveConversation(conversation);
        Message message = persistMessage(
                conversation, actor, request.getBody(),
                request.getReplyToMessageID(), request.getMentions());
        return afterMessagePersisted(conversation, actor, message);
    }

    /** Persists message data after workflow authorization has succeeded. */
    private Message persistMessage(
            Conversation conversation, ActorContext actor, String body,
            String replyToMessageID, List<MentionRequestDTO> mentionRequests) {
        String trimmed = requireBody(body);
        LocalDateTime now = LocalDateTime.now(clock);
        if (replyToMessageID != null && !replyToMessageID.isBlank()) {
            Message parent = messageRepository.findById(replyToMessageID)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "The message being replied to was not found."));
            if (!Objects.equals(parent.getConversationID(),
                    conversation.getConversationID())) {
                throw new IllegalArgumentException(
                        "You can only reply to a message in this conversation.");
            }
        }

        Message message = new Message();
        message.setMessageID(UUID.randomUUID().toString());
        message.setConversationID(conversation.getConversationID());
        if (actor.isExecutive()) {
            message.setSenderStudentNumber(actor.studentNumber());
        } else {
            message.setSenderSdoStaffNumber(actor.sdoStaffNumber());
        }
        message.setBody(trimmed);
        message.setReplyToMessageID(
                replyToMessageID == null || replyToMessageID.isBlank()
                        ? null : replyToMessageID);
        message.setStatus(MessageStatus.VISIBLE);
        message.setCreatedAt(now);
        messageRepository.save(message);

        persistMentions(conversation, message, actor, mentionRequests);
        conversation.setLastMessageAt(now);
        conversationRepository.save(conversation);
        markConversationRead(conversation, actor, now);
        return message;
    }

    private MessageView afterMessagePersisted(
            Conversation conversation, ActorContext actor, Message message) {
        List<MentionView> mentions = loadMentions(List.of(message))
                .getOrDefault(message.getMessageID(), List.of());
        MessageView view = toMessageView(
                message, actor, LocalDateTime.now(clock), mentions);
        notifyRecipients(conversation, actor, message);
        publishRealtime(conversation, view);
        return view;
    }

    private void persistMentions(
            Conversation conversation, Message message, ActorContext actor,
            List<MentionRequestDTO> requests) {
        if (requests == null || requests.isEmpty()) return;
        for (MentionRequestDTO request : requests) {
            if (request == null || request.getMentionType() == null) continue;
            MessageMention mention = new MessageMention();
            mention.setMentionID(UUID.randomUUID().toString());
            mention.setMessageID(message.getMessageID());
            mention.setMentionType(request.getMentionType());

            if (request.getMentionType() == MentionType.USER) {
                String target = request.getTargetStudentNumber();
                if (target == null || target.isBlank()
                        || !studentRepository.existsById(target)) {
                    throw new IllegalArgumentException(
                            "Mentioned user was not found.");
                }
                if (!participantRepository.isActiveParticipant(
                        conversation.getConversationID(), target)) {
                    throw new IllegalArgumentException(
                            "You may only mention a current conversation participant.");
                }
                mention.setTargetStudentNumber(target);
            } else {
                String eventID = request.getTargetEventID();
                if (eventID == null || eventID.isBlank()
                        || !isEventMentionableBy(actor, conversation, eventID)) {
                    throw new IllegalArgumentException(
                            "You can only mention events belonging to this messaging context.");
                }
                mention.setTargetEventID(eventID);
            }
            mentionRepository.save(mention);
        }
    }

    private boolean isEventMentionableBy(
            ActorContext actor, Conversation conversation, String eventID) {
        Set<String> allowedSocieties = conversation.getSocietyID() == null
                ? actor.societyIDs() : Set.of(conversation.getSocietyID());
        return allowedSocieties.stream().anyMatch(societyID ->
                hosterRepository.existsByIdEventIDAndIdSocietyID(
                        eventID, societyID));
    }

    // ================================================================
    // Executive direct-message request state machine
    // ================================================================

    /**
     * Creates a direct conversation and its one opening message. Cross-society
     * requests stay PENDING until the recipient responds. An existing ACTIVE
     * conversation treats this endpoint as a normal send; an existing PENDING
     * request cannot be used to add another opening message.
     */
    @Transactional
    public ConversationDetailView startDirectConversation(
            String authenticatedEmail,
            StartDirectConversationRequestDTO request) {
        ActorContext actor = requireActor(authenticatedEmail);

        // Resolve the recipient (executive or SDO).
        boolean recipientIsSdo = request.isSdoRecipient();
        String recipientStudent = request.getRecipientStudentNumber();
        String recipientSdo = request.getRecipientSdoStaffNumber();

        if (recipientIsSdo) {
            if (recipientSdo == null || recipientSdo.isBlank()) {
                throw new IllegalArgumentException("Recipient is required.");
            }
            if (actor.isSdo()
                    && recipientSdo.equals(actor.sdoStaffNumber())) {
                throw new IllegalArgumentException(
                        "You cannot start a conversation with yourself.");
            }
            if (sdoRepository.findById(recipientSdo).isEmpty()) {
                throw new ResourceNotFoundException("That SDO was not found.");
            }
        } else {
            if (recipientStudent == null || recipientStudent.isBlank()) {
                throw new IllegalArgumentException("Recipient is required.");
            }
            if (actor.isExecutive()
                    && recipientStudent.equals(actor.studentNumber())) {
                throw new IllegalArgumentException(
                        "You cannot start a conversation with yourself.");
            }
            if (activeSocietyIDsOf(recipientStudent).isEmpty()) {
                throw new ResourceNotFoundException(
                        "That executive is no longer active.");
            }
        }

        // Reason gate: only cross-society executive-to-executive chats become a
        // request. Any chat involving an SDO is institutional and opens ACTIVE.
        boolean bothExecutives = actor.isExecutive() && !recipientIsSdo;
        boolean sameScope = true;
        if (bothExecutives) {
            Set<String> recipientSocieties = activeSocietyIDsOf(recipientStudent);
            sameScope = !Collections.disjoint(
                    actor.societyIDs(), recipientSocieties);
        }

        // Reuse an existing (non-rejected) conversation between the two.
        Conversation existing = findExistingDirect(
                actor, recipientIsSdo, recipientStudent, recipientSdo);
        if (existing != null) {
            requireParticipant(existing, actor);
            requireActiveConversation(existing);
            Message message = persistMessage(
                    existing, actor, request.getBody(), null,
                    request.getMentions());
            afterMessagePersisted(existing, actor, message);
            return getConversation(
                    authenticatedEmail, existing.getConversationID(), 0, 30);
        }

        String reason = request.getContactReason() == null
                ? null : request.getContactReason().trim();
        if (!sameScope && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException(
                    "A reason is required to message an executive outside your society.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Conversation conversation = new Conversation();
        conversation.setConversationID(UUID.randomUUID().toString());
        conversation.setType(ConversationType.DIRECT);
        conversation.setStatus(sameScope
                ? ConversationStatus.ACTIVE : ConversationStatus.PENDING);
        conversation.setContactReason(sameScope ? null : reason);
        if (actor.isExecutive()) {
            conversation.setInitiatedByStudentNumber(actor.studentNumber());
        }
        conversation.setCreatedAt(now);
        conversationRepository.save(conversation);

        // Wire both participants across the appropriate tables.
        addActorParticipant(conversation, actor, now);
        if (recipientIsSdo) {
            SDO recipientSdoEntity = sdoRepository.findById(recipientSdo)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "That SDO was not found."));
            addSdoParticipant(conversation, recipientSdoEntity, now);
        } else {
            addExecutiveParticipant(conversation, recipientStudent, now);
        }

        // Conversation creation owns the sole message allowed while a
        // cross-society request is PENDING. Later sends require ACTIVE.
        Message opening = persistMessage(
                conversation, actor, request.getBody(), null,
                request.getMentions());
        notifyDirectConversationStart(
                conversation, actor, recipientIsSdo,
                recipientStudent, recipientSdo, sameScope);
        MessageView openingView = toMessageView(
                opening, actor, now,
                loadMentions(List.of(opening))
                        .getOrDefault(opening.getMessageID(), List.of()));
        publishRealtime(conversation, openingView);
        return getConversation(
                authenticatedEmail, conversation.getConversationID(), 0, 30);
    }

    /** Finds a reusable (non-rejected) direct conversation between the pair. */
    private Conversation findExistingDirect(
            ActorContext actor, boolean recipientIsSdo,
            String recipientStudent, String recipientSdo) {
        List<Conversation> candidates;
        if (actor.isExecutive() && !recipientIsSdo) {
            candidates = conversationRepository.findDirectConversationsBetween(
                    actor.studentNumber(), recipientStudent);
        } else if (actor.isExecutive()) { // exec -> sdo
            candidates = conversationRepository
                    .findDirectConversationsBetweenStudentAndSdo(
                            actor.studentNumber(), recipientSdo);
        } else if (!recipientIsSdo) { // sdo -> exec
            candidates = conversationRepository
                    .findDirectConversationsBetweenStudentAndSdo(
                            recipientStudent, actor.sdoStaffNumber());
        } else { // sdo -> sdo
            candidates = conversationRepository.findDirectConversationsBetweenSdos(
                    actor.sdoStaffNumber(), recipientSdo);
        }
        return candidates.stream()
                .filter(conversation -> conversation.getStatus()
                        != ConversationStatus.REJECTED)
                .findFirst().orElse(null);
    }

    /** Adds the authenticated actor as a participant of a direct conversation. */
    private void addActorParticipant(
            Conversation conversation, ActorContext actor, LocalDateTime now) {
        if (actor.isExecutive()) {
            addExecutiveParticipant(conversation, actor.studentNumber(), now);
        } else {
            addSdoParticipant(conversation, actor.sdo(), now);
        }
    }

    /** Notifies the recipient that a direct conversation was started. */
    private void notifyDirectConversationStart(
            Conversation conversation, ActorContext actor,
            boolean recipientIsSdo, String recipientStudent,
            String recipientSdo, boolean sameScope) {
        if (recipientIsSdo) {
            String email = emailOfSdo(recipientSdo);
            if (email != null) {
                createNotification(email,
                        "New message",
                        actorDisplayName(actor) + " sent you a message.",
                        NotificationType.MESSAGE_RECEIVED,
                        conversation.getConversationID());
            }
        } else {
            notifyExecutiveConversationStart(
                    conversation, actor, recipientStudent, sameScope);
        }
    }

    private String actorDisplayName(ActorContext actor) {
        return actor.isExecutive()
                ? displayName(actor.studentNumber())
                : displaySdoName(actor.sdoStaffNumber());
    }

    private Set<String> activeSocietyIDsOf(String studentNumber) {
        return executiveRepository.findActiveExecutiveRoles(
                        studentNumber, LocalDate.now(clock)).stream()
                .map(executive -> executive.getId().getSocietyID())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional
    public ConversationDetailView respondToRequest(
            String authenticatedEmail, String conversationID, boolean accept) {
        ActorContext actor = requireExecutive(authenticatedEmail);
        Conversation conversation = requireConversation(conversationID);
        requireParticipant(conversation, actor);
        if (conversation.getType() != ConversationType.DIRECT
                || conversation.getStatus() != ConversationStatus.PENDING) {
            throw new IllegalStateException(
                    "This request has already been answered.");
        }
        if (Objects.equals(actor.studentNumber(),
                conversation.getInitiatedByStudentNumber())) {
            throw new ForbiddenOperationException(
                    "Only the recipient can respond to this request.");
        }
        conversation.setStatus(accept
                ? ConversationStatus.ACTIVE : ConversationStatus.REJECTED);
        conversationRepository.save(conversation);
        notifyRequestResponse(conversation, actor, accept);
        publishConversationUpdates(conversation);
        return getConversation(authenticatedEmail, conversationID, 0, 30);
    }

    // ================================================================
    // Edit/delete window
    // ================================================================

    @Transactional
    public MessageView editMessage(
            String authenticatedEmail, String conversationID,
            String messageID, EditMessageRequestDTO request) {
        ActorContext actor = requireActor(authenticatedEmail);
        Conversation conversation = requireConversation(conversationID);
        synchronizeIfInstitutional(conversation);
        requireParticipant(conversation, actor);
        Message message = requireMessageInConversation(messageID, conversationID);
        requireOwnEditableMessage(message, actor);

        message.setBody(requireBody(request.getBody()));
        message.setEditedAt(LocalDateTime.now(clock));
        messageRepository.save(message);
        mentionRepository.deleteAll(mentionRepository.findByMessageID(messageID));
        persistMentions(conversation, message, actor, request.getMentions());
        MessageView view = toMessageView(
                message, actor, LocalDateTime.now(clock),
                loadMentions(List.of(message))
                        .getOrDefault(messageID, List.of()));
        publishRealtime(conversation, view);
        return view;
    }

    @Transactional
    public MessageView deleteMessage(
            String authenticatedEmail, String conversationID,
            String messageID) {
        ActorContext actor = requireActor(authenticatedEmail);
        Conversation conversation = requireConversation(conversationID);
        synchronizeIfInstitutional(conversation);
        requireParticipant(conversation, actor);
        Message message = requireMessageInConversation(messageID, conversationID);
        requireOwnEditableMessage(message, actor);

        message.setStatus(MessageStatus.DELETED);
        message.setEditedAt(LocalDateTime.now(clock));
        messageRepository.save(message);
        MessageView view = toMessageView(
                message, actor, LocalDateTime.now(clock), List.of());
        publishRealtime(conversation, view);
        return view;
    }

    private void requireOwnEditableMessage(
            Message message, ActorContext actor) {
        if (!actor.sent(message)) {
            throw new ForbiddenOperationException(
                    "You can only edit or delete your own messages.");
        }
        if (message.getStatus() == MessageStatus.DELETED) {
            throw new IllegalStateException(
                    "This message has already been deleted.");
        }
        if (LocalDateTime.now(clock).isAfter(
                message.getCreatedAt().plus(EDIT_WINDOW))) {
            throw new IllegalStateException(
                    "Messages can only be edited or deleted within 15 minutes of sending.");
        }
    }

    // ================================================================
    // Read state
    // ================================================================

    @Transactional
    public void markRead(String authenticatedEmail, String conversationID) {
        ActorContext actor = requireActor(authenticatedEmail);
        Conversation conversation = requireConversation(conversationID);
        synchronizeIfInstitutional(conversation);
        requireParticipant(conversation, actor);
        markConversationRead(conversation, actor, LocalDateTime.now(clock));
        publishConversationUpdate(conversation, actor);
    }

    private void markConversationRead(
            Conversation conversation, ActorContext actor, LocalDateTime at) {
        if (actor.isExecutive()) {
            participantRepository
                    .findByIdConversationIDAndIdStudentNumber(
                            conversation.getConversationID(), actor.studentNumber())
                    .ifPresent(participant -> {
                        participant.setLastReadAt(at);
                        participantRepository.save(participant);
                    });
            return;
        }
        sdoParticipantRepository
                .findByIdConversationIDAndIdSdoStaffNumber(
                        conversation.getConversationID(), actor.sdoStaffNumber())
                .ifPresent(participant -> {
                    participant.setLastReadAt(at);
                    sdoParticipantRepository.save(participant);
                });
    }

    // ================================================================
    // Directory and mentionable events
    // ================================================================

    @Transactional(readOnly = true)
    public List<DirectoryEntryView> searchDirectory(
            String authenticatedEmail, String query) {
        ActorContext actor = requireActor(authenticatedEmail);
        String pattern = query == null || query.isBlank()
                ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        LocalDate today = LocalDate.now(clock);
        List<DirectoryEntryView> results = new ArrayList<>();

        if (actor.isExecutive()) {
            // Executives see the executives of their own society/societies
            // (no reason needed), plus the SDO(s) supervising those societies.
            if (!actor.societyIDs().isEmpty()) {
                executiveRepository.searchDirectoryInSocieties(
                                actor.societyIDs(), pattern,
                                actor.studentNumber(), today)
                        .forEach(view -> results.add(
                                executiveEntry(view, true)));
                societyRepository.findSupervisingSdoStaffNumbers(
                                actor.societyIDs())
                        .forEach(staffNumber ->
                                addSdoEntry(results, staffNumber, pattern, true));
            }
        } else {
            // SDOs see the executives of every society they supervise, plus
            // other SDOs.
            if (!actor.societyIDs().isEmpty()) {
                executiveRepository.searchDirectoryInSocieties(
                                actor.societyIDs(), pattern, "", today)
                        .forEach(view -> results.add(
                                executiveEntry(view, true)));
            }
            sdoRepository.searchDirectory(pattern, actor.sdoStaffNumber())
                    .forEach(view -> results.add(sdoEntry(view, true)));
        }
        return results;
    }

    /** Maps an executive projection to a directory entry. */
    private DirectoryEntryView executiveEntry(
            ExecutiveDirectoryView view, boolean sameScope) {
        return DirectoryEntryView.builder()
                .type("EXECUTIVE")
                .name((safe(view.getFirstName()) + " "
                        + safe(view.getLastName())).trim())
                .studentNumber(view.getStudentNumber())
                .position(view.getPosition())
                .societyID(view.getSocietyID())
                .societyName(view.getSocietyName())
                .sameSociety(sameScope)
                .build();
    }

    /** Maps an SDO projection to a directory entry. */
    private DirectoryEntryView sdoEntry(SdoDirectoryView view, boolean sameScope) {
        return DirectoryEntryView.builder()
                .type("SDO")
                .name((safe(view.getFirstName()) + " "
                        + safe(view.getLastName())).trim())
                .sdoStaffNumber(view.getStaffNumber())
                .sameSociety(sameScope)
                .build();
    }

    /** Adds a supervising SDO to the results if they match the search term. */
    private void addSdoEntry(
            List<DirectoryEntryView> results, String staffNumber,
            String pattern, boolean sameScope) {
        sdoRepository.findByStaffNumberWithUser(staffNumber)
                .filter(sdo -> sdo.getUser() != null)
                .filter(sdo -> matchesName(sdo.getUser(), pattern))
                .ifPresent(sdo -> {
                    User u = sdo.getUser();
                    boolean already = results.stream().anyMatch(entry ->
                            "SDO".equals(entry.getType())
                                    && staffNumber.equals(entry.getSdoStaffNumber()));
                    if (!already) {
                        results.add(DirectoryEntryView.builder()
                                .type("SDO")
                                .name((safe(u.getFirstName()) + " "
                                        + safe(u.getLastName())).trim())
                                .sdoStaffNumber(staffNumber)
                                .sameSociety(sameScope)
                                .build());
                    }
                });
    }

    private boolean matchesName(User user, String pattern) {
        if (pattern == null) {
            return true;
        }
        String needle = pattern.replace("%", "").toLowerCase(Locale.ROOT);
        if (needle.isBlank()) {
            return true;
        }
        String full = (safe(user.getFirstName()) + " "
                + safe(user.getLastName())).toLowerCase(Locale.ROOT);
        return full.contains(needle);
    }



    @Transactional(readOnly = true)
    public List<MentionableEventView> getMentionableEvents(
            String authenticatedEmail, String query) {
        ActorContext actor = requireActor(authenticatedEmail);
        String pattern = query == null || query.isBlank()
                ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        Map<String, MentionableEventView> byID = new LinkedHashMap<>();
        for (String societyID : actor.societyIDs()) {
            for (Event event : hosterRepository
                    .findMentionableEventsForSociety(societyID, pattern)) {
                byID.putIfAbsent(event.getEventID(),
                        MentionableEventView.builder()
                                .eventID(event.getEventID())
                                .eventName(event.getEventName())
                                .eventStatus(event.getEventStatus() == null
                                        ? null : event.getEventStatus().name())
                                .eventDate(event.getEventDate())
                                .build());
            }
        }
        return new ArrayList<>(byID.values());
    }

    // ================================================================
    // Notifications and after-commit realtime events
    // ================================================================

    private void notifyRecipients(
            Conversation conversation, ActorContext sender, Message message) {
        String senderLabel = sender.isSdo()
                ? "Student Development Office"
                : displayName(sender.studentNumber());
        Set<String> mentionedStudents = mentionRepository
                .findByMessageID(message.getMessageID()).stream()
                .filter(mention -> mention.getMentionType() == MentionType.USER)
                .map(MessageMention::getTargetStudentNumber)
                .collect(Collectors.toSet());

        for (String studentNumber : participantRepository
                .findActiveStudentNumbers(conversation.getConversationID())) {
            if (sender.isExecutive()
                    && Objects.equals(studentNumber, sender.studentNumber())) {
                continue;
            }
            String email = emailOfStudent(studentNumber);
            if (email == null) continue;
            boolean mentioned = mentionedStudents.contains(studentNumber);
            createNotification(
                    email,
                    mentioned ? "You were mentioned" : "New message",
                    (mentioned ? senderLabel + " mentioned you: "
                            : senderLabel + ": ") + preview(message.getBody()),
                    mentioned ? NotificationType.MESSAGE_MENTION
                            : NotificationType.MESSAGE_RECEIVED,
                    conversation.getConversationID());
        }
        for (String staffNumber : sdoParticipantRepository
                .findActiveSdoStaffNumbers(conversation.getConversationID())) {
            if (sender.isSdo()
                    && Objects.equals(staffNumber, sender.sdoStaffNumber())) {
                continue;
            }
            String email = emailOfSdo(staffNumber);
            if (email != null) {
                createNotification(
                        email, "New message",
                        senderLabel + ": " + preview(message.getBody()),
                        NotificationType.MESSAGE_RECEIVED,
                        conversation.getConversationID());
            }
        }
    }

    private void notifyExecutiveConversationStart(
            Conversation conversation, ActorContext sender,
            String recipient, boolean sameSociety) {
        String email = emailOfStudent(recipient);
        if (email == null) return;
        String senderName = actorDisplayName(sender);
        if (!sameSociety
                && conversation.getStatus() == ConversationStatus.PENDING) {
            createNotification(
                    email, "New message request",
                    senderName + " wants to chat: "
                            + preview(conversation.getContactReason()),
                    NotificationType.CONVERSATION_REQUEST,
                    conversation.getConversationID());
        } else {
            createNotification(
                    email, "New message",
                    senderName + " started a conversation with you.",
                    NotificationType.MESSAGE_RECEIVED,
                    conversation.getConversationID());
        }
    }

    private void notifyRequestResponse(
            Conversation conversation, ActorContext responder,
            boolean accepted) {
        String email = emailOfStudent(conversation.getInitiatedByStudentNumber());
        if (email == null) return;
        String responderName = displayName(responder.studentNumber());
        createNotification(
                email,
                accepted ? "Message request accepted" : "Message request declined",
                accepted
                        ? responderName + " accepted your message request."
                        : responderName + " declined your message request.",
                NotificationType.CONVERSATION_REQUEST,
                conversation.getConversationID());
    }

    private void createNotification(
            String email, String title, String message,
            NotificationType type, String relatedID) {
        try {
            Notification notification = new Notification();
            notification.setNotificationID(generateNotificationID());
            notification.setRecipientEmail(email);
            notification.setTitle(title);
            notification.setMessage(limit(message, 500));
            notification.setNotifType(type);
            notification.setRelatedID(relatedID);
            notificationService.create(notification);
        } catch (Exception ex) {
            log.warn("Failed to create messaging notification for {}: {}",
                    email, ex.getMessage());
        }
    }

    private void publishRealtime(
            Conversation conversation, MessageView message) {
        List<String> emails = activeParticipantEmails(conversation);
        eventPublisher.publishEvent(new MessagingMessageChangedEvent(
                List.copyOf(emails), message));
        publishConversationUpdates(conversation);
    }

    private void publishConversationUpdates(Conversation conversation) {
        for (String email : activeParticipantEmails(conversation)) {
            ActorContext actor = findActiveActor(email);
            if (actor != null) {
                eventPublisher.publishEvent(
                        new MessagingConversationChangedEvent(
                                email, toSummary(conversation, actor)));
            }
        }
    }

    private void publishConversationUpdate(
            Conversation conversation, ActorContext actor) {
        eventPublisher.publishEvent(new MessagingConversationChangedEvent(
                actor.email(), toSummary(conversation, actor)));
    }

    private List<String> activeParticipantEmails(Conversation conversation) {
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        participantRepository.findActiveStudentNumbers(
                        conversation.getConversationID()).stream()
                .map(this::emailOfStudent)
                .filter(Objects::nonNull)
                .forEach(emails::add);
        sdoParticipantRepository.findActiveSdoStaffNumbers(
                        conversation.getConversationID()).stream()
                .map(this::emailOfSdo)
                .filter(Objects::nonNull)
                .forEach(emails::add);
        return new ArrayList<>(emails);
    }

    private ActorContext findActiveActor(String email) {
        try {
            return requireActor(email);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String emailOfStudent(String studentNumber) {
        return studentRepository.findById(studentNumber)
                .map(Student::getEmail).orElse(null);
    }

    private String emailOfSdo(String staffNumber) {
        return sdoRepository.findById(staffNumber)
                .map(SDO::getEmail).orElse(null);
    }

    // ================================================================
    // Shared validation
    // ================================================================

    private Conversation requireConversation(String conversationID) {
        if (conversationID == null || conversationID.isBlank()) {
            throw new ResourceNotFoundException("Conversation not found.");
        }
        return conversationRepository.findById(conversationID)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation not found."));
    }

    private Society requireSociety(String societyID) {
        if (societyID == null || societyID.isBlank()) {
            throw new ResourceNotFoundException("Society not found.");
        }
        return societyRepository.findById(societyID)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Society not found."));
    }

    private void requireActiveConversation(Conversation conversation) {
        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new IllegalStateException(
                    "This conversation is not open for messaging.");
        }
    }

    private void requireParticipant(
            Conversation conversation, ActorContext actor) {
        boolean participant = actor.isExecutive()
                ? participantRepository.isActiveParticipant(
                        conversation.getConversationID(), actor.studentNumber())
                : sdoParticipantRepository.isActiveParticipant(
                        conversation.getConversationID(), actor.sdoStaffNumber());
        if (!participant) {
            throw new ForbiddenOperationException(
                    "You are not a participant in this conversation.");
        }
    }

    private Message requireMessageInConversation(
            String messageID, String conversationID) {
        Message message = messageRepository.findById(messageID)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Message not found."));
        if (!Objects.equals(message.getConversationID(), conversationID)) {
            throw new ResourceNotFoundException("Message not found.");
        }
        return message;
    }

    private String requireBody(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Message body is required.");
        }
        String trimmed = body.strip();
        if (trimmed.length() > 2000) {
            throw new IllegalArgumentException(
                    "Message must not exceed 2000 characters.");
        }
        return trimmed;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String generateNotificationID() {
        return "NTF" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 17).toUpperCase(Locale.ROOT);
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
