package com.aiko.orchestrator.service;

import com.aiko.orchestrator.exception.ResourceNotFoundException;
import com.aiko.orchestrator.model.Knowledge;
import com.aiko.orchestrator.repository.KnowledgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {
    
    private final KnowledgeRepository knowledgeRepository;
    private final MongoTemplate mongoTemplate;
    
    public Knowledge uploadDocument(MultipartFile file, String title, List<String> tags) {
        try {
            Knowledge document = new Knowledge();
            document.setTitle(title);
            document.setContent(new String(file.getBytes()));
            document.setTags(tags);
            document.setSource(file.getOriginalFilename());
            document.setSizeInBytes(file.getSize());
            document.setDocumentType(determineDocumentType(file.getOriginalFilename()));
            
            // Extract summary (mock implementation)
            String content = new String(file.getBytes());
            document.setSummary(content.length() > 200 ? 
                              content.substring(0, 200) + "..." : content);
            
            return knowledgeRepository.save(document);
            
        } catch (IOException e) {
            log.error("Error uploading document: {}", e.getMessage());
            throw new RuntimeException("Failed to upload document", e);
        }
    }
    
    @Cacheable(value = "knowledgeSearch", key = "T(com.aiko.orchestrator.util.CacheKeyGenerator).generateKeyForSearch(#query, #limit)")
    public List<Knowledge> searchKnowledge(String query, int limit) {
        // Text search
        TextCriteria criteria = TextCriteria.forDefaultLanguage()
                .matchingPhrase(query);
        
        return knowledgeRepository.findAllBy(criteria)
                .stream()
                .limit(limit)
                .toList();
    }
    
    @Cacheable(value = "documents", key = "#id")
    public Knowledge getDocumentById(String id) {
        Knowledge document = knowledgeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        
        // Increment usage count
        document.setUsageCount(document.getUsageCount() + 1);
        knowledgeRepository.save(document);
        
        return document;
    }
    
    public Page<Knowledge> listDocuments(Pageable pageable) {
        return knowledgeRepository.findAll(pageable);
    }
    
    @CacheEvict(value = "documents", key = "#id")
    public void deleteDocument(String id) {
        if (!knowledgeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Document not found with id: " + id);
        }
        knowledgeRepository.deleteById(id);
    }
    
    /**
     * Asynchronously process and index a document
     * This runs in a separate thread for heavy document processing
     */
    @Async("knowledgeTaskExecutor")
    public CompletableFuture<Knowledge> processDocumentAsync(String documentId) {
        log.info("Starting async document processing for ID: {} on thread: {}", 
                documentId, Thread.currentThread().getName());
        
        try {
            Knowledge document = knowledgeRepository.findById(documentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
            
            // Update status to processing
            document.setProcessingStatus(Knowledge.ProcessingStatus.PROCESSING);
            document.setProcessingStartTime(LocalDateTime.now());
            knowledgeRepository.save(document);
            
            // Simulate heavy processing (replace with actual processing logic)
            Thread.sleep(3000); // Simulated processing time
            
            // Extract metadata
            Map<String, Object> metadata = extractMetadata(document.getContent());
            document.setMetadata(metadata);
            
            // Generate embeddings (mock)
            document.setEmbeddings(generateEmbeddings(document.getContent()));
            
            // Extract entities (mock)
            document.setExtractedEntities(extractEntities(document.getContent()));
            
            // Update status to indexed
            document.setProcessingStatus(Knowledge.ProcessingStatus.INDEXED);
            document.setProcessingEndTime(LocalDateTime.now());
            document.setIndexedAt(LocalDateTime.now());
            
            Knowledge savedDocument = knowledgeRepository.save(document);
            
            log.info("Document {} processed and indexed successfully", documentId);
            return CompletableFuture.completedFuture(savedDocument);
            
        } catch (Exception e) {
            log.error("Error processing document {}: {}", documentId, e.getMessage(), e);
            
            // Update status to failed
            try {
                Knowledge document = knowledgeRepository.findById(documentId).orElse(null);
                if (document != null) {
                    document.setProcessingStatus(Knowledge.ProcessingStatus.FAILED);
                    document.setProcessingEndTime(LocalDateTime.now());
                    document.setErrorMessage(e.getMessage());
                    knowledgeRepository.save(document);
                }
            } catch (Exception saveError) {
                log.error("Failed to update document status: {}", saveError.getMessage());
            }
            
            throw new RuntimeException("Document processing failed", e);
        }
    }
    
    /**
     * Batch process multiple documents asynchronously
     */
    @Async("knowledgeTaskExecutor")
    public CompletableFuture<Integer> batchProcessDocuments(List<String> documentIds) {
        log.info("Starting batch processing for {} documents on thread: {}", 
                documentIds.size(), Thread.currentThread().getName());
        
        int processedCount = 0;
        
        for (String documentId : documentIds) {
            try {
                processDocumentAsync(documentId);
                processedCount++;
            } catch (Exception e) {
                log.error("Failed to process document {}: {}", documentId, e.getMessage());
            }
        }
        
        log.info("Batch processing initiated for {} documents", processedCount);
        return CompletableFuture.completedFuture(processedCount);
    }
    
    /**
     * Asynchronously parse and ingest a large document
     */
    @Async("knowledgeTaskExecutor")
    public CompletableFuture<Knowledge> ingestLargeDocumentAsync(MultipartFile file, String title, List<String> tags) {
        log.info("Starting async ingestion of large document: {} on thread: {}", 
                title, Thread.currentThread().getName());
        
        try {
            Knowledge document = new Knowledge();
            document.setTitle(title);
            document.setTags(tags);
            document.setSource(file.getOriginalFilename());
            document.setSizeInBytes(file.getSize());
            document.setDocumentType(determineDocumentType(file.getOriginalFilename()));
            document.setProcessingStatus(Knowledge.ProcessingStatus.PROCESSING);
            
            // Save initially
            Knowledge savedDocument = knowledgeRepository.save(document);
            
            // Process content in chunks for large files
            String content = new String(file.getBytes());
            List<String> chunks = splitIntoChunks(content, 1000); // 1000 chars per chunk
            
            StringBuilder processedContent = new StringBuilder();
            for (String chunk : chunks) {
                // Process each chunk (could include cleaning, formatting, etc.)
                processedContent.append(processChunk(chunk));
                
                // Simulate processing time
                Thread.sleep(100);
            }
            
            savedDocument.setContent(processedContent.toString());
            savedDocument.setSummary(generateSummary(processedContent.toString()));
            savedDocument.setProcessingStatus(Knowledge.ProcessingStatus.INDEXED);
            savedDocument.setIndexedAt(LocalDateTime.now());
            
            savedDocument = knowledgeRepository.save(savedDocument);
            
            // Trigger async indexing
            processDocumentAsync(savedDocument.getId());
            
            log.info("Large document {} ingested successfully", savedDocument.getId());
            return CompletableFuture.completedFuture(savedDocument);
            
        } catch (Exception e) {
            log.error("Error ingesting large document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to ingest large document", e);
        }
    }
    
    /**
     * Asynchronously update search index
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> updateSearchIndexAsync() {
        log.info("Starting search index update on thread: {}", Thread.currentThread().getName());
        
        try {
            // Get all documents that need reindexing
            List<Knowledge> documents = knowledgeRepository.findByProcessingStatus(
                    Knowledge.ProcessingStatus.PENDING);
            
            log.info("Found {} documents to index", documents.size());
            
            for (Knowledge document : documents) {
                try {
                    processDocumentAsync(document.getId());
                    Thread.sleep(500); // Rate limiting
                } catch (Exception e) {
                    log.error("Failed to index document {}: {}", document.getId(), e.getMessage());
                }
            }
            
            log.info("Search index update completed");
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Error updating search index: {}", e.getMessage(), e);
            throw new RuntimeException("Search index update failed", e);
        }
    }
    
    // Helper methods
    
    private Map<String, Object> extractMetadata(String content) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("wordCount", content.split("\\s+").length);
        metadata.put("characterCount", content.length());
        metadata.put("extractedAt", LocalDateTime.now());
        // Add more metadata extraction logic here
        return metadata;
    }
    
    private List<Double> generateEmbeddings(String content) {
        // Mock embedding generation - replace with actual embedding service
        List<Double> embeddings = new ArrayList<>();
        for (int i = 0; i < 768; i++) { // Standard embedding size
            embeddings.add(Math.random());
        }
        return embeddings;
    }
    
    private List<String> extractEntities(String content) {
        // Mock entity extraction - replace with actual NER service
        List<String> entities = new ArrayList<>();
        // Simple mock: extract capitalized words as entities
        String[] words = content.split("\\s+");
        for (String word : words) {
            if (word.length() > 0 && Character.isUpperCase(word.charAt(0))) {
                entities.add(word);
            }
        }
        return entities.stream().distinct().limit(20).toList();
    }
    
    private List<String> splitIntoChunks(String content, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < content.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, content.length());
            chunks.add(content.substring(i, end));
        }
        return chunks;
    }
    
    private String processChunk(String chunk) {
        // Process individual chunk (cleaning, formatting, etc.)
        return chunk.trim().replaceAll("\\s+", " ");
    }
    
    private String generateSummary(String content) {
        // Mock summary generation - replace with actual summarization service
        return content.length() > 200 ? 
               content.substring(0, 200) + "..." : content;
    }
    
    private Knowledge.DocumentType determineDocumentType(String filename) {
        if (filename == null) return Knowledge.DocumentType.OTHER;
        
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> Knowledge.DocumentType.PDF;
            case "txt" -> Knowledge.DocumentType.TEXT;
            case "md" -> Knowledge.DocumentType.MARKDOWN;
            case "java", "py", "js", "ts" -> Knowledge.DocumentType.CODE;
            default -> Knowledge.DocumentType.OTHER;
        };
    }
}