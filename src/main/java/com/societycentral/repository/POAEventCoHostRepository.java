package com.societycentral.repository;

import com.societycentral.model.POAEventCoHost;
import com.societycentral.model.POACoHostStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface POAEventCoHostRepository extends JpaRepository<POAEventCoHost, String> {
    List<POAEventCoHost> findByPoaEventID(String poaEventID);
    List<POAEventCoHost> findByInvitedSocietyIDAndStatus(
            String societyID, POACoHostStatus status);

    /** Used when deleting a POAEvent,  must clear FK children first */
    void deleteByPoaEventID(String poaEventID);
}