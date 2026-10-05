package com.societycentral.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Enforces the complete persisted markup contract for Society Highlights.
 * No attributes are permitted, so event handlers and URL-based payloads
 * cannot survive sanitisation.
 */
@Component
public class SocietyHighlightRichTextSanitizer {

    private static final Safelist EMPHASIS_SAFELIST = Safelist.none()
            .addTags("strong", "em");
    private static final Safelist ARTICLE_SAFELIST = Safelist.none()
            .addTags("strong", "em", "u", "ul", "ol", "li", "p", "br");
    private static final Document.OutputSettings OUTPUT_SETTINGS =
            new Document.OutputSettings().prettyPrint(false);

    public String sanitizeEmphasis(String value) {
        return clean(value, EMPHASIS_SAFELIST);
    }

    public String sanitizeArticle(String value) {
        return clean(value, ARTICLE_SAFELIST);
    }

    public String visibleText(String sanitizedHtml) {
        if (sanitizedHtml == null || sanitizedHtml.isBlank()) {
            return "";
        }
        return Jsoup.parseBodyFragment(sanitizedHtml)
                .body()
                .wholeText()
                .replace("\u200B", "")
                .trim();
    }

    public int visibleCharacterCount(String sanitizedHtml) {
        String text = visibleText(sanitizedHtml);
        return text.codePointCount(0, text.length());
    }

    private String clean(String value, Safelist safelist) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Jsoup.clean(value, "", safelist, OUTPUT_SETTINGS)
                .replace("\u200B", "")
                .trim();
    }
}
