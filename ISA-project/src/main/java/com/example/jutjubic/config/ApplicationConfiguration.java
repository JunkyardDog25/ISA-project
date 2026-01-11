package com.example.jutjubic.config;

import com.example.jutjubic.repositories.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
class ApplicationConfiguration {
    public ApplicationConfiguration(UserRepository userRepository) { }

    @Bean
    UserDetailsService userDetailsService(UserRepository userRepository) {
        return identifier -> {
            // Try to find by email first (for login)
            return userRepository.findByEmail(identifier)
                    .or(() -> userRepository.findByUsername(identifier)) // Fallback to username (for JWT token)
                    .orElseThrow(() -> new RuntimeException("User not found with identifier: " + identifier));
        };
    }

    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, BCryptPasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);

        authProvider.setPasswordEncoder(passwordEncoder);

        return authProvider;
    }
}
