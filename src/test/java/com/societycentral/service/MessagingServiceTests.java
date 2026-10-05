package com.societycentral.service;

import com.societycentral.dto.request.EditMessageRequestDTO;
import com.societycentral.dto.request.MentionRequestDTO;
import com.societycentral.dto.request.SendMessageRequestDTO;
import com.societycentral.dto.request.StartDirectConversationRequestDTO;
import com.societycentral.dto.response.ConversationDetailView;
import com.societycentral.dto.response.ConversationSummaryView;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.exception.GlobalExceptionHandler;
import com.societycentral.model.Conversation;
import com.societycentral.model.ConversationParticipant;
import com.societycentral.model.ConversationParticipantId;
import com.societycentral.model.ConversationSdoParticipant;
import com.societycentral.model.ConversationSdoParticipantId;
import com.societycentral.model.ConversationStatus;
import com.societycentral.model.ConversationType;
import com.societycentral.model.Executive;
import com.societycentral.model.ExecutiveId;
import com.societycentral.model.Message;
import com.societycentral.model.MessageStatus;
import com.societycentral.model.MentionType;
import com.societycentral.model.Notification;
import com.societycentral.model.SDO;
import com.societycentral.model.Society;
import com.societycentral.model.Student;
import com.societycentral.repository.ConversationParticipantRepository;
import com.societycentral.repository.ConversationRepository;
import com.societycentral.repository.ConversationSdoParticipantRepository;
import com.societycentral.repository.EventRepository;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.HosterRepository;
import com.societycentral.repository.MessageMentionRepository;
import com.societycentral.repository.MessageRepository;
import com.societycentral.repository.SDORepository;
import com.societycentral.repository.SocietyRepository;
import com.societycentral.repository.StudentRepository;
import com.societycentral.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagingServiceTests {

    private static final String EXECUTIVE_A = "220000001";
    private static final String EXECUTIVE_B = "220000010";
    private static final String EXECUTIVE_C = "220000020";
    private static final String EMAIL_A = "executive.a@nmu.ac.za";
    private static final String EMAIL_B = "executive.b@nmu.ac.za";
    private static final String EMAIL_C = "executive.c@nmu.ac.za";
    private static final String ORDINARY_STUDENT = "220000099";
    private static final String ORDINARY_EMAIL = "student@nmu.ac.za";
    private static final String SDO_STAFF = "SDO0001";
    private static final String SDO_EMAIL = "sdo@nmu.ac.za";
    private static final String OTHER_SDO_STAFF = "SDO0002";
    private static final String OTHER_SDO_EMAIL = "other.sdo@nmu.ac.za";
    private static final String SOCIETY_A = "SOC001";
    private static final String SOCIETY_B = "SOC010";
    private static final String SOCIETY_C = "SOC020";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.now(CLOCK);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private ConversationRepository conversationRepository;
    private ConversationParticipantRepository participantRepository;
    private ConversationSdoParticipantRepository sdoParticipantRepository;
    private MessageRepository messageRepository;
    private MessageMentionRepository mentionRepository;
    private ExecutiveRepository executiveRepository;
    private StudentRepository studentRepository;
    private UserRepository userRepository;
    private SDORepository sdoRepository;
    private SocietyRepository societyRepository;
    private HosterRepository hosterRepository;
    private EventRepository eventRepository;
    private NotificationService notificationService;
    private ApplicationEventPublisher eventPublisher;

    private MessagingService service;

    private final Map<String, Student> studentsByNumber = new HashMap<>();
    private final Map<String, Student> studentsByEmail = new HashMap<>();
    private final Map<String, List<Executive>> rolesByStudent = new HashMap<>();
    private final Map<String, SDO> sdosByStaff = new HashMap<>();
    private final Map<String, SDO> sdosByEmail = new HashMap<>();
    private final Map<String, Society> societies = new HashMap<>();
    private final Map<String, Conversation> conversations = new LinkedHashMap<>();
    private final List<Conversation> pairConversations = new ArrayList<>();
    private final Map<String, ConversationParticipant> participants =
            new LinkedHashMap<>();
    private final Map<String, ConversationSdoParticipant> sdoParticipants =
            new LinkedHashMap<>();
    private final Map<String, Message> messages = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        participantRepository = mock(ConversationParticipantRepository.class);
        sdoParticipantRepository = mock(
                ConversationSdoParticipantRepository.class);
        messageRepository = mock(MessageRepository.class);
        mentionRepository = mock(MessageMentionRepository.class);
        executiveRepository = mock(ExecutiveRepository.class);
        studentRepository = mock(StudentRepository.class);
        userRepository = mock(UserRepository.class);
        sdoRepository = mock(SDORepository.class);
        societyRepository = mock(SocietyRepository.class);
        hosterRepository = mock(HosterRepository.class);
        eventRepository = mock(EventRepository.class);
        notificationService = mock(NotificationService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        studentsByNumber.clear();
        studentsByEmail.clear();
        rolesByStudent.clear();
        sdosByStaff.clear();
        sdosByEmail.clear();
        societies.clear();
        conversations.clear();
        pairConversations.clear();
        participants.clear();
        sdoParticipants.clear();
        messages.clear();

        registerSdo(SDO_STAFF, SDO_EMAIL);
        registerSociety(SOCIETY_A, "Computer Science Society", SDO_STAFF);
        registerSociety(SOCIETY_B, "Law Students Society", SDO_STAFF);
        registerSociety(SOCIETY_C, "Arts Society", SDO_STAFF);
        registerExecutive(EXECUTIVE_A, EMAIL_A, SOCIETY_A, "President");
        registerExecutive(EXECUTIVE_B, EMAIL_B, SOCIETY_B, "President");
        registerExecutive(EXECUTIVE_C, EMAIL_C, SOCIETY_C, "Treasurer");

        configureRepositoryBackings();

        service = new MessagingService(
                conversationRepository,
                participantRepository,
                sdoParticipantRepository,
                messageRepository,
                mentionRepository,
                executiveRepository,
                studentRepository,
                userRepository,
                sdoRepository,
                societyRepository,
                hosterRepository,
                eventRepository,
                notificationService,
                eventPublisher,
                CLOCK);
    }

    @Test
    void sameSocietyStartCreatesActiveConversationAndAllowsBothToMessage() {
        rolesByStudent.put(EXECUTIVE_B,
                List.of(executive(EXECUTIVE_B, SOCIETY_A)));

        ConversationDetailView detail = service.startDirectConversation(
                EMAIL_A, startRequest(EXECUTIVE_B, null, "Hello teammate"));

        assertEquals(ConversationStatus.ACTIVE, detail.getStatus());
        assertTrue(detail.isCanSend());
        assertEquals(1, conversations.size());
        assertEquals(1, messages.size());

        service.sendMessage(
                EMAIL_B, detail.getConversationID(), sendRequest("Hello back"));

        assertEquals(2, messages.size());
    }

    @Test
    void crossSocietyStartCreatesPendingConversation() {
        ConversationDetailView detail = service.startDirectConversation(
                EMAIL_A,
                startRequest(EXECUTIVE_B, "  Event collaboration  ", "Hello"));

        Conversation conversation = conversations.get(detail.getConversationID());
        assertNotNull(conversation);
        assertEquals(ConversationStatus.PENDING, conversation.getStatus());
        assertEquals("Event collaboration", conversation.getContactReason());
        assertEquals(EXECUTIVE_A, conversation.getInitiatedByStudentNumber());
        assertFalse(detail.isCanSend());

        ConversationDetailView recipientView = service.getConversation(
                EMAIL_B, conversation.getConversationID(), 0, 30);
        assertTrue(recipientView.isIncomingRequest());
        assertFalse(recipientView.isCanSend());
    }

    @Test
    void crossSocietyStartRequiresContactReason() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.startDirectConversation(
                        EMAIL_A, startRequest(EXECUTIVE_B, "  ", "Hello")));

        assertTrue(error.getMessage().contains("reason is required"));
        assertTrue(conversations.isEmpty());
        assertTrue(messages.isEmpty());
    }

    @Test
    void crossSocietyStartSavesExactlyOneOpeningMessage() {
        ConversationDetailView detail = service.startDirectConversation(
                EMAIL_A,
                startRequest(EXECUTIVE_B, "Event collaboration", "  Opening  "));

        List<Message> saved = messagesFor(detail.getConversationID());
        assertEquals(1, saved.size());
        assertEquals("Opening", saved.getFirst().getBody());
        assertEquals(EXECUTIVE_A, saved.getFirst().getSenderStudentNumber());
    }

    @Test
    void normalSendEndpointRejectsSecondMessageWhilePending() {
        Conversation pending = directConversation(ConversationStatus.PENDING);
        addMessage(pending, EXECUTIVE_A, "Opening", NOW.minusMinutes(1));

        assertThrows(IllegalStateException.class,
                () -> service.sendMessage(
                        EMAIL_A, pending.getConversationID(),
                        sendRequest("Second message")));

        assertEquals(1, messagesFor(pending.getConversationID()).size());
    }

    @Test
    void repeatedStartWhilePendingCannotAddAnotherMessage() {
        Conversation pending = directConversation(ConversationStatus.PENDING);
        addMessage(pending, EXECUTIVE_A, "Opening", NOW.minusMinutes(1));

        assertThrows(IllegalStateException.class,
                () -> service.startDirectConversation(
                        EMAIL_A,
                        startRequest(EXECUTIVE_B, "Another reason", "Second")));
        assertThrows(IllegalStateException.class,
                () -> service.startDirectConversation(
                        EMAIL_B,
                        startRequest(EXECUTIVE_A, "Reverse request", "Reply")));

        assertEquals(1, messagesFor(pending.getConversationID()).size());
        assertEquals(1, pairConversations.size());
    }

    @Test
    void recipientCanAcceptPendingRequest() {
        Conversation pending = directConversation(ConversationStatus.PENDING);
        addMessage(pending, EXECUTIVE_A, "Opening", NOW.minusMinutes(1));

        ConversationDetailView detail = service.respondToRequest(
                EMAIL_B, pending.getConversationID(), true);

        assertEquals(ConversationStatus.ACTIVE, pending.getStatus());
        assertEquals(ConversationStatus.ACTIVE, detail.getStatus());
        assertTrue(detail.isCanSend());
    }

    @Test
    void initiatorCannotAcceptOwnRequest() {
        Conversation pending = directConversation(ConversationStatus.PENDING);

        assertThrows(ForbiddenOperationException.class,
                () -> service.respondToRequest(
                        EMAIL_A, pending.getConversationID(), true));

        assertEquals(ConversationStatus.PENDING, pending.getStatus());
    }

    @Test
    void acceptedConversationAllowsTwoWayMessaging() {
        Conversation pending = directConversation(ConversationStatus.PENDING);
        addMessage(pending, EXECUTIVE_A, "Opening", NOW.minusMinutes(1));
        service.respondToRequest(EMAIL_B, pending.getConversationID(), true);

        service.sendMessage(
                EMAIL_A, pending.getConversationID(), sendRequest("From A"));
        service.sendMessage(
                EMAIL_B, pending.getConversationID(), sendRequest("From B"));

        assertEquals(3, messagesFor(pending.getConversationID()).size());
    }

    @Test
    void recipientCanDeclineAndRejectedConversationBlocksMessaging() {
        Conversation pending = directConversation(ConversationStatus.PENDING);
        addMessage(pending, EXECUTIVE_A, "Opening", NOW.minusMinutes(1));

        ConversationDetailView detail = service.respondToRequest(
                EMAIL_B, pending.getConversationID(), false);

        assertEquals(ConversationStatus.REJECTED, pending.getStatus());
        assertEquals(ConversationStatus.REJECTED, detail.getStatus());
        assertFalse(detail.isCanSend());
        assertThrows(IllegalStateException.class,
                () -> service.sendMessage(
                        EMAIL_A, pending.getConversationID(), sendRequest("From A")));
        assertThrows(IllegalStateException.class,
                () -> service.sendMessage(
                        EMAIL_B, pending.getConversationID(), sendRequest("From B")));
        assertEquals(1, messagesFor(pending.getConversationID()).size());
    }

    @Test
    void nonParticipantCannotReadSendOrRespond() {
        Conversation pending = directConversation(ConversationStatus.PENDING);

        ForbiddenOperationException readError = assertThrows(
                ForbiddenOperationException.class,
                () -> service.getConversation(
                        EMAIL_C, pending.getConversationID(), 0, 30));
        assertThrows(ForbiddenOperationException.class,
                () -> service.sendMessage(
                        EMAIL_C, pending.getConversationID(), sendRequest("Intrusion")));
        assertThrows(ForbiddenOperationException.class,
                () -> service.respondToRequest(
                        EMAIL_C, pending.getConversationID(), true));

        assertEquals(
                HttpStatus.FORBIDDEN,
                new GlobalExceptionHandler()
                        .handleForbiddenOperation(readError)
                        .getStatusCode());
        assertTrue(messages.isEmpty());
    }

    @Test
    void editAndDeleteStillHonorFifteenMinuteWindow() {
        Conversation active = directConversation(ConversationStatus.ACTIVE);
        Message editable = addMessage(
                active, EXECUTIVE_A, "Original", NOW.minusMinutes(14));
        Message deletable = addMessage(
                active, EXECUTIVE_A, "Delete me", NOW.minusMinutes(15));
        Message expired = addMessage(
                active, EXECUTIVE_A, "Too old", NOW.minusMinutes(16));

        EditMessageRequestDTO edit = new EditMessageRequestDTO();
        edit.setBody("Updated");
        service.editMessage(
                EMAIL_A, active.getConversationID(), editable.getMessageID(), edit);
        service.deleteMessage(
                EMAIL_A, active.getConversationID(), deletable.getMessageID());

        assertEquals("Updated", editable.getBody());
        assertEquals(MessageStatus.DELETED, deletable.getStatus());
        assertThrows(IllegalStateException.class,
                () -> service.editMessage(
                        EMAIL_A, active.getConversationID(), expired.getMessageID(), edit));
        assertThrows(IllegalStateException.class,
                () -> service.deleteMessage(
                        EMAIL_A, active.getConversationID(), expired.getMessageID()));
    }

    @Test
    void activeSocietyGroupMessagingRemainsAvailable() {
        Conversation group = conversation(
                "group-1", ConversationType.SOCIETY_GROUP,
                ConversationStatus.ACTIVE, null);
        group.setSocietyID(SOCIETY_A);
        storeConversation(group);
        storeParticipant(group, EXECUTIVE_A);

        service.sendMessage(
                EMAIL_A, group.getConversationID(), sendRequest("Group update"));

        assertEquals(1, messagesFor(group.getConversationID()).size());
        assertEquals("Group update",
                messagesFor(group.getConversationID()).getFirst().getBody());
    }

    @Test
    void repeatedStartForActiveConversationSendsNormallyWithoutDuplicatingIt() {
        Conversation active = directConversation(ConversationStatus.ACTIVE);
        addMessage(active, EXECUTIVE_A, "Earlier", NOW.minusMinutes(2));

        ConversationDetailView detail = service.startDirectConversation(
                EMAIL_A,
                startRequest(EXECUTIVE_B, null, "Active follow-up"));

        assertEquals(active.getConversationID(), detail.getConversationID());
        assertEquals(1, pairConversations.size());
        assertEquals(1, conversations.size());
        assertEquals(2, messagesFor(active.getConversationID()).size());

        var notificationCaptor =
                org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).create(notificationCaptor.capture());
        assertEquals("New message", notificationCaptor.getValue().getTitle());
        assertTrue(notificationCaptor.getValue().getMessage()
                .contains("Active follow-up"));
    }

    @Test
    void ordinaryStudentCannotAccessInstitutionalMessaging() {
        Student student = new Student();
        student.setStudentNumber(ORDINARY_STUDENT);
        student.setEmail(ORDINARY_EMAIL);
        studentsByNumber.put(ORDINARY_STUDENT, student);
        studentsByEmail.put(ORDINARY_EMAIL, student);

        assertThrows(ForbiddenOperationException.class,
                () -> service.getInbox(ORDINARY_EMAIL));
    }

    @Test
    void societyGroupMembershipTracksCurrentExecutiveAppointments() {
        Conversation group = conversation(
                "group-sync", ConversationType.SOCIETY_GROUP,
                ConversationStatus.ACTIVE, null);
        group.setSocietyID(SOCIETY_A);
        storeConversation(group);
        storeParticipant(group, EXECUTIVE_A);
        storeParticipant(group, EXECUTIVE_B);

        service.ensureSocietyGroup(SOCIETY_A);

        assertNull(participants.get(participantKey(
                group.getConversationID(), EXECUTIVE_A)).getLeftAt());
        assertNotNull(participants.get(participantKey(
                group.getConversationID(), EXECUTIVE_B)).getLeftAt());
    }

    @Test
    void inactiveExecutiveLosesActiveSocietyGroupAccess() {
        Conversation group = conversation(
                "group-inactive", ConversationType.SOCIETY_GROUP,
                ConversationStatus.ACTIVE, null);
        group.setSocietyID(SOCIETY_A);
        storeConversation(group);
        storeParticipant(group, EXECUTIVE_A);
        rolesByStudent.put(EXECUTIVE_A, List.of());

        service.ensureSocietyGroup(SOCIETY_A);

        assertNotNull(participants.get(participantKey(
                group.getConversationID(), EXECUTIVE_A)).getLeftAt());
        assertThrows(ForbiddenOperationException.class,
                () -> service.getConversation(
                        EMAIL_A, group.getConversationID(), 0, 30));
    }

    @Test
    void sdoInitiatesOneInstitutionalChannelForPresidentAndSecretary() {
        rolesByStudent.put(EXECUTIVE_B,
                List.of(executive(EXECUTIVE_B, SOCIETY_A, "Secretary")));

        ConversationDetailView detail = service.startSdoSocietyConversation(
                SDO_EMAIL, SOCIETY_A, sendRequest("Please submit the proposal"));

        Conversation conversation = conversations.get(detail.getConversationID());
        assertEquals(ConversationType.SDO_SOCIETY, conversation.getType());
        assertEquals(ConversationStatus.ACTIVE, conversation.getStatus());
        assertEquals(SOCIETY_A, conversation.getSocietyID());
        assertTrue(participantRepository.isActiveParticipant(
                conversation.getConversationID(), EXECUTIVE_A));
        assertTrue(participantRepository.isActiveParticipant(
                conversation.getConversationID(), EXECUTIVE_B));
        assertFalse(participantRepository.isActiveParticipant(
                conversation.getConversationID(), EXECUTIVE_C));
        assertTrue(sdoParticipantRepository.isActiveParticipant(
                conversation.getConversationID(), SDO_STAFF));
        assertEquals(SDO_STAFF,
                messagesFor(conversation.getConversationID()).getFirst()
                        .getSenderSdoStaffNumber());
        assertEquals(1, messagesFor(conversation.getConversationID()).size());
    }

    @Test
    void presidentAndSecretaryCanUseExistingSdoConversation() {
        rolesByStudent.put(EXECUTIVE_B,
                List.of(executive(EXECUTIVE_B, SOCIETY_A, "Secretary")));
        ConversationDetailView started = service.startSdoSocietyConversation(
                SDO_EMAIL, SOCIETY_A, sendRequest("Initial guidance"));

        ConversationDetailView presidentView = service.getConversation(
                EMAIL_A, started.getConversationID(), 0, 30);
        ConversationDetailView secretaryView = service.getConversation(
                EMAIL_B, started.getConversationID(), 0, 30);
        service.sendMessage(
                EMAIL_A, started.getConversationID(), sendRequest("Received"));

        assertTrue(presidentView.isCanSend());
        assertTrue(secretaryView.isCanSend());
        assertEquals(2, messagesFor(started.getConversationID()).size());
    }

    @Test
    void nonLeadershipExecutiveCannotAccessSdoConversation() {
        ConversationDetailView started = service.startSdoSocietyConversation(
                SDO_EMAIL, SOCIETY_A, sendRequest("Initial guidance"));

        assertThrows(ForbiddenOperationException.class,
                () -> service.getConversation(
                        EMAIL_C, started.getConversationID(), 0, 30));
        assertThrows(ForbiddenOperationException.class,
                () -> service.sendMessage(
                        EMAIL_C, started.getConversationID(),
                        sendRequest("Impersonation attempt")));
    }

    @Test
    void leadershipChangeUpdatesAccessWithoutDeletingHistory() {
        rolesByStudent.put(EXECUTIVE_B,
                List.of(executive(EXECUTIVE_B, SOCIETY_A, "Secretary")));
        ConversationDetailView started = service.startSdoSocietyConversation(
                SDO_EMAIL, SOCIETY_A, sendRequest("Historical guidance"));

        rolesByStudent.put(EXECUTIVE_A,
                List.of(executive(EXECUTIVE_A, SOCIETY_A, "Treasurer")));
        rolesByStudent.put(EXECUTIVE_C,
                List.of(executive(EXECUTIVE_C, SOCIETY_A, "President")));

        ConversationDetailView newPresidentView = service.getConversation(
                EMAIL_C, started.getConversationID(), 0, 30);

        assertTrue(newPresidentView.isCanSend());
        assertThrows(ForbiddenOperationException.class,
                () -> service.getConversation(
                        EMAIL_A, started.getConversationID(), 0, 30));
        assertEquals(1, messagesFor(started.getConversationID()).size());
        assertEquals("Historical guidance",
                messagesFor(started.getConversationID()).getFirst().getBody());
    }

    @Test
    void sdoCannotInitiateForAnUnassignedSociety() {
        registerSdo(OTHER_SDO_STAFF, OTHER_SDO_EMAIL);

        assertThrows(ForbiddenOperationException.class,
                () -> service.startSdoSocietyConversation(
                        OTHER_SDO_EMAIL, SOCIETY_A,
                        sendRequest("Unauthorised message")));
        assertTrue(conversations.values().stream().noneMatch(conversation ->
                conversation.getType() == ConversationType.SDO_SOCIETY));
    }

    @Test
    void loadingSdoInboxDoesNotCreateEmptyInstitutionalChannels() {
        service.getInbox(SDO_EMAIL);

        assertTrue(conversations.values().stream().noneMatch(conversation ->
                conversation.getType() == ConversationType.SDO_SOCIETY));
    }

    @Test
    void userMentionsAreLimitedToCurrentConversationParticipants() {
        Conversation active = directConversation(ConversationStatus.ACTIVE);
        SendMessageRequestDTO request = sendRequest("Hello @outsider");
        MentionRequestDTO mention = new MentionRequestDTO();
        mention.setMentionType(MentionType.USER);
        mention.setTargetStudentNumber(EXECUTIVE_C);
        request.setMentions(List.of(mention));

        assertThrows(IllegalArgumentException.class,
                () -> service.sendMessage(
                        EMAIL_A, active.getConversationID(), request));
        verify(mentionRepository,
                org.mockito.Mockito.never()).save(any());
    }

    @Test
    void unreadCountUsesPersistedParticipantReadState() {
        Conversation active = directConversation(ConversationStatus.ACTIVE);
        addMessage(active, EXECUTIVE_A, "Unread for B", NOW.minusMinutes(2));

        ConversationSummaryView summary = service.getInbox(EMAIL_B).stream()
                .filter(item -> item.getConversationID()
                        .equals(active.getConversationID()))
                .findFirst().orElseThrow();

        assertEquals(1, summary.getUnreadCount());
    }

    @Test
    void otherParticipantCannotEditOrDeleteSendersMessage() {
        Conversation active = directConversation(ConversationStatus.ACTIVE);
        Message message = addMessage(
                active, EXECUTIVE_A, "Owned by A", NOW.minusMinutes(1));
        EditMessageRequestDTO edit = new EditMessageRequestDTO();
        edit.setBody("Changed by B");

        assertThrows(ForbiddenOperationException.class,
                () -> service.editMessage(
                        EMAIL_B, active.getConversationID(),
                        message.getMessageID(), edit));
        assertThrows(ForbiddenOperationException.class,
                () -> service.deleteMessage(
                        EMAIL_B, active.getConversationID(),
                        message.getMessageID()));
        assertEquals("Owned by A", message.getBody());
        assertEquals(MessageStatus.VISIBLE, message.getStatus());
    }

    private void configureRepositoryBackings() {
        when(studentRepository.findByEmail(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(studentsByEmail.get(invocation.getArgument(0))));
        when(studentRepository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(studentsByNumber.get(invocation.getArgument(0))));
        when(studentRepository.existsById(anyString())).thenAnswer(invocation ->
                studentsByNumber.containsKey(invocation.getArgument(0)));
        when(userRepository.findById(anyString())).thenReturn(Optional.empty());
        when(sdoRepository.findByEmail(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(sdosByEmail.get(invocation.getArgument(0))));
        when(sdoRepository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(sdosByStaff.get(invocation.getArgument(0))));
        when(societyRepository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(societies.get(invocation.getArgument(0))));
        when(societyRepository.findBySdoStaffNumber(anyString()))
                .thenAnswer(invocation -> societies.values().stream()
                        .filter(society -> invocation.getArgument(0)
                                .equals(society.getSdoStaffNumber()))
                        .toList());
        when(executiveRepository.findActiveExecutiveRoles(
                anyString(), any(LocalDate.class))).thenAnswer(invocation ->
                rolesByStudent.getOrDefault(invocation.getArgument(0), List.of()));
        when(executiveRepository.findCurrentExecutivesBySociety(
                anyString(), any(LocalDate.class))).thenAnswer(invocation -> {
                    String societyID = invocation.getArgument(0);
                    return rolesByStudent.values().stream()
                            .flatMap(List::stream)
                            .filter(role -> societyID.equals(
                                    role.getId().getSocietyID()))
                            .toList();
                });

        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(invocation -> {
                    Conversation saved = invocation.getArgument(0);
                    conversations.put(saved.getConversationID(), saved);
                    if (saved.getType() == ConversationType.DIRECT
                            && pairConversations.stream().noneMatch(c ->
                            c.getConversationID().equals(saved.getConversationID()))) {
                        pairConversations.add(saved);
                    }
                    return saved;
                });
        when(conversationRepository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(conversations.get(invocation.getArgument(0))));
        when(conversationRepository.findDirectConversationsBetween(
                anyString(), anyString())).thenAnswer(invocation ->
                new ArrayList<>(pairConversations));
        when(conversationRepository.findByTypeAndSocietyID(any(), anyString()))
                .thenAnswer(invocation -> conversations.values().stream()
                        .filter(conversation -> conversation.getType()
                                == invocation.getArgument(0))
                        .filter(conversation -> Objects.equals(
                                conversation.getSocietyID(),
                                invocation.getArgument(1)))
                        .findFirst());
        when(conversationRepository.findActiveConversationIdsForStudent(anyString()))
                .thenAnswer(invocation -> {
                    String studentNumber = invocation.getArgument(0);
                    return participants.values().stream()
                            .filter(participant -> participant.getLeftAt() == null)
                            .filter(participant -> studentNumber.equals(
                                    participant.getId().getStudentNumber()))
                            .map(participant -> participant.getId().getConversationID())
                            .filter(id -> conversations.get(id).getStatus()
                                    != ConversationStatus.REJECTED)
                            .distinct().toList();
                });
        when(conversationRepository.findActiveConversationIdsForSdo(anyString()))
                .thenAnswer(invocation -> {
                    String staffNumber = invocation.getArgument(0);
                    return sdoParticipants.values().stream()
                            .filter(participant -> participant.getLeftAt() == null)
                            .filter(participant -> staffNumber.equals(
                                    participant.getId().getSdoStaffNumber()))
                            .map(participant -> participant.getId().getConversationID())
                            .filter(id -> conversations.get(id).getStatus()
                                    != ConversationStatus.REJECTED)
                            .distinct().toList();
                });

        when(participantRepository.save(any(ConversationParticipant.class)))
                .thenAnswer(invocation -> {
                    ConversationParticipant saved = invocation.getArgument(0);
                    participants.put(participantKey(
                            saved.getId().getConversationID(),
                            saved.getId().getStudentNumber()), saved);
                    return saved;
                });
        when(participantRepository.isActiveParticipant(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    ConversationParticipant participant = participants.get(
                            participantKey(
                                    invocation.getArgument(0),
                                    invocation.getArgument(1)));
                    return participant != null && participant.getLeftAt() == null;
                });
        when(participantRepository
                .findByIdConversationIDAndIdStudentNumber(anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(participants.get(
                        participantKey(
                                invocation.getArgument(0),
                                invocation.getArgument(1)))));
        when(participantRepository.findByIdConversationID(anyString()))
                .thenAnswer(invocation -> participantsFor(invocation.getArgument(0)));
        when(participantRepository.findActiveByConversationID(anyString()))
                .thenAnswer(invocation -> participantsFor(invocation.getArgument(0))
                        .stream()
                        .filter(participant -> participant.getLeftAt() == null)
                        .toList());
        when(participantRepository.findActiveStudentNumbers(anyString()))
                .thenAnswer(invocation -> participantsFor(invocation.getArgument(0))
                        .stream()
                        .filter(participant -> participant.getLeftAt() == null)
                        .map(participant -> participant.getId().getStudentNumber())
                        .toList());

        when(sdoParticipantRepository
                .findByIdConversationIDAndIdSdoStaffNumber(anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(sdoParticipants.get(
                        sdoParticipantKey(
                                invocation.getArgument(0),
                                invocation.getArgument(1)))));
        when(sdoParticipantRepository.findByIdConversationID(anyString()))
                .thenAnswer(invocation -> sdoParticipantsFor(
                        invocation.getArgument(0)));
        when(sdoParticipantRepository.findActiveByConversationID(anyString()))
                .thenAnswer(invocation -> sdoParticipantsFor(
                                invocation.getArgument(0)).stream()
                        .filter(participant -> participant.getLeftAt() == null)
                        .toList());
        when(sdoParticipantRepository.findActiveSdoStaffNumbers(anyString()))
                .thenAnswer(invocation -> sdoParticipantsFor(
                                invocation.getArgument(0)).stream()
                        .filter(participant -> participant.getLeftAt() == null)
                        .map(participant -> participant.getId().getSdoStaffNumber())
                        .toList());
        when(sdoParticipantRepository.isActiveParticipant(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    ConversationSdoParticipant participant = sdoParticipants.get(
                            sdoParticipantKey(
                                    invocation.getArgument(0),
                                    invocation.getArgument(1)));
                    return participant != null && participant.getLeftAt() == null;
                });
        when(sdoParticipantRepository.save(any(ConversationSdoParticipant.class)))
                .thenAnswer(invocation -> {
                    ConversationSdoParticipant saved = invocation.getArgument(0);
                    sdoParticipants.put(sdoParticipantKey(
                            saved.getId().getConversationID(),
                            saved.getId().getSdoStaffNumber()), saved);
                    return saved;
                });

        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            messages.put(saved.getMessageID(), saved);
            return saved;
        });
        when(messageRepository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(messages.get(invocation.getArgument(0))));
        when(messageRepository.findFirstByConversationIDOrderByCreatedAtDesc(
                anyString())).thenAnswer(invocation -> messagesFor(
                                invocation.getArgument(0)).stream()
                        .max(Comparator.comparing(Message::getCreatedAt)));
        when(messageRepository.countUnread(
                anyString(), anyString(), any())).thenAnswer(invocation -> {
                    String conversationID = invocation.getArgument(0);
                    String studentNumber = invocation.getArgument(1);
                    LocalDateTime lastReadAt = invocation.getArgument(2);
                    return messagesFor(conversationID).stream()
                            .filter(message -> !studentNumber.equals(
                                    message.getSenderStudentNumber()))
                            .filter(message -> lastReadAt == null
                                    || message.getCreatedAt().isAfter(lastReadAt))
                            .count();
                });
        when(messageRepository.countUnreadForSdo(
                anyString(), anyString(), any())).thenAnswer(invocation -> {
                    String conversationID = invocation.getArgument(0);
                    String staffNumber = invocation.getArgument(1);
                    LocalDateTime lastReadAt = invocation.getArgument(2);
                    return messagesFor(conversationID).stream()
                            .filter(message -> !staffNumber.equals(
                                    message.getSenderSdoStaffNumber()))
                            .filter(message -> lastReadAt == null
                                    || message.getCreatedAt().isAfter(lastReadAt))
                            .count();
                });
        when(messageRepository.findByConversationIDOrderByCreatedAtDesc(
                anyString(), any(Pageable.class))).thenAnswer(invocation -> {
                    String conversationID = invocation.getArgument(0);
                    Pageable pageable = invocation.getArgument(1);
                    List<Message> found = messagesFor(conversationID).stream()
                            .sorted(Comparator.comparing(Message::getCreatedAt).reversed())
                            .toList();
                    return new PageImpl<>(found, pageable, found.size());
                });

        when(mentionRepository.findByMessageID(anyString())).thenReturn(List.of());
        when(mentionRepository.findByMessageIDIn(any())).thenReturn(List.of());
    }

    private void registerExecutive(
            String studentNumber, String email, String societyID) {
        registerExecutive(studentNumber, email, societyID, "Member");
    }

    private void registerExecutive(
            String studentNumber, String email, String societyID,
            String position) {
        Student student = new Student();
        student.setStudentNumber(studentNumber);
        student.setEmail(email);
        studentsByNumber.put(studentNumber, student);
        studentsByEmail.put(email, student);
        rolesByStudent.put(studentNumber,
                List.of(executive(studentNumber, societyID, position)));
    }

    private Executive executive(String studentNumber, String societyID) {
        return executive(studentNumber, societyID, "Member");
    }

    private Executive executive(
            String studentNumber, String societyID, String position) {
        Executive executive = new Executive();
        executive.setId(new ExecutiveId(
                studentNumber, societyID, TODAY.minusMonths(1)));
        executive.setPosition(position);
        executive.setSociety(societies.get(societyID));
        executive.setStudent(studentsByNumber.get(studentNumber));
        return executive;
    }

    private void registerSdo(String staffNumber, String email) {
        SDO sdo = new SDO();
        sdo.setStaffNumber(staffNumber);
        sdo.setEmail(email);
        sdosByStaff.put(staffNumber, sdo);
        sdosByEmail.put(email, sdo);
    }

    private void registerSociety(
            String societyID, String name, String sdoStaffNumber) {
        Society society = new Society();
        society.setSocietyID(societyID);
        society.setSocietyName(name);
        society.setSdoStaffNumber(sdoStaffNumber);
        society.setSdo(sdosByStaff.get(sdoStaffNumber));
        society.setActiveStatus(true);
        societies.put(societyID, society);
    }

    private Conversation directConversation(ConversationStatus status) {
        Conversation conversation = conversation(
                "direct-1", ConversationType.DIRECT, status, EXECUTIVE_A);
        conversation.setContactReason(
                status == ConversationStatus.ACTIVE ? null : "Event collaboration");
        storeConversation(conversation);
        storeParticipant(conversation, EXECUTIVE_A);
        storeParticipant(conversation, EXECUTIVE_B);
        return conversation;
    }

    private Conversation conversation(
            String id, ConversationType type, ConversationStatus status,
            String initiator) {
        Conversation conversation = new Conversation();
        conversation.setConversationID(id);
        conversation.setType(type);
        conversation.setStatus(status);
        conversation.setInitiatedByStudentNumber(initiator);
        conversation.setCreatedAt(NOW.minusHours(1));
        return conversation;
    }

    private void storeConversation(Conversation conversation) {
        conversations.put(conversation.getConversationID(), conversation);
        if (conversation.getType() == ConversationType.DIRECT) {
            pairConversations.add(conversation);
        }
    }

    private void storeParticipant(
            Conversation conversation, String studentNumber) {
        ConversationParticipant participant = new ConversationParticipant();
        participant.setId(new ConversationParticipantId(
                conversation.getConversationID(), studentNumber));
        participant.setConversation(conversation);
        participant.setStudent(studentsByNumber.get(studentNumber));
        participant.setRole("MEMBER");
        participant.setJoinedAt(conversation.getCreatedAt());
        participants.put(participantKey(
                conversation.getConversationID(), studentNumber), participant);
    }

    private List<ConversationParticipant> participantsFor(String conversationID) {
        return participants.values().stream()
                .filter(participant -> participant.getId().getConversationID()
                        .equals(conversationID))
                .toList();
    }

    private Message addMessage(
            Conversation conversation, String sender, String body,
            LocalDateTime createdAt) {
        Message message = new Message();
        message.setMessageID("message-" + (messages.size() + 1));
        message.setConversationID(conversation.getConversationID());
        message.setSenderStudentNumber(sender);
        message.setBody(body);
        message.setStatus(MessageStatus.VISIBLE);
        message.setCreatedAt(createdAt);
        messages.put(message.getMessageID(), message);
        conversation.setLastMessageAt(createdAt);
        return message;
    }

    private List<Message> messagesFor(String conversationID) {
        return messages.values().stream()
                .filter(message -> message.getConversationID().equals(conversationID))
                .toList();
    }

    private StartDirectConversationRequestDTO startRequest(
            String recipient, String reason, String body) {
        StartDirectConversationRequestDTO request =
                new StartDirectConversationRequestDTO();
        request.setRecipientStudentNumber(recipient);
        request.setContactReason(reason);
        request.setBody(body);
        return request;
    }

    private SendMessageRequestDTO sendRequest(String body) {
        SendMessageRequestDTO request = new SendMessageRequestDTO();
        request.setBody(body);
        return request;
    }

    private String participantKey(String conversationID, String studentNumber) {
        return conversationID + "|" + studentNumber;
    }

    private String sdoParticipantKey(
            String conversationID, String staffNumber) {
        return conversationID + "|" + staffNumber;
    }

    private List<ConversationSdoParticipant> sdoParticipantsFor(
            String conversationID) {
        return sdoParticipants.values().stream()
                .filter(participant -> participant.getId().getConversationID()
                        .equals(conversationID))
                .toList();
    }
}
