package com.aiko.orchestrator.repository;

import com.aiko.orchestrator.model.Knowledge;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface KnowledgeRepository extends MongoRepository<Knowledge, String> {
    
    Page<Knowledge> findByTagsIn(List<String> tags, Pageable pageable);
    
    @Query("{ $text: { $search: ?0 } }")
    List<Knowledge> searchByText(String searchTerm);
    
    List<Knowledge> findByDocumentType(Knowledge.DocumentType type);
    
    @Query("{ 'usageCount': { $gte: ?0 } }")
    List<Knowledge> findPopularDocuments(Integer minUsage);
    
    // For text search with score
    List<Knowledge> findAllBy(TextCriteria criteria);
    
    // For async processing support
    List<Knowledge> findByProcessingStatus(Knowledge.ProcessingStatus status);
    
    @Query("{ 'processingStatus': 'PENDING' }")
    List<Knowledge> findPendingDocuments();
    
    @Query("{ 'processingStatus': 'PROCESSING', 'processingStartTime': { $lt: ?0 } }")
    List<Knowledge> findStuckProcessingDocuments(java.time.LocalDateTime cutoffTime);
}