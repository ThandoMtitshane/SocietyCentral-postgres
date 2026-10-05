package com.societycentral.service;

import com.societycentral.dto.request.SocietyHighlightRequestDTO;
import com.societycentral.model.Society;
import com.societycentral.model.SocietyHighlight;
import com.societycentral.model.SocietyImageType;
import com.societycentral.model.SocietyMedia;
import com.societycentral.repository.SocietyHighlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocietyHighlightServiceTests {

    private static final String EMAIL = "president@nmu.ac.za";
    private static final String SOCIETY_ID = "SOC001";
    private static final LocalDateTime NOW =
            LocalDateTime.of(2030, 8, 11, 8, 30);

    @Mock
    private SocietyHighlightRepository highlightRepository;
    @Mock
    private SocietyProfileEditAuthorizationService authorizationService;
    @Mock
    private SocietyMediaService mediaService;

    private SocietyHighlightService service;
    private Society society;

    @BeforeEach
    void setUp() {
        service = new SocietyHighlightService(
                highlightRepository,
                authorizationService,
                mediaService,
                new SocietyHighlightRichTextSanitizer(),
                Clock.fixed(
                        Instant.parse("2030-08-11T08:30:00Z"),
                        ZoneOffset.UTC));
        society = new Society();
        society.setSocietyID(SOCIETY_ID);
        society.setSocietyName("Computing Society");
    }

    @Test
    void createTrimsCopyPreservesParagraphsAndStoresDedicatedCover() {
        MockMultipartFile cover = new MockMultipartFile(
                "coverImage", "hackathon.png", "image/png",
                new byte[]{1, 2, 3});
        SocietyMedia storedCover = cover("COVER001");
        when(authorizationService.requireCanEditSocietyProfile(
                EMAIL, SOCIETY_ID)).thenReturn(context());
        when(mediaService.createHighlightCover(
                EMAIL, society, cover)).thenReturn(storedCover);
        when(highlightRepository.findMaximumSortOrder(SOCIETY_ID))
                .thenReturn(2);
        when(highlightRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createHighlight(
                EMAIL,
                SOCIETY_ID,
                request(
                        "  Computer Science Society Hosts Annual Hackathon  ",
                        "  Students spent 24 hours building solutions.  ",
                        "  Opening paragraph.\n\nClosing paragraph.  ",
                        "  Technology  ",
                        true),
                cover);

        ArgumentCaptor<SocietyHighlight> saved =
                ArgumentCaptor.forClass(SocietyHighlight.class);
        verify(highlightRepository).saveAndFlush(saved.capture());
        assertEquals("Computer Science Society Hosts Annual Hackathon",
                saved.getValue().getHeadline());
        assertEquals("Opening paragraph.\n\nClosing paragraph.",
                saved.getValue().getArticle());
        assertEquals("COVER001", saved.getValue().getCoverMediaID());
        assertEquals(3, saved.getValue().getSortOrder());
        assertEquals(NOW, saved.getValue().getPublishedAt());
        assertEquals(saved.getValue().getHighlightID(),
                result.getHighlightID());
        assertEquals("/media/societies/highlights/cover.png",
                result.getCoverImageUrl());
    }

    @Test
    void createPersistsOnlySupportedRichTextAndUsesVisibleCharacterLimits() {
        MockMultipartFile cover = new MockMultipartFile(
                "coverImage", "story.png", "image/png",
                new byte[]{1, 2, 3});
        when(authorizationService.requireCanEditSocietyProfile(
                EMAIL, SOCIETY_ID)).thenReturn(context());
        when(mediaService.createHighlightCover(
                EMAIL, society, cover)).thenReturn(cover("COVER002"));
        when(highlightRepository.findMaximumSortOrder(SOCIETY_ID))
                .thenReturn(0);
        when(highlightRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String visibleHeadline = "H".repeat(150);
        service.createHighlight(
                EMAIL,
                SOCIETY_ID,
                request(
                        "<strong>" + visibleHeadline + "</strong>",
                        "<em>Formatted caption</em>",
                        "<p onclick=\"bad()\"><strong>Opening</strong></p>"
                                + "<script>alert(1)</script>"
                                + "<ul><li>First item</li></ul>",
                        "News",
                        true),
                cover);

        ArgumentCaptor<SocietyHighlight> saved =
                ArgumentCaptor.forClass(SocietyHighlight.class);
        verify(highlightRepository).saveAndFlush(saved.capture());
        assertEquals("<strong>" + visibleHeadline + "</strong>",
                saved.getValue().getHeadline());
        assertEquals("<em>Formatted caption</em>",
                saved.getValue().getCaption());
        assertEquals(
                "<p><strong>Opening</strong></p>"
                        + "<ul><li>First item</li></ul>",
                saved.getValue().getArticle());

        IllegalArgumentException tooLong = assertThrows(
                IllegalArgumentException.class,
                () -> service.createHighlight(
                        EMAIL,
                        SOCIETY_ID,
                        request(
                                "<strong>" + "H".repeat(151) + "</strong>",
                                "Caption",
                                "Article",
                                null,
                                true),
                        cover));
        assertEquals("Headline must not exceed 150 characters.",
                tooLong.getMessage());
    }

    @Test
    void updateMutatesTheSameRecordInsteadOfCreatingADuplicate() {
        SocietyHighlight existing = highlight("HIGHLIGHT001", true);
        when(authorizationService.requireCanEditSocietyProfile(
                EMAIL, SOCIETY_ID)).thenReturn(context());
        when(highlightRepository.findByHighlightIDAndSocietyID(
                "HIGHLIGHT001", SOCIETY_ID))
                .thenReturn(Optional.of(existing));
        when(highlightRepository.saveAndFlush(existing))
                .thenReturn(existing);

        var result = service.updateHighlight(
                EMAIL,
                SOCIETY_ID,
                "HIGHLIGHT001",
                request(
                        "Updated headline",
                        "Updated caption",
                        "First updated paragraph.\n\nSecond updated paragraph.",
                        "Campus News",
                        true),
                null);

        assertEquals("HIGHLIGHT001", result.getHighlightID());
        assertEquals("Updated headline", existing.getHeadline());
        assertEquals(
                "First updated paragraph.\n\nSecond updated paragraph.",
                existing.getArticle());
        verify(highlightRepository, never()).save(any());
        verify(mediaService, never()).createHighlightCover(
                any(), any(), any());
    }

    @Test
    void validationReportsEveryRequiredArticleFieldAndCover() {
        when(authorizationService.requireCanEditSocietyProfile(
                EMAIL, SOCIETY_ID)).thenReturn(context());

        IllegalArgumentException headline = assertThrows(
                IllegalArgumentException.class,
                () -> service.createHighlight(
                        EMAIL,
                        SOCIETY_ID,
                        request(" ", "Caption", "Article", null, true),
                        null));
        IllegalArgumentException caption = assertThrows(
                IllegalArgumentException.class,
                () -> service.createHighlight(
                        EMAIL,
                        SOCIETY_ID,
                        request("Headline", " ", "Article", null, true),
                        null));
        IllegalArgumentException article = assertThrows(
                IllegalArgumentException.class,
                () -> service.createHighlight(
                        EMAIL,
                        SOCIETY_ID,
                        request("Headline", "Caption", " ", null, true),
                        null));
        IllegalArgumentException cover = assertThrows(
                IllegalArgumentException.class,
                () -> service.createHighlight(
                        EMAIL,
                        SOCIETY_ID,
                        request("Headline", "Caption", "Article", null, true),
                        null));

        assertEquals("Headline is required.", headline.getMessage());
        assertEquals("Caption is required.", caption.getMessage());
        assertEquals("Article is required.", article.getMessage());
        assertEquals("Cover image is required.", cover.getMessage());
        verify(mediaService, never()).createHighlightCover(
                any(), any(), any());
    }

    @Test
    void publicListExcludesArticleBodiesAndRepositorySuppliesOnlyActiveRows() {
        SocietyHighlight published = highlight("HIGHLIGHT001", true);
        when(highlightRepository
                .findBySocietyIDAndActiveStatusTrueOrderBySortOrderAscPublishedAtDescHighlightIDAsc(
                        SOCIETY_ID))
                .thenReturn(List.of(published));

        var result = service.getPublishedHighlights(SOCIETY_ID);

        assertEquals(1, result.size());
        assertEquals("Annual Hackathon", result.getFirst().getHeadline());
        assertNull(result.getFirst().getArticle());
        assertTrue(result.getFirst().getActiveStatus());
        verify(highlightRepository)
                .findBySocietyIDAndActiveStatusTrueOrderBySortOrderAscPublishedAtDescHighlightIDAsc(
                        SOCIETY_ID);
    }

    @Test
    void draftArticleCannotBeReadThroughPublicDetailQuery() {
        when(highlightRepository
                .findByHighlightIDAndSocietyIDAndActiveStatusTrue(
                        "DRAFT001", SOCIETY_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                com.societycentral.exception.ResourceNotFoundException.class,
                () -> service.getPublishedArticle(
                        SOCIETY_ID, "DRAFT001", "Computing Society"));
    }

    @Test
    void reorderRequiresEveryHighlightAndPersistsTheRequestedOrder() {
        SocietyHighlight first = highlight("HIGHLIGHT001", true);
        first.setSortOrder(0);
        SocietyHighlight second = highlight("HIGHLIGHT002", false);
        second.setSortOrder(1);
        when(authorizationService.requireCanEditSocietyProfile(
                EMAIL, SOCIETY_ID)).thenReturn(context());
        when(highlightRepository
                .findBySocietyIDOrderBySortOrderAscCreatedAtAscHighlightIDAsc(
                        SOCIETY_ID))
                .thenReturn(List.of(first, second));

        var result = service.reorderHighlights(
                EMAIL,
                SOCIETY_ID,
                List.of("HIGHLIGHT002", "HIGHLIGHT001"));

        assertEquals(0, second.getSortOrder());
        assertEquals(1, first.getSortOrder());
        assertEquals(List.of("HIGHLIGHT002", "HIGHLIGHT001"),
                result.stream().map(item -> item.getHighlightID()).toList());
        verify(highlightRepository).saveAllAndFlush(
                List.of(first, second));
    }

    @Test
    void deletingArticleAlsoDeletesItsManagedCover() {
        SocietyHighlight existing = highlight("HIGHLIGHT001", true);
        when(authorizationService.requireCanEditSocietyProfile(
                EMAIL, SOCIETY_ID)).thenReturn(context());
        when(highlightRepository.findByHighlightIDAndSocietyID(
                "HIGHLIGHT001", SOCIETY_ID))
                .thenReturn(Optional.of(existing));

        service.deleteHighlight(
                EMAIL, SOCIETY_ID, "HIGHLIGHT001");

        verify(highlightRepository).delete(existing);
        verify(highlightRepository).flush();
        verify(mediaService).deleteHighlightCover(existing.getCoverMedia());
    }

    private ExecutiveSocietyResolver.ActiveExecutiveSociety context() {
        return new ExecutiveSocietyResolver.ActiveExecutiveSociety(
                null, society, null);
    }

    private SocietyHighlightRequestDTO request(
            String headline,
            String caption,
            String article,
            String category,
            boolean active) {
        SocietyHighlightRequestDTO request =
                new SocietyHighlightRequestDTO();
        request.setHeadline(headline);
        request.setCaption(caption);
        request.setArticle(article);
        request.setCategory(category);
        request.setActiveStatus(active);
        return request;
    }

    private SocietyHighlight highlight(
            String highlightID,
            boolean active) {
        SocietyHighlight highlight = new SocietyHighlight();
        highlight.setHighlightID(highlightID);
        highlight.setSocietyID(SOCIETY_ID);
        highlight.setHeadline("Annual Hackathon");
        highlight.setCaption("Students built practical solutions.");
        highlight.setArticle("Opening paragraph.\n\nClosing paragraph.");
        highlight.setCoverMediaID("COVER001");
        highlight.setCoverMedia(cover("COVER001"));
        highlight.setCategory("Technology");
        highlight.setSortOrder(0);
        highlight.setPublishedAt(active ? NOW : null);
        highlight.setActiveStatus(active);
        highlight.setCreatedBy(EMAIL);
        highlight.setCreatedAt(NOW);
        highlight.setUpdatedAt(NOW);
        return highlight;
    }

    private SocietyMedia cover(String mediaID) {
        SocietyMedia cover = new SocietyMedia();
        cover.setMediaID(mediaID);
        cover.setSocietyID(SOCIETY_ID);
        cover.setMediaType(SocietyImageType.HIGHLIGHT_COVER);
        cover.setMediaUrl("/media/societies/highlights/cover.png");
        cover.setSortOrder(0);
        cover.setUploadedAt(NOW);
        cover.setUploadedBy(EMAIL);
        return cover;
    }
}
