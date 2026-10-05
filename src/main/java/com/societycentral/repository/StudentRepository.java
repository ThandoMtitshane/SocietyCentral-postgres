package com.societycentral.repository;

import com.societycentral.model.School;
import com.societycentral.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, String> {
    // ID type = String (studentNumber)

    Optional<Student> findByEmail(String email);

    // for each school in that faculty, or use a @Query with school enum
    // values for the desired faculty.
    List<Student> findBySchool(School school);

    List<Student> findByCourse(String course);

    @Modifying(flushAutomatically = true)
    @Query("update Student s set s.email = :newEmail where s.email = :oldEmail")
    int reassignEmail(@Param("oldEmail") String oldEmail,
                      @Param("newEmail") String newEmail);
}
