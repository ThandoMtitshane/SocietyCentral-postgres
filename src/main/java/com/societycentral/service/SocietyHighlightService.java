package com.societycentral.service;

import com.societycentral.dto.request.SocietyHighlightRequestDTO;
import com.societycentral.dto.response.SocietyHighlightResponseDTO;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.model.Society;
import com.societycentral.model.SocietyHighlight;
import com.societycentral.model.SocietyMedia;
import com.societycentral.repository.SocietyHighlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Article persistence and secured management for A500 highlights. */
@Service
@RequiredArgsConstructor
public class SocietyHighlightService {

    private static final int HEADLINE_MAX_LENGTH = 150;
    private static final int CAPTION_MAX_LENGTH = 300;
    private static final int CATEGORY_MAX_LENGTH = 80;

    private final SocietyHighlightRepository highlightRepository;
    private final SocietyProfileEditAuthorizationService authorizationService;
    private final SocietyMediaService mediaService;
    private final SocietyHighlightRichTextSanitizer richTextSanitizer;
    private final Clock clock;

    /** Public carousel projection: active records only, without article body. */
    @Transactional(readOnly = true)
    public List<SocietyHighlightResponseDTO> getPublishedHighlights(
            String societyID) {
        return highlightRepository
                .findBySocietyIDAndActiveStatusTrueOrderBySortOrderAscPublishedAtDescHighlightIDAsc(
                        requireSocietyID(societyID))
                .stream()
                .map(highlight -> map(highlight, false, false))
                .toList();
    }

    /** Full editor projection, including drafts and article bodies. */
    @Transactional(readOnly = true)
    public List<SocietyHighlightResponseDTO> getManageableHighlights(
            String societyID) {
        return highlightRepository
                .findBySocietyIDOrderBySortOrderAscCreatedAtAscHighlightIDAsc(
                        requireSocietyID(societyID))
                .stream()
                .map(highlight -> map(highlight, true, true))
                .toList();
    }

    /** Full public article, protected from draft disclosure by the query. */
    @Transactional(readOnly = true)
    public SocietyHighlightResponseDTO getPublishedArticle(
            String societyID,
            String highlightID,
            String societyName) {
        SocietyHighlight highlight = highlightRepository
                .findByHighlightIDAndSocietyIDAndActiveStatusTrue(
                        requireHighlightID(highlightID),
                        requireSocietyID(societyID))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Society highlight not found."));
        return map(highlight, true, false, societyName);
    }

    @Transactional
    public SocietyHighlightResponseDTO createHighlight(
            String authenticatedEmail,
            String societyID,
            SocietyHighlightRequestDTO request,
            MultipartFile coverImage) {
        Society society = resolveEditableSociety(
                authenticatedEmail, societyID);
        ValidatedHighlight values = validate(request);
        requireCoverImage(coverImage);

        SocietyMedia cover = mediaService.createHighlightCover(
                authenticatedEmail, society, coverImage);
        LocalDateTime now = LocalDateTime.now(clock);

        SocietyHighlight highlight = new SocietyHighlight();
        highlight.setHighlightID(UUID.randomUUID().toString());
        highlight.setSocietyID(society.getSocietyID());
        highlight.setSociety(society);
        applyValues(highlight, values);
        highlight.setCoverMediaID(cover.getMediaID());
        highlight.setCoverMedia(cover);
        highlight.setSortOrder(highlightRepository.findMaximumSortOrder(
                society.getSocietyID()) + 1);
        highlight.setPublishedAt(values.activeStatus() ? now : null);
        highlight.setCreatedBy(requireAuthenticatedEmail(authenticatedEmail));
        highlight.setCreatedAt(now);
        highlight.setUpdatedAt(now);

        return map(highlightRepository.saveAndFlush(highlight), true, true);
    }

    @Transactional
    public SocietyHighlightResponseDTO updateHighlight(
            String authenticatedEmail,
            String societyID,
            String highlightID,
            SocietyHighlightRequestDTO request,
            MultipartFile replacementCover) {
        Society society = resolveEditableSociety(
                authenticatedEmail, societyID);
        ValidatedHighlight values = validate(request);
        SocietyHighlight highlight = findOwnedHighlight(
                society.getSocietyID(), highlightID);
        SocietyMedia previousCover = highlight.getCoverMedia();

        if (replacementCover != null && !replacementCover.isEmpty()) {
            SocietyMedia cover = mediaService.createHighlightCover(
                    authenticatedEmail, society, replacementCover);
            highlight.setCoverMediaID(cover.getMediaID());
            highlight.setCoverMedia(cover);
        } else if (previousCover == null
                || previousCover.getMediaUrl() == null
                || previousCover.getMediaUrl().isBlank()) {
            throw new IllegalArgumentException("Cover image is required.");
        }

        boolean wasActive = Boolean.TRUE.equals(highlight.getActiveStatus());
        applyValues(highlight, values);
        if (!wasActive && values.activeStatus()
                && highlight.getPublishedAt() == null) {
            highlight.setPublishedAt(LocalDateTime.now(clock));
        }
        highlight.setUpdatedAt(LocalDateTime.now(clock));

        SocietyHighlight saved = highlightRepository.saveAndFlush(highlight);
        if (replacementCover != null && !replacementCover.isEmpty()
                && previousCover != null
                && !previousCover.getMediaID().equals(
                        saved.getCoverMediaID())) {
            mediaService.deleteHighlightCover(previousCover);
        }
        return map(saved, true, true);
    }

    @Transactional
    public List<SocietyHighlightResponseDTO> reorderHighlights(
            String authenticatedEmail,
            String societyID,
            List<String> orderedHighlightIDs) {
        Society society = resolveEditableSociety(
                authenticatedEmail, societyID);
        if (orderedHighlightIDs == null || orderedHighlightIDs.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one highlight ID is required.");
        }

        List<SocietyHighlight> current = highlightRepository
                .findBySocietyIDOrderBySortOrderAscCreatedAtAscHighlightIDAsc(
                        society.getSocietyID());
        List<String> normalisedIDs = orderedHighlightIDs.stream()
                .map(this::requireHighlightID)
                .toList();
        Set<String> requestedIDs = new HashSet<>(normalisedIDs);
        Set<String> currentIDs = current.stream()
                .map(SocietyHighlight::getHighlightID)
                .collect(Collectors.toSet());
        if (requestedIDs.size() != normalisedIDs.size()
                || !requestedIDs.equals(currentIDs)) {
            throw new IllegalArgumentException(
                    "Highlight order must contain every current highlight exactly once.");
        }

        java.util.Map<String, SocietyHighlight> byID = current.stream()
                .collect(Collectors.toMap(
                        SocietyHighlight::getHighlightID,
                        highlight -> highlight));
        LocalDateTime now = LocalDateTime.now(clock);
        for (int index = 0; index < normalisedIDs.size(); index++) {
            SocietyHighlight highlight = byID.get(normalisedIDs.get(index));
            highlight.setSortOrder(index);
            highlight.setUpdatedAt(now);
        }
        highlightRepository.saveAllAndFlush(current);

        return normalisedIDs.stream()
                .map(byID::get)
                .map(highlight -> map(highlight, true, true))
                .toList();
    }

    @Transactional
    public void deleteHighlight(
            String authenticatedEmail,
            String societyID,
            String highlightID) {
        Society society = resolveEditableSociety(
                authenticatedEmail, societyID);
        SocietyHighlight highlight = findOwnedHighlight(
                society.getSocietyID(), highlightID);
        SocietyMedia cover = highlight.getCoverMedia();

        highlightRepository.delete(highlight);
        highlightRepository.flush();
        if (cover != null) {
            mediaService.deleteHighlightCover(cover);
        }
    }

    private Society resolveEditableSociety(
            String authenticatedEmail,
            String societyID) {
        return societyID == null
                ? authorizationService
                        .requireCanEditOwnSociety(authenticatedEmail)
                        .society()
                : authorizationService
                        .requireCanEditSocietyProfile(
                                authenticatedEmail, societyID)
                        .society();
    }

    private SocietyHighlight findOwnedHighlight(
            String societyID,
            String highlightID) {
        return highlightRepository.findByHighlightIDAndSocietyID(
                        requireHighlightID(highlightID), societyID)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Society highlight not found."));
    }

    private ValidatedHighlight validate(
            SocietyHighlightRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Highlight request is required.");
        }
        // Character limits apply to visible text rather than persisted markup.
        String headline = richTextSanitizer.sanitizeEmphasis(
                request.getHeadline());
        String caption = richTextSanitizer.sanitizeEmphasis(
                request.getCaption());
        String article = richTextSanitizer.sanitizeArticle(
                request.getArticle());
        return new ValidatedHighlight(
                requiredRichText(
                        headline,
                        HEADLINE_MAX_LENGTH,
                        "Headline is required.",
                        "Headline"),
                requiredRichText(
                        caption,
                        CAPTION_MAX_LENGTH,
                        "Caption is required.",
                        "Caption"),
                requiredArticle(article),
                optionalText(
                        request.getCategory(),
                        CATEGORY_MAX_LENGTH,
                        "Category"),
                request.isActiveStatus());
    }

    private void applyValues(
            SocietyHighlight highlight,
            ValidatedHighlight values) {
        highlight.setHeadline(values.headline());
        highlight.setCaption(values.caption());
        highlight.setArticle(values.article());
        highlight.setCategory(values.category());
        highlight.setActiveStatus(values.activeStatus());
    }

    private String requiredRichText(
            String value,
            int maximumLength,
            String requiredMessage,
            String fieldName) {
        String visibleText = richTextSanitizer.visibleText(value);
        if (visibleText.isEmpty()) {
            throw new IllegalArgumentException(requiredMessage);
        }
        if (richTextSanitizer.visibleCharacterCount(value) > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed "
                            + maximumLength + " characters.");
        }
        return value;
    }

    private String requiredArticle(String value) {
        if (richTextSanitizer.visibleText(value).isEmpty()) {
            throw new IllegalArgumentException("Article is required.");
        }
        return value;
    }

    private String optionalText(
            String value,
            int maximumLength,
            String fieldName) {
        if (value == null) {
            return null;
        }
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

    private void requireCoverImage(MultipartFile coverImage) {
        if (coverImage == null || coverImage.isEmpty()) {
            throw new IllegalArgumentException("Cover image is required.");
        }
    }

    private String requireSocietyID(String societyID) {
        if (societyID == null || societyID.isBlank()) {
            throw new IllegalArgumentException("Society ID is required.");
        }
        return societyID.trim();
    }

    private String requireHighlightID(String highlightID) {
        if (highlightID == null || highlightID.isBlank()) {
            throw new IllegalArgumentException("Highlight ID is required.");
        }
        return highlightID.trim();
    }

    private String requireAuthenticatedEmail(String authenticatedEmail) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new IllegalArgumentException("Authentication is required.");
        }
        return authenticatedEmail.trim();
    }

    private SocietyHighlightResponseDTO map(
            SocietyHighlight highlight,
            boolean includeArticle,
            boolean includeManagementFields) {
        return map(
                highlight,
                includeArticle,
                includeManagementFields,
                null);
    }

    private SocietyHighlightResponseDTO map(
            SocietyHighlight highlight,
            boolean includeArticle,
            boolean includeManagementFields,
            String societyName) {
        SocietyMedia cover = highlight.getCoverMedia();
        return SocietyHighlightResponseDTO.builder()
                .highlightID(highlight.getHighlightID())
                .societyName(societyName)
                .headline(richTextSanitizer.sanitizeEmphasis(
                        highlight.getHeadline()))
                .caption(richTextSanitizer.sanitizeEmphasis(
                        highlight.getCaption()))
                .article(includeArticle
                        ? richTextSanitizer.sanitizeArticle(
                                highlight.getArticle())
                        : null)
                .coverImageUrl(cover == null ? null : cover.getMediaUrl())
                .category(highlight.getCategory())
                .sortOrder(highlight.getSortOrder())
                .publishedAt(highlight.getPublishedAt())
                .activeStatus(highlight.getActiveStatus())
                .createdBy(includeManagementFields
                        ? highlight.getCreatedBy() : null)
                .createdAt(includeManagementFields
                        ? highlight.getCreatedAt() : null)
                .updatedAt(includeManagementFields
                        ? highlight.getUpdatedAt() : null)
                .build();
    }

    private record ValidatedHighlight(
            String headline,
            String caption,
            String article,
            String category,
            boolean activeStatus) {
    }
}
