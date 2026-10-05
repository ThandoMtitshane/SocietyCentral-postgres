package com.societycentral.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocietyHighlightRichTextSanitizerTests {

    private final SocietyHighlightRichTextSanitizer sanitizer =
            new SocietyHighlightRichTextSanitizer();

    @Test
    void articleKeepsOnlySupportedFormatting() {
        String sanitized = sanitizer.sanitizeArticle(
                "<p><strong>Bold</strong> <em>italic</em> "
                        + "<u>underlined</u></p>"
                        + "<ul><li>One</li></ul><ol><li>Two</li></ol>");

        assertEquals(
                "<p><strong>Bold</strong> <em>italic</em> "
                        + "<u>underlined</u></p>"
                        + "<ul><li>One</li></ul><ol><li>Two</li></ol>",
                sanitized);
    }

    @Test
    void emphasisFieldsRemoveUnsupportedElementsAndEveryAttribute() {
        String sanitized = sanitizer.sanitizeEmphasis(
                "<strong onclick=\"alert(1)\">Safe</strong>"
                        + "<u>plain</u><a href=\"javascript:alert(1)\">link</a>");

        assertEquals("<strong>Safe</strong>plainlink", sanitized);
        assertFalse(sanitized.contains("onclick"));
        assertFalse(sanitized.contains("javascript:"));
    }

    @Test
    void executableAndEmbeddedContentCannotSurviveSanitisation() {
        String sanitized = sanitizer.sanitizeArticle(
                "<script>alert(1)</script><style>body{display:none}</style>"
                        + "<iframe src=\"https://example.org\"></iframe>"
                        + "<p onmouseover=\"alert(2)\">Readable text</p>");

        assertEquals("<p>Readable text</p>", sanitized);
        assertFalse(sanitized.contains("alert"));
        assertFalse(sanitized.contains("iframe"));
    }

    @Test
    void visibleCountsIgnoreMarkupAndPlainTextRemainsCompatible() {
        assertEquals(5, sanitizer.visibleCharacterCount(
                "<strong>Hello</strong>"));
        assertEquals("Existing plain-text article.",
                sanitizer.sanitizeArticle("Existing plain-text article."));
        assertTrue(sanitizer.visibleText("<p> </p>").isEmpty());
    }
}
