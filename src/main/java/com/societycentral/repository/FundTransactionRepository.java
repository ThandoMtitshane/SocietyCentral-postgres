package com.societycentral.repository;

import com.societycentral.model.FundTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FundTransactionRepository extends JpaRepository<FundTransaction, String> {

    // Full transaction history for a society, newest first
    List<FundTransaction> findBySocietyIDOrderByTransactionDateDesc(String societyID);

    // Most recent N transactions - used in dashboard summary
    // (use Pageable in the service layer for limiting)
}