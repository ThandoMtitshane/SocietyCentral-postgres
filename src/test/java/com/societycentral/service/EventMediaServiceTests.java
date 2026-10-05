package com.societycentral.service;

import com.societycentral.dto.response.EventMediaUploadResponseDTO;
import com.societycentral.exception.EventMediaException;
import com.societycentral.model.EventImageType;
import com.societycentral.model.Executive;
import com.societycentral.model.ExecutiveId;
import com.societycentral.model.Student;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.StudentRepository;
import com.societycentral.utils.StoredEventMedia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventMediaServiceTests {

    private static final String EXECUTIVE_EMAIL = "executive@nmu.ac.za";
    private static final String STUDENT_NUMBER = "220000001";

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ExecutiveRepository executiveRepository;
    @Mock
    private EventMediaStorageService storageService;

    @InjectMocks
    private EventMediaService eventMediaService;

    @Test
    void uploadsValidPoster() throws IOException {
        authoriseActiveExecutive();
        MockMultipartFile poster = imageFile(1080, 1350, "image/png");
        Instant createdAt = Instant.parse("2026-07-23T00:00:00Z");
        StoredEventMedia stored = new StoredEventMedia(
                "http://localhost:8080/media/events/posters/poster.png",
                "poster.png",
                EventImageType.POSTER,
                "image/png",
                1080,
                1350,
                poster.getSize(),
                createdAt);
        when(storageService.store(
                same(poster),
                eq(EventImageType.POSTER),
                eq("image/png"),
                eq(1080),
                eq(1350))).thenReturn(stored);

        EventMediaUploadResponseDTO response = eventMediaService.upload(
                EXECUTIVE_EMAIL,
                poster,
                EventImageType.POSTER);

        assertEquals(EventImageType.POSTER, response.imageType());
        assertEquals(1080, response.width());
        assertEquals(1350, response.height());
        assertEquals(poster.getSize(), response.sizeBytes());
        assertEquals(createdAt, response.createdAt());
    }

    @Test
    void uploadsValidBanner() throws IOException {
        authoriseActiveExecutive();
        MockMultipartFile banner = imageFile(1500, 500, "image/png");
        StoredEventMedia stored = new StoredEventMedia(
                "http://localhost:8080/media/events/banners/banner.png",
                "banner.png",
                EventImageType.BANNER,
                "image/png",
                1500,
                500,
                banner.getSize(),
                Instant.now());
        when(storageService.store(
                same(banner),
                eq(EventImageType.BANNER),
                eq("image/png"),
                eq(1500),
                eq(500))).thenReturn(stored);

        EventMediaUploadResponseDTO response = eventMediaService.upload(
                EXECUTIVE_EMAIL,
                banner,
                EventImageType.BANNER);

        assertEquals(EventImageType.BANNER, response.imageType());
        assertEquals(1500, response.width());
        assertEquals(500, response.height());
    }

    @Test
    void rejectsIncorrectProcessedPosterDimensions() throws IOException {
        authoriseActiveExecutive();
        MockMultipartFile poster = imageFile(1080, 1349, "image/png");

        EventMediaException exception = assertThrows(
                EventMediaException.class,
                () -> eventMediaService.upload(
                        EXECUTIVE_EMAIL,
                        poster,
                        EventImageType.POSTER));

        assertEquals(
                EventMediaException.Reason.INVALID_DIMENSIONS,
                exception.getReason());
        assertEquals(
                "Processed poster must be exactly 1080 \u00D7 1350 pixels.",
                exception.getMessage());
        verifyNoInteractions(storageService);
    }

    @Test
    void rejectsIncorrectProcessedBannerDimensions() throws IOException {
        authoriseActiveExecutive();
        MockMultipartFile banner = imageFile(1499, 500, "image/png");

        EventMediaException exception = assertThrows(
                EventMediaException.class,
                () -> eventMediaService.upload(
                        EXECUTIVE_EMAIL,
                        banner,
                        EventImageType.BANNER));

        assertEquals(
                EventMediaException.Reason.INVALID_DIMENSIONS,
                exception.getReason());
        assertEquals(
                "Processed banner must be exactly 1500 \u00D7 500 pixels.",
                exception.getMessage());
        verifyNoInteractions(storageService);
    }

    @Test
    void rejectsOversizedImage() {
        authoriseActiveExecutive();
        MockMultipartFile oversized = new MockMultipartFile(
                "file",
                "oversized.png",
                "image/png",
                new byte[(int) EventMediaService.MAX_FILE_SIZE_BYTES + 1]);

        EventMediaException exception = assertThrows(
                EventMediaException.class,
                () -> eventMediaService.upload(
                        EXECUTIVE_EMAIL,
                        oversized,
                        EventImageType.POSTER));

        assertEquals(
                EventMediaException.Reason.FILE_TOO_LARGE,
                exception.getReason());
        verifyNoInteractions(storageService);
    }

    @Test
    void rejectsUnsupportedMimeType() {
        authoriseActiveExecutive();
        MockMultipartFile unsupported = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                new byte[]{1, 2, 3});

        EventMediaException exception = assertThrows(
                EventMediaException.class,
                () -> eventMediaService.upload(
                        EXECUTIVE_EMAIL,
                        unsupported,
                        EventImageType.POSTER));

        assertEquals(
                EventMediaException.Reason.UNSUPPORTED_MEDIA_TYPE,
                exception.getReason());
        assertEquals("Unsupported event image format.", exception.getMessage());
        verifyNoInteractions(storageService);
    }

    @Test
    void rejectsEmptyFile() {
        authoriseActiveExecutive();
        MockMultipartFile empty = new MockMultipartFile(
                "file",
                "empty.png",
                "image/png",
                new byte[0]);

        EventMediaException exception = assertThrows(
                EventMediaException.class,
                () -> eventMediaService.upload(
                        EXECUTIVE_EMAIL,
                        empty,
                        EventImageType.POSTER));

        assertEquals(
                EventMediaException.Reason.INVALID_FILE,
                exception.getReason());
        verifyNoInteractions(storageService);
    }

    @Test
    void rejectsInvalidImageType() {
        authoriseActiveExecutive();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "poster.png",
                "image/png",
                new byte[]{1});

        EventMediaException exception = assertThrows(
                EventMediaException.class,
                () -> eventMediaService.upload(EXECUTIVE_EMAIL, file, null));

        assertEquals(
                EventMediaException.Reason.INVALID_IMAGE_TYPE,
                exception.getReason());
    }

    @Test
    void rejectsUnreadableImageContents() {
        authoriseActiveExecutive();
        MockMultipartFile unreadable = new MockMultipartFile(
                "file",
                "fake.png",
                "image/png",
                new byte[]{1, 2, 3, 4});

        EventMediaException exception = assertThrows(
                EventMediaException.class,
                () -> eventMediaService.upload(
                        EXECUTIVE_EMAIL,
                        unreadable,
                        EventImageType.POSTER));

        assertEquals(
                EventMediaException.Reason.UNREADABLE_IMAGE,
                exception.getReason());
        assertEquals("Unable to read the uploaded image.", exception.getMessage());
        verifyNoInteractions(storageService);
    }

    @Test
    void rejectsReportedMimeTypeThatDoesNotMatchContents() throws IOException {
        authoriseActiveExecutive();
        MockMultipartFile mismatched = imageFile(10, 10, "image/jpeg");

        EventMediaException exception = assertThrows(
                EventMediaException.class,
                () -> eventMediaService.upload(
                        EXECUTIVE_EMAIL,
                        mismatched,
                        EventImageType.POSTER));

        assertEquals(
                EventMediaException.Reason.UNSUPPORTED_MEDIA_TYPE,
                exception.getReason());
        verifyNoInteractions(storageService);
    }

    @Test
    void rejectsStudentWithoutActiveExecutiveRole() {
        Student student = student();
        when(studentRepository.findByEmail(EXECUTIVE_EMAIL))
                .thenReturn(Optional.of(student));
        when(executiveRepository.findByIdStudentNumber(STUDENT_NUMBER))
                .thenReturn(List.of());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "poster.png",
                "image/png",
                new byte[]{1});

        EventMediaException exception = assertThrows(
                EventMediaException.class,
                () -> eventMediaService.upload(
                        EXECUTIVE_EMAIL,
                        file,
                        EventImageType.POSTER));

        assertEquals(
                EventMediaException.Reason.UNAUTHORISED_EXECUTIVE,
                exception.getReason());
        verifyNoInteractions(storageService);
    }

    @Test
    void webpImageReaderIsAvailable() {
        assertTrue(
                ImageIO.getImageReadersByFormatName("webp").hasNext(),
                "The configured ImageIO provider must register a WebP reader.");
    }

    private void authoriseActiveExecutive() {
        Student student = student();
        Executive executive = new Executive();
        executive.setId(new ExecutiveId(
                STUDENT_NUMBER,
                "SOC001",
                LocalDate.now().minusMonths(1)));
        executive.setTermEndDate(null);

        when(studentRepository.findByEmail(EXECUTIVE_EMAIL))
                .thenReturn(Optional.of(student));
        when(executiveRepository.findByIdStudentNumber(STUDENT_NUMBER))
                .thenReturn(List.of(executive));
    }

    private Student student() {
        Student student = new Student();
        student.setStudentNumber(STUDENT_NUMBER);
        student.setEmail(EXECUTIVE_EMAIL);
        return student;
    }

    private MockMultipartFile imageFile(
            int width,
            int height,
            String reportedMimeType) throws IOException {
        BufferedImage image = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile(
                "file",
                "client-name.png",
                reportedMimeType,
                output.toByteArray());
    }
}
