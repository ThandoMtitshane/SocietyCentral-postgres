package com.societycentral.service;

import com.societycentral.dto.request.UpdateCurrentProfileRequest;
import com.societycentral.dto.request.ChangePasswordRequest;
import com.societycentral.dto.response.CurrentProfileResponse;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.time.LocalDateTime;

@Service
public class CurrentProfileService {
    private final UserRepository users;
    private final StudentRepository students;
    private final SDORepository sdos;
    private final ExecutiveRepository executives;
    private final UserProfilePictureRepository pictures;
    private final ProgrammeReferenceRepository programmes;
    private final ProgrammeCampusRepository programmeCampuses;
    private final ResidenceReferenceRepository residences;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder;

    public CurrentProfileService(UserRepository users, StudentRepository students,
                                 SDORepository sdos, ExecutiveRepository executives,
                                 UserProfilePictureRepository pictures,
                                 ProgrammeReferenceRepository programmes,
                                 ProgrammeCampusRepository programmeCampuses,
                                 ResidenceReferenceRepository residences,
                                 Clock clock, PasswordEncoder passwordEncoder) {
        this.users = users; this.students = students; this.sdos = sdos;
        this.executives = executives; this.pictures = pictures;
        this.programmes = programmes; this.programmeCampuses = programmeCampuses;
        this.residences = residences; this.clock = clock; this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = requireUser(email);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) throw new IllegalArgumentException("Current password is incorrect.");
        if (!request.getNewPassword().matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,}$")) throw new IllegalArgumentException("Password must meet all security requirements.");
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordTemporary(false);
        users.save(user);
    }

    @Transactional(readOnly = true)
    public CurrentProfileResponse get(String email) { return response(requireUser(email)); }

    @Transactional
    public CurrentProfileResponse update(String email, UpdateCurrentProfileRequest request) {
        User user = requireUser(email);
        user.setTitle(request.getTitle().trim()); user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim()); user.setCampus(request.getCampus());
        if (user.getUserType() == UserType.STUDENT) {
            Student s = students.findByEmail(email).orElseThrow();
            ProgrammeReference programme = programmes.findById(request.getProgrammeCode())
                    .orElseThrow(() -> new IllegalArgumentException("A valid programme must be selected."));
            var mappings = programmeCampuses.findByProgrammeProgrammeCode(programme.getProgrammeCode());
            if (!mappings.isEmpty() && mappings.stream().noneMatch(mapping -> mapping.getCampus().getCampusCode().equals(request.getCampus().name()))) {
                throw new IllegalArgumentException("The selected campus is not valid for this programme.");
            }
            s.setProgramme(programme); s.setCourse(programme.getProgrammeName()); s.setLevel(blank(request.getLevel()));
            s.setNationality(blank(request.getNationality()));
            updateResidence(s, request.getResidenceID(), request.getCampus());
            s.setSchool(legacySchool(programme)); s.setCellPhoneNumber(blank(request.getCellPhoneNumber()));
            students.save(s);
        } else {
            SDO sdo = sdos.findByEmail(email).orElseThrow();
            sdo.setOfficeNumber(request.getOfficeNumber()); sdo.setPhoneExtension(blank(request.getPhoneExtension()));
            sdos.save(sdo);
        }
        return response(users.save(user));
    }

    @Transactional
    public CurrentProfileResponse upload(String email, MultipartFile file) {
        User user = requireUser(email);
        if (file == null || file.isEmpty() || !Set.of("image/jpeg", "image/png", "image/webp").contains(file.getContentType())) throw new IllegalArgumentException("Only JPEG, PNG, or WebP images are supported.");
        if (file.getSize() > 2_000_000) throw new IllegalArgumentException("Profile picture must not exceed 2 MB.");
        try {
            UserProfilePicture picture = pictures.findByUserEmail(email).orElseGet(() -> {
                UserProfilePicture created = new UserProfilePicture();
                // userEmail is the entity identifier and the picture table's FK.
                // Establish it before the entity is associated with the managed User
                // or passed to a repository, so Hibernate can never see a null ID.
                created.setUserEmail(email);
                return created;
            });
            picture.setUserEmail(email); picture.setImageData(file.getBytes());
            picture.setContentType(file.getContentType()); picture.setOriginalFileName(file.getOriginalFilename());
            picture.setFileSize(file.getSize()); picture.setUpdatedAt(LocalDateTime.now(clock));
            pictures.save(picture);
            user.setProfilePictureURL(null);
            return response(users.save(user));
        } catch (IOException ex) { throw new IllegalStateException("Unable to store profile picture.", ex); }
    }

    public record PictureData(byte[] data, String contentType) {}
    @Transactional(readOnly = true) public PictureData picture(String email) {
        UserProfilePicture p=pictures.findById(email).orElseThrow(() -> new com.societycentral.exception.ResourceNotFoundException("Profile picture not found."));
        return new PictureData(p.getImageData(), p.getContentType());
    }
    @Transactional public CurrentProfileResponse removePicture(String email) { User u=requireUser(email); pictures.deleteById(email); u.setProfilePictureURL(null); return response(users.save(u)); }
    private User requireUser(String email) { return users.findById(email).orElseThrow(() -> new IllegalArgumentException("Authenticated user was not found.")); }
    private String blank(String v) { return v == null || v.isBlank() ? null : v.trim(); }

    /**
     * ResidenceReference is the authoritative source for on-campus residence
     * identity. The legacy Student.residence text is synchronised for older
     * consumers, but it is never accepted from the client as authority.
     */
    private void updateResidence(Student student, Integer residenceID, Campus campus) {
        if (residenceID == null) {
            student.setResidenceReference(null);
            student.setResidence(null);
            return;
        }

        ResidenceReference residence = residences.findById(residenceID)
                .orElseThrow(() -> new IllegalArgumentException("A valid residence must be selected."));
        if (!residence.isActive() || campus == null
                || !residence.getCampusCode().equals(campus.name())) {
            throw new IllegalArgumentException("The selected residence is not valid for the selected campus.");
        }
        student.setResidenceReference(residence);
        student.setResidence(residence.getResidenceName());
    }
    private School legacySchool(ProgrammeReference programme) {
        if (programme.getSchool() == null) return School.NONE;
        try { return School.valueOf(programme.getSchool().getSchoolCode()); }
        catch (IllegalArgumentException ignored) { return School.NONE; }
    }
    private String label(String value) { return java.util.Arrays.stream(value.toLowerCase().split("_")).map(x -> x.isEmpty() ? x : Character.toUpperCase(x.charAt(0)) + x.substring(1)).collect(java.util.stream.Collectors.joining(" ")); }
    private String extension(String type) { return switch(type) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; default -> ".webp"; }; }
    private CurrentProfileResponse response(User u) {
        Student s=students.findByEmail(u.getEmail()).orElse(null); SDO d=sdos.findByEmail(u.getEmail()).orElse(null);
        Executive e=s==null?null:executives.findActiveExecutiveRoles(s.getStudentNumber(), LocalDate.now(clock)).stream().findFirst().orElse(null);
        ProgrammeReference p = s == null ? null : s.getProgramme();
        return CurrentProfileResponse.builder().email(u.getEmail()).title(u.getTitle()).firstName(u.getFirstName()).lastName(u.getLastName()).campus(u.getCampus()).userType(u.getUserType().name()).dashboardType(e!=null?"EXECUTIVE":u.getUserType().name()).profilePictureURL(null).hasProfilePicture(pictures.existsById(u.getEmail()))
            .programmeCode(p==null?null:p.getProgrammeCode()).programmeName(p==null?null:p.getProgrammeName()).facultyCode(p==null?null:p.getFaculty().getFacultyCode()).facultyName(p==null?null:p.getFaculty().getFacultyName()).schoolCode(p==null||p.getSchool()==null?null:p.getSchool().getSchoolCode()).schoolName(p==null||p.getSchool()==null?null:p.getSchool().getSchoolName()).campusCode(u.getCampus()==null?null:u.getCampus().name()).campusName(u.getCampus()==null?null:label(u.getCampus().name()))
            .studentNumber(s==null?null:s.getStudentNumber()).course(s==null?null:s.getCourse()).level(s==null?null:s.getLevel()).nationality(s==null?null:s.getNationality()).residence(s==null?null:s.getResidence()).residenceID(s==null||s.getResidenceReference()==null?null:s.getResidenceReference().getResidenceID()).school(p==null||p.getSchool()==null?null:p.getSchool().getSchoolName()).faculty(p==null||p.getFaculty()==null?null:p.getFaculty().getFacultyName()).cellPhoneNumber(s==null?null:s.getCellPhoneNumber())
            .executivePosition(e==null?null:e.getPosition()).executiveSocietyID(e==null?null:e.getSociety().getSocietyID()).executiveSocietyName(e==null?null:e.getSociety().getSocietyName()).termStartDate(e==null?null:e.getId().getTermStartDate()).termEndDate(e==null?null:e.getTermEndDate())
            .staffNumber(d==null?null:d.getStaffNumber()).officeNumber(d==null?null:d.getOfficeNumber()).phoneExtension(d==null?null:d.getPhoneExtension()).build();
    }

}
