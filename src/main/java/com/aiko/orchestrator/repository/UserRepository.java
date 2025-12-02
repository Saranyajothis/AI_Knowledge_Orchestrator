package com.aiko.orchestrator.repository;

import com.aiko.orchestrator.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByUsernameOrEmail(String username, String email);
    
    Boolean existsByUsername(String username);
    
    Boolean existsByEmail(String email);
    
    @Query("{ 'refreshToken': ?0 }")
    Optional<User> findByRefreshToken(String refreshToken);
    
    @Query("{ 'roles': { $in: [?0] } }")
    List<User> findByRole(String role);
    
    @Query("{ 'enabled': true }")
    List<User> findAllActiveUsers();
    
    @Query("{ 'accountNonLocked': false }")
    List<User> findAllLockedUsers();
}
