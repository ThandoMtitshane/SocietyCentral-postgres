package com.societycentral.service;

import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.model.AttendingType;
import com.societycentral.model.Event;
import com.societycentral.repository.HosterRepository;
import com.societycentral.repository.SocietyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/** Attendee eligibility is independent of executive browsing or management access. */
@Service
@RequiredArgsConstructor
public class EventAttendanceEligibility {
    private final HosterRepository hosterRepository;
    private final SocietyMemberRepository societyMemberRepository;
    private final Clock clock;

    public void requireEligibleStudent(Event event, String studentNumber) {
        if (event.getAttendingType() == AttendingType.EVERY_STUDENT) {
            return;
        }

        // Match ordinary browsing: current membership in any hosting society.
        if (event.getAttendingType() == AttendingType.MEMBERS
                && hosterRepository.findByIdEventID(event.getEventID()).stream()
                .anyMatch(host -> societyMemberRepository.existsActiveMembership(
                        studentNumber, host.getId().getSocietyID(), LocalDate.now(clock)))) {
            return;
        }

        throw new ForbiddenOperationException(
                "Only current members of a hosting society can RSVP to this event.");
    }
}
