package com.example.jutjubic.controllers;

import com.example.jutjubic.models.User;
import com.example.jutjubic.repositories.UserRepository;
import com.example.jutjubic.services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class UserController {
    private final UserRepository userRepository;
    private final UserService userService;

    public UserController(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @GetMapping("/users")
    public Iterable<User> getAllUsers(){
        return userRepository.findAll();
    }
}
