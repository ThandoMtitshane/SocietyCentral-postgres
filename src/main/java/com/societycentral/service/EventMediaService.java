package com.societycentral.service;

import com.societycentral.dto.response.EventMediaUploadResponseDTO;
import com.societycentral.exception.EventMediaException;
import com.societycentral.model.EventImageType;
import com.societycentral.model.Executive;
import com.societycentral.model.Student;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.StudentRepository;
import com.societycentral.utils.StoredEventMedia;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Authorizes, validates, and stores event poster and banner uploads.
 *
 * <p>Uploads intentionally occur before event creation. An upload that is not
 * later referenced by an event can therefore remain orphaned. Stored metadata
 * includes a creation time and the storage abstraction exposes deletion so a
 * bounded cleanup policy can be added later without changing this workflow.</p>
 */
@Service
public class EventMediaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            EventMediaService.class);

    static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp");

    private final StudentRepository studentRepository;
    private final ExecutiveRepository executiveRepository;
    private final EventMediaStorageService storageService;

    /**
     * Creates the event-media service.
     *
     * @param studentRepository repository used to resolve the authenticated student
     * @param executiveRepository repository used to verify an active executive term
     * @param storageService storage abstraction used after validation succeeds
     */
    public EventMediaService(
            StudentRepository studentRepository,
            ExecutiveRepository executiveRepository,
            EventMediaStorageService storageService) {
        this.studentRepository = studentRepository;
        this.executiveRepository = executiveRepository;
        this.storageService = storageService;
    }

    /**
     * Uploads one frontend-cropped and resized event image for the
     * authenticated executive. The received file is treated as the final
     * processed output; no original source image is accepted or inspected.
     *
     * @param executiveEmail authenticated account email from the security context
     * @param file uploaded image part
     * @param imageType requested image type
     * @return validated and stored image metadata
     */
    public EventMediaUploadResponseDTO upload(
            String executiveEmail,
            MultipartFile file,
            EventImageType imageType) {

        requireActiveExecutive(executiveEmail);
        if (imageType == null) {
            throw EventMediaException.invalidImageType();
        }
        validateBasicFileRules(file);

        String reportedMimeType = normaliseMimeType(file.getContentType());
        if (!ALLOWED_MIME_TYPES.contains(reportedMimeType)) {
            throw EventMediaException.unsupportedMediaType(
                    "Unsupported event image format.");
        }

        DecodedImageMetadata decoded = decodeAndValidate(
                file,
                imageType,
                reportedMimeType);

        StoredEventMedia stored = storageService.store(
                file,
                imageType,
                decoded.mimeType(),
                decoded.width(),
                decoded.height());

        return new EventMediaUploadResponseDTO(
                stored.fileUrl(),
                stored.fileName(),
                stored.imageType(),
                stored.width(),
                stored.height(),
                stored.sizeBytes(),
                stored.createdAt());
    }

    private void requireActiveExecutive(String executiveEmail) {
        Student student = studentRepository.findByEmail(executiveEmail)
                .orElseThrow(EventMediaException::unauthorisedExecutive);

        // BUSINESS RULE: Only a currently serving society executive may
        // upload media through the executive event workflow.
        boolean activeExecutive = executiveRepository
                .findByIdStudentNumber(student.getStudentNumber())
                .stream()
                .anyMatch(this::isExecutiveTermActive);

        if (!activeExecutive) {
            throw EventMediaException.unauthorisedExecutive();
        }
    }

    private boolean isExecutiveTermActive(Executive executive) {
        return executive.getTermEndDate() == null || !executive.getTermEndDate().isBefore(LocalDate.now());
    }

    private void validateBasicFileRules(MultipartFile file) {
        if (file == null) {
            throw EventMediaException.invalidFile("Event image file is required.");
        }

        if (file.isEmpty()) {
            throw EventMediaException.invalidFile("Event image file must not be empty.");
        }

        // BUSINESS RULE: Posters and banners are limited to five mebibytes.
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw EventMediaException.fileTooLarge();
        }
    }

    private DecodedImageMetadata decodeAndValidate(
            MultipartFile file,
            EventImageType imageType,
            String reportedMimeType) {

        ImageReader reader = null;
        try (ImageInputStream imageInput =
                     ImageIO.createImageInputStream(file.getInputStream())) {

            if (imageInput == null) {
                throw EventMediaException.unreadableImage(null);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw EventMediaException.unreadableImage(null);
            }

            reader = readers.next();
            String decodedMimeType = mimeTypeForFormat(reader.getFormatName());
            if (decodedMimeType == null) {
                throw EventMediaException.unsupportedMediaType(
                        "Unsupported event image format.");
            }

            if (!reportedMimeType.equals(decodedMimeType)) {
                throw EventMediaException.unsupportedMediaType(
                        "Unsupported event image format.");
            }

            reader.setInput(imageInput, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);

            LOGGER.debug(
                    "Event media upload received: type={}, filename={}, contentType={}, sizeBytes={}, decodedWidth={}, decodedHeight={}, expectedWidth={}, expectedHeight={}",
                    imageType,
                    file.getOriginalFilename(),
                    reportedMimeType,
                    file.getSize(),
                    width,
                    height,
                    imageType.getRequiredWidth(),
                    imageType.getRequiredHeight());

            // BUSINESS RULE: The endpoint receives only the frontend's final
            // cropped/resized output, which must retain the exact required
            // dimensions because client-side validation can be bypassed.
            if (width != imageType.getRequiredWidth() || height != imageType.getRequiredHeight()) {
                throw EventMediaException.invalidDimensions(imageType);
            }

            BufferedImage decodedImage = reader.read(0);
            if (decodedImage == null || decodedImage.getWidth() != width || decodedImage.getHeight() != height) {
                throw EventMediaException.unreadableImage(null);
            }

            return new DecodedImageMetadata(
                    decodedMimeType,
                    width,
                    height);
        } catch (EventMediaException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            throw EventMediaException.unreadableImage(ex);
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private String normaliseMimeType(String mimeType) {
        return mimeType == null
                ? ""
                : mimeType.trim().toLowerCase(Locale.ROOT);
    }

    private String mimeTypeForFormat(String formatName) {
        return switch (formatName.toLowerCase(Locale.ROOT)) {
            case "jpeg", "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> null;
        };
    }

    private record DecodedImageMetadata(
            String mimeType,
            int width,
            int height) {
    }
}
