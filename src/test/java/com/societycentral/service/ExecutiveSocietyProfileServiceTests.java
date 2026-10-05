package com.societycentral.service;

import com.societycentral.dto.request.UpdateSocietyPublicProfileRequestDTO;
import com.societycentral.dto.response.ExecutiveSocietyProfileDTO;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.model.Campus;
import com.societycentral.model.Executive;
import com.societycentral.model.ExecutiveId;
import com.societycentral.model.Society;
import com.societycentral.model.SocietyImageType;
import com.societycentral.model.SocietyType;
import com.societycentral.model.Student;
import com.societycentral.repository.SocietyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutiveSocietyProfileServiceTests {

    private static final String EMAIL = "president@nmu.ac.za";
    private static final String SOCIETY_ID = "SOC001";

    @Mock
    private ExecutiveSocietyResolver executiveSocietyResolver;
    @Mock
    private SocietyProfileEditAuthorizationService authorizationService;
    @Mock
    private SocietyBrowseService societyBrowseService;
    @Mock
    private SocietyRepository societyRepository;
    @Mock
    private SocietyMediaService societyMediaService;
    @Mock
    private SocietyHighlightService societyHighlightService;

    private ExecutiveSocietyProfileService service;
    private Society society;
    private ExecutiveSocietyResolver.ActiveExecutiveSociety context;

    @BeforeEach
    void setUp() {
        service = new ExecutiveSocietyProfileService(
                executiveSocietyResolver,
                authorizationService,
                societyBrowseService,
                societyRepository,
                societyMediaService,
                societyHighlightService);
        society = society();
        context = context(society, "President");
    }

    @Test
    void everyActiveExecutiveCanLoadOwnProfile() {
        when(executiveSocietyResolver.resolve(EMAIL)).thenReturn(context);
        ExecutiveSocietyProfileDTO expected =
                ExecutiveSocietyProfileDTO.builder()
                        .societyID(SOCIETY_ID)
                        .canManageProfile(true)
                        .build();
        when(societyBrowseService.getExecutiveSocietyProfile(
                SOCIETY_ID, EMAIL)).thenReturn(expected);

        assertEquals(expected, service.getOwnProfile(EMAIL));
    }

    @Test
    void scopedProfileResolvesTheExactPersistedRole() {
        when(executiveSocietyResolver.resolve(EMAIL, SOCIETY_ID))
                .thenReturn(context);
        ExecutiveSocietyProfileDTO expected =
                ExecutiveSocietyProfileDTO.builder()
                        .societyID(SOCIETY_ID)
                        .canManageProfile(true)
                        .build();
        when(societyBrowseService.getExecutiveSocietyProfile(
                SOCIETY_ID, EMAIL)).thenReturn(expected);

        assertEquals(expected, service.getProfile(EMAIL, SOCIETY_ID));
    }

    @Test
    void updateChangesOnlyAllowedPublicFields() {
        when(authorizationService.requireCanEditOwnSociety(EMAIL))
                .thenReturn(context);
        ExecutiveSocietyProfileDTO expected =
                ExecutiveSocietyProfileDTO.builder()
                        .societyID(SOCIETY_ID)
                        .canManageProfile(true)
                        .build();
        when(societyBrowseService.getExecutiveSocietyProfile(
                SOCIETY_ID, EMAIL)).thenReturn(expected);

        UpdateSocietyPublicProfileRequestDTO request =
                new UpdateSocietyPublicProfileRequestDTO();
        request.setDescription("  Updated public description  ");
        request.setVision("   ");
        request.setMission("  Build an inclusive technical community.  ");
        request.setContactEmail(" profile@nmu.ac.za ");
        request.setContactPhone(" 041-555-0101 ");
        request.setFacebookURL(" https://facebook.com/computing ");
        request.setInstagramURL("   ");
        request.setTiktokURL("https://tiktok.com/@computing");

        ExecutiveSocietyProfileDTO result = service.updatePublicProfile(
                EMAIL, request);

        assertEquals(expected, result);
        assertEquals("Updated public description", society.getDescription());
        assertNull(society.getVision());
        assertEquals("Build an inclusive technical community.",
                society.getMission());
        assertEquals("profile@nmu.ac.za", society.getEmail());
        assertEquals("041-555-0101", society.getContactNumber());
        assertEquals("https://facebook.com/computing",
                society.getFacebookURL());
        assertNull(society.getInstagramURL());
        assertEquals("https://tiktok.com/@computing",
                society.getTiktokURL());

        // Protected fields are not represented by the request DTO.
        assertEquals("Computing Society", society.getSocietyName());
        assertEquals(SocietyType.ACADEMIC, society.getSocietyType());
        assertEquals(Campus.SOUTH_CAMPUS, society.getCampus());
        assertEquals(new BigDecimal("75.00"), society.getMembershipFee());
        assertEquals(new BigDecimal("5000.00"), society.getCurrentBalance());
        verify(societyRepository).saveAndFlush(society);
    }

    @Test
    void malformedSocialUrlIsRejectedBeforeSave() {
        when(authorizationService.requireCanEditOwnSociety(EMAIL))
                .thenReturn(context);
        UpdateSocietyPublicProfileRequestDTO request =
                new UpdateSocietyPublicProfileRequestDTO();
        request.setFacebookURL("javascript:alert(1)");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePublicProfile(EMAIL, request));

        assertEquals(
                "Social media links must be valid HTTP or HTTPS URLs.",
                exception.getMessage());
        verify(societyRepository, never()).saveAndFlush(society);
    }

    @Test
    void persistenceFailureIsPropagatedBeforeAProfileSuccessIsMapped() {
        when(authorizationService.requireCanEditOwnSociety(EMAIL))
                .thenReturn(context);
        when(societyRepository.saveAndFlush(society))
                .thenThrow(new IllegalStateException("database write failed"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.updatePublicProfile(
                        EMAIL,
                        new UpdateSocietyPublicProfileRequestDTO()));

        assertEquals("database write failed", exception.getMessage());
        verify(societyBrowseService, never())
                .getExecutiveSocietyProfile(SOCIETY_ID, EMAIL);
    }

    @Test
    void readOnlyExecutiveCannotReachPersistence() {
        when(authorizationService.requireCanEditOwnSociety(EMAIL))
                .thenThrow(new ForbiddenOperationException(
                        "Only the President or Secretary may edit the society profile."));

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.updatePublicProfile(
                        EMAIL,
                        new UpdateSocietyPublicProfileRequestDTO()));
        verify(societyRepository, never()).saveAndFlush(society);
    }

    @Test
    void scopedUpdateRejectsAnotherSocietyBeforePersistence() {
        when(authorizationService.requireCanEditSocietyProfile(
                EMAIL, "SOC002"))
                .thenThrow(new ForbiddenOperationException(
                        "You are not authorised to edit this society profile."));

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.updatePublicProfile(
                        EMAIL,
                        "SOC002",
                        new UpdateSocietyPublicProfileRequestDTO()));

        verify(societyRepository, never()).saveAndFlush(any(Society.class));
    }

    @Test
    void uploadMethodsFixTheMediaTypeServerSide() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", new byte[]{1});

        service.uploadSocietyLogo(EMAIL, file);
        service.uploadSocietyBanner(EMAIL, file);

        verify(societyMediaService).uploadForExecutive(
                EMAIL, file, SocietyImageType.LOGO);
        verify(societyMediaService).uploadForExecutive(
                EMAIL, file, SocietyImageType.BANNER);
    }

    private Society society() {
        Society value = new Society();
        value.setSocietyID(SOCIETY_ID);
        value.setSocietyName("Computing Society");
        value.setSdoStaffNumber("SDO001");
        value.setActiveStatus(true);
        value.setSocietyType(SocietyType.ACADEMIC);
        value.setCampus(Campus.SOUTH_CAMPUS);
        value.setMembershipFee(new BigDecimal("75.00"));
        value.setCurrentBalance(new BigDecimal("5000.00"));
        value.setAnnualBudgetAllocation(new BigDecimal("10000.00"));
        return value;
    }

    private ExecutiveSocietyResolver.ActiveExecutiveSociety context(
            Society value,
            String position) {
        Student student = new Student();
        student.setStudentNumber("220000001");
        student.setEmail(EMAIL);

        Executive role = new Executive();
        role.setId(new ExecutiveId(
                student.getStudentNumber(),
                SOCIETY_ID,
                LocalDate.of(2030, 1, 1)));
        role.setStudent(student);
        role.setSociety(value);
        role.setPosition(position);
        return new ExecutiveSocietyResolver.ActiveExecutiveSociety(
                student, value, role);
    }
}
