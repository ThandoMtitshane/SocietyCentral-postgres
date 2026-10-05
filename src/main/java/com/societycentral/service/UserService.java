package com.societycentral.service;

import com.societycentral.model.User;
import com.societycentral.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findById(email);
    }

    public User save(User user) {
        // TODO: AUTH - password hashing belongs here. (for register)
        // Before saving, passwordHash must be hashed via Spring Security's
        // PasswordEncoder (e.g. BCryptPasswordEncoder) - never store plain text.
        // This service should not receive a raw password directly from a
        // controller; that conversion happens in AuthService / a dedicated
        // registration flow which then calls this save().
        return userRepository.save(user);
    }

    public void deleteByEmail(String email) {
        userRepository.deleteById(email);
    }

    // TODO: AUTH - login/authentication itself (verifying password against
    // passwordHash, issuing JWT/session token) belongs in AuthService, not here.
    // This service stays focused on User CRUD only.
}
