package com.societycentral.service;

import com.societycentral.dto.response.SocietyMediaUploadResponseDTO;
import com.societycentral.dto.response.SocietyGalleryMediaResponseDTO;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.exception.SocietyMediaException;
import com.societycentral.model.Executive;
import com.societycentral.model.ExecutiveId;
import com.societycentral.model.SDO;
import com.societycentral.model.Society;
import com.societycentral.model.SocietyImageType;
import com.societycentral.model.SocietyMedia;
import com.societycentral.model.Student;
import com.societycentral.repository.SDORepository;
import com.societycentral.repository.SocietyMediaRepository;
import com.societycentral.repository.SocietyRepository;
import com.societycentral.utils.StoredSocietyMedia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocietyMediaServiceTests {

    private static final String SDO_EMAIL = "sdo@nmu.ac.za";
    private static final String SOCIETY_ID = "SOC001";

    @Mock
    private SDORepository sdoRepository;
    @Mock
    private SocietyRepository societyRepository;
    @Mock
    private SocietyMediaRepository societyMediaRepository;
    @Mock
    private SocietyMediaStorageService storageService;
    @Mock
    private SocietyProfileEditAuthorizationService
            profileEditAuthorizationService;

    private SocietyMediaService service;
    private Society society;

    @BeforeEach
    void setUp() {
        service = new SocietyMediaService(
                sdoRepository,
                societyRepository,
                societyMediaRepository,
                storageService,
                profileEditAuthorizationService,
                Clock.fixed(
                        Instant.parse("2030-08-11T08:30:00Z"),
                        ZoneOffset.UTC));
        society = new Society();
        society.setSocietyID(SOCIETY_ID);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void acceptsValidLogoWithoutMinimumSourceDimensions()
            throws IOException {
        MockMultipartFile file = pngFile("logo.png", 120, 80);
        StoredSocietyMedia stored = stored(
                SocietyImageType.LOGO,
                "/media/societies/logos/logo.png",
                120,
                80);
        stubAuthorisedSociety();
        when(storageService.store(
                eq(file),
                eq(SocietyImageType.LOGO),
                eq("image/png"),
                eq(120),
                eq(80))).thenReturn(stored);

        SocietyMediaUploadResponseDTO result = service.upload(
                SDO_EMAIL,
                SOCIETY_ID,
                file,
                SocietyImageType.LOGO);

        assertEquals(stored.fileUrl(), result.fileUrl());
        assertEquals(stored.fileUrl(), society.getLogoUrl());
        verify(societyRepository).saveAndFlush(society);
    }

    @Test
    void acceptsBannerWithoutRequiringExactDisplayDimensions()
            throws IOException {
        MockMultipartFile file = pngFile("banner.png", 800, 800);
        StoredSocietyMedia stored = stored(
                SocietyImageType.BANNER,
                "https://api.example.com/media/societies/banners/banner.png",
                800,
                800);
        stubAuthorisedSociety();
        when(storageService.store(
                file,
                SocietyImageType.BANNER,
                "image/png",
                800,
                800)).thenReturn(stored);

        SocietyMediaUploadResponseDTO result = service.upload(
                SDO_EMAIL,
                SOCIETY_ID,
                file,
                SocietyImageType.BANNER);

        assertEquals(800, result.width());
        assertEquals(800, result.height());
        assertEquals(stored.fileUrl(), society.getBannerUrl());
        verify(societyRepository).saveAndFlush(society);
    }

    @Test
    void enforcesTwoMegabyteLogoLimitBeforeDecoding() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.png",
                "image/png",
                new byte[(2 * 1024 * 1024) + 1]);
        stubAuthorisedSociety();

        SocietyMediaException exception = assertThrows(
                SocietyMediaException.class,
                () -> service.upload(
                        SDO_EMAIL,
                        SOCIETY_ID,
                        file,
                        SocietyImageType.LOGO));

        assertEquals(
                "Society logo must not exceed 2 MB.",
                exception.getMessage());
        verify(storageService, never()).store(
                any(), any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void executiveUploadUsesAuthorisedPersistedSociety() throws IOException {
        MockMultipartFile file = pngFile("logo.png", 450, 300);
        StoredSocietyMedia stored = stored(
                SocietyImageType.LOGO,
                "/media/societies/logos/logo.png",
                450,
                300);
        when(profileEditAuthorizationService.requireCanEditOwnSociety(
                "president@nmu.ac.za"))
                .thenReturn(executiveContext("President"));
        when(storageService.store(
                file,
                SocietyImageType.LOGO,
                "image/png",
                450,
                300)).thenReturn(stored);

        SocietyMediaUploadResponseDTO result = service.uploadForExecutive(
                "president@nmu.ac.za",
                file,
                SocietyImageType.LOGO);

        assertEquals(stored.fileUrl(), result.fileUrl());
        assertEquals(stored.fileUrl(), society.getLogoUrl());
        verify(societyRepository).saveAndFlush(society);
    }

    @Test
    void scopedExecutiveUploadUsesExactSocietyGuard() throws IOException {
        MockMultipartFile file = pngFile("banner.png", 900, 300);
        StoredSocietyMedia stored = stored(
                SocietyImageType.BANNER,
                "/media/societies/banners/banner.png",
                900,
                300);
        when(profileEditAuthorizationService
                .requireCanEditSocietyProfile(
                        "secretary@nmu.ac.za", SOCIETY_ID))
                .thenReturn(executiveContext("Secretary"));
        when(storageService.store(
                file,
                SocietyImageType.BANNER,
                "image/png",
                900,
                300)).thenReturn(stored);

        SocietyMediaUploadResponseDTO result = service.uploadForExecutive(
                "secretary@nmu.ac.za",
                SOCIETY_ID,
                file,
                SocietyImageType.BANNER);

        assertEquals(stored.fileUrl(), result.fileUrl());
        assertEquals(stored.fileUrl(), society.getBannerUrl());
        verify(societyRepository).saveAndFlush(society);
    }

    @Test
    void executiveBannerEnforcesFiveMegabyteLimitBeforeDecoding() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.png",
                "image/png",
                new byte[(5 * 1024 * 1024) + 1]);
        when(profileEditAuthorizationService.requireCanEditOwnSociety(
                "secretary@nmu.ac.za"))
                .thenReturn(executiveContext("Secretary"));

        SocietyMediaException exception = assertThrows(
                SocietyMediaException.class,
                () -> service.uploadForExecutive(
                        "secretary@nmu.ac.za",
                        file,
                        SocietyImageType.BANNER));

        assertEquals(
                "Society banner must not exceed 5 MB.",
                exception.getMessage());
        verify(storageService, never()).store(
                any(), any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void executiveLogoRejectsUnsupportedMimeWithLogoSpecificMessage() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.gif",
                "image/gif",
                new byte[]{1, 2, 3});
        when(profileEditAuthorizationService.requireCanEditOwnSociety(
                "president@nmu.ac.za"))
                .thenReturn(executiveContext("President"));

        SocietyMediaException exception = assertThrows(
                SocietyMediaException.class,
                () -> service.uploadForExecutive(
                        "president@nmu.ac.za",
                        file,
                        SocietyImageType.LOGO));

        assertEquals(
                "Society logo must be a PNG, JPEG or WebP image.",
                exception.getMessage());
    }

    @Test
    void secretaryUploadsPersistedGalleryImageForExactSociety()
            throws IOException {
        String email = "secretary@nmu.ac.za";
        MockMultipartFile file = pngFile("highlight.png", 1200, 500);
        StoredSocietyMedia stored = stored(
                SocietyImageType.GALLERY_IMAGE,
                "/media/societies/gallery/gallery-image.png",
                1200,
                500);
        when(profileEditAuthorizationService
                .requireCanEditSocietyProfile(email, SOCIETY_ID))
                .thenReturn(executiveContext("Secretary"));
        when(storageService.store(
                file,
                SocietyImageType.GALLERY_IMAGE,
                "image/png",
                1200,
                500)).thenReturn(stored);
        when(societyMediaRepository.findMaximumSortOrder(
                SOCIETY_ID, SocietyImageType.GALLERY_IMAGE))
                .thenReturn(-1);
        when(societyMediaRepository.saveAndFlush(any(SocietyMedia.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SocietyGalleryMediaResponseDTO result =
                service.uploadGalleryForExecutive(
                        email,
                        SOCIETY_ID,
                        file,
                        "  Coding outreach day  ");

        assertEquals(stored.fileUrl(), result.getMediaUrl());
        assertEquals("Coding outreach day", result.getCaption());
        assertEquals(0, result.getSortOrder());

        ArgumentCaptor<SocietyMedia> mediaCaptor =
                ArgumentCaptor.forClass(SocietyMedia.class);
        verify(societyMediaRepository).saveAndFlush(mediaCaptor.capture());
        assertEquals(SOCIETY_ID,
                mediaCaptor.getValue().getSocietyID());
        assertEquals(email, mediaCaptor.getValue().getUploadedBy());
        assertEquals(LocalDateTime.of(2030, 8, 11, 8, 30),
                mediaCaptor.getValue().getUploadedAt());
    }

    @Test
    void galleryUploadStopsBeforeStorageWhenSocietyGuardRejects()
            throws IOException {
        String email = "president@nmu.ac.za";
        MockMultipartFile file = pngFile("highlight.png", 1200, 500);
        when(profileEditAuthorizationService
                .requireCanEditSocietyProfile(email, "SOC002"))
                .thenThrow(new ForbiddenOperationException(
                        "You are not authorised to edit this society profile."));

        assertThrows(ForbiddenOperationException.class,
                () -> service.uploadGalleryForExecutive(
                        email, "SOC002", file, null));

        verify(storageService, never()).store(
                any(), any(), any(), any(Integer.class), any(Integer.class));
        verify(societyMediaRepository, never())
                .saveAndFlush(any(SocietyMedia.class));
    }

    @Test
    void deletingGalleryImageChecksSocietyOwnershipAndRemovesFile() {
        String email = "president@nmu.ac.za";
        SocietyMedia media = new SocietyMedia();
        media.setMediaID("MEDIA001");
        media.setSocietyID(SOCIETY_ID);
        media.setMediaType(SocietyImageType.GALLERY_IMAGE);
        media.setMediaUrl(
                "/media/societies/gallery/"
                        + "00000000-0000-0000-0000-000000000001.png");
        when(profileEditAuthorizationService
                .requireCanEditSocietyProfile(email, SOCIETY_ID))
                .thenReturn(executiveContext("President"));
        when(societyMediaRepository
                .findByMediaIDAndSocietyIDAndMediaType(
                        "MEDIA001",
                        SOCIETY_ID,
                        SocietyImageType.GALLERY_IMAGE))
                .thenReturn(Optional.of(media));

        TransactionSynchronizationManager.initSynchronization();

        service.deleteGalleryForExecutive(
                email, SOCIETY_ID, "MEDIA001");

        verify(societyMediaRepository).delete(media);
        verify(societyMediaRepository).flush();
        verify(storageService, never()).delete(
                SocietyImageType.GALLERY_IMAGE,
                "00000000-0000-0000-0000-000000000001.png");

        commitTransactionSynchronization();

        verify(storageService).delete(
                SocietyImageType.GALLERY_IMAGE,
                "00000000-0000-0000-0000-000000000001.png");
    }

    @Test
    void replacingLogoDeletesPreviousGeneratedFileOnlyAfterCommit()
            throws IOException {
        String previousFile =
                "00000000-0000-0000-0000-000000000001.png";
        String newFile =
                "00000000-0000-0000-0000-000000000002.png";
        society.setLogoUrl(
                "/media/societies/logos/" + previousFile);
        MockMultipartFile file = pngFile("logo.png", 120, 80);
        StoredSocietyMedia stored = new StoredSocietyMedia(
                "/media/societies/logos/" + newFile,
                newFile,
                SocietyImageType.LOGO,
                "image/png",
                120,
                80,
                1024,
                Instant.parse("2030-08-11T08:30:00Z"));
        stubAuthorisedSociety();
        when(storageService.store(
                file,
                SocietyImageType.LOGO,
                "image/png",
                120,
                80)).thenReturn(stored);
        TransactionSynchronizationManager.initSynchronization();

        service.upload(
                SDO_EMAIL,
                SOCIETY_ID,
                file,
                SocietyImageType.LOGO);

        assertEquals(stored.fileUrl(), society.getLogoUrl());
        verify(storageService, never()).delete(
                SocietyImageType.LOGO, previousFile);

        commitTransactionSynchronization();

        verify(storageService).delete(
                SocietyImageType.LOGO, previousFile);
        verify(storageService, never()).delete(
                SocietyImageType.LOGO, newFile);
    }

    @Test
    void rolledBackLogoReplacementKeepsOldFileAndDeletesNewFile()
            throws IOException {
        String previousFile =
                "00000000-0000-0000-0000-000000000001.png";
        String newFile =
                "00000000-0000-0000-0000-000000000002.png";
        society.setLogoUrl(
                "/media/societies/logos/" + previousFile);
        MockMultipartFile file = pngFile("logo.png", 120, 80);
        StoredSocietyMedia stored = new StoredSocietyMedia(
                "/media/societies/logos/" + newFile,
                newFile,
                SocietyImageType.LOGO,
                "image/png",
                120,
                80,
                1024,
                Instant.parse("2030-08-11T08:30:00Z"));
        stubAuthorisedSociety();
        when(storageService.store(
                file,
                SocietyImageType.LOGO,
                "image/png",
                120,
                80)).thenReturn(stored);
        TransactionSynchronizationManager.initSynchronization();

        service.upload(
                SDO_EMAIL,
                SOCIETY_ID,
                file,
                SocietyImageType.LOGO);

        rollbackTransactionSynchronization();

        verify(storageService, never()).delete(
                SocietyImageType.LOGO, previousFile);
        verify(storageService).delete(
                SocietyImageType.LOGO, newFile);
    }

    private void stubAuthorisedSociety() {
        when(sdoRepository.findByEmail(SDO_EMAIL))
                .thenReturn(Optional.of(new SDO()));
        when(societyRepository.findById(SOCIETY_ID))
                .thenReturn(Optional.of(society));
    }

    private void commitTransactionSynchronization() {
        var synchronizations = TransactionSynchronizationManager
                .getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(synchronization ->
                synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void rollbackTransactionSynchronization() {
        var synchronizations = TransactionSynchronizationManager
                .getSynchronizations();
        synchronizations.forEach(synchronization ->
                synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
    }

    private MockMultipartFile pngFile(
            String fileName,
            int width,
            int height) throws IOException {
        BufferedImage image = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile(
                "file",
                fileName,
                "image/png",
                output.toByteArray());
    }

    private StoredSocietyMedia stored(
            SocietyImageType imageType,
            String url,
            int width,
            int height) {
        return new StoredSocietyMedia(
                url,
                imageType.name().toLowerCase() + ".png",
                imageType,
                "image/png",
                width,
                height,
                1024,
                Instant.parse("2030-08-11T08:30:00Z"));
    }

    private ExecutiveSocietyResolver.ActiveExecutiveSociety
    executiveContext(String position) {
        Student student = new Student();
        student.setStudentNumber("220000001");
        student.setEmail("executive@nmu.ac.za");
        Executive role = new Executive();
        role.setId(new ExecutiveId(
                student.getStudentNumber(),
                SOCIETY_ID,
                LocalDate.of(2030, 1, 1)));
        role.setStudent(student);
        role.setSociety(society);
        role.setPosition(position);
        return new ExecutiveSocietyResolver.ActiveExecutiveSociety(
                student, society, role);
    }
}
