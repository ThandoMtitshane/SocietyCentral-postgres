package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Value;
import java.time.LocalDate;

@Value @Builder
public class AttendedEventSummaryDTO {
    String eventID, eventName, venue;
    LocalDate eventDate;
    String attendanceStatus;
}
