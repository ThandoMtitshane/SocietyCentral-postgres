package com.societycentral.service;

import com.societycentral.dto.response.SocietyMediaUploadResponseDTO;
import com.societycentral.dto.response.SocietyGalleryMediaResponseDTO;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.exception.SocietyMediaException;
import com.societycentral.model.Society;
import com.societycentral.model.SocietyImageType;
import com.societycentral.model.SocietyMedia;
import com.societycentral.repository.SDORepository;
import com.societycentral.repository.SocietyMediaRepository;
import com.societycentral.repository.SocietyRepository;
import com.societycentral.utils.StoredSocietyMedia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Validates, stores, and assigns society logos and banners.
 */
@Service
public class SocietyMediaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SocietyMediaService.class);
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp");

    private final SDORepository sdoRepository;
    private final SocietyRepository societyRepository;
    private final SocietyMediaRepository societyMediaRepository;
    private final SocietyMediaStorageService storageService;
    private final SocietyProfileEditAuthorizationService
            profileEditAuthorizationService;
    private final Clock clock;

    public SocietyMediaService(
            SDORepository sdoRepository,
            SocietyRepository societyRepository,
            SocietyMediaRepository societyMediaRepository,
            SocietyMediaStorageService storageService,
            SocietyProfileEditAuthorizationService
                    profileEditAuthorizationService,
            Clock clock) {
        this.sdoRepository = sdoRepository;
        this.societyRepository = societyRepository;
        this.societyMediaRepository = societyMediaRepository;
        this.storageService = storageService;
        this.profileEditAuthorizationService =
                profileEditAuthorizationService;
        this.clock = clock;
    }

    /**
     * Stores a validated profile image and assigns its URL to the society.
     *
     * @param authenticatedEmail authenticated SDO email
     * @param societyID society receiving the image
     * @param file uploaded source image
     * @param imageType LOGO or BANNER
     * @return stored image metadata
     */
    @Transactional
    public SocietyMediaUploadResponseDTO upload(
            String authenticatedEmail,
            String societyID,
            MultipartFile file,
            SocietyImageType imageType) {
        // 1. Authorise the existing SDO management workflow
        if (authenticatedEmail == null || authenticatedEmail.isBlank()
                || sdoRepository.findByEmail(authenticatedEmail).isEmpty()) {
            throw new ForbiddenOperationException(
                    "Only a registered SDO may update society media.");
        }

        // 2. Resolve the target society and requested image type
        if (societyID == null || societyID.isBlank()) {
            throw new IllegalArgumentException("Society ID is required.");
        }
        Society society = societyRepository.findById(societyID.trim())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Society not found."));
        requireImageType(imageType);

        return validateStoreAndAssign(society, file, imageType);
    }

    /**
     * Stores profile media for the authenticated President or Secretary's
     * own active society. Authority is resolved from persistence here rather
     * than accepted from a controller or multipart parameter.
     */
    @Transactional
    public SocietyMediaUploadResponseDTO uploadForExecutive(
            String authenticatedEmail,
            MultipartFile file,
            SocietyImageType imageType) {
        requireImageType(imageType);
        Society society = profileEditAuthorizationService
                .requireCanEditOwnSociety(authenticatedEmail)
                .society();
        return validateStoreAndAssign(society, file, imageType);
    }

    /**
     * Society-scoped executive upload for callers with concurrent active
     * roles. Ownership and position are re-checked in the service layer.
     */
    @Transactional
    public SocietyMediaUploadResponseDTO uploadForExecutive(
            String authenticatedEmail,
            String societyID,
            MultipartFile file,
            SocietyImageType imageType) {
        requireImageType(imageType);
        Society society = profileEditAuthorizationService
                .requireCanEditSocietyProfile(
                        authenticatedEmail, societyID)
                .society();
        return validateStoreAndAssign(society, file, imageType);
    }

    /**
     * Adds one ordered gallery image to the authenticated profile manager's
     * own society.
     */
    @Transactional
    public SocietyGalleryMediaResponseDTO uploadGalleryForExecutive(
            String authenticatedEmail,
            MultipartFile file,
            String caption) {
        Society society = profileEditAuthorizationService
                .requireCanEditOwnSociety(authenticatedEmail)
                .society();
        return validateStoreAndCreateGalleryMedia(
                authenticatedEmail, society, file, caption);
    }

    /**
     * Adds one gallery image after authorising the caller for the exact
     * society contained in the route.
     */
    @Transactional
    public SocietyGalleryMediaResponseDTO uploadGalleryForExecutive(
            String authenticatedEmail,
            String societyID,
            MultipartFile file,
            String caption) {
        Society society = profileEditAuthorizationService
                .requireCanEditSocietyProfile(
                        authenticatedEmail, societyID)
                .society();
        return validateStoreAndCreateGalleryMedia(
                authenticatedEmail, society, file, caption);
    }

    /**
     * Stores a validated article cover as a managed media record. Caller
     * authorization is performed by SocietyHighlightService for the exact
     * society before this method is reached.
     */
    @Transactional
    public SocietyMedia createHighlightCover(
            String authenticatedEmail,
            Society society,
            MultipartFile file) {
        if (society == null || society.getSocietyID() == null
                || society.getSocietyID().isBlank()) {
            throw new IllegalArgumentException("Society is required.");
        }

        StoredSocietyMedia stored = validateAndStore(
                file, SocietyImageType.HIGHLIGHT_COVER);
        try {
            SocietyMedia media = new SocietyMedia();
            media.setMediaID(UUID.randomUUID().toString());
            media.setSocietyID(society.getSocietyID());
            media.setMediaUrl(stored.fileUrl());
            media.setMediaType(SocietyImageType.HIGHLIGHT_COVER);
            media.setCaption(null);
            media.setSortOrder(societyMediaRepository.findMaximumSortOrder(
                    society.getSocietyID(),
                    SocietyImageType.HIGHLIGHT_COVER) + 1);
            media.setUploadedAt(LocalDateTime.now(clock));
            media.setUploadedBy(requireAuthenticatedEmail(
                    authenticatedEmail));
            SocietyMedia saved = societyMediaRepository.saveAndFlush(media);
            deleteStoredMediaAfterRollback(stored);
            return saved;
        } catch (RuntimeException ex) {
            deleteStoredMediaAfterFailure(stored, ex);
            throw ex;
        }
    }

    /**
     * Removes a cover row and its generated file as one transaction. Legacy
     * gallery-backed covers are accepted so migrated A500 data remains
     * manageable.
     */
    @Transactional
    public void deleteHighlightCover(SocietyMedia media) {
        if (media == null || (media.getMediaType()
                != SocietyImageType.HIGHLIGHT_COVER
                && media.getMediaType()
                != SocietyImageType.GALLERY_IMAGE)) {
            throw new IllegalArgumentException(
                    "Highlight cover media is required.");
        }
        String storedFileName = storedFileName(media.getMediaUrl());
        SocietyImageType imageType = media.getMediaType();
        societyMediaRepository.delete(media);
        societyMediaRepository.flush();
        deleteAfterCommit(imageType, storedFileName);
    }

    /**
     * Updates a gallery caption without allowing any media ownership fields
     * to be changed.
     */
    @Transactional
    public SocietyGalleryMediaResponseDTO updateGalleryCaptionForExecutive(
            String authenticatedEmail,
            String societyID,
            String mediaID,
            String caption) {
        Society society = resolveEditableSociety(
                authenticatedEmail, societyID);
        SocietyMedia media = findGalleryMedia(
                society.getSocietyID(), mediaID);
        media.setCaption(normaliseCaption(caption));
        return mapGalleryMedia(
                societyMediaRepository.saveAndFlush(media));
    }

    /**
     * Reorders the complete gallery. Every supplied identifier must belong
     * to the authorised society and each current image must occur once.
     */
    @Transactional
    public List<SocietyGalleryMediaResponseDTO>
    reorderGalleryForExecutive(
            String authenticatedEmail,
            String societyID,
            List<String> orderedMediaIDs) {
        Society society = resolveEditableSociety(
                authenticatedEmail, societyID);
        if (orderedMediaIDs == null || orderedMediaIDs.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one gallery image is required.");
        }

        List<SocietyMedia> currentMedia = societyMediaRepository
                .findBySocietyIDAndMediaTypeOrderBySortOrderAscUploadedAtAscMediaIDAsc(
                        society.getSocietyID(),
                        SocietyImageType.GALLERY_IMAGE);
        Set<String> currentIDs = currentMedia.stream()
                .map(SocietyMedia::getMediaID)
                .collect(java.util.stream.Collectors.toSet());
        List<String> normalisedIDs = orderedMediaIDs.stream()
                .map(this::requireMediaID)
                .toList();
        Set<String> requestedIDs = new HashSet<>(normalisedIDs);
        if (requestedIDs.size() != normalisedIDs.size()
                || !requestedIDs.equals(currentIDs)) {
            throw new IllegalArgumentException(
                    "Gallery order must contain every current image exactly once.");
        }

        java.util.Map<String, SocietyMedia> mediaByID = currentMedia.stream()
                .collect(java.util.stream.Collectors.toMap(
                        SocietyMedia::getMediaID,
                        media -> media));
        for (int index = 0; index < normalisedIDs.size(); index++) {
            mediaByID.get(normalisedIDs.get(index)).setSortOrder(index);
        }
        societyMediaRepository.saveAllAndFlush(currentMedia);

        return normalisedIDs.stream()
                .map(mediaByID::get)
                .map(this::mapGalleryMedia)
                .toList();
    }

    /**
     * Removes one gallery image belonging to the authorised society.
     */
    @Transactional
    public void deleteGalleryForExecutive(
            String authenticatedEmail,
            String societyID,
            String mediaID) {
        Society society = resolveEditableSociety(
                authenticatedEmail, societyID);
        SocietyMedia media = findGalleryMedia(
                society.getSocietyID(), mediaID);
        String storedFileName = storedFileName(media.getMediaUrl());
        societyMediaRepository.delete(media);
        societyMediaRepository.flush();
        deleteAfterCommit(
                SocietyImageType.GALLERY_IMAGE,
                storedFileName);
    }

    private Society resolveEditableSociety(
            String authenticatedEmail,
            String societyID) {
        return societyID == null
                ? profileEditAuthorizationService
                        .requireCanEditOwnSociety(authenticatedEmail)
                        .society()
                : profileEditAuthorizationService
                        .requireCanEditSocietyProfile(
                                authenticatedEmail, societyID)
                        .society();
    }

    private SocietyGalleryMediaResponseDTO validateStoreAndCreateGalleryMedia(
            String authenticatedEmail,
            Society society,
            MultipartFile file,
            String caption) {
        StoredSocietyMedia stored = validateAndStore(
                file, SocietyImageType.GALLERY_IMAGE);
        try {
            SocietyMedia media = new SocietyMedia();
            media.setMediaID(UUID.randomUUID().toString());
            media.setSocietyID(society.getSocietyID());
            media.setMediaUrl(stored.fileUrl());
            media.setMediaType(SocietyImageType.GALLERY_IMAGE);
            media.setCaption(normaliseCaption(caption));
            media.setSortOrder(societyMediaRepository.findMaximumSortOrder(
                    society.getSocietyID(),
                    SocietyImageType.GALLERY_IMAGE) + 1);
            media.setUploadedAt(LocalDateTime.now(clock));
            media.setUploadedBy(requireAuthenticatedEmail(
                    authenticatedEmail));
            SocietyMedia saved = societyMediaRepository.saveAndFlush(media);
            deleteStoredMediaAfterRollback(stored);
            return mapGalleryMedia(saved);
        } catch (RuntimeException ex) {
            deleteStoredMediaAfterFailure(stored, ex);
            throw ex;
        }
    }

    private SocietyMediaUploadResponseDTO validateStoreAndAssign(
            Society society,
            MultipartFile file,
            SocietyImageType imageType) {
        String previousUrl = imageType == SocietyImageType.LOGO
                ? society.getLogoUrl()
                : society.getBannerUrl();
        StoredSocietyMedia stored = validateAndStore(file, imageType);
        try {
            if (imageType == SocietyImageType.LOGO) {
                society.setLogoUrl(stored.fileUrl());
            } else {
                society.setBannerUrl(stored.fileUrl());
            }
            // Flush while the stored-file cleanup guard is still in scope.
            // The transaction interceptor commits before the controller can
            // return a success response.
            societyRepository.saveAndFlush(society);
            registerReplacementCleanup(
                    stored,
                    generatedFileName(previousUrl, imageType));
        } catch (RuntimeException ex) {
            try {
                storageService.delete(imageType, stored.fileName());
            } catch (RuntimeException cleanupFailure) {
                ex.addSuppressed(cleanupFailure);
            }
            throw ex;
        }

        return new SocietyMediaUploadResponseDTO(
                stored.fileUrl(),
                stored.fileName(),
                stored.imageType(),
                stored.width(),
                stored.height(),
                stored.sizeBytes(),
                stored.createdAt());
    }

    private StoredSocietyMedia validateAndStore(
            MultipartFile file,
            SocietyImageType imageType) {
        validateBasicFileRules(file, imageType);
        String reportedMimeType = normaliseMimeType(file.getContentType());
        if (!ALLOWED_MIME_TYPES.contains(reportedMimeType)) {
            throw SocietyMediaException.unsupportedMediaType(imageType);
        }

        DecodedImageMetadata decoded = decodeAndValidate(
                file, imageType, reportedMimeType);
        return storageService.store(
                file,
                imageType,
                decoded.mimeType(),
                decoded.width(),
                decoded.height());
    }

    private void requireImageType(SocietyImageType imageType) {
        if (imageType != SocietyImageType.LOGO
                && imageType != SocietyImageType.BANNER) {
            throw SocietyMediaException.invalidFile(
                    "imageType must be LOGO or BANNER.");
        }
    }

    private void validateBasicFileRules(
            MultipartFile file,
            SocietyImageType imageType) {
        if (file == null || file.isEmpty()) {
            throw SocietyMediaException.invalidFile(
                    "Society image file must not be empty.");
        }
        if (file.getSize() > imageType.getMaximumSizeBytes()) {
            throw SocietyMediaException.fileTooLarge(imageType);
        }
    }

    private DecodedImageMetadata decodeAndValidate(
            MultipartFile file,
            SocietyImageType imageType,
            String reportedMimeType) {
        ImageReader reader = null;
        try (ImageInputStream imageInput =
                     ImageIO.createImageInputStream(file.getInputStream())) {
            if (imageInput == null) {
                throw SocietyMediaException.unreadableImage(null);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw SocietyMediaException.unreadableImage(null);
            }

            reader = readers.next();
            String decodedMimeType = mimeTypeForFormat(reader.getFormatName());
            if (decodedMimeType == null
                    || !decodedMimeType.equals(reportedMimeType)) {
                throw SocietyMediaException.unsupportedMediaType(imageType);
            }

            reader.setInput(imageInput, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);

            BufferedImage decodedImage = reader.read(0);
            if (decodedImage == null
                    || decodedImage.getWidth() != width
                    || decodedImage.getHeight() != height) {
                throw SocietyMediaException.unreadableImage(null);
            }

            return new DecodedImageMetadata(
                    decodedMimeType, width, height);
        } catch (SocietyMediaException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            throw SocietyMediaException.unreadableImage(ex);
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

    private SocietyMedia findGalleryMedia(
            String societyID,
            String mediaID) {
        return societyMediaRepository
                .findByMediaIDAndSocietyIDAndMediaType(
                        requireMediaID(mediaID),
                        societyID,
                        SocietyImageType.GALLERY_IMAGE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Society gallery image not found."));
    }

    private String requireMediaID(String mediaID) {
        if (mediaID == null || mediaID.isBlank()) {
            throw new IllegalArgumentException(
                    "Gallery media ID is required.");
        }
        return mediaID.trim();
    }

    private String requireAuthenticatedEmail(String authenticatedEmail) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new ForbiddenOperationException(
                    SocietyProfileEditAuthorizationService
                            .NOT_AUTHORISED_MESSAGE);
        }
        return authenticatedEmail.trim();
    }

    private String normaliseCaption(String caption) {
        if (caption == null) {
            return null;
        }
        String normalised = caption.trim();
        if (normalised.isEmpty()) {
            return null;
        }
        if (normalised.length() > 200) {
            throw new IllegalArgumentException(
                    "Gallery caption must not exceed 200 characters.");
        }
        return normalised;
    }

    private String storedFileName(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            throw new IllegalStateException(
                    "Stored society image URL is missing.");
        }
        String normalised = mediaUrl.trim();
        int separatorIndex = normalised.lastIndexOf('/');
        if (separatorIndex < 0
                || separatorIndex == normalised.length() - 1) {
            throw new IllegalStateException(
                    "Stored society image URL is invalid.");
        }
        return normalised.substring(separatorIndex + 1);
    }

    /**
     * Keeps the previous logo/banner until the database transaction commits.
     * If the transaction rolls back after the flush, the new generated file
     * is removed instead so the persisted URL never points at a missing file.
     */
    private void registerReplacementCleanup(
            StoredSocietyMedia stored,
            String previousFileName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (previousFileName != null
                                && !previousFileName.equals(
                                        stored.fileName())) {
                            safelyDelete(
                                    stored.imageType(),
                                    previousFileName);
                        }
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            safelyDelete(
                                    stored.imageType(),
                                    stored.fileName());
                        }
                    }
                });
    }

    /** Removes a newly stored gallery file if its database insert rolls back. */
    private void deleteStoredMediaAfterRollback(
            StoredSocietyMedia stored) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            safelyDelete(
                                    stored.imageType(),
                                    stored.fileName());
                        }
                    }
                });
    }

    /** Deletes a persisted media file only after its database row is gone. */
    private void deleteAfterCommit(
            SocietyImageType imageType,
            String fileName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        safelyDelete(imageType, fileName);
                    }
                });
    }

    private String generatedFileName(
            String mediaUrl,
            SocietyImageType imageType) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return null;
        }

        try {
            String path = URI.create(mediaUrl.trim()).getPath();
            String prefix = "/media/societies/"
                    + imageType.getFolderName()
                    + "/";
            if (path == null || !path.startsWith(prefix)) {
                return null;
            }

            String fileName = path.substring(prefix.length());
            return fileName.isBlank() || fileName.contains("/")
                    ? null
                    : fileName;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void safelyDelete(
            SocietyImageType imageType,
            String fileName) {
        try {
            storageService.delete(imageType, fileName);
        } catch (RuntimeException cleanupFailure) {
            // The database transaction has already reached its final state.
            // Cleanup failure must not turn a committed upload into a false
            // HTTP failure or make the persisted URL unusable.
            LOGGER.warn(
                    "Could not remove superseded society media file {}.",
                    fileName,
                    cleanupFailure);
        }
    }

    private SocietyGalleryMediaResponseDTO mapGalleryMedia(
            SocietyMedia media) {
        return SocietyGalleryMediaResponseDTO.builder()
                .mediaID(media.getMediaID())
                .mediaUrl(media.getMediaUrl())
                .caption(media.getCaption())
                .sortOrder(media.getSortOrder())
                .uploadedAt(media.getUploadedAt())
                .build();
    }

    private void deleteStoredMediaAfterFailure(
            StoredSocietyMedia stored,
            RuntimeException originalFailure) {
        try {
            storageService.delete(
                    stored.imageType(), stored.fileName());
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private record DecodedImageMetadata(
            String mimeType,
            int width,
            int height) {
    }
}
