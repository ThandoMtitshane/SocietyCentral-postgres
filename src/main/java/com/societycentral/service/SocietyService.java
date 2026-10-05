package com.societycentral.service;

import com.societycentral.dto.response.SocietyResponseDTO;
import com.societycentral.dto.response.SocietySummaryDTO;
import com.societycentral.model.Society;
import com.societycentral.repository.SocietyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class SocietyService {

    private final SocietyRepository societyRepository;

    @Autowired
    public SocietyService(SocietyRepository societyRepository) {
        this.societyRepository = societyRepository;
    }

    /**
     * Retrieves every society stored in the database.
     *
     * This method includes both active and inactive societies and should
     * therefore mainly be used for authorised SDO management operations.
     *
     * @return all registered societies.
     */
    public List<Society> findAll() {
        return societyRepository.findAll();
    }

    /**
     * Retrieves all societies currently marked as active.
     *
     * BUSINESS RULE:
     * A society must be designated as active before students are allowed
     * to browse it or view its public profile.
     *
     * Student-facing browsing endpoints should use this method instead
     * of findAll().
     *
     * @return all active societies.
     */
    public List<Society> findAllActive() {
        return societyRepository.findByActiveStatusTrue();
    }

    /**
     * Searches for a society using its unique society identifier.
     *
     * This method does not enforce the active-status business rule.
     * It may therefore be used by authorised management functionality
     * that needs access to both active and inactive societies.
     *
     * @param societyID unique identifier of the society.
     * @return Optional containing the society when found.
     */
    public Optional<Society> findById(String societyID) {
        return societyRepository.findById(societyID);
    }

    /**
     * Retrieves societies belonging to a particular faculty.
     *
     * @param faculty faculty used to filter the societies.
     * @return societies associated with the specified faculty.
     */
    public List<Society> findByFaculty(String faculty) {
        return societyRepository.findByFaculty(faculty);
    }

    /**
     * Retrieves societies belonging to a particular school.
     *
     * @param school school used to filter the societies.
     * @return societies associated with the specified school.
     */
    public List<Society> findBySchool(String school) {
        return societyRepository.findBySchool(school);
    }

    /**
     * Retrieves societies operating on a particular campus.
     *
     * @param campus campus used to filter the societies.
     * @return societies associated with the specified campus.
     */
    public List<Society> findByCampus(String campus) {
        return societyRepository.findByCampus(campus);
    }

    /**
     * Retrieves compact details of all active societies for A500:
     * Browse Societies.
     *
     * Each Society entity is converted into a SocietySummaryDTO so that
     * the browsing page receives only the information required to display
     * its society cards.
     *
     * BUSINESS RULE:
     * Inactive societies must not appear on the student browsing page.
     *
     * @return compact representations of all active societies.
     */
    public List<SocietySummaryDTO> getActiveSocietySummaries() {

        return societyRepository.findByActiveStatusTrue()
                .stream()
                .map(this::convertToSummaryDTO)
                .toList();
    }

    /**
     * Retrieves the complete public profile of one active society.
     *
     * BUSINESS RULE:
     * Students may only open the profiles of active societies. An inactive
     * society is treated as unavailable even when its identifier exists.
     *
     * @param societyID unique identifier of the requested society.
     * @return complete details of the active society.
     * @throws ResponseStatusException when the society does not exist or
     *                                 is not currently active.
     */
    public SocietyResponseDTO getActiveSocietyDetails(String societyID) {

        Society society = societyRepository.findById(societyID)
                .filter(foundSociety ->
                        Boolean.TRUE.equals(foundSociety.getActiveStatus()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active society with ID " + societyID + " was not found."
                ));

        return convertToResponseDTO(society);
    }

    /**
     * Registers a new society.
     *
     * @param society society information to be saved.
     * @param requestingSdoEmail email address of the SDO making the request.
     * @return newly registered society.
     */
    public Society registerSociety(
            Society society,
            String requestingSdoEmail
    ) {

        // TODO: BUSINESS RULE - "Only authorised Student Development Officers
        // may register or update societies."
        //
        // 1. Verify that requestingSdoEmail belongs to a valid SDO by
        //    injecting SDORepository or SDOService.
        //
        // 2. Society.sdoStaffNumber must be set to the staff number of the
        //    authorised SDO because every society must have a registering SDO.
        //
        // 3. Confirm whether newly registered societies must initially have
        //    activeStatus = false until they have been formally activated.

        return societyRepository.save(society);
    }

    /**
     * Updates an existing society.
     *
     * @param society society containing the updated information.
     * @param requestingSdoEmail email address of the SDO making the request.
     * @return updated society.
     */
    public Society updateSociety(
            Society society,
            String requestingSdoEmail
    ) {

        // TODO: BUSINESS RULE - Apply the same authorisation validation used
        // during registration.
        //
        // Confirm whether only the SDO assigned to the society may update it,
        // or whether any authorised SDO may perform the update.

        return societyRepository.save(society);
    }

    /**
     * Deletes a society using its unique identifier.
     *
     * Authorisation and existence validation should be performed before this
     * method is exposed through a controller.
     *
     * @param societyID unique identifier of the society to delete.
     */
    public void deleteById(String societyID) {
        societyRepository.deleteById(societyID);
    }

    /**
     * Converts a Society entity into the compact representation required
     * by society cards on the Browse Societies page.
     *
     * Keeping the mapping in one helper method prevents duplicate mapping
     * logic across service methods.
     *
     * @param society society entity retrieved from the database.
     * @return compact society summary.
     */
    private SocietySummaryDTO convertToSummaryDTO(Society society) {

        return new SocietySummaryDTO(
                society.getSocietyID(),
                society.getName(),
                society.getDescription(),
                society.getSocietyType() == null
                        ? null
                        : society.getSocietyType().name(),
                society.getLogoUrl()
        );
    }

    /**
     * Converts a Society entity into the complete response representation.
     *
     * This DTO supports the full society profile and existing SDO society
     * management functionality.
     *
     * @param society society entity retrieved from the database.
     * @return complete society response.
     */
    private SocietyResponseDTO convertToResponseDTO(Society society) {

        return SocietyResponseDTO.builder()
                .societyID(society.getSocietyID())
                .societyName(society.getName())
                .acronym(society.getAcronym())
                .societyType(String.valueOf(society.getSocietyType()))
                .faculty(String.valueOf(society.getFaculty()))
                .school(String.valueOf(society.getSchool()))
                .description(society.getDescription())
                .vision(society.getVision())
                .mission(society.getMission())
                .yearEstablished(society.getYearEstablished())
                .contactNumber(society.getContactNumber())
                .email(society.getEmail())
                .logoUrl(society.getLogoUrl())
                .bannerUrl(society.getBannerUrl())
                .facebookURL(society.getFacebookURL())
                .instagramURL(society.getInstagramURL())
                .tiktokURL(society.getTiktokURL())
                .campus(String.valueOf(society.getCampus()))
                .activeStatus(society.getActiveStatus())
                .isFlagged(society.getIsFlagged())
                .numberOfMembers(society.getNumberOfMembers())
                .sdoStaffNumber(society.getSdoStaffNumber())
                .membershipFee(society.getMembershipFee())
                .annualBudgetAllocation(
                        society.getAnnualBudgetAllocation()
                )
                .currentBalance(society.getCurrentBalance())
                .build();
    }

    // TODO: RECOMMENDATION SYSTEM
    //
    // Queries for finding societies joined or browsed by similar students
    // belong in RecommendationService rather than SocietyService.
    //
    // RecommendationService will eventually use SocietyRepository together
    // with SocietyMemberRepository, RSVPRepository and a future
    // BrowsingLogRepository.
    //
    // The recommendation functionality still needs to be discussed and
    // confirmed with the team.
}
