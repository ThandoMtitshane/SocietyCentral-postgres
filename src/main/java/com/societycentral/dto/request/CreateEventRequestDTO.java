package com.societycentral.dto.request;

import com.societycentral.model.AttendingType;
import com.societycentral.model.Campus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Request payload used by a society executive when creating a new event
 * proposal.
 *
 * Phase 1 (proposal submission): name, date, time, venue, campus, description,
 * attendingType, images, budget, and optional co-host societies.
 *
 * Phase 2 (publishing): rsvpOpenDate, rsvpCloseDate, eventLimit are provided
 * via {@link PublishEventRequestDTO} after SDO approval.
 */
@Data
public class CreateEventRequestDTO {

    /**
     * Official name of the proposed event.
     */
    @NotBlank(message = "Event name is required")
    @Size(max = 100, message = "Event name may not exceed 100 characters")
    private String eventName;

    /**
     * Date on which the event will take place. Must be a future date.
     */
    @NotNull(message = "Event date is required")
    private LocalDate eventDate;

    /**
     * Starting time used for availability and RSVP chronology.
     */
    @NotNull(message = "Event start time is required.")
    private LocalTime eventStartTime;

    /**
     * Ending time used for venue overlap detection.
     */
    @NotNull(message = "Event end time is required.")
    private LocalTime eventEndTime;

    /**
     * Legacy single-time value retained only for request deserialization
     * compatibility. New event creation ignores it.
     *
     * @deprecated use {@link #eventStartTime} and {@link #eventEndTime}
     */
    @Deprecated
    private LocalTime eventTime;

    /**
     * Identifier of the authoritative Venue selected by the frontend.
     */
    @NotBlank(message = "Venue selection is required.")
    @Size(max = 10, message = "Venue code may not exceed 10 characters")
    private String venueCode;

    /**
     * Legacy free-text venue retained only for request deserialization
     * compatibility. New event creation ignores it.
     *
     * @deprecated venue details are resolved using {@link #venueCode}
     */
    @Deprecated
    @Size(max = 100, message = "Event venue may not exceed 100 characters")
    private String eventVenue;

    /**
     * NMU campus where the event will take place.
     */
    @NotNull(message = "Event campus is required")
    private Campus eventCampus;

    /**
     * Detailed description explaining the purpose and content of the event.
     * Limited to 1000 characters.
     */
    @NotBlank(message = "Event description is required")
    @Size(max = 1000, message = "Event description may not exceed 1000 characters")
    private String eventDescription;

    /**
     * Date and time from which students may begin submitting RSVPs.
     * Only required at publish time (Phase 2), nullable during proposal.
     */
    private LocalDateTime rsvpOpenDate;

    /**
     * Date and time after which RSVPs will no longer be accepted.
     * Only required at publish time (Phase 2), nullable during proposal.
     */
    private LocalDateTime rsvpCloseDate;

    /**
     * Maximum number of attendees allowed at the event.
     * Only required at publish time (Phase 2), nullable during proposal.
     */
    @Min(value = 1, message = "Event attendance limit must be at least 1")
    private Integer eventLimit;

    /**
     * Defines whether attendance is restricted to society members or open
     * to every student.
     */
    @NotNull(message = "Attending type is required")
    private AttendingType attendingType;

    /**
     * Legacy single-image URL retained only for request compatibility.
     *
     * @deprecated use {@link #posterUrl} and {@link #bannerUrl}
     */
    @Deprecated
    @Size(max = 255, message = "Image URL may not exceed 255 characters")
    private String imageUrl;

    /**
     * URL returned by the event poster upload endpoint.
     */
    @Size(max = 500, message = "Event poster URL may not exceed 500 characters")
    private String posterUrl;

    /**
     * URL returned by the event banner upload endpoint.
     */
    @Size(max = 500, message = "Event banner URL may not exceed 500 characters")
    private String bannerUrl;

    // ═══════════════════════════════════════════════════════════════════════════
    // BUDGET (inline, mirrors POAEvent budget structure)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Income from the society's allocated account balance. */
    private BigDecimal budgetIncomeFromAccount;

    /** Income from external sponsorships. */
    private BigDecimal budgetIncomeSponsorship;

    /** Expense: promotional materials (posters, banners, etc.) */
    private BigDecimal budgetExpensePromoMaterial;

    /** Expense: data and airtime costs. */
    private BigDecimal budgetExpenseDataAirtime;

    /** Expense: gifts and prizes. */
    private BigDecimal budgetExpenseGifts;

    /** Expense: venue hire costs. */
    private BigDecimal budgetExpenseVenue;

    /** Expense: other costs not covered by the above categories. */
    private BigDecimal budgetExpenseOther;

    /** Required when budgetExpenseOther > 0. Specifies what the "other" cost covers. */
    @Size(max = 200, message = "Budget other specification may not exceed 200 characters")
    private String budgetExpenseOtherSpecification;

    // ═══════════════════════════════════════════════════════════════════════════
    // CO-HOST COLLABORATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Society IDs to invite as co-hosts for this event.
     * Invitations are sent when the proposal is submitted (PROPOSED status).
     */
    private List<String> coHostSocietyIDs;

    // ═══════════════════════════════════════════════════════════════════════════
    // POA LINK
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Optional POA event ID to link this proposal to a planned POA event.
     * When provided, the system records the association.
     */
    @Size(max = 36, message = "POA event ID may not exceed 36 characters")
    private String poaEventID;
}

