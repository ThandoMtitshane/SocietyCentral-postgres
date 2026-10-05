package com.societycentral.service;

import com.societycentral.dto.response.EventProposalResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Generates a branded PDF event proposal document for SDO review.
 *
 * Layout matches SocietyCentral branding:
 * - Navy header with "SOCIETY CENTRAL" + gold "EVENT PROPOSAL" badge
 * - Event details section with gold left accent
 * - Budget breakdown table
 * - Co-host information
 * - Footer with branding
 */
@Service
@Slf4j
public class EventProposalPdfService {

    private static final float[] NAVY = {0.106f, 0.169f, 0.294f};
    private static final float[] NAVY_DARK = {0.071f, 0.122f, 0.220f};
    private static final float[] GOLD = {0.961f, 0.651f, 0.137f};
    private static final float[] WHITE = {1f, 1f, 1f};
    private static final float[] TEXT_DARK = {0.102f, 0.102f, 0.180f};
    private static final float[] TEXT_MUTED = {0.420f, 0.447f, 0.502f};
    private static final float[] BORDER = {0.898f, 0.906f, 0.922f};
    private static final float[] CARD_BG = {0.969f, 0.973f, 0.980f};

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 50f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    public byte[] generateProposalPdf(EventProposalResponseDTO dto) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float y = PAGE_HEIGHT;

                // ── HEADER ───────────────────────────────────────────────
                float headerH = 80f;
                y -= headerH;
                cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2]);
                cs.addRect(0, y, PAGE_WIDTH, headerH);
                cs.fill();

                cs.setNonStrokingColor(NAVY_DARK[0], NAVY_DARK[1], NAVY_DARK[2]);
                cs.addRect(0, y + headerH - 4, PAGE_WIDTH, 4);
                cs.fill();

                cs.beginText();
                cs.setFont(bold, 22);
                cs.setNonStrokingColor(WHITE[0], WHITE[1], WHITE[2]);
                cs.newLineAtOffset(MARGIN, y + 38);
                cs.showText("SOCIETY CENTRAL");
                cs.endText();

                cs.beginText();
                cs.setFont(regular, 10);
                cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
                cs.newLineAtOffset(MARGIN, y + 18);
                cs.showText("NELSON MANDELA UNIVERSITY");
                cs.endText();

                // Gold badge
                float badgeW = 130f, badgeH = 30f;
                float badgeX = PAGE_WIDTH - MARGIN - badgeW - 10;
                float badgeY = y + 25;
                cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
                cs.addRect(badgeX, badgeY, badgeW, badgeH);
                cs.fill();
                cs.beginText();
                cs.setFont(bold, 10);
                cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2]);
                cs.newLineAtOffset(badgeX + 12, badgeY + 10);
                cs.showText("EVENT PROPOSAL");
                cs.endText();

                // ── SOCIETY & SUBMITTER INFO ─────────────────────────────
                y -= 35f;
                cs.beginText();
                cs.setFont(bold, 12);
                cs.setNonStrokingColor(TEXT_DARK[0], TEXT_DARK[1], TEXT_DARK[2]);
                cs.newLineAtOffset(MARGIN, y);
                cs.showText(safe(dto.getSocietyName()));
                cs.endText();

                y -= 16f;
                cs.beginText();
                cs.setFont(regular, 9);
                cs.setNonStrokingColor(TEXT_MUTED[0], TEXT_MUTED[1], TEXT_MUTED[2]);
                cs.newLineAtOffset(MARGIN, y);
                cs.showText("Submitted by: " + safe(dto.getSubmittedByName())
                        + " (" + safe(dto.getSubmittedByStudentNumber()) + ")");
                cs.endText();

                // ── EVENT DETAILS CARD ───────────────────────────────────
                y -= 30f;
                float cardH = 150f;
                float cardY = y - cardH;

                cs.setNonStrokingColor(CARD_BG[0], CARD_BG[1], CARD_BG[2]);
                cs.addRect(MARGIN, cardY, CONTENT_WIDTH, cardH);
                cs.fill();
                cs.setStrokingColor(BORDER[0], BORDER[1], BORDER[2]);
                cs.setLineWidth(0.5f);
                cs.addRect(MARGIN, cardY, CONTENT_WIDTH, cardH);
                cs.stroke();

                // Gold left accent
                cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
                cs.addRect(MARGIN, cardY, 4f, cardH);
                cs.fill();

                float dx = MARGIN + 20f;
                float dy = cardY + cardH - 25f;

                drawLabel(cs, bold, "EVENT NAME", dx, dy);
                dy -= 14f;
                drawValue(cs, bold, safe(dto.getEventName()), dx, dy, 13);
                dy -= 24f;

                drawLabel(cs, bold, "DATE & TIME", dx, dy);
                dy -= 14f;
                drawValue(cs, regular, formatDateTime(dto.getEventDate(),
                        dto.getEventStartTime(), dto.getEventEndTime()), dx, dy, 10);
                dy -= 24f;

                drawLabel(cs, bold, "VENUE", dx, dy);
                dy -= 14f;
                drawValue(cs, regular, safe(dto.getVenueName()), dx, dy, 10);
                dy -= 24f;

                drawLabel(cs, bold, "ATTENDANCE", dx, dy);
                dy -= 14f;
                drawValue(cs, regular, safe(dto.getAttendingType()), dx, dy, 10);

                y = cardY - 20f;

                // ── DESCRIPTION ──────────────────────────────────────────
                drawLabel(cs, bold, "EVENT DESCRIPTION", MARGIN, y);
                y -= 14f;
                String desc = safe(dto.getEventDescription());
                // Wrap description to multiple lines
                List<String> descLines = wrapText(desc, regular, 10, CONTENT_WIDTH - 10);
                cs.setFont(regular, 10);
                cs.setNonStrokingColor(TEXT_DARK[0], TEXT_DARK[1], TEXT_DARK[2]);
                for (String line : descLines) {
                    cs.beginText();
                    cs.newLineAtOffset(MARGIN, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 13f;
                }

                y -= 15f;

                // ── BUDGET TABLE ─────────────────────────────────────────
                drawLabel(cs, bold, "PROPOSED BUDGET", MARGIN, y);
                y -= 18f;

                // Income
                y = drawBudgetRow(cs, regular, "Income from Account",
                        dto.getBudgetIncomeFromAccount(), MARGIN, y);
                y = drawBudgetRow(cs, regular, "Income from Sponsorship",
                        dto.getBudgetIncomeSponsorship(), MARGIN, y);
                y = drawBudgetRow(cs, bold, "TOTAL INCOME",
                        dto.getBudgetTotalIncome(), MARGIN, y);

                y -= 8f;

                // Expenses
                y = drawBudgetRow(cs, regular, "Promotional Material",
                        dto.getBudgetExpensePromoMaterial(), MARGIN, y);
                y = drawBudgetRow(cs, regular, "Data & Airtime",
                        dto.getBudgetExpenseDataAirtime(), MARGIN, y);
                y = drawBudgetRow(cs, regular, "Gifts & Prizes",
                        dto.getBudgetExpenseGifts(), MARGIN, y);
                y = drawBudgetRow(cs, regular, "Venue Costs",
                        dto.getBudgetExpenseVenue(), MARGIN, y);
                if (dto.getBudgetExpenseOther() != null
                        && dto.getBudgetExpenseOther().compareTo(BigDecimal.ZERO) > 0) {
                    String otherLabel = "Other"
                            + (dto.getBudgetExpenseOtherSpecification() != null
                            ? " (" + dto.getBudgetExpenseOtherSpecification() + ")" : "");
                    y = drawBudgetRow(cs, regular, otherLabel,
                            dto.getBudgetExpenseOther(), MARGIN, y);
                }
                y = drawBudgetRow(cs, bold, "TOTAL EXPENSES",
                        dto.getBudgetTotalExpenses(), MARGIN, y);

                y -= 15f;

                // ── CO-HOSTS ─────────────────────────────────────────────
                if (dto.getCoHosts() != null && !dto.getCoHosts().isEmpty()) {
                    drawLabel(cs, bold, "COLLABORATING SOCIETIES", MARGIN, y);
                    y -= 14f;
                    for (EventProposalResponseDTO.CoHostDTO coHost : dto.getCoHosts()) {
                        cs.beginText();
                        cs.setFont(regular, 9);
                        cs.setNonStrokingColor(TEXT_DARK[0], TEXT_DARK[1], TEXT_DARK[2]);
                        cs.newLineAtOffset(MARGIN + 10, y);
                        cs.showText("- " + safe(coHost.getSocietyName())
                                + " (" + safe(coHost.getStatus()) + ")");
                        cs.endText();
                        y -= 13f;
                    }
                    y -= 10f;
                }

                // ── FOOTER ───────────────────────────────────────────────
                float footerY = 40f;
                cs.setStrokingColor(BORDER[0], BORDER[1], BORDER[2]);
                cs.setLineWidth(0.5f);
                cs.moveTo(MARGIN, footerY + 15f);
                cs.lineTo(PAGE_WIDTH - MARGIN, footerY + 15f);
                cs.stroke();

                cs.beginText();
                cs.setFont(regular, 7);
                cs.setNonStrokingColor(TEXT_MUTED[0], TEXT_MUTED[1], TEXT_MUTED[2]);
                cs.newLineAtOffset(MARGIN, footerY);
                cs.showText("Society Central  |  Nelson Mandela University  |  "
                        + "This document is for internal review purposes only");
                cs.endText();

                String rightFooter = "societycentral.nmu.ac.za";
                float rw = regular.getStringWidth(rightFooter) / 1000f * 7f;
                cs.beginText();
                cs.setFont(regular, 7);
                cs.newLineAtOffset(PAGE_WIDTH - MARGIN - rw, footerY);
                cs.showText(rightFooter);
                cs.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();

        } catch (IOException e) {
            log.error("Failed to generate event proposal PDF: {}", e.getMessage());
            throw new RuntimeException("Failed to generate proposal PDF.", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void drawLabel(PDPageContentStream cs, PDType1Font font,
                           String text, float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(font, 8);
        cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private void drawValue(PDPageContentStream cs, PDType1Font font,
                           String text, float x, float y, int size) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(TEXT_DARK[0], TEXT_DARK[1], TEXT_DARK[2]);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private float drawBudgetRow(PDPageContentStream cs, PDType1Font font,
                                String label, BigDecimal amount,
                                float x, float y) throws IOException {
        String amountStr = amount != null ? "R " + String.format("%.2f", amount) : "R 0.00";

        cs.beginText();
        cs.setFont(font, 9);
        cs.setNonStrokingColor(TEXT_DARK[0], TEXT_DARK[1], TEXT_DARK[2]);
        cs.newLineAtOffset(x + 10, y);
        cs.showText(label);
        cs.endText();

        float amountWidth = font.getStringWidth(amountStr) / 1000f * 9f;
        cs.beginText();
        cs.setFont(font, 9);
        cs.newLineAtOffset(x + CONTENT_WIDTH - amountWidth - 10, y);
        cs.showText(amountStr);
        cs.endText();

        return y - 14f;
    }

    private List<String> wrapText(String text, PDType1Font font,
                                  int fontSize, float maxWidth) throws IOException {
        List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("No description provided.");
            return lines;
        }
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String test = current.isEmpty() ? word : current + " " + word;
            float width = font.getStringWidth(test) / 1000f * fontSize;
            if (width > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(test);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    private String formatDateTime(LocalDate date, LocalTime start, LocalTime end) {
        StringBuilder sb = new StringBuilder();
        if (date != null) {
            sb.append(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)));
        } else {
            sb.append("Date TBC");
        }
        sb.append("  |  ");
        if (start != null) {
            sb.append(start.format(DateTimeFormatter.ofPattern("HH:mm")));
            if (end != null) sb.append(" - ").append(end.format(DateTimeFormatter.ofPattern("HH:mm")));
        } else {
            sb.append("Time TBC");
        }
        return sb.toString();
    }

    private String safe(String text) {
        if (text == null || text.isBlank()) return "Not available";
        return text.replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", "");
    }
}
