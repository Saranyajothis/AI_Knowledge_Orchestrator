package com.aiko.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Document(collection = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String username;
    
    @Indexed(unique = true)
    private String email;
    
    private String password;
    
    private String firstName;
    private String lastName;
    
    @Builder.Default
    private Set<String> roles = Set.of("USER");
    
    @Builder.Default
    private boolean enabled = true;
    
    @Builder.Default
    private boolean accountNonExpired = true;
    
    @Builder.Default
    private boolean accountNonLocked = true;
    
    @Builder.Default
    private boolean credentialsNonExpired = true;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    
    private String refreshToken;
    private LocalDateTime refreshTokenExpiryDate;
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
    
    @Override
    public String getPassword() {
        return this.password;
    }
    
    @Override
    public String getUsername() {
        return this.username;
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return this.accountNonExpired;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return this.accountNonLocked;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return this.credentialsNonExpired;
    }
    
    @Override
    public boolean isEnabled() {
        return this.enabled;
    }
}
