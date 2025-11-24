package com.aiko.orchestrator.repository;

import com.aiko.orchestrator.model.Query;
import com.aiko.orchestrator.model.Query.QueryStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface QueryRepository extends MongoRepository<Query, String> {
    
    Page<Query> findByUserId(String userId, Pageable pageable);
    
    List<Query> findByStatus(QueryStatus status);
    
    @org.springframework.data.mongodb.repository.Query("{ 'status': ?0, 'priority': { $gte: ?1 } }")
    List<Query> findUrgentQueries(QueryStatus status, Integer minPriority);
    
    @org.springframework.data.mongodb.repository.Query("{ 'createdAt': { $gte: ?0, $lte: ?1 } }")
    List<Query> findQueriesInDateRange(LocalDateTime start, LocalDateTime end);
    
    Long countByStatusAndCreatedAtAfter(QueryStatus status, LocalDateTime date);
}