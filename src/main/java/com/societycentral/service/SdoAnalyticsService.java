package com.societycentral.service;

import com.societycentral.dto.response.SdoAnalyticsReportDTO;
import com.societycentral.model.Event;
import com.societycentral.model.EventFeedback;
import com.societycentral.model.FundTransaction;
import com.societycentral.model.FundTransactionDirection;
import com.societycentral.model.Society;
import com.societycentral.repository.EventFeedbackRepository;
import com.societycentral.repository.EventRepository;
import com.societycentral.repository.FundTransactionRepository;
import com.societycentral.repository.HosterRepository;
import com.societycentral.repository.RSVPRepository;
import com.societycentral.repository.SocietyMemberRepository;
import com.societycentral.repository.SocietyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portfolio-wide analytics for an SDO across every society they supervise.
 * Aggregates in-service (mirrors ExecutiveAnalyticsService) and reuses
 * EventReportService for per-event rating data.
 */
@Service
@RequiredArgsConstructor
public class SdoAnalyticsService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SocietyRepository societyRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final HosterRepository hosterRepository;
    private final EventRepository eventRepository;
    private final RSVPRepository rsvpRepository;
    private final EventFeedbackRepository feedbackRepository;
    private final SocietyMemberRepository societyMemberRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SdoAnalyticsReportDTO buildReport(String sdoEmail,
                                             LocalDate fromDate,
                                             LocalDate toDate) {
        LocalDate today = LocalDate.now(clock);
        validateRange(fromDate, toDate, today);

        List<Society> societies = societyRepository.findAllBySdo_Email(sdoEmail);

        List<SdoAnalyticsReportDTO.SocietyRow> rows = new ArrayList<>();
        List<SdoAnalyticsReportDTO.EventReportRow> reportRows = new ArrayList<>();

        // Portfolio accumulators
        long totalMembers = 0, totalEvents = 0, totalRSVPs = 0, totalAttendance = 0;
        double societyRatingSum = 0; int societyRatingCount = 0;
        double eventRatingSum = 0; int eventRatingCount = 0;
        BigDecimal totalAllocated = BigDecimal.ZERO, totalBalance = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO, totalDebits = BigDecimal.ZERO;

        // Event-report overview accumulators
        double ovOverall = 0, ovOrg = 0, ovVenue = 0, ovContent = 0;
        int ovOverallCount = 0, ovOrgCount = 0, ovVenueCount = 0, ovContentCount = 0;
        long ovFeedbackTotal = 0;
        Map<String, Long> eventsByStatus = new LinkedHashMap<>();

        for (Society society : societies) {
            String societyID = society.getSocietyID();

            // ── Financial (in range) ──
            BigDecimal[] credDeb = creditsDebitsInRange(societyID, fromDate, toDate);
            totalCredits = totalCredits.add(credDeb[0]);
            totalDebits = totalDebits.add(credDeb[1]);
            BigDecimal balance = society.getCurrentBalance() == null
                    ? BigDecimal.ZERO : society.getCurrentBalance();
            BigDecimal allocation = society.getAnnualBudgetAllocation() == null
                    ? BigDecimal.ZERO : society.getAnnualBudgetAllocation();
            totalBalance = totalBalance.add(balance);
            totalAllocated = totalAllocated.add(allocation);

            // ── Members ──
            long memberCount = societyMemberRepository
                    .findCurrentMembersBySocietyID(societyID, today).size();
            totalMembers += memberCount;

            // ── Society rating ──
            double societyRating = society.getRating() == null ? 0.0 : society.getRating();
            if (societyRating > 0) { societyRatingSum += societyRating; societyRatingCount++; }

            // ── Events for this society ──
            List<Event> events = hosterRepository.findByIdSocietyID(societyID).stream()
                    .map(h -> eventRepository.findById(h.getId().getEventID()).orElse(null))
                    .filter(e -> e != null)
                    .filter(e -> withinRange(e.getEventDate(), fromDate, toDate))
                    .sorted(Comparator.comparing(Event::getEventDate,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();

            long socRSVPs = 0, socAttendance = 0;
            double socRatingSum = 0; int socRatingCount = 0;

            for (Event e : events) {
                String status = e.getEventStatus() == null ? "UNKNOWN" : e.getEventStatus().name();
                eventsByStatus.merge(status, 1L, Long::sum);

                long rsvps = rsvpRepository.countByIdEventID(e.getEventID());
                long attended = rsvpRepository.countByIdEventIDAndScannedStatusTrue(e.getEventID());
                socRSVPs += rsvps;
                socAttendance += attended;

                List<EventFeedback> feedbacks = feedbackRepository.findByEventID(e.getEventID());
                double avgOverall = avg(feedbacks, EventFeedback::getRating);
                if (!feedbacks.isEmpty() && avgOverall > 0) {
                    socRatingSum += avgOverall; socRatingCount++;
                    eventRatingSum += avgOverall; eventRatingCount++;
                }

                // Event-report overview (per rating category + status)
                double aO = avg(feedbacks, EventFeedback::getRating);
                double aOrg = avg(feedbacks, EventFeedback::getOrganizationRating);
                double aV = avg(feedbacks, EventFeedback::getVenueRating);
                double aC = avg(feedbacks, EventFeedback::getContentRating);
                if (aO > 0) { ovOverall += aO; ovOverallCount++; }
                if (aOrg > 0) { ovOrg += aOrg; ovOrgCount++; }
                if (aV > 0) { ovVenue += aV; ovVenueCount++; }
                if (aC > 0) { ovContent += aC; ovContentCount++; }
                ovFeedbackTotal += feedbacks.size();

                boolean reportReady = e.getEventDate() != null
                        && LocalDate.now(clock).isAfter(e.getEventDate().plusDays(6));

                reportRows.add(SdoAnalyticsReportDTO.EventReportRow.builder()
                        .eventID(e.getEventID())
                        .eventName(e.getEventName())
                        .eventDate(e.getEventDate())
                        .status(status)
                        .societyID(societyID)
                        .societyName(society.getSocietyName())
                        .rsvpCount(rsvps)
                        .attendanceCount(attended)
                        .attendanceRate(rsvps == 0 ? 0.0 : round1((double) attended / rsvps * 100.0))
                        .averageOverallRating(round1(aO))
                        .feedbackCount(feedbacks.size())
                        .reportReady(reportReady)
                        .build());
            }

            totalEvents += events.size();
            totalRSVPs += socRSVPs;
            totalAttendance += socAttendance;

            rows.add(SdoAnalyticsReportDTO.SocietyRow.builder()
                    .societyID(societyID)
                    .societyName(society.getSocietyName())
                    .acronym(society.getAcronym())
                    .active(Boolean.TRUE.equals(society.getActiveStatus()))
                    .flagged(Boolean.TRUE.equals(society.getIsFlagged()))
                    .societyRating(round1(societyRating))
                    .memberCount(memberCount)
                    .eventCount(events.size())
                    .totalRSVPs(socRSVPs)
                    .totalAttendance(socAttendance)
                    .averageEventRating(socRatingCount == 0 ? 0.0 : round1(socRatingSum / socRatingCount))
                    .currentBalance(balance)
                    .totalCredits(credDeb[0])
                    .totalDebits(credDeb[1])
                    .build());
        }

        // Sort report rows newest-first across the portfolio
        reportRows.sort(Comparator.comparing(
                SdoAnalyticsReportDTO.EventReportRow::getEventDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        var summary = SdoAnalyticsReportDTO.PortfolioSummary.builder()
                .totalSocieties(societies.size())
                .activeSocieties((int) societies.stream()
                        .filter(s -> Boolean.TRUE.equals(s.getActiveStatus())).count())
                .flaggedSocieties((int) societies.stream()
                        .filter(s -> Boolean.TRUE.equals(s.getIsFlagged())).count())
                .totalMembers(totalMembers)
                .totalEvents(totalEvents)
                .totalRSVPs(totalRSVPs)
                .totalAttendance(totalAttendance)
                .averageSocietyRating(societyRatingCount == 0 ? 0.0 : round1(societyRatingSum / societyRatingCount))
                .averageEventRating(eventRatingCount == 0 ? 0.0 : round1(eventRatingSum / eventRatingCount))
                .totalAllocated(totalAllocated)
                .totalCurrentBalance(totalBalance)
                .totalCredits(totalCredits)
                .totalDebits(totalDebits)
                .build();

        var overview = SdoAnalyticsReportDTO.EventReportOverview.builder()
                .totalReportableEvents(reportRows.size())
                .averageOverallRating(ovOverallCount == 0 ? 0.0 : round1(ovOverall / ovOverallCount))
                .averageOrganizationRating(ovOrgCount == 0 ? 0.0 : round1(ovOrg / ovOrgCount))
                .averageVenueRating(ovVenueCount == 0 ? 0.0 : round1(ovVenue / ovVenueCount))
                .averageContentRating(ovContentCount == 0 ? 0.0 : round1(ovContent / ovContentCount))
                .totalFeedbackCount(ovFeedbackTotal)
                .eventsByStatus(eventsByStatus)
                .rows(reportRows)
                .build();

        return SdoAnalyticsReportDTO.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .generatedAt(LocalDateTime.now(clock).format(ISO))
                .summary(summary)
                .societies(rows)
                .eventReports(overview)
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private BigDecimal[] creditsDebitsInRange(String societyID,
                                              LocalDate fromDate, LocalDate toDate) {
        BigDecimal credits = BigDecimal.ZERO, debits = BigDecimal.ZERO;
        for (FundTransaction tx : fundTransactionRepository
                .findBySocietyIDOrderByTransactionDateDesc(societyID)) {
            if (!withinRange(tx.getTransactionDate(), fromDate, toDate)) continue;
            BigDecimal amount = tx.getAmount() == null ? BigDecimal.ZERO : tx.getAmount();
            if (tx.getDirection() == FundTransactionDirection.CREDIT) {
                credits = credits.add(amount);
            } else {
                debits = debits.add(amount);
            }
        }
        return new BigDecimal[]{credits, debits};
    }

    private double avg(List<EventFeedback> feedbacks,
                       java.util.function.Function<EventFeedback, Integer> getter) {
        return feedbacks.stream()
                .map(getter)
                .filter(v -> v != null)
                .mapToInt(Integer::intValue)
                .average().orElse(0.0);
    }

    private void validateRange(LocalDate from, LocalDate to, LocalDate today) {
        if (from != null && from.isAfter(today)) {
            throw new IllegalArgumentException("The 'from' date cannot be in the future.");
        }
        if (to != null && to.isAfter(today)) {
            throw new IllegalArgumentException("The 'to' date cannot be in the future.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("The 'from' date cannot be after the 'to' date.");
        }
    }

    private boolean withinRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) return false;
        if (from != null && date.isBefore(from)) return false;
        if (to != null && date.isAfter(to)) return false;
        return true;
    }

    private boolean withinRange(LocalDateTime dateTime, LocalDate from, LocalDate to) {
        if (dateTime == null) return false;
        LocalDate date = dateTime.toLocalDate();
        if (from != null && date.isBefore(from)) return false;
        if (to != null && date.isAfter(to)) return false;
        return true;
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
