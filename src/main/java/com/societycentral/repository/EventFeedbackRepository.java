package com.societycentral.repository;

import com.societycentral.model.EventFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventFeedbackRepository extends JpaRepository<EventFeedback, String> {
    // ID type = String (feedbackID)

    List<EventFeedback> findByEventID(String eventID);

    List<EventFeedback> findByStudentNumber(String studentNumber);

    // Useful for computing Event.overallRating
    boolean existsByEventIDAndStudentNumber(String eventID, String studentNumber);
    Optional<EventFeedback> findByEventIDAndStudentNumber(String eventID, String studentNumber);
}
