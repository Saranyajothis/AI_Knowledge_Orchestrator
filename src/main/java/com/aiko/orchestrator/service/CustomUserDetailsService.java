package com.aiko.orchestrator.service;

import com.aiko.orchestrator.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);
        
        return userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> {
                    log.error("User not found with username or email: {}", username);
                    return new UsernameNotFoundException(
                        "User not found with username or email: " + username
                    );
                });
    }
    
    public UserDetails loadUserById(String id) {
        log.debug("Loading user by id: {}", id);
        
        return userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("User not found with id: {}", id);
                    return new UsernameNotFoundException(
                        "User not found with id: " + id
                    );
                });
    }
}
