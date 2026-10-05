package com.societycentral.config;

import com.societycentral.model.User;
import com.societycentral.model.UserType;
import com.societycentral.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * On startup, ensure a default administrator account exists for local/dev use.
 * The account uses the existing PasswordEncoder to store the hashed password.
 */
@Component
@Order(100)
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        final String adminEmail = "admin@societycentral.com";
        final String adminPassword = "DevPassword123";

        if (!userRepository.existsById(adminEmail)) {
            User admin = new User();
            admin.setEmail(adminEmail);
            admin.setFirstName("Admin");
            admin.setLastName("User");
            admin.setTitle("Mr");
            admin.setUserType(UserType.ADMIN);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            userRepository.save(admin);
            System.out.println("Default admin created: " + adminEmail);
        }
    }
}
