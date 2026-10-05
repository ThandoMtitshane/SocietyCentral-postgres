package com.societycentral.repository;

import com.societycentral.model.UserProfilePicture;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserProfilePictureRepository extends JpaRepository<UserProfilePicture, String> {
    Optional<UserProfilePicture> findByUserEmail(String email);
}
