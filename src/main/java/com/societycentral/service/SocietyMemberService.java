package com.societycentral.service;

import com.societycentral.model.SocietyMember;
import com.societycentral.model.SocietyMemberId;
import com.societycentral.dto.response.ExecutiveMemberListItemDTO;
import com.societycentral.service.ExecutiveSocietyResolver.ActiveExecutiveSociety;
import com.societycentral.repository.SocietyMemberRepository;
import com.societycentral.repository.UserProfilePictureRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.time.Clock;
import java.time.LocalDate;

@Service
public class SocietyMemberService {

    private final SocietyMemberRepository societyMemberRepository;
    private final ExecutiveSocietyResolver executiveSocietyResolver;
    private final Clock clock;
    private final UserProfilePictureRepository userProfilePictureRepository;

    @Autowired
    public SocietyMemberService(SocietyMemberRepository societyMemberRepository,
                                ExecutiveSocietyResolver executiveSocietyResolver,
                                Clock clock,
                                UserProfilePictureRepository userProfilePictureRepository) {
        this.societyMemberRepository = societyMemberRepository;
        this.executiveSocietyResolver = executiveSocietyResolver;
        this.clock = clock;
        this.userProfilePictureRepository = userProfilePictureRepository;
    }

    public List<SocietyMember> findAll() {
        return societyMemberRepository.findAll();
    }

    public Optional<SocietyMember> findById(SocietyMemberId id) {
        return societyMemberRepository.findById(id);
    }

    public List<SocietyMember> findSocietiesForStudent(String studentNumber) {
        return societyMemberRepository.findByIdStudentNumber(studentNumber);
    }

    public List<SocietyMember> findMembersOfSociety(String societyID) {
        return societyMemberRepository.findByIdSocietyID(societyID);
    }

    public SocietyMember joinSociety(SocietyMember membership) {
        // REMEMBER: joining requires the society to be active
        // (Society.activeStatus == true) - confirm with business rules
        //
        //
        // TODO: Society.numberOfMembers is a denormalised count. When a
        // membership is added, increment Society.numberOfMembers
        // (inject SocietyRepository/SocietyService, or handle via a
        // database trigger - decide on one approach and document it).
        return societyMemberRepository.save(membership);
    }

    public List<ExecutiveMemberListItemDTO> findCurrentMembersForExecutive(String email) {
        ActiveExecutiveSociety context = executiveSocietyResolver.resolve(email);
        LocalDate today = LocalDate.now(clock);
        return societyMemberRepository.findCurrentMembersBySocietyID(context.society().getSocietyID(), today)
                .stream().map(member -> ExecutiveMemberListItemDTO.builder()
                        .studentNumber(member.getStudent().getStudentNumber())
                        .firstName(member.getStudent().getUser().getFirstName())
                        .lastName(member.getStudent().getUser().getLastName())
                        .email(member.getStudent().getEmail())
                        .profilePictureURL(member.getStudent().getUser().getProfilePictureURL())
                        .hasProfilePicture(userProfilePictureRepository.existsById(member.getStudent().getEmail()))
                        .course(member.getStudent().getCourse())
                        .level(member.getStudent().getLevel())
                        .campus(member.getStudent().getUser().getCampus() == null ? null : member.getStudent().getUser().getCampus().name())
                        .joinDate(member.getJoinDate()).expireDate(member.getExpireDate()).build()).toList();
    }

    @org.springframework.transaction.annotation.Transactional
    public void removeMemberForExecutive(String email, String studentNumber) {
        ActiveExecutiveSociety context = executiveSocietyResolver.resolve(email);
        SocietyMemberId id = new SocietyMemberId(studentNumber, context.society().getSocietyID());
        SocietyMember member = societyMemberRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Member not found."));
        member.setExpireDate(LocalDate.now(clock));
        societyMemberRepository.save(member);
    }
    // not sure if we need this one but just in case.
    public void leaveSociety(SocietyMemberId id) {
        // TODO: mirror of joinSociety - decrement Society.numberOfMembers
        // if using the denormalised-counter approach.
        societyMemberRepository.deleteById(id);
    }
}
