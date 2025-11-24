package com.aiko.orchestrator.repository;

import com.aiko.orchestrator.model.Response;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResponseRepository extends MongoRepository<Response, String> {
    
    Optional<Response> findByQueryId(String queryId);
    
    List<Response> findByAgentType(String agentType);
    
    @Aggregation(pipeline = {
        "{ $group: { _id: '$agentType', avgConfidence: { $avg: '$confidenceScore' } } }"
    })
    List<Object> getAverageConfidenceByAgent();
    
    List<Response> findByConfidenceScoreGreaterThan(Double minScore);
}