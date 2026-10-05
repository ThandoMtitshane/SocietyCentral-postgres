package com.societycentral.service;

import com.societycentral.dto.request.ForgotPasswordRequest;
import com.societycentral.dto.request.ResetPasswordRequest;
import com.societycentral.model.PasswordResetToken;
import com.societycentral.dto.request.LoginRequest;
import com.societycentral.dto.request.RegisterRequest;
import com.societycentral.dto.request.RegisterSDORequest;
import com.societycentral.dto.request.UpdateSDORequestDTO;
import com.societycentral.dto.response.AuthResponse;
import com.societycentral.dto.response.SDOProfileResponse;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import com.societycentral.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * Handles authentication concerns: registration (STUDENT self-registration
 * only - see RegisterRequest) and login (issuing a JWT on success).
 * <p>
 * Login returns an expanded AuthResponse including dashboardType, which
 * tells the frontend which dashboard to render:
 * <ul>
 *   <li>SDO       → SDO Dashboard</li>
 *   <li>EXECUTIVE → Executive Dashboard (student who is a current exec)</li>
 *   <li>STUDENT   → Student Dashboard</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.societycentral.repository.ProgrammeReferenceRepository programmeReferences;
    @org.springframework.beans.factory.annotation.Autowired
    private com.societycentral.repository.ProgrammeCampusRepository programmeCampuses;
    @org.springframework.beans.factory.annotation.Autowired
    private com.societycentral.repository.CampusReferenceRepository campusReferences;
    @Autowired private AccommodationTypeRepository accommodationTypeRepository;
    @Autowired private ResidenceReferenceRepository residenceReferenceRepository;
    @Autowired private OffCampusPropertyRepository offCampusPropertyRepository;
    @Autowired private OffCampusAccreditationRepository offCampusAccreditationRepository;

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ExecutiveRepository executiveRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final SDORepository sdoRepository;
    private final EmailService emailService;
    @Autowired private UserProfilePictureRepository userProfilePictureRepository;

    /**
     * Base URL used to build the password reset link in the email.
     * Set in application.properties as: app.base-url=http://localhost:5173
     */
    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    /**
     * Repository responsible for storing audit records.
     *
     * This repository allows the system to save historical
     * records whenever important information is created,
     * updated or deleted.
     *
     * The audit trail improves accountability by recording:
     * • What changed
     * • Who made the change
     * • When the change occurred
     */
    private final AuditLogRepository auditLogRepository;
    @Autowired
    /**
     * Constructs the AuthService and injects all required dependencies.
     *
     * Spring Boot automatically calls this constructor when the
     * application starts and supplies each dependency from the
     * Spring Application Context.
     *
     * Constructor Injection is preferred because:
     * • Dependencies are mandatory.
     * • Fields can be declared final.
     * • Objects cannot exist in an incomplete state.
     * • Dependencies are easy to identify.
     *
     * @param userRepository Repository for User table operations.
     * @param studentRepository Repository for Student table operations.
     * @param executiveRepository Repository for Executive table operations.
     * @param sdoRepository Repository for Student Development Officer table operations.
     * @param auditLogRepository Repository for storing audit history.
     * @param passwordEncoder Encrypts passwords before they are stored.
     * @param authenticationManager Authenticates users during login.
     * @param jwtUtil Generates and validates JSON Web Tokens (JWTs).
     */
    public AuthService(UserRepository userRepository, StudentRepository studentRepository, ExecutiveRepository executiveRepository, SDORepository sdoRepository, AuditLogRepository auditLogRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, JwtUtil jwtUtil,PasswordResetTokenRepository passwordResetTokenRepository,EmailService emailService)
    {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.sdoRepository = sdoRepository;
        this.executiveRepository = executiveRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.resetTokenRepository=passwordResetTokenRepository;
        this.emailService=emailService;
    }

    /**
     * Registers a new STUDENT user: creates both the User row (with a
     * BCrypt-hashed password) and the linked Student row.
     * Registration no longer auto-logs in - client must call /login separately.
     */
    @Transactional
    public void register(RegisterRequest request) {
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase();
        if (email == null || email.isBlank() || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        if (userRepository.existsById(email)) {
            throw new IllegalStateException("This email address is already registered.");
        }
        if (request.getPassword() == null || !request.getPassword().matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,}$")) {
            throw new IllegalArgumentException("Password must meet all security requirements.");
        }
        if (request.getStudentNumber() == null || request.getStudentNumber().isBlank()) {
            throw new IllegalArgumentException("Student number is required.");
        }
        if (studentRepository.existsById(request.getStudentNumber())) {
            throw new IllegalStateException("This student number is already registered.");
        }
        if (request.getProgrammeCode() == null || request.getProgrammeCode().isBlank()) {
            throw new IllegalArgumentException("A programme is required for student registration.");
        }
        if (request.getCampus() == null) throw new IllegalArgumentException("A campus is required for student registration.");
        if (!"Gqeberha".equals(request.getStudyCity()) && !"George".equals(request.getStudyCity())) {
            throw new IllegalArgumentException("Study city must be Gqeberha or George.");
        }
        com.societycentral.model.ProgrammeReference programme = programmeReferences.findById(request.getProgrammeCode())
                .filter(com.societycentral.model.ProgrammeReference::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive programme."));
        String campusCode = request.getCampus().name();
        CampusReference campusReference = campusReferences.findById(campusCode)
                .filter(CampusReference::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive campus."));
        if (!request.getStudyCity().equals(campusReference.getCity())) {
            throw new IllegalArgumentException("Selected campus does not belong to the selected city.");
        }
        var mappings = programmeCampuses.findByProgrammeProgrammeCode(programme.getProgrammeCode());
        if (!mappings.isEmpty() && mappings.stream().noneMatch(m -> campusCode.equals(m.getCampus().getCampusCode()))) {
            throw new IllegalArgumentException("This programme is not offered at the selected campus.");
        }

        User user = new User();
        user.setEmail(email);
        user.setTitle(request.getTitle());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setUserType(UserType.STUDENT);
        user.setCampus(request.getCampus());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        Student student = new Student();
        student.setStudentNumber(request.getStudentNumber());
        student.setEmail(email);
        student.setProgramme(programme);
        student.setCourse(programme.getProgrammeName());
        student.setLevel(request.getLevel());
        student.setNationality(request.getNationality());
        student.setStudyCity(request.getStudyCity());
        if (request.getGender() == null) throw new IllegalArgumentException("Gender is required for student registration.");
        student.setGender(request.getGender());
        student.setResidence(request.getResidence());
        student.setSchool(legacySchool(programme));
        student.setCellPhoneNumber(request.getCellPhoneNumber());
        if (request.getAccommodationTypeID() != null) {
            AccommodationType type = accommodationTypeRepository.findById(request.getAccommodationTypeID()).orElseThrow(() -> new IllegalArgumentException("Unknown accommodation type."));
            student.setAccommodationType(type);
            String code = switch (type.getAccommodationTypeID()) { case 1 -> "ON_CAMPUS"; case 2 -> "ACCREDITED_OFF_CAMPUS"; case 3 -> "PRIVATE_OFF_CAMPUS"; case 4 -> "HOME"; case 5 -> "OTHER"; default -> ""; };
            if ("ON_CAMPUS".equals(code)) {
                if (request.getResidenceID() == null) throw new IllegalArgumentException("Please select your residence.");
                ResidenceReference residence = residenceReferenceRepository.findById(request.getResidenceID()).orElseThrow(() -> new IllegalArgumentException("Unknown residence."));
                if (!campusCode.equals(residence.getCampusCode())) throw new IllegalArgumentException("Selected residence does not belong to the selected campus.");
                student.setResidenceReference(residence); student.setResidence(residence.getResidenceName());
            } else if ("PRIVATE_OFF_CAMPUS".equals(code) || "OTHER".equals(code)) {
                if (request.getOtherAccommodationName() == null || request.getOtherAccommodationName().trim().isBlank()) throw new IllegalArgumentException("Accommodation details are required.");
                student.setOtherAccommodationName(request.getOtherAccommodationName().trim()); student.setResidence(student.getOtherAccommodationName());
            } else if ("ACCREDITED_OFF_CAMPUS".equals(code)) {
                if (request.getOffCampusPropertyID() == null) throw new IllegalArgumentException("Please select an accredited accommodation property.");
                OffCampusProperty property = offCampusPropertyRepository.findById(request.getOffCampusPropertyID()).orElseThrow(() -> new IllegalArgumentException("Unknown accommodation property."));
                boolean valid = offCampusAccreditationRepository.findByAcademicYearAndAccreditedTrue((short)2021).stream().anyMatch(a -> a.getProperty().getOffCampusPropertyID().equals(property.getOffCampusPropertyID()));
                if (!valid) throw new IllegalArgumentException("Selected property is not currently accredited.");
                student.setOffCampusProperty(property); student.setResidence(property.getPropertyName());
            }
        }
        studentRepository.save(student);
    }

    private School legacySchool(ProgrammeReference programme) {
        if (programme == null || programme.getSchool() == null) {
            return School.NONE;
        }
        try {
            return School.valueOf(programme.getSchool().getSchoolCode());
        } catch (IllegalArgumentException ignored) {
            return School.NONE;
        }
    }


    /**
     * Registers a new Student Development Officer (SDO).
     *
     * This method creates records in both the User and SDO tables.
     * The entire operation runs inside a transaction, meaning
     * either both records are saved successfully or neither is.
     *
     * @param request Contains all information required to register an SDO.
     */
    @Transactional
    public void registerSDO(RegisterSDORequest request) {



        //===============================================
        // 1. VALIDATE USER
        //================================================
        //Validate Request: Check whether another user already has this email address.
        if(userRepository.existsById(request.getEmail())){
            throw new IllegalArgumentException("A user with this email already exists");
        }
        // Validate Request: Check whether another user already has this staff number.
        if(sdoRepository.existsById(request.getStaffNumber())){
            throw new IllegalArgumentException("A SDO with this staff number already exists");
        }


        //===============================================
        // 2.CREATE AND SAVE USER OBJECT
        //================================================

        // General information shared by all system users.
        User user = new User();
        //Set the SDO's email address.-->This is also the primary key in the User table.
        user.setEmail(request.getEmail());

        //Set the SDO's title (Mr, Ms, Dr, etc.).
        user.setTitle(request.getTitle());

        //Set the SDO's first name.
        user.setFirstName(request.getFirstName());
        // Set the SDO's last name.
        user.setLastName(request.getLastName());

        //Specify that this user is an SDO. --> This determines permissions and dashboard access.
        user.setUserType(UserType.SDO);

        //Store the campus where the SDO works.
        user.setCampus(request.getCampus());

        // Use an unusable random initial secret; current A101 uses AdminService setup tokens.
        user.setPasswordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));

        // Save the User record to the database.-->This must happen before saving the SDO because the SDO references the User through the email address.
        userRepository.save(user);
        //=================================================
        // 3.CREATE SDO
        //==================================================

        // specific to Student Development Officers.
        SDO sdo = new SDO();

        // Create the SDO entity that stores information --> specific to Student Development Officers.
        sdo.setStaffNumber(request.getStaffNumber());

        // Associate this SDO with the User record using the email address.
        sdo.setEmail(request.getEmail());

        // Store the office number.
        sdo.setOfficeNumber(request.getOfficeNumber());

        // Store the internal telephone extension.
        sdo.setPhoneExtension(request.getPhoneExtension());

        // Save the SDO record to the database.--> At this point both the User and SDO records will exist in their respective tables.
        sdoRepository.save(sdo);
    }

    /**
     * Searches for an existing Student Development Officer (SDO).
     *
     * Purpose:
     * Allows an Administrator to retrieve the complete profile of an
     * existing Student Development Officer before performing an update.
     *
     * The Administrator may search using either:
     * - Staff Number
     * - Email Address
     *
     * The method retrieves information from both the SDO table and the
     * User table before combining the information into a single
     * SDOProfileResponse object.
     *
     * Business Process:
     * 1. Validate the search criteria.
     * 2. Search for the SDO.
     * 3. Retrieve the associated User.
     * 4. Combine the information into a response DTO.
     * 5. Return the completed response object.
     *
     * @param staffNumber The staff number entered by the Administrator.
     * @param email The email address entered by the Administrator.
     * @return A complete Student Development Officer profile.
     */
    public SDOProfileResponse searchSDO(String staffNumber, String email) {

        //===========================================================
        // 1. VALIDATE SEARCH CRITERIA
        //===========================================================

        if ((staffNumber == null || staffNumber.isBlank()) && (email == null || email.isBlank())) {

            throw new IllegalArgumentException("Please provide either a staff number or an email address.");
        }

        //===========================================================
        // 2. FIND THE STUDENT DEVELOPMENT OFFICER
        //===========================================================


        SDO sdo;
        //If the Administrator supplied a staff number, search using the staff number because it is the primary key of the SDO table.
        if (staffNumber != null && !staffNumber.isBlank()) {sdo = sdoRepository.findById(staffNumber).orElseThrow(() ->
                            new IllegalArgumentException("Student Development Officer not found."));

        }
        // Otherwise, search using the email address.
        else {sdo = sdoRepository.findByEmail(email).orElseThrow(() ->
                new IllegalArgumentException("Student Development Officer not found."));}

        //===========================================================
        // 3. FIND THE ASSOCIATED USER
        //===========================================================

        //Every Student Development Officer is associate with exactly one User record.
        // The email stored in the SDO table is used to retrieve the corresponding User.

        User user = userRepository.findById(sdo.getEmail()).orElseThrow(() -> new IllegalArgumentException("Associated user not found."));

        //===========================================================
        // 4. BUILD THE RESPONSE DTO
        //===========================================================

        // Create a new response object that will be sent back to the frontend.

        SDOProfileResponse response = new SDOProfileResponse();

        //Copy User information into the response DTO.
        response.setEmail(user.getEmail());
        response.setTitle(user.getTitle());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setCampus(user.getCampus());

        // Copy SDO information into the response DTO.
        response.setStaffNumber(sdo.getStaffNumber());
        response.setOfficeNumber(sdo.getOfficeNumber());
        response.setPhoneExtension(sdo.getPhoneExtension());

        //===========================================================
        // 5. RETURN THE RESPONSE
        //===========================================================

        /*
         * Return the completed profile to the controller.
         *
         * The controller will convert this object into JSO and send it back to the frontend or Postman.
         */
        return response;
    }


    /**
     * Creates and stores an audit record.
     *
     * Purpose:
     * Records a single field change made within the SocietyCentral system.
     *
     * Each audit record captures:
     * • The affected SDO
     * • The entity that changed
     * • The operation performed
     * • The field that changed
     * • The previous value
     * • The new value
     * • The administrator who made the change
     * • The date and time of the change
     * • The reason for the change (optional)
     *
     * This method follows the Single Responsibility Principle (SRP)
     * because its only responsibility is recording audit information.
     *
     * @param staffNumber Permanent staff number of the SDO.
     * @param entityName Name of the entity being updated.
     * @param operation Type of operation (UPDATE, CREATE, DELETE).
     * @param fieldChanged Name of the field that changed.
     * @param oldValue Previous value.
     * @param newValue Updated value.
     * @param changedBy Administrator performing the update.
     * @param reason Optional explanation for the change.
     */
    private void createAuditRecord(String staffNumber, String entityName, String operation, String fieldChanged, String oldValue, String newValue, String changedBy, String reason) {

        //===========================================================
        // 1. CREATE AUDIT ENTITY
        //===========================================================

        AuditLog auditLog = new AuditLog();

        //===========================================================
        // 2. POPULATE AUDIT INFORMATION
        //===========================================================

        // Record the permanent staff number of the affected SDO.
        auditLog.setStaffNumber(staffNumber);

        // Record which entity was modified.
        auditLog.setEntityName(entityName);

        // Record the type of operation performed.
        auditLog.setOperation(operation);

        // Record which field changed.
        auditLog.setFieldChanged(fieldChanged);

        // Record the previous value.
        auditLog.setOldValue(oldValue);

        // Record the new value.
        auditLog.setNewValue(newValue);

        // Record the Administrator responsible for the change.
        auditLog.setChangedBy(changedBy);

        // Record the date and time of the change.
        auditLog.setChangedDate(LocalDateTime.now());

        // Record the reason for the change.
        auditLog.setReason(reason);

        //===========================================================
        // 3. SAVE THE AUDIT RECORD
        //===========================================================

        auditLogRepository.save(auditLog);

    }


    /**
     * Updates an existing Student Development Officer (SDO).
     *
     * Purpose:
     * Updates information stored in both the User table and the
     * Student Development Officer table.
     *
     * The Administrator first searches for an SDO using the
     * Search API. Once the information is displayed and edited,
     * the frontend submits the updated information to this method.
     *
     * Business Process:
     * 1. Retrieve the existing SDO.
     * 2. Retrieve the associated User.
     * 3. Validate the updated information.
     * 4. Update the User entity.
     * 5. Update the SDO entity.
     * 6. Save both entities.
     *
     * The entire operation executes inside a transaction to ensure
     * that both tables are updated successfully or rolled back if
     * an error occurs.
     *
     * @param currentStaffNumber The permanent staff number of the SDO.
     * @param request Contains the updated information.
     */
    @Transactional
    public void updateSDO(String currentStaffNumber, UpdateSDORequestDTO request) {

        //===========================================================
        // 1. FIND THE EXISTING STUDENT DEVELOPMENT OFFICER
        //===========================================================

        SDO existingSDO = sdoRepository.findById(currentStaffNumber)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Student Development Officer not found."));

        //===========================================================
        // 2. FIND THE ASSOCIATED USER
        //===========================================================

        User existingUser = userRepository.findById(existingSDO.getEmail())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Associated user not found."));

        //===========================================================
        // 3. VALIDATE UPDATED INFORMATION
        //===========================================================

        // If the email has changed, ensure another User does not already use it.

        if (!Objects.equals(existingUser.getEmail(), request.getEmail())) {

            if (userRepository.existsById(request.getEmail())) {

                throw new IllegalArgumentException("Email already exists.");
            }
        }

        //===========================================================
        // 4. UPDATE USER ENTITY
        //===========================================================

        //Email
        if (!Objects.equals(existingUser.getEmail(), request.getEmail())) {

            createAuditRecord(
                    existingSDO.getStaffNumber(),
                    "User",
                    "UPDATE",
                    "Email",
                    existingUser.getEmail(),
                    request.getEmail(),
                    "Administrator",
                    "Email address updated"
            );

            existingUser.setEmail(request.getEmail());
            existingSDO.setEmail(request.getEmail());
        }


        //Title
        if (!Objects.equals(existingUser.getTitle(), request.getTitle())) {

            createAuditRecord(
                    existingSDO.getStaffNumber(),
                    "User",
                    "UPDATE",
                    "Title",
                    existingUser.getTitle(),
                    request.getTitle(),
                    "Administrator",
                    "Profile information updated"
            );

            existingUser.setTitle(request.getTitle());
        }
        //First Name

        if (!Objects.equals(existingUser.getFirstName(), request.getFirstName())) {

            createAuditRecord(
                    existingSDO.getStaffNumber(),
                    "User",
                    "UPDATE",
                    "First Name",
                    existingUser.getFirstName(),
                    request.getFirstName(),
                    "Administrator",
                    "Profile information updated"
            );

            existingUser.setFirstName(request.getFirstName());
        }

        // Last Name
        if (!Objects.equals(existingUser.getLastName(), request.getLastName())) {

            createAuditRecord(
                    existingSDO.getStaffNumber(),
                    "User",
                    "UPDATE",
                    "Last Name",
                    existingUser.getLastName(),
                    request.getLastName(),
                    "Administrator",
                    "Profile information updated"
            );

            existingUser.setLastName(request.getLastName());
        }

        //Campus

        if (!Objects.equals(existingUser.getCampus(), request.getCampus())) {

            createAuditRecord(
                    existingSDO.getStaffNumber(),
                    "User",
                    "UPDATE",
                    "Campus",
                    existingUser.getCampus() == null ? null : existingUser.getCampus().name(),
                    request.getCampus() == null ? null : request.getCampus().name(),
                    "Administrator",
                    "Profile information updated"
            );

            existingUser.setCampus(request.getCampus());
        }

        //===========================================================
        // 5. UPDATE SDO ENTITY
        //===========================================================

        //Office Number
        if (!Objects.equals(existingSDO.getOfficeNumber(), request.getOfficeNumber())) {

            createAuditRecord(
                    existingSDO.getStaffNumber(),
                    "SDO",
                    "UPDATE",
                    "Office Number",
                    existingSDO.getOfficeNumber(),
                    request.getOfficeNumber(),
                    "Administrator",
                    "Office information updated"
            );

            existingSDO.setOfficeNumber(request.getOfficeNumber());
        }

        //Phone Extension
        if (!Objects.equals(existingSDO.getPhoneExtension(), request.getPhoneExtension())) {

            createAuditRecord(
                    existingSDO.getStaffNumber(),
                    "SDO",
                    "UPDATE",
                    "Phone Extension",
                    existingSDO.getPhoneExtension(),
                    request.getPhoneExtension(),
                    "Administrator",
                    "Office information updated"
            );

            existingSDO.setPhoneExtension(request.getPhoneExtension());
        }

        //===========================================================
        // 6. SAVE BOTH ENTITIES
        //===========================================================

        //Save the updated User record.

        userRepository.save(existingUser);

        // Save the updated Student Development Officer record.

        sdoRepository.save(existingSDO);
    }

    /**
     * Authenticates a user by email + password and issues a JWT on success.
     * Returns an expanded AuthResponse with dashboardType to drive frontend routing.
     */
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(), request.getPassword()));
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findById(request.getEmail())
                .orElseThrow(() -> new IllegalStateException(
                        "User not found after authentication"));

        if (user.getUserType() == UserType.SDO && !user.isEmailVerified()) {
            throw new BadCredentialsException("Please verify your email before signing in.");
        }

        Student student = studentRepository.findByEmail(user.getEmail()).orElse(null);
        String dashboardType = resolveDashboardType(user, student);
        String executivePosition = resolveExecutivePosition(student, dashboardType);
        String societyID = resolveSocietyID(student, dashboardType);

        return AuthResponse.builder()
                .token(jwtUtil.generateToken(user.getEmail(), user.getUserType().name()))
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .userType(user.getUserType().name())
                .dashboardType(dashboardType)
                .executivePosition(executivePosition)
                .societyID(societyID)
                .studentNumber(student != null ? student.getStudentNumber() : null)
                .faculty(student != null && student.getSchool() != null
                        ? student.getSchool().getFaculty().name() : null)
                .school(student != null && student.getSchool() != null
                        ? student.getSchool().name() : null)
                .campus(user.getCampus() != null ? user.getCampus().name() : null)
                .course(student != null ? student.getCourse() : null)
                .profilePictureURL(null)
                .hasProfilePicture(userProfilePictureRepository.existsById(user.getEmail()))
                .build();
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        PasswordResetToken token = resetTokenRepository.findByTokenAndUsedFalse(rawToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification link."));
        if (token.isExpired()) { resetTokenRepository.delete(token); throw new IllegalArgumentException("This verification link has expired."); }
        User user = userRepository.findById(token.getEmail()).orElseThrow(() -> new IllegalArgumentException("Account not found."));
        user.setEmailVerified(true);
        userRepository.save(user);
        token.setUsed(true);
        resetTokenRepository.save(token);
    }

    // ── Logout ─────────────────────────────────────────────────────────────────

    /**
     * Logs out the currently authenticated user.
     * <p>
     * Note: JWTs are stateless — the token remains technically valid until
     * it expires (see jwt.expiration-ms). This method does not revoke the
     * token server-side; the frontend is responsible for discarding it
     * (e.g. clearing it from memory/storage) so it is no longer sent on
     * subsequent requests.
     * <p>
     * We still expose this as a real endpoint (rather than handling logout
     * purely client-side) so that:
     *   1. The action is auditable / loggable server-side.
     *   2. If server-side token revocation (e.g. a blacklist table) is added
     *      later, this is the single place that logic will live — no
     *      frontend changes required.
     *
     * @param email The email of the user logging out, taken from the
     *              authenticated security context.
     */
    public void logout(String email) {
        log.info("User logged out: {}", email);
        // Future enhancement: persist token/email to a blacklist table here
        // if server-side revocation becomes a requirement.
    }

    // ── Forgot Password ───────────────────────────────────────────────────────

    /**
     * Issues a password reset token and sends a reset link by email.
     *
     * Security note: we always return the same success message regardless of
     * whether the email exists in the system. This prevents user enumeration
     *,  an attacker cannot tell which emails are registered by probing this
     * endpoint.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        userRepository.findById(email).ifPresent(user -> {
            // Invalidate any existing unused tokens for this email
            resetTokenRepository.deleteByEmail(email);

            PasswordResetToken token = PasswordResetToken.create(email);
            resetTokenRepository.save(token);

            String resetLink = baseUrl + "/reset-password?token=" + token.getToken();

            emailService.send(
                    EmailType.FORGOT_PASSWORD,
                    email,
                    Map.of(
                            "resetLink", resetLink,
                            "firstName", user.getFirstName()
                    )
            );
            log.info("Password reset token issued for {}", email);
        });
    }

    // ── Reset Password ────────────────────────────────────────────────────────

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = resetTokenRepository
                .findByTokenAndUsedFalse(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid or expired reset link. Please request a new one."));

        if (token.isExpired()) {
            resetTokenRepository.delete(token);
            throw new IllegalArgumentException(
                    "This reset link has expired. Please request a new one.");
        }

        User user = userRepository.findById(token.getEmail())
                .orElseThrow(() -> new IllegalStateException(
                        "User not found for this reset token."));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Mark token as used so it cannot be replayed
        token.setUsed(true);
        resetTokenRepository.save(token);

        log.info("Password reset successfully for {}", token.getEmail());
    }


    /**
     * Determines which dashboard the user should see after login.
     * <p>
     * Priority order:
     * 1. SDO       → always SDO dashboard
     * 2. EXECUTIVE → student who is a CURRENT executive of any society
     *                (termEndDate is null or in the future)
     * 3. STUDENT   → default student dashboard
     */

    private String resolveDashboardType(User user, Student student) {
        // Priority: ADMIN -> SDO -> EXECUTIVE -> STUDENT
        if (user.getUserType() == UserType.ADMIN) return "ADMIN";
        if (user.getUserType() == UserType.SDO) return "SDO";

        if (student != null) {
            boolean isCurrentExec = !executiveRepository
                    .findActiveExecutiveRoles(
                            student.getStudentNumber(), LocalDate.now())
                    .isEmpty();
            if (isCurrentExec) return "EXECUTIVE";
        }
        return "STUDENT";
    }

    /**
     * Returns the position string of the first active executive role,
     * or null if the user is not an executive.
     * Frontend uses this (case-insensitively) to control sidebar visibility.
     */
    private String resolveExecutivePosition(Student student, String dashboardType) {
        if (!"EXECUTIVE".equals(dashboardType) || student == null) return null;

        return executiveRepository
                .findActiveExecutiveRoles(
                        student.getStudentNumber(), LocalDate.now())
                .stream()
                .findFirst()
                .map(e -> e.getPosition())
                .orElse(null);
    }

    /**
     * Returns the societyID of the first active executive role,
     * or null if the user is not an executive.
     * Used by the frontend for executive-scoped actions
     * (e.g. budget requests, announcements).
     */
    private String resolveSocietyID(Student student, String dashboardType) {
        if (!"EXECUTIVE".equals(dashboardType) || student == null) return null;

        return executiveRepository
                .findByIdStudentNumber(student.getStudentNumber())
                .stream()
                .filter(e -> e.getTermEndDate() == null
                        || e.getTermEndDate().isAfter(LocalDate.now()))
                .findFirst()
                .map(e -> e.getId().getSocietyID())
                .orElse(null);
    }

}

