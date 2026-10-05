package com.societycentral.repository;

import com.societycentral.model.POAEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface POAEventRepository extends JpaRepository<POAEvent, String> {

    List<POAEvent> findByPoaIDOrderBySortOrderAsc(String poaID);
    void deleteByPoaID(String poaID);
        List<POAEvent> findByPoaID(String poaID);
}