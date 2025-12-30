package com.example.jutjubic.controllers;

import com.example.jutjubic.models.User;
import com.example.jutjubic.repositories.UserRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/users")
class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping
    public User createUser(@Valid @RequestBody User user){
        user.setCreated_at(LocalDateTime.now());
        user.setUpdated_at(LocalDateTime.now());
        return userRepository.save(user);
    }
    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    @GetMapping
    public Iterable<User> getAllUsers(){
        return userRepository.findAll();
    }
}
