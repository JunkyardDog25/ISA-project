package com.example.jutjubic.controllers;

import com.example.jutjubic.dto.UserProfileDto;
import com.example.jutjubic.models.User;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.repositories.UserRepository;
import com.example.jutjubic.services.UserService;
import com.example.jutjubic.services.VideoService;
import com.example.jutjubic.utils.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
class UserController {
    private final UserRepository userRepository;
    private final UserService userService;
    private final VideoService videoService;

    public UserController(UserRepository userRepository, UserService userService, VideoService videoService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.videoService = videoService;
    }

    @GetMapping("/users")
    public Iterable<User> getAllUsers(){
        return userRepository.findAll();
    }

    /**
     * Javni endpoint za dobijanje profila korisnika.
     * Dostupan svim korisnicima (autentifikovanim i neautentifikovanim).
     * Vraća samo javne informacije o korisniku.
     */
    @GetMapping("/users/{id}/profile")
    public ResponseEntity<UserProfileDto> getUserProfile(@PathVariable UUID id) {
        User user = userService.getUserById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        UserProfileDto profileDto = new UserProfileDto(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getCreatedAt()
        );

        return ResponseEntity.ok(profileDto);
    }

    /**
     * Javni endpoint za dobijanje video objava korisnika.
     * Dostupan svim korisnicima (autentifikovanim i neautentifikovanim).
     * Vraća paginiranu listu video objava sortiranu po vremenu kreiranja (najnovije prvo).
     */
    @GetMapping("/users/{id}/videos")
    public ResponseEntity<PageResponse<Video>> getUserVideos(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "16") int size
    ) {
        User user = userService.getUserById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        PageResponse<Video> videos = videoService.getVideosByUserId(id, page, size);
        return ResponseEntity.ok(videos);
    }
}
