package com.societycentral.service;

import com.societycentral.model.*;
import com.societycentral.repository.*;
import com.societycentral.dto.response.AcademicReferenceResponses;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service @Transactional(readOnly = true)
public class AcademicReferenceService {
    private final FacultyReferenceRepository faculties; private final CampusReferenceRepository campuses;
    private final SchoolReferenceRepository schools; private final ProgrammeReferenceRepository programmes;
    private final ProgrammeCampusRepository programmeCampuses;
    private final AccommodationTypeRepository accommodationTypes; private final ResidenceReferenceRepository residences; private final OffCampusPropertyRepository properties; private final OffCampusAccreditationRepository accreditations;
    public AcademicReferenceService(FacultyReferenceRepository f, CampusReferenceRepository c, SchoolReferenceRepository s, ProgrammeReferenceRepository p, ProgrammeCampusRepository pc, AccommodationTypeRepository at, ResidenceReferenceRepository rr, OffCampusPropertyRepository op, OffCampusAccreditationRepository oa) { faculties=f; campuses=c; schools=s; programmes=p; programmeCampuses=pc; accommodationTypes=at; residences=rr; properties=op; accreditations=oa; }
    public List<AcademicReferenceResponses.AccommodationType> accommodationTypes(){return accommodationTypes.findByActiveTrueOrderByAccommodationTypeIDAsc().stream().map(x->new AcademicReferenceResponses.AccommodationType(x.getAccommodationTypeID(),codeFor(x.getAccommodationTypeID()),nameFor(x.getAccommodationTypeID()))).toList();}
    private String codeFor(Integer id){ return switch(id == null ? 0 : id){case 1 -> "ON_CAMPUS"; case 2 -> "ACCREDITED_OFF_CAMPUS"; case 3 -> "PRIVATE_OFF_CAMPUS"; case 4 -> "HOME"; case 5 -> "OTHER"; default -> "";}; }
    private String nameFor(Integer id){ return switch(id == null ? 0 : id){case 1 -> "On Campus"; case 2 -> "NMU Accredited Off Campus"; case 3 -> "Private / Non-accredited Off Campus"; case 4 -> "Living at Home"; case 5 -> "Other"; default -> "Other";}; }
    public List<AcademicReferenceResponses.Residence> residences(String campus){return residences.findByCampusCodeAndActiveTrueOrderByResidenceNameAsc(campus).stream().map(x->new AcademicReferenceResponses.Residence(x.getResidenceID(),x.getResidenceName(),x.getFormerName(),x.getCampusCode())).toList();}
    public List<AcademicReferenceResponses.Property> accreditedProperties(){return accreditations.findByAcademicYearAndAccreditedTrue((short)2021).stream().map(x->x.getProperty()).filter(OffCampusProperty::isActive).map(x->new AcademicReferenceResponses.Property(x.getOffCampusPropertyID(),x.getPropertyName(),null)).distinct().toList();}
    public List<AcademicReferenceResponses.Faculty> faculties(){return faculties.findByActiveTrueOrderByFacultyNameAsc().stream().map(f->new AcademicReferenceResponses.Faculty(f.getFacultyCode(),f.getFacultyName())).toList();}
    public List<AcademicReferenceResponses.Campus> campuses(){return campuses.findByActiveTrueOrderByCampusNameAsc().stream().map(c->new AcademicReferenceResponses.Campus(c.getCampusCode(),c.getCampusName(),c.getCity(),c.isHasOnCampusResidence())).toList();}
    public List<AcademicReferenceResponses.School> schools(String code){return schools.findByFacultyFacultyCodeAndActiveTrueOrderBySchoolNameAsc(code).stream().map(s->new AcademicReferenceResponses.School(s.getSchoolCode(),s.getSchoolName())).toList();}
    public List<AcademicReferenceResponses.Programme> programmes(){
        List<ProgrammeReference> references =
                programmes.findByActiveTrueOrderByProgrammeNameAsc();
        Map<String, List<ProgrammeCampus>> campusesByProgramme =
                programmeCampusesByProgramme(references);
        return references.stream()
                .map(p -> programme(p, campusesByProgramme))
                .toList();
    }
    public AcademicReferenceResponses.Programme programme(String code){
        ProgrammeReference reference = programmes.findByProgrammeCode(code)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown programmeCode: " + code));
        return programme(reference,
                Map.of(reference.getProgrammeCode(),
                        programmeCampuses.findByProgrammeProgrammeCode(code)));
    }
    public List<AcademicReferenceResponses.Campus> programmeCampuses(String code){
        return mapCampuses(programmeCampuses.findByProgrammeProgrammeCode(code));
    }
    private Map<String, List<ProgrammeCampus>> programmeCampusesByProgramme(
            List<ProgrammeReference> references) {
        if (references.isEmpty()) {
            return Map.of();
        }
        return programmeCampuses.findByProgrammeProgrammeCodeIn(
                        references.stream()
                                .map(ProgrammeReference::getProgrammeCode)
                                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.groupingBy(
                        x -> x.getProgramme().getProgrammeCode()));
    }
    private AcademicReferenceResponses.Programme programme(
            ProgrammeReference p,
            Map<String, List<ProgrammeCampus>> campusesByProgramme) {
        return new AcademicReferenceResponses.Programme(
                p.getProgrammeCode(), p.getProgrammeName(),
                p.getQualificationLevel(),
                new AcademicReferenceResponses.Faculty(
                        p.getFaculty().getFacultyCode(),
                        p.getFaculty().getFacultyName()),
                p.getSchool() == null ? null
                        : new AcademicReferenceResponses.School(
                                p.getSchool().getSchoolCode(),
                                p.getSchool().getSchoolName()),
                mapCampuses(campusesByProgramme.getOrDefault(
                        p.getProgrammeCode(), List.of())));
    }
    private List<AcademicReferenceResponses.Campus> mapCampuses(
            List<ProgrammeCampus> mappings) {
        return mappings.stream()
                .map(x -> new AcademicReferenceResponses.Campus(
                        x.getCampus().getCampusCode(),
                        x.getCampus().getCampusName(),
                        x.getCampus().getCity(),
                        x.getCampus().isHasOnCampusResidence()))
                .toList();
    }
}
