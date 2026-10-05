package com.societycentral.repository;

import com.societycentral.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    // ID type = String (email)

    /**
     * Checks whether a user with the specified email address already exists.
     *
     * @param email the email address to check
     * @return true if a user with the email exists, otherwise false
     */
    boolean existsByEmail(String email);

}


