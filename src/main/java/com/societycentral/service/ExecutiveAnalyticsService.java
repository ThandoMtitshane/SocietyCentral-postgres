package com.societycentral.service;

import com.societycentral.dto.response.AnalyticsReportDTO;
import com.societycentral.dto.response.AnalyticsReportDTO.CategoryTotal;
import com.societycentral.dto.response.AnalyticsReportDTO.EventPerformanceReport;
import com.societycentral.dto.response.AnalyticsReportDTO.EventPerformanceRow;
import com.societycentral.dto.response.AnalyticsReportDTO.FinancialReport;
import com.societycentral.dto.response.AnalyticsReportDTO.MembershipReport;
import com.societycentral.model.Event;
import com.societycentral.model.EventFeedback;
import com.societycentral.model.FundTransaction;
import com.societycentral.model.FundTransactionDirection;
import com.societycentral.model.Society;
import com.societycentral.model.SocietyMember;
import com.societycentral.repository.EventFeedbackRepository;
import com.societycentral.repository.EventRepository;
import com.societycentral.repository.FundTransactionRepository;
import com.societycentral.repository.HosterRepository;
import com.societycentral.repository.RSVPRepository;
import com.societycentral.repository.SocietyMemberRepository;
import com.societycentral.service.ExecutiveSocietyResolver.ActiveExecutiveSociety;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the aggregated Analytics & Reports payload for the authenticated
 * executive's active society over an optional date range. All aggregation is
 * done in-service (matching the ExecutiveDashboardService / EventReportService
 * pattern) so no new heavy queries are required.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveAnalyticsService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ExecutiveSocietyResolver executiveSocietyResolver;
    private final FundTransactionRepository fundTransactionRepository;
    private final HosterRepository hosterRepository;
    private final EventRepository eventRepository;
    private final RSVPRepository rsvpRepository;
    private final EventFeedbackRepository feedbackRepository;
    private final SocietyMemberRepository societyMemberRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AnalyticsReportDTO buildReport(String executiveEmail,
                                          LocalDate fromDate,
                                          LocalDate toDate) {
        ActiveExecutiveSociety context = executiveSocietyResolver.resolve(executiveEmail);
        Society society = context.society();
        String societyID = society.getSocietyID();

        // Guard: no future dates, and from must not be after to.
        LocalDate today = LocalDate.now(clock);
        if (fromDate != null && fromDate.isAfter(today)) {
            throw new IllegalArgumentException("The 'from' date cannot be in the future.");
        }
        if (toDate != null && toDate.isAfter(today)) {
            throw new IllegalArgumentException("The 'to' date cannot be in the future.");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("The 'from' date cannot be after the 'to' date.");
        }

        return AnalyticsReportDTO.builder()
                .societyID(societyID)
                .societyName(society.getSocietyName())
                .fromDate(fromDate)
                .toDate(toDate)
                .generatedAt(LocalDateTime.now(clock).format(ISO))
                .financial(buildFinancial(society, fromDate, toDate))
                .events(buildEventPerformance(societyID, fromDate, toDate))
                .membership(buildMembership(societyID, fromDate, toDate))
                .build();
    }

    // ── Financial ──────────────────────────────────────────────────────────

    private FinancialReport buildFinancial(Society society,
                                           LocalDate fromDate,
                                           LocalDate toDate) {
        // Newest-first from the repo; sort ascending for opening/closing logic.
        List<FundTransaction> all =
                fundTransactionRepository.findBySocietyIDOrderByTransactionDateDesc(
                        society.getSocietyID());

        List<FundTransaction> inRange = all.stream()
                .filter(tx -> withinRange(tx.getTransactionDate(), fromDate, toDate))
                .sorted(Comparator.comparing(FundTransaction::getTransactionDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalDebits = BigDecimal.ZERO;
        // reason|direction -> [total, count]
        Map<String, BigDecimal[]> reasonTotals = new LinkedHashMap<>();
        Map<String, Integer> reasonCounts = new LinkedHashMap<>();

        for (FundTransaction tx : inRange) {
            BigDecimal amount = tx.getAmount() == null ? BigDecimal.ZERO : tx.getAmount();
            boolean credit = tx.getDirection() == FundTransactionDirection.CREDIT;
            if (credit) {
                totalCredits = totalCredits.add(amount);
            } else {
                totalDebits = totalDebits.add(amount);
            }
            String reasonName = tx.getReason() == null ? "OTHER" : tx.getReason().name();
            String directionName = tx.getDirection() == null ? "CREDIT" : tx.getDirection().name();
            String key = reasonName + "|" + directionName;
            reasonTotals.merge(key, new BigDecimal[]{amount},
                    (a, b) -> new BigDecimal[]{a[0].add(b[0])});
            reasonCounts.merge(key, 1, Integer::sum);
        }

        List<CategoryTotal> byReason = new ArrayList<>();
        reasonTotals.forEach((key, total) -> {
            String[] parts = key.split("\\|");
            byReason.add(CategoryTotal.builder()
                    .reason(parts[0])
                    .direction(parts[1])
                    .total(total[0])
                    .count(reasonCounts.getOrDefault(key, 0))
                    .build());
        });

        BigDecimal opening = inRange.isEmpty() ? null
                : inRange.get(0).getBalanceBefore();
        BigDecimal closing = inRange.isEmpty() ? null
                : inRange.get(inRange.size() - 1).getBalanceAfter();

        return FinancialReport.builder()
                .openingBalance(opening)
                .closingBalance(closing)
                .currentBalance(society.getCurrentBalance() == null
                        ? BigDecimal.ZERO : society.getCurrentBalance())
                .totalCredits(totalCredits)
                .totalDebits(totalDebits)
                .transactionCount(inRange.size())
                .byReason(byReason)
                .build();
    }

    // ── Event Performance ──────────────────────────────────────────────────

    private EventPerformanceReport buildEventPerformance(String societyID,
                                                         LocalDate fromDate,
                                                         LocalDate toDate) {
        List<String> eventIDs = hosterRepository.findByIdSocietyID(societyID).stream()
                .map(h -> h.getId().getEventID())
                .toList();

        List<Event> events = eventIDs.stream()
                .map(id -> eventRepository.findById(id).orElse(null))
                .filter(e -> e != null)
                .filter(e -> withinRange(e.getEventDate(), fromDate, toDate))
                .sorted(Comparator.comparing(Event::getEventDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        List<EventPerformanceRow> rows = new ArrayList<>();
        long totalRSVPs = 0;
        long totalAttendance = 0;
        double rateSum = 0;
        int rateCount = 0;
        double ratingSum = 0;
        int ratingCount = 0;

        for (Event e : events) {
            String status = e.getEventStatus() == null ? "UNKNOWN" : e.getEventStatus().name();
            byStatus.merge(status, 1L, Long::sum);

            long rsvps = rsvpRepository.countByIdEventID(e.getEventID());
            long attended = rsvpRepository.countByIdEventIDAndScannedStatusTrue(e.getEventID());
            totalRSVPs += rsvps;
            totalAttendance += attended;

            double attendanceRate = rsvps == 0 ? 0.0
                    : round1((double) attended / rsvps * 100.0);
            if (rsvps > 0) { rateSum += attendanceRate; rateCount++; }

            List<EventFeedback> feedbacks = feedbackRepository.findByEventID(e.getEventID());
            double avgRating = feedbacks.stream()
                    .filter(f -> f.getRating() != null)
                    .mapToInt(EventFeedback::getRating)
                    .average().orElse(0.0);
            avgRating = round1(avgRating);
            if (!feedbacks.isEmpty() && avgRating > 0) { ratingSum += avgRating; ratingCount++; }

            rows.add(EventPerformanceRow.builder()
                    .eventID(e.getEventID())
                    .eventName(e.getEventName())
                    .eventDate(e.getEventDate())
                    .status(status)
                    .proposedAt(fmt(e.getCreatedAt()))
                    .reviewedAt(fmt(e.getReviewedAt()))
                    .publishedAt(fmt(e.getPublishedAt()))
                    .rsvpCount(rsvps)
                    .attendanceCount(attended)
                    .attendanceRate(attendanceRate)
                    .averageRating(avgRating)
                    .feedbackCount(feedbacks.size())
                    .build());
        }

        return EventPerformanceReport.builder()
                .totalEvents(events.size())
                .byStatus(byStatus)
                .totalRSVPs(totalRSVPs)
                .totalAttendance(totalAttendance)
                .averageAttendanceRate(rateCount == 0 ? 0.0 : round1(rateSum / rateCount))
                .averageRating(ratingCount == 0 ? 0.0 : round1(ratingSum / ratingCount))
                .rows(rows)
                .build();
    }

    // ── Membership ─────────────────────────────────────────────────────────

    private MembershipReport buildMembership(String societyID,
                                             LocalDate fromDate,
                                             LocalDate toDate) {
        LocalDate today = LocalDate.now(clock);
        List<SocietyMember> current =
                societyMemberRepository.findCurrentMembersBySocietyID(societyID, today);

        // "New in range" is always join-date filtered.
        long newInRange = current.stream()
                .filter(m -> withinRange(m.getJoinDate(), fromDate, toDate))
                .count();

        // When a date range is applied, the whole membership section reflects
        // members who JOINED within that range. With no range, it reflects all
        // current active members.
        boolean rangeApplied = fromDate != null || toDate != null;
        List<SocietyMember> scoped = rangeApplied
                ? current.stream()
                    .filter(m -> withinRange(m.getJoinDate(), fromDate, toDate))
                    .toList()
                : current;

        Map<String, Long> byCampus = new LinkedHashMap<>();
        Map<String, Long> byLevel = new LinkedHashMap<>();
        Map<String, Long> byCourse = new LinkedHashMap<>();

        for (SocietyMember m : scoped) {
            var student = m.getStudent();
            if (student == null) continue;
            String campus = student.getUser() != null && student.getUser().getCampus() != null
                    ? student.getUser().getCampus().name() : "Unknown";
            String level = student.getLevel() == null || student.getLevel().isBlank()
                    ? "Unknown" : student.getLevel();
            String course = student.getCourse() == null || student.getCourse().isBlank()
                    ? "Unknown" : student.getCourse();
            byCampus.merge(campus, 1L, Long::sum);
            byLevel.merge(level, 1L, Long::sum);
            byCourse.merge(course, 1L, Long::sum);
        }

        return MembershipReport.builder()
                .totalActiveMembers(scoped.size())
                .newMembersInRange(newInRange)
                .byCampus(byCampus)
                .byLevel(byLevel)
                .byCourse(byCourse)
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    private String fmt(LocalDateTime value) {
        return value == null ? null : value.format(ISO);
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
