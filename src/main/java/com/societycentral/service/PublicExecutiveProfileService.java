package com.societycentral.service;

import com.societycentral.dto.response.AttendedEventSummaryDTO;
import com.societycentral.dto.response.PublicExecutiveProfileResponse;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.model.Executive;
import com.societycentral.model.Student;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.RSVPRepository;
import com.societycentral.repository.SocietyMemberRepository;
import com.societycentral.repository.StudentRepository;
import com.societycentral.repository.UserProfilePictureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Builds the public executive profile while its required lazy data is available. */
@Service
@RequiredArgsConstructor
public class PublicExecutiveProfileService {
    private final StudentRepository students;
    private final ExecutiveRepository executives;
    private final UserProfilePictureRepository pictures;
    private final SocietyMemberRepository members;
    private final RSVPRepository rsvps;

    @Transactional(readOnly = true)
    public PublicExecutiveProfileResponse get(String studentNumber) {
        Student student = students.findById(studentNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found."));
        LocalDate today = LocalDate.now();
        Executive role = executives.findByIdStudentNumber(studentNumber).stream()
                .filter(e -> e.getId().getTermStartDate() != null && !e.getId().getTermStartDate().isAfter(today))
                .filter(e -> e.getTermEndDate() == null || !e.getTermEndDate().isBefore(today))
                .findFirst().orElse(null);
        var membership = members.findByIdStudentNumber(studentNumber).stream()
                .filter(m -> m.getExpireDate() == null || !m.getExpireDate().isBefore(today))
                .findFirst().orElse(null);
        var attended = rsvps.findByIdStudentNumber(studentNumber).stream()
                .filter(r -> Boolean.TRUE.equals(r.getScannedStatus()))
                .map(r -> AttendedEventSummaryDTO.builder()
                        .eventID(r.getEvent().getEventID())
                        .eventName(r.getEvent().getEventName())
                        .eventDate(r.getEvent().getEventDate())
                        .venue(r.getEvent().getEventVenue() != null ? r.getEvent().getEventVenue() : r.getEvent().getVenueCode())
                        .attendanceStatus("CHECKED_IN").build()).toList();
        var picture = pictures.findById(student.getEmail());

        // Map the lazy programme relationship inside the service transaction;
        // controllers receive a fully materialised response DTO.
        return PublicExecutiveProfileResponse.builder()
                .studentNumber(student.getStudentNumber()).title(student.getUser().getTitle())
                .firstName(student.getUser().getFirstName()).lastName(student.getUser().getLastName())
                .fullName((student.getUser().getFirstName() + " " + student.getUser().getLastName()).trim())
                .campus(student.getUser().getCampus()).course(student.getCourse())
                .programmeName(student.getProgramme() == null ? null : student.getProgramme().getProgrammeName())
                .level(student.getLevel()).faculty(student.getFaculty().name())
                .school(student.getSchool() == null ? null : student.getSchool().name())
                .email(student.getEmail()).cellPhoneNumber(student.getCellPhoneNumber())
                .nationality(student.getNationality()).residence(student.getResidence())
                .accommodationType(student.getAccommodationType() == null ? null : "Registered accommodation")
                .membershipStatus(membership == null ? null : "ACTIVE")
                .membershipSocietyID(membership == null ? null : membership.getSociety().getSocietyID())
                .membershipSocietyName(membership == null ? null : membership.getSociety().getSocietyName())
                .memberSince(membership == null ? null : membership.getJoinDate())
                .attendedEventCount(attended.size()).attendedEvents(attended)
                .executivePosition(role == null ? null : role.getPosition())
                .executiveSocietyID(role == null ? null : role.getSociety().getSocietyID())
                .executiveSocietyName(role == null ? null : role.getSociety().getSocietyName())
                .executiveTermStart(role == null ? null : role.getId().getTermStartDate())
                .executiveTermEnd(role == null ? null : role.getTermEndDate())
                .hasProfilePicture(picture.isPresent())
                .profilePictureVersion(picture.map(p -> p.getUpdatedAt() == null ? null : p.getUpdatedAt().toString()).orElse(null))
                .build();
    }
}
