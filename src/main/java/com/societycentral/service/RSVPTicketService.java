package com.societycentral.service;

import com.societycentral.dto.response.RSVPResponseDTO;
import com.societycentral.utils.QRCodeUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Generates branded PDF event tickets for confirmed RSVPs.
 *
 * The ticket design matches the SocietyCentral brand:
 * - Navy header strip with "SOCIETY CENTRAL" and "NELSON MANDELA UNIVERSITY"
 * - Gold "EVENT TICKET" badge
 * - Formal letter body with event details
 * - Large QR code for door scanning
 * - Footer with branding and non-transferable notice
 */
@Service
@Slf4j
public class RSVPTicketService {

    // ── Brand colours (RGB floats 0-1) ────────────────────────────────────────
    private static final float[] NAVY = {0.106f, 0.169f, 0.294f};       // #1B2B4B
    private static final float[] NAVY_DARK = {0.071f, 0.122f, 0.220f};  // #121F38
    private static final float[] GOLD = {0.961f, 0.651f, 0.137f};       // #F5A623
    private static final float[] WHITE = {1f, 1f, 1f};
    private static final float[] TEXT_DARK = {0.102f, 0.102f, 0.180f};  // #1A1A2E
    private static final float[] TEXT_MUTED = {0.420f, 0.447f, 0.502f}; // #6B7280
    private static final float[] BORDER_LIGHT = {0.898f, 0.906f, 0.922f}; // #E5E7EB
    private static final float[] CARD_BG = {0.969f, 0.973f, 0.980f};    // #F7F8FA

    // ── Page dimensions ───────────────────────────────────────────────────────
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 50f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    /**
     * Generates a complete PDF ticket for the given RSVP.
     *
     * @param dto RSVP response containing all event and student details
     * @return PDF file as byte array
     */
    public byte[] generateTicketPdf(RSVPResponseDTO dto) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontOblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float y = PAGE_HEIGHT;

                // ═══════════════════════════════════════════════════════════════
                // HEADER STRIP (navy background with branding)
                // ═══════════════════════════════════════════════════════════════
                float headerHeight = 80f;
                y -= headerHeight;

                // Navy background
                cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2]);
                cs.addRect(0, y, PAGE_WIDTH, headerHeight);
                cs.fill();

                // Dark navy top accent line
                cs.setNonStrokingColor(NAVY_DARK[0], NAVY_DARK[1], NAVY_DARK[2]);
                cs.addRect(0, y + headerHeight - 4, PAGE_WIDTH, 4);
                cs.fill();

                // "SOCIETY CENTRAL" text
                cs.beginText();
                cs.setFont(fontBold, 22);
                cs.setNonStrokingColor(WHITE[0], WHITE[1], WHITE[2]);
                cs.newLineAtOffset(MARGIN, y + 38);
                cs.showText("SOCIETY CENTRAL");
                cs.endText();

                // "NELSON MANDELA UNIVERSITY" subtitle
                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
                cs.newLineAtOffset(MARGIN, y + 18);
                cs.showText("NELSON MANDELA UNIVERSITY");
                cs.endText();

                // "EVENT TICKET" badge (gold rounded rect on the right)
                float badgeWidth = 110f;
                float badgeHeight = 30f;
                float badgeX = PAGE_WIDTH - MARGIN - badgeWidth - 10;
                float badgeY = y + 25;

                cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
                drawRoundedRect(cs, badgeX, badgeY, badgeWidth, badgeHeight, 15f);
                cs.fill();

                cs.beginText();
                cs.setFont(fontBold, 11);
                cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2]);
                cs.newLineAtOffset(badgeX + 16, badgeY + 10);
                cs.showText("EVENT TICKET");
                cs.endText();

                // ═══════════════════════════════════════════════════════════════
                // LETTER BODY
                // ═══════════════════════════════════════════════════════════════
                y -= 40f;

                // "Dear {name},"
                cs.beginText();
                cs.setFont(fontBold, 14);
                cs.setNonStrokingColor(TEXT_DARK[0], TEXT_DARK[1], TEXT_DARK[2]);
                cs.newLineAtOffset(MARGIN, y);
                cs.showText("Dear " + sanitize(dto.getStudentName()) + ",");
                cs.endText();

                y -= 24f;

                // Confirmation paragraph
                String[] confirmLines = {
                        "This serves as confirmation of your attendance for the event below.",
                        "Please present this ticket, or have it available on your phone, for",
                        "scanning at the door on the day of the event."
                };
                cs.setFont(fontRegular, 10);
                cs.setNonStrokingColor(TEXT_MUTED[0], TEXT_MUTED[1], TEXT_MUTED[2]);
                for (String line : confirmLines) {
                    cs.beginText();
                    cs.newLineAtOffset(MARGIN, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 15f;
                }

                // ═══════════════════════════════════════════════════════════════
                // EVENT DETAILS CARD
                // ═══════════════════════════════════════════════════════════════
                y -= 20f;
                float cardHeight = 140f;
                float cardY = y - cardHeight;

                // Card background
                cs.setNonStrokingColor(CARD_BG[0], CARD_BG[1], CARD_BG[2]);
                cs.addRect(MARGIN, cardY, CONTENT_WIDTH, cardHeight);
                cs.fill();

                // Card border
                cs.setStrokingColor(BORDER_LIGHT[0], BORDER_LIGHT[1], BORDER_LIGHT[2]);
                cs.setLineWidth(0.5f);
                cs.addRect(MARGIN, cardY, CONTENT_WIDTH, cardHeight);
                cs.stroke();

                // Gold left accent bar
                cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
                cs.addRect(MARGIN, cardY, 4f, cardHeight);
                cs.fill();

                // Event details inside the card
                float detailX = MARGIN + 20f;
                float detailY = cardY + cardHeight - 28f;

                // EVENT label + name
                drawLabel(cs, fontBold, "EVENT", detailX, detailY);
                detailY -= 16f;
                drawValue(cs, fontBold, sanitize(dto.getEventName()), detailX, detailY, 13);
                detailY -= 26f;

                // DATE & TIME
                drawLabel(cs, fontBold, "DATE & TIME", detailX, detailY);
                detailY -= 16f;
                String dateTime = formatEventDateTime(dto.getEventDate(), dto.getEventStartTime(), dto.getEventEndTime());
                drawValue(cs, fontRegular, dateTime, detailX, detailY, 11);
                detailY -= 26f;

                // VENUE
                drawLabel(cs, fontBold, "VENUE", detailX, detailY);
                detailY -= 16f;
                drawValue(cs, fontRegular, sanitize(dto.getVenueName()), detailX, detailY, 11);
                detailY -= 26f;

                // HOSTED BY
                drawLabel(cs, fontBold, "HOSTED BY", detailX, detailY);
                detailY -= 16f;
                drawValue(cs, fontRegular, sanitize(dto.getSocietyName()), detailX, detailY, 11);

                y = cardY - 30f;

                // ═══════════════════════════════════════════════════════════════
                // QR CODE SECTION
                // ═══════════════════════════════════════════════════════════════
                float qrSectionHeight = 160f;
                float qrSectionY = y - qrSectionHeight;

                // Section card background
                cs.setNonStrokingColor(WHITE[0], WHITE[1], WHITE[2]);
                cs.addRect(MARGIN, qrSectionY, CONTENT_WIDTH, qrSectionHeight);
                cs.fill();

                // Section border
                cs.setStrokingColor(BORDER_LIGHT[0], BORDER_LIGHT[1], BORDER_LIGHT[2]);
                cs.setLineWidth(0.5f);
                cs.addRect(MARGIN, qrSectionY, CONTENT_WIDTH, qrSectionHeight);
                cs.stroke();

                // QR Code image (left side)
                float qrSize = 130f;
                float qrX = MARGIN + 15f;
                float qrY = qrSectionY + (qrSectionHeight - qrSize) / 2f;

                BufferedImage qrImage = QRCodeUtil.generateQRCodeBufferedImage(
                        dto.getQrCodeTicket(), 300);
                PDImageXObject pdImage = LosslessFactory.createFromImage(document, qrImage);
                cs.drawImage(pdImage, qrX, qrY, qrSize, qrSize);

                // QR info text (right side)
                float qrTextX = qrX + qrSize + 25f;
                float qrTextY = qrSectionY + qrSectionHeight - 40f;

                cs.beginText();
                cs.setFont(fontBold, 13);
                cs.setNonStrokingColor(TEXT_DARK[0], TEXT_DARK[1], TEXT_DARK[2]);
                cs.newLineAtOffset(qrTextX, qrTextY);
                cs.showText("Scan at the door");
                cs.endText();

                qrTextY -= 20f;
                cs.setFont(fontRegular, 9);
                cs.setNonStrokingColor(TEXT_MUTED[0], TEXT_MUTED[1], TEXT_MUTED[2]);

                String[] qrInfoLines = {
                        "This QR code is unique to your RSVP",
                        "and can only be scanned once."
                };
                for (String line : qrInfoLines) {
                    cs.beginText();
                    cs.newLineAtOffset(qrTextX, qrTextY);
                    cs.showText(line);
                    cs.endText();
                    qrTextY -= 14f;
                }

                // RSVP reference
                qrTextY -= 12f;
                cs.beginText();
                cs.setFont(fontRegular, 8);
                cs.setNonStrokingColor(TEXT_MUTED[0], TEXT_MUTED[1], TEXT_MUTED[2]);
                cs.newLineAtOffset(qrTextX, qrTextY);
                cs.showText("RSVP reference");
                cs.endText();

                qrTextY -= 16f;
                cs.beginText();
                cs.setFont(fontBold, 10);
                cs.setNonStrokingColor(TEXT_DARK[0], TEXT_DARK[1], TEXT_DARK[2]);
                cs.newLineAtOffset(qrTextX, qrTextY);
                cs.showText(sanitize(dto.getRsvpReference()));
                cs.endText();

                // ═══════════════════════════════════════════════════════════════
                // FOOTER
                // ═══════════════════════════════════════════════════════════════
                float footerY = 40f;

                // Separator line
                cs.setStrokingColor(BORDER_LIGHT[0], BORDER_LIGHT[1], BORDER_LIGHT[2]);
                cs.setLineWidth(0.5f);
                cs.moveTo(MARGIN, footerY + 15f);
                cs.lineTo(PAGE_WIDTH - MARGIN, footerY + 15f);
                cs.stroke();

                // Left footer text
                cs.beginText();
                cs.setFont(fontRegular, 7);
                cs.setNonStrokingColor(TEXT_MUTED[0], TEXT_MUTED[1], TEXT_MUTED[2]);
                cs.newLineAtOffset(MARGIN, footerY);
                cs.showText("Society Central  |  Nelson Mandela University  |  This ticket is not transferable");
                cs.endText();

                // Right footer text
                String rightFooter = "societycentral.nmu.ac.za";
                float rightWidth = fontRegular.getStringWidth(rightFooter) / 1000f * 7f;
                cs.beginText();
                cs.setFont(fontRegular, 7);
                cs.newLineAtOffset(PAGE_WIDTH - MARGIN - rightWidth, footerY);
                cs.showText(rightFooter);
                cs.endText();
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();

        } catch (IOException e) {
            log.error("Failed to generate PDF ticket: {}", e.getMessage());
            throw new RuntimeException("Failed to generate event ticket PDF.", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DRAWING HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void drawLabel(PDPageContentStream cs, PDType1Font font, String text,
                           float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(font, 8);
        cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private void drawValue(PDPageContentStream cs, PDType1Font font, String text,
                           float x, float y, int fontSize) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.setNonStrokingColor(TEXT_DARK[0], TEXT_DARK[1], TEXT_DARK[2]);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    /**
     * Draws a rounded rectangle path (approximate using Bezier curves).
     */
    private void drawRoundedRect(PDPageContentStream cs, float x, float y,
                                  float width, float height, float radius) throws IOException {
        float k = 0.5523f; // Bezier approximation constant for quarter-circle
        float kr = k * radius;

        cs.moveTo(x + radius, y);
        cs.lineTo(x + width - radius, y);
        cs.curveTo(x + width - radius + kr, y, x + width, y + radius - kr, x + width, y + radius);
        cs.lineTo(x + width, y + height - radius);
        cs.curveTo(x + width, y + height - radius + kr, x + width - radius + kr, y + height, x + width - radius, y + height);
        cs.lineTo(x + radius, y + height);
        cs.curveTo(x + radius - kr, y + height, x, y + height - radius + kr, x, y + height - radius);
        cs.lineTo(x, y + radius);
        cs.curveTo(x, y + radius - kr, x + radius - kr, y, x + radius, y);
        cs.closePath();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FORMATTING HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private String formatEventDateTime(LocalDate date, LocalTime startTime, LocalTime endTime) {
        StringBuilder sb = new StringBuilder();

        if (date != null) {
            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
            sb.append(date.format(dateFormat));
        } else {
            sb.append("Date TBC");
        }

        sb.append("  |  ");

        if (startTime != null) {
            sb.append(startTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            if (endTime != null) {
                sb.append(" - ").append(endTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            }
        } else {
            sb.append("Time TBC");
        }

        return sb.toString();
    }

    /**
     * Sanitizes text for PDF output. Replaces null/blank with a fallback
     * and removes characters that PDFBox cannot encode in standard fonts.
     */
    private String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "Not available";
        }

        // Standard Type1 fonts only support WinAnsiEncoding; strip anything outside it
        return text.replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", "");
    }
}
