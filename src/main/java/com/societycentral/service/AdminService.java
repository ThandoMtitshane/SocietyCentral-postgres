package com.societycentral.service;

import com.societycentral.dto.request.UpdateSDORequestDTO;
import com.societycentral.dto.response.GetSDOResponse;
import com.societycentral.dto.response.SDOListResponse;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.societycentral.dto.request.RegisterSDORequest;
import com.societycentral.dto.response.RegisterSDOResponse;

import java.util.List;
import java.util.Map;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class AdminService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final SDORepository sdoRepository;
    private final PasswordEncoder passwordEncoder;
    private final StudentRepository studentRepository;
    private final TaskRepository taskRepository;
    private final AnnouncementRepository announcementRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final CampusReferenceRepository campusReferenceRepository;
    private final EmailService emailService;
    @org.springframework.beans.factory.annotation.Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    /**
     * Creates an administrator account if one does not already exist.
     *
     * @param email       administrator email address
     * @param rawPassword administrator password before encryption
     * @return existing or newly created administrator
     */
    public User createAdminIfNotExists(String email, String rawPassword) {
        return userRepository.findById(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setFirstName("Admin");
            user.setLastName("User");
            user.setTitle("Mr");
            user.setUserType(UserType.ADMIN);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            return userRepository.save(user);
        });
    }


    /**
     * Updates the details of an existing Student Development Officer.
     *
     * @param staffNumber the unique staff number of the SDO
     * @param request     the updated SDO information
     */
    @Transactional
    public void updateSDO(String staffNumber, UpdateSDORequestDTO request) {

        // Find the SDO using the staff number
        SDO sdo = sdoRepository.findById(staffNumber)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Student Development Officer not found."
                        ));

        // Find the associated user account using the SDO's email
        User user = userRepository.findById(sdo.getEmail()).orElseThrow(() ->
                        new EntityNotFoundException("User not found."));

        // Check whether another SDO already uses this phone extension
        if (sdoRepository.existsByPhoneExtensionAndStaffNumberNot(request.getPhoneExtension(),
                staffNumber)) {
            throw new IllegalArgumentException(
                    "Phone number is already assigned to another Student Development Officer."
            );
        }

        String currentEmail = user.getEmail();
        String requestedEmail = request.getEmail().trim();
        boolean emailChanged = !currentEmail.equalsIgnoreCase(requestedEmail);

        CampusReference campusReference = campusReferenceRepository.findById(request.getCampus().name())
                .filter(CampusReference::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive campus."));
        if (request.getLocation() != null && !request.getLocation().isBlank()
                && !request.getLocation().equalsIgnoreCase(campusReference.getCity())) {
            throw new IllegalArgumentException("Selected campus does not belong to the selected location.");
        }

        // If the email has changed, ensure it is not already in use
        if (emailChanged) {

            if (userRepository.existsById(requestedEmail)) {
                throw new IllegalArgumentException(
                        "Email address already exists."
                );
            }
        }

        // Update SDO-specific information
        sdo.setOfficeNumber(request.getOfficeNumber());
        sdo.setPhoneExtension(request.getPhoneExtension());

        if (emailChanged) {
            migrateUserEmail(user, sdo, requestedEmail, request);
        } else {
            updateUserDetails(user, request);
            userRepository.save(user);
            sdoRepository.save(sdo);
        }
    }

    /**
     * Email is the primary key of User, so Hibernate cannot mutate it on a
     * managed entity. Create the replacement account first, move all foreign
     * keys to it, and only then remove the old account.
     */
    private void migrateUserEmail(User existingUser,
                                  SDO sdo,
                                  String newEmail,
                                  UpdateSDORequestDTO request) {
        String oldEmail = existingUser.getEmail();

        User replacementUser = new User();
        replacementUser.setEmail(newEmail);
        replacementUser.setUserType(existingUser.getUserType());
        replacementUser.setPasswordHash(existingUser.getPasswordHash());
        replacementUser.setProfilePictureURL(existingUser.getProfilePictureURL());
        updateUserDetails(replacementUser, request);

        // The new parent row must exist before any child foreign key moves.
        userRepository.saveAndFlush(replacementUser);

        studentRepository.reassignEmail(oldEmail, newEmail);
        taskRepository.reassignAssignedByEmail(oldEmail, newEmail);
        announcementRepository.reassignSenderEmail(oldEmail, newEmail);
        notificationRepository.reassignRecipientEmail(oldEmail, newEmail);
        passwordResetTokenRepository.reassignEmail(oldEmail, newEmail);

        sdo.setEmail(newEmail);
        sdo.setUser(replacementUser);
        sdoRepository.saveAndFlush(sdo);

        userRepository.delete(existingUser);
        userRepository.flush();
    }

    private void updateUserDetails(User user, UpdateSDORequestDTO request) {
        user.setTitle(request.getTitle());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setCampus(request.getCampus());
    }

    /**
     * Retrieves the details of a Student Development Officer.
     *
     * @param staffNumber the unique staff number of the Student Development Officer
     * @return the Student Development Officer details
     */
    public GetSDOResponse getSDOByStaffNumber(String staffNumber) {

        // Find the Student Development Officer using the staff number
        SDO sdo = sdoRepository.findById(staffNumber)
                .orElseThrow(() ->
                        new EntityNotFoundException("Student Development Officer not found."));

        // Find the associated user account
        User user = userRepository.findById(sdo.getEmail())
                .orElseThrow(() ->
                        new EntityNotFoundException("User not found."));

        // Populate the response object
        GetSDOResponse response = new GetSDOResponse();
        response.setStaffNumber(sdo.getStaffNumber());
        response.setTitle(user.getTitle());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEmail(user.getEmail());
        response.setCampus(user.getCampus());
        response.setOfficeNumber(sdo.getOfficeNumber());
        response.setPhoneExtension(sdo.getPhoneExtension());

        // Return the response
        return response;
    }

    /**
     * Registers a new Student Development Officer.
     *
     * @param request registration details
     * @return registration response
     */
    @Transactional
    public RegisterSDOResponse registerSDO(RegisterSDORequest request) {

        // Check if the email already exists
        if (userRepository.existsById(request.getEmail())) {
            throw new IllegalArgumentException("Email address already exists.");
        }

        // Check if the staff number already exists
        if (sdoRepository.existsById(request.getStaffNumber())) {
            throw new IllegalArgumentException("Staff number already exists.");
        }

        String email = request.getEmail().trim().toLowerCase();
        CampusReference campus = campusReferenceRepository.findById(request.getCampus().name())
                .filter(CampusReference::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive campus."));
        if (!request.getLocation().equalsIgnoreCase(campus.getCity())) {
            throw new IllegalArgumentException("Selected campus does not belong to the selected location.");
        }



        // Check if the phone extension is already assigned to another SDO
        if (sdoRepository.existsByPhoneExtension(request.getPhoneExtension())) {
            throw new IllegalArgumentException(
                    "Phone number is already assigned to another Student Development Officer."
            );
        }

        // Create the User account
        User user = new User();
        user.setEmail(email);
        user.setTitle(request.getTitle());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setCampus(request.getCampus());
        user.setUserType(UserType.SDO);
        String temporaryPassword = generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setEmailVerified(false);
        user.setPasswordTemporary(true);

        userRepository.save(user);

        // Create the SDO record
        SDO sdo = new SDO();
        sdo.setStaffNumber(request.getStaffNumber());
        sdo.setEmail(email);
        sdo.setOfficeNumber(request.getOfficeNumber());
        sdo.setPhoneExtension(request.getPhoneExtension());

        sdoRepository.save(sdo);

        passwordResetTokenRepository.deleteByEmail(email);
        PasswordResetToken setupToken = PasswordResetToken.create(email);
        passwordResetTokenRepository.save(setupToken);
        boolean emailSent = emailService.send(EmailType.SDO_ONBOARDING, email, Map.of(
                "firstName", user.getFirstName(), "staffNumber", sdo.getStaffNumber(),
                "campus", campus.getCampusName(),
                "email", email, "temporaryPassword", temporaryPassword,
                "location", request.getLocation(),
                "verificationLink", baseUrl + "/verify-email?token=" + setupToken.getToken()));

        // Build the response
        RegisterSDOResponse response = new RegisterSDOResponse();
        response.setStaffNumber(sdo.getStaffNumber());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setMessage("Student Development Officer registered successfully.");
        response.setEmailSent(emailSent);

        return response;
    }

    private String generateTemporaryPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ", lower = "abcdefghijkmnopqrstuvwxyz";
        String digits = "23456789", symbols = "!@#$%^&*_-+", all = upper + lower + digits + symbols;
        StringBuilder result = new StringBuilder();
        result.append(upper.charAt(SECURE_RANDOM.nextInt(upper.length())));
        result.append(lower.charAt(SECURE_RANDOM.nextInt(lower.length())));
        result.append(digits.charAt(SECURE_RANDOM.nextInt(digits.length())));
        result.append(symbols.charAt(SECURE_RANDOM.nextInt(symbols.length())));
        while (result.length() < 16) result.append(all.charAt(SECURE_RANDOM.nextInt(all.length())));
        for (int i = result.length() - 1; i > 0; i--) { int j = SECURE_RANDOM.nextInt(i + 1); char c = result.charAt(i); result.setCharAt(i, result.charAt(j)); result.setCharAt(j, c); }
        return result.toString();
    }

    /**
     * Retrieves all registered Student Development Officers.
     *
     * @return a list containing summary information for all Student Development Officers
     */
    public List<SDOListResponse> getAllSDOs() {

        // Retrieve all Student Development Officer records
        return sdoRepository.findAll()
                .stream()
                .map(sdo -> {

                    // Retrieve the associated user account
                    User user = userRepository.findById(sdo.getEmail())
                            .orElseThrow(() ->
                                    new EntityNotFoundException("User not found."));

                    // Populate the response object
                    SDOListResponse response = new SDOListResponse();

                    response.setStaffNumber(sdo.getStaffNumber());
                    response.setTitle(user.getTitle());
                    response.setFirstName(user.getFirstName());
                    response.setLastName(user.getLastName());
                    response.setEmail(user.getEmail());
                    response.setCampus(user.getCampus());

                    return response;
                })
                .toList();
    }


    /**
     * Deletes an existing Student Development Officer
     * and the associated user account.
     *
     * @param staffNumber the unique staff number of the SDO
     */
    @Transactional
    public void deleteSDO(String staffNumber) {

        SDO sdo = sdoRepository.findById(staffNumber).orElseThrow(() ->
                        new EntityNotFoundException("Student Development Officer not found."));

        String email = sdo.getEmail();

        User user = userRepository.findById(email).orElseThrow(() ->
                        new EntityNotFoundException("Associated user account not found.")
                );

        // Delete the child record first
        sdoRepository.delete(sdo);

        // Force the SDO DELETE statement to run immediately
        // before deleting the associated User.
        sdoRepository.flush();

        // Delete the parent user record
        userRepository.delete(user);
    }

}


