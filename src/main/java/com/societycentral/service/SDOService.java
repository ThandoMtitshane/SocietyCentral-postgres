package com.societycentral.service;

import com.societycentral.dto.response.SocietySummaryDTO;
import com.societycentral.model.SDO;
import com.societycentral.repository.SDORepository;
import com.societycentral.repository.SocietyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SDOService {

    private final SDORepository sdoRepository;
    private final SocietyRepository societyRepository;

    @Autowired
    public SDOService(SDORepository sdoRepository, SocietyRepository societyRepository) {
        this.sdoRepository = sdoRepository;
        this.societyRepository = societyRepository;
    }

    public List<SDO> findAll() {
        return sdoRepository.findAll();
    }

    public Optional<SDO> findByStaffNumber(String staffNumber) {
        return sdoRepository.findById(staffNumber);
    }

    public Optional<SDO> findByEmail(String email) {
        return sdoRepository.findByEmail(email);
    }

    public SDO save(SDO sdo) {
        // TODO: BUSINESS RULE - "Only authorised Student Development Officers
        // may register or update societies and executives."
        // This service handles SDO record CRUD itself. The AUTHORISATION
        // check (i.e. "is the currently logged-in user an SDO?") belongs in
        // SocietyService/ExecutiveService methods that create/update
        // societies and executives - those methods should verify the caller
        // is a valid SDO before proceeding.
        return sdoRepository.save(sdo);
    }

    public void deleteByStaffNumber(String staffNumber) {
        sdoRepository.deleteById(staffNumber);
    }

    /**
     * Returns the societies supervised by the given SDO email.
     * Used to populate society-selection UI, e.g. announcement targeting.
     */
    public List<SocietySummaryDTO> getSupervisedSocieties(String email) {

        SDO sdo = sdoRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("SDO not found."));

        return societyRepository.findBySdoStaffNumber(sdo.getStaffNumber())
                .stream()
                .map(society -> {
                    SocietySummaryDTO dto = new SocietySummaryDTO();
                    dto.setSocietyID(society.getSocietyID());
                    dto.setSocietyName(society.getSocietyName());
                    return dto;
                })
                .toList();
    }
}
