package com.societycentral.service;

import com.societycentral.dto.request.SocietyRequestDTO;
import com.societycentral.model.SDO;
import com.societycentral.model.Society;
import com.societycentral.repository.EventRepository;
import com.societycentral.repository.POARepository;
import com.societycentral.repository.SDORepository;
import com.societycentral.repository.SocietyMemberRepository;
import com.societycentral.repository.SocietyRepository;
import com.societycentral.repository.TaskAllocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocietySocialLinkValidationTests {

    private static final String SDO_EMAIL = "sdo@nmu.ac.za";

    @Mock
    private SocietyRepository societyRepository;
    @Mock
    private SDORepository sdoRepository;
    @Mock
    private FundTransactionService fundTransactionService;
    @Mock
    private POARepository poaRepository;
    @Mock
    private TaskAllocationRepository taskAllocationRepository;
    @Mock
    private SocietyMemberRepository societyMemberRepository;
    @Mock
    private EventRepository eventRepository;

    private SocietyManagementService service;

    @BeforeEach
    void setUp() {
        service = new SocietyManagementService(
                societyRepository,
                sdoRepository,
                fundTransactionService,
                poaRepository,
                taskAllocationRepository,
                societyMemberRepository,
                eventRepository);
        SDO sdo = new SDO();
        sdo.setStaffNumber("SDO001");
        when(sdoRepository.findByEmail(SDO_EMAIL))
                .thenReturn(Optional.of(sdo));
    }

    @Test
    void registrationNormalisesBlankSocialLinksAndTrimsValidUrls() {
        when(societyRepository.findAll()).thenReturn(List.of());
        when(societyRepository.count()).thenReturn(0L);
        when(societyRepository.findById("SOC001"))
                .thenReturn(Optional.empty());
        SocietyRequestDTO request = request();
        request.setFacebookURL(" https://facebook.com/computing ");
        request.setInstagramURL("   ");
        request.setTiktokURL("https://www.tiktok.com/@computing");

        service.registerSociety(request, SDO_EMAIL);

        ArgumentCaptor<Society> captor =
                ArgumentCaptor.forClass(Society.class);
        verify(societyRepository).save(captor.capture());
        Society saved = captor.getValue();
        assertEquals("https://facebook.com/computing",
                saved.getFacebookURL());
        assertNull(saved.getInstagramURL());
        assertEquals("https://www.tiktok.com/@computing",
                saved.getTiktokURL());
    }

    @Test
    void registrationRejectsNonHttpSocialLinks() {
        when(societyRepository.findAll()).thenReturn(List.of());
        SocietyRequestDTO request = request();
        request.setFacebookURL("javascript:alert(1)");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.registerSociety(request, SDO_EMAIL));

        assertEquals(
                "Social media links must be valid HTTP or HTTPS URLs.",
                exception.getMessage());
    }

    @Test
    void updateRejectsMalformedSocialLinks() {
        Society existing = new Society();
        existing.setSocietyID("SOC001");
        existing.setSocietyName("Computing Society");
        when(societyRepository.findById("SOC001"))
                .thenReturn(Optional.of(existing));
        when(societyRepository.findAll())
                .thenReturn(List.of(existing));
        SocietyRequestDTO request = request();
        request.setInstagramURL("https://");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateSociety(
                        "SOC001", request, SDO_EMAIL));

        assertEquals(
                "Social media links must be valid HTTP or HTTPS URLs.",
                exception.getMessage());
    }

    private SocietyRequestDTO request() {
        SocietyRequestDTO request = new SocietyRequestDTO();
        request.setSocietyName("Computing Society");
        return request;
    }
}
