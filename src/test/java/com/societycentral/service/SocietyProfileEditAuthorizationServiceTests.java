package com.societycentral.service;

import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.model.Executive;
import com.societycentral.model.ExecutiveId;
import com.societycentral.model.Society;
import com.societycentral.model.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocietyProfileEditAuthorizationServiceTests {

    private static final String EMAIL = "executive@nmu.ac.za";
    private static final String SOCIETY_ID = "SOC001";

    @Mock
    private ExecutiveSocietyResolver executiveSocietyResolver;

    private SocietyProfileEditAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new SocietyProfileEditAuthorizationService(
                executiveSocietyResolver);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PRESIDENT",
            "President",
            " president ",
            "SECRETARY",
            "Secretary"
    })
    void presidentAndSecretaryCanEditWithLegacyCasing(String position) {
        when(executiveSocietyResolver.resolve(EMAIL))
                .thenReturn(context(position));
        when(executiveSocietyResolver.resolve(EMAIL, SOCIETY_ID))
                .thenReturn(context(position));

        assertTrue(service.canEditSocietyProfile(EMAIL, SOCIETY_ID));
        assertEquals(SOCIETY_ID,
                service.requireCanEditOwnSociety(EMAIL)
                        .society().getSocietyID());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DEPUTY_PRESIDENT",
            "Deputy President",
            "DEPUTY-SECRETARY",
            "Treasurer",
            "PRO",
            "Legacy Chair"
    })
    void otherPositionsRemainReadOnly(String position) {
        when(executiveSocietyResolver.resolve(EMAIL))
                .thenReturn(context(position));
        when(executiveSocietyResolver.resolve(EMAIL, SOCIETY_ID))
                .thenReturn(context(position));

        assertFalse(service.canEditSocietyProfile(EMAIL, SOCIETY_ID));
        ForbiddenOperationException exception = assertThrows(
                ForbiddenOperationException.class,
                () -> service.requireCanEditOwnSociety(EMAIL));
        assertEquals(
                "Only the President or Secretary may edit the society profile.",
                exception.getMessage());
    }

    @Test
    void presidentCannotEditAnotherSociety() {
        when(executiveSocietyResolver.resolve(EMAIL, "SOC002"))
                .thenThrow(new ForbiddenOperationException(
                        "Authenticated user is not an active society executive."));

        assertFalse(service.canEditSocietyProfile(EMAIL, "SOC002"));
        ForbiddenOperationException exception = assertThrows(
                ForbiddenOperationException.class,
                () -> service.requireCanEditSocietyProfile(
                        EMAIL, "SOC002"));
        assertEquals(
                "You are not authorised to edit this society profile.",
                exception.getMessage());
    }

    @Test
    void ordinaryStudentCannotEdit() {
        when(executiveSocietyResolver.resolve(EMAIL))
                .thenThrow(new ForbiddenOperationException(
                        "Authenticated user is not an active society executive."));
        when(executiveSocietyResolver.resolve(EMAIL, SOCIETY_ID))
                .thenThrow(new ForbiddenOperationException(
                        "Authenticated user is not an active society executive."));

        assertFalse(service.canEditSocietyProfile(EMAIL, SOCIETY_ID));
        ForbiddenOperationException exception = assertThrows(
                ForbiddenOperationException.class,
                () -> service.requireCanEditOwnSociety(EMAIL));
        assertEquals(
                "You are not authorised to edit this society profile.",
                exception.getMessage());
    }

    private ExecutiveSocietyResolver.ActiveExecutiveSociety context(
            String position) {
        Student student = new Student();
        student.setStudentNumber("220000001");
        student.setEmail(EMAIL);

        Society society = new Society();
        society.setSocietyID(SOCIETY_ID);
        society.setActiveStatus(true);

        Executive role = new Executive();
        role.setId(new ExecutiveId(
                student.getStudentNumber(),
                SOCIETY_ID,
                LocalDate.of(2030, 1, 1)));
        role.setStudent(student);
        role.setSociety(society);
        role.setPosition(position);

        return new ExecutiveSocietyResolver.ActiveExecutiveSociety(
                student, society, role);
    }
}
