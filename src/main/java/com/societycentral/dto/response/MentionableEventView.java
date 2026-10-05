package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * One event the sender may @mention. Limited to events hosted by the sender's
 * own society.
 */
@Getter
@Builder
public class MentionableEventView {
    private String eventID;
    private String eventName;
    private String eventStatus;
    private LocalDate eventDate;
}
