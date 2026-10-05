package com.societycentral.service;

import com.societycentral.dto.request.UpdateSocietyPublicProfileRequestDTO;
import com.societycentral.dto.request.SocietyHighlightRequestDTO;
import com.societycentral.dto.response.ExecutiveSocietyProfileDTO;
import com.societycentral.dto.response.SocietyGalleryMediaResponseDTO;
import com.societycentral.dto.response.SocietyHighlightResponseDTO;
import com.societycentral.dto.response.SocietyMediaUploadResponseDTO;
import com.societycentral.model.Society;
import com.societycentral.model.SocietyImageType;
import com.societycentral.repository.SocietyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Executive-owned A500 society profile management workflow.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveSocietyProfileService {

    private final ExecutiveSocietyResolver executiveSocietyResolver;
    private final SocietyProfileEditAuthorizationService authorizationService;
    private final SocietyBrowseService societyBrowseService;
    private final SocietyRepository societyRepository;
    private final SocietyMediaService societyMediaService;
    private final SocietyHighlightService societyHighlightService;

    /**
     * Returns the authenticated executive's own internal society profile.
     */
    @Transactional(readOnly = true)
    public ExecutiveSocietyProfileDTO getOwnProfile(
            String authenticatedEmail) {
        ExecutiveSocietyResolver.ActiveExecutiveSociety context =
                executiveSocietyResolver.resolve(authenticatedEmail);
        return societyBrowseService.getExecutiveSocietyProfile(
                context.society().getSocietyID(), authenticatedEmail);
    }

    /**
     * Returns one exact active executive society when the caller has
     * concurrent roles.
     */
    @Transactional(readOnly = true)
    public ExecutiveSocietyProfileDTO getProfile(
            String authenticatedEmail,
            String societyID) {
        ExecutiveSocietyResolver.ActiveExecutiveSociety context =
                executiveSocietyResolver.resolve(
                        authenticatedEmail, societyID);
        return societyBrowseService.getExecutiveSocietyProfile(
                context.society().getSocietyID(), authenticatedEmail);
    }

    /**
     * Updates only the public fields allowed by the A500 edit contract.
     */
    @Transactional
    public ExecutiveSocietyProfileDTO updatePublicProfile(
            String authenticatedEmail,
            UpdateSocietyPublicProfileRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Profile update request is required.");
        }

        ExecutiveSocietyResolver.ActiveExecutiveSociety context =
                authorizationService.requireCanEditOwnSociety(
                        authenticatedEmail);
        return applyPublicProfileUpdate(
                authenticatedEmail, context.society(), request);
    }

    /**
     * Society-scoped update for callers with more than one active role.
     */
    @Transactional
    public ExecutiveSocietyProfileDTO updatePublicProfile(
            String authenticatedEmail,
            String societyID,
            UpdateSocietyPublicProfileRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Profile update request is required.");
        }

        ExecutiveSocietyResolver.ActiveExecutiveSociety context =
                authorizationService.requireCanEditSocietyProfile(
                        authenticatedEmail, societyID);
        return applyPublicProfileUpdate(
                authenticatedEmail, context.society(), request);
    }

    private ExecutiveSocietyProfileDTO applyPublicProfileUpdate(
            String authenticatedEmail,
            Society society,
            UpdateSocietyPublicProfileRequestDTO request) {

        if (request.getDescription() != null) {
            society.setDescription(normaliseText(
                    request.getDescription(), 2500, "Description"));
        }
        if (request.getVision() != null) {
            society.setVision(normaliseText(
                    request.getVision(), 500, "Vision"));
        }
        if (request.getMission() != null) {
            society.setMission(normaliseText(
                    request.getMission(), 500, "Mission"));
        }
        if (request.getContactEmail() != null) {
            society.setEmail(normaliseText(
                    request.getContactEmail(), 100, "Contact email"));
        }
        if (request.getContactPhone() != null) {
            society.setContactNumber(normaliseText(
                    request.getContactPhone(), 10, "Contact number"));
        }
        if (request.getFacebookURL() != null) {
            society.setFacebookURL(SocietyManagementService
                    .normaliseSocialUrl(request.getFacebookURL()));
        }
        if (request.getInstagramURL() != null) {
            society.setInstagramURL(SocietyManagementService
                    .normaliseSocialUrl(request.getInstagramURL()));
        }
        if (request.getTiktokURL() != null) {
            society.setTiktokURL(SocietyManagementService
                    .normaliseSocialUrl(request.getTiktokURL()));
        }

        // Flush while the transaction is still inside the service boundary so
        // constraint/database failures cannot be reported to React as a
        // successful profile update.
        societyRepository.saveAndFlush(society);
        return societyBrowseService.getExecutiveSocietyProfile(
                society.getSocietyID(), authenticatedEmail);
    }

    @Transactional
    public SocietyMediaUploadResponseDTO uploadSocietyLogo(
            String authenticatedEmail,
            MultipartFile file) {
        return societyMediaService.uploadForExecutive(
                authenticatedEmail, file, SocietyImageType.LOGO);
    }

    @Transactional
    public SocietyMediaUploadResponseDTO uploadSocietyLogo(
            String authenticatedEmail,
            String societyID,
            MultipartFile file) {
        return societyMediaService.uploadForExecutive(
                authenticatedEmail,
                societyID,
                file,
                SocietyImageType.LOGO);
    }

    @Transactional
    public SocietyMediaUploadResponseDTO uploadSocietyBanner(
            String authenticatedEmail,
            MultipartFile file) {
        return societyMediaService.uploadForExecutive(
                authenticatedEmail, file, SocietyImageType.BANNER);
    }

    @Transactional
    public SocietyMediaUploadResponseDTO uploadSocietyBanner(
            String authenticatedEmail,
            String societyID,
            MultipartFile file) {
        return societyMediaService.uploadForExecutive(
                authenticatedEmail,
                societyID,
                file,
                SocietyImageType.BANNER);
    }

    @Transactional
    public SocietyGalleryMediaResponseDTO uploadGalleryImage(
            String authenticatedEmail,
            String societyID,
            MultipartFile file,
            String caption) {
        return societyID == null
                ? societyMediaService.uploadGalleryForExecutive(
                        authenticatedEmail, file, caption)
                : societyMediaService.uploadGalleryForExecutive(
                        authenticatedEmail,
                        societyID,
                        file,
                        caption);
    }

    @Transactional
    public SocietyGalleryMediaResponseDTO updateGalleryCaption(
            String authenticatedEmail,
            String societyID,
            String mediaID,
            String caption) {
        return societyMediaService.updateGalleryCaptionForExecutive(
                authenticatedEmail,
                societyID,
                mediaID,
                caption);
    }

    @Transactional
    public List<SocietyGalleryMediaResponseDTO> reorderGallery(
            String authenticatedEmail,
            String societyID,
            List<String> mediaIDs) {
        return societyMediaService.reorderGalleryForExecutive(
                authenticatedEmail, societyID, mediaIDs);
    }

    @Transactional
    public void deleteGalleryImage(
            String authenticatedEmail,
            String societyID,
            String mediaID) {
        societyMediaService.deleteGalleryForExecutive(
                authenticatedEmail, societyID, mediaID);
    }

    @Transactional
    public SocietyHighlightResponseDTO createHighlight(
            String authenticatedEmail,
            String societyID,
            SocietyHighlightRequestDTO request,
            MultipartFile coverImage) {
        return societyHighlightService.createHighlight(
                authenticatedEmail, societyID, request, coverImage);
    }

    @Transactional
    public SocietyHighlightResponseDTO updateHighlight(
            String authenticatedEmail,
            String societyID,
            String highlightID,
            SocietyHighlightRequestDTO request,
            MultipartFile coverImage) {
        return societyHighlightService.updateHighlight(
                authenticatedEmail,
                societyID,
                highlightID,
                request,
                coverImage);
    }

    @Transactional
    public List<SocietyHighlightResponseDTO> reorderHighlights(
            String authenticatedEmail,
            String societyID,
            List<String> highlightIDs) {
        return societyHighlightService.reorderHighlights(
                authenticatedEmail, societyID, highlightIDs);
    }

    @Transactional
    public void deleteHighlight(
            String authenticatedEmail,
            String societyID,
            String highlightID) {
        societyHighlightService.deleteHighlight(
                authenticatedEmail, societyID, highlightID);
    }

    private String normaliseText(
            String value,
            int maximumLength,
            String fieldName) {
        String normalised = value.trim();
        if (normalised.isEmpty()) {
            return null;
        }
        if (normalised.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed "
                            + maximumLength + " characters.");
        }
        return normalised;
    }
}
