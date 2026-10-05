package com.societycentral.service;

import com.societycentral.dto.request.UpdateSDORequestDTO;
import com.societycentral.model.Campus;
import com.societycentral.model.SDO;
import com.societycentral.model.User;
import com.societycentral.model.UserType;
import com.societycentral.repository.AnnouncementRepository;
import com.societycentral.repository.NotificationRepository;
import com.societycentral.repository.PasswordResetTokenRepository;
import com.societycentral.repository.SDORepository;
import com.societycentral.repository.StudentRepository;
import com.societycentral.repository.TaskRepository;
import com.societycentral.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTests {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SDORepository sdoRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private AnnouncementRepository announcementRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void updateSDOMigratesAccountWhenEmailChanges() {
        String oldEmail = "old@nmu.ac.za";
        String newEmail = "new@nmu.ac.za";

        User existingUser = new User();
        existingUser.setEmail(oldEmail);
        existingUser.setUserType(UserType.SDO);
        existingUser.setPasswordHash("stored-password-hash");
        existingUser.setProfilePictureURL("profile.png");

        SDO sdo = new SDO();
        sdo.setStaffNumber("SDO001");
        sdo.setEmail(oldEmail);

        UpdateSDORequestDTO request = new UpdateSDORequestDTO();
        request.setEmail(newEmail);
        request.setTitle("Dr");
        request.setFirstName("Updated");
        request.setLastName("Officer");
        request.setCampus(Campus.SOUTH_CAMPUS);
        request.setOfficeNumber("B101");
        request.setPhoneExtension("1234");

        when(sdoRepository.findById("SDO001")).thenReturn(Optional.of(sdo));
        when(userRepository.findById(oldEmail)).thenReturn(Optional.of(existingUser));
        when(sdoRepository.existsByPhoneExtensionAndStaffNumberNot("1234", "SDO001"))
                .thenReturn(false);
        when(userRepository.existsById(newEmail)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        adminService.updateSDO("SDO001", request);

        ArgumentCaptor<User> replacementCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(replacementCaptor.capture());
        User replacement = replacementCaptor.getValue();

        assertNotSame(existingUser, replacement);
        assertEquals(newEmail, replacement.getEmail());
        assertEquals("stored-password-hash", replacement.getPasswordHash());
        assertEquals(UserType.SDO, replacement.getUserType());
        assertEquals("Updated", replacement.getFirstName());
        assertEquals(newEmail, sdo.getEmail());
        assertEquals(replacement, sdo.getUser());
        assertEquals(oldEmail, existingUser.getEmail());

        verify(studentRepository).reassignEmail(oldEmail, newEmail);
        verify(taskRepository).reassignAssignedByEmail(oldEmail, newEmail);
        verify(announcementRepository).reassignSenderEmail(oldEmail, newEmail);
        verify(notificationRepository).reassignRecipientEmail(oldEmail, newEmail);
        verify(passwordResetTokenRepository).reassignEmail(oldEmail, newEmail);
        verify(sdoRepository).saveAndFlush(sdo);
        verify(userRepository).delete(existingUser);
        verify(userRepository).flush();
    }
}
