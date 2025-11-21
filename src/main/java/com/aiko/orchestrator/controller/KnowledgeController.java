package com.aiko.orchestrator.controller;

import com.aiko.orchestrator.model.Knowledge;
import com.aiko.orchestrator.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
@Tag(name = "Knowledge Base", description = "Manage knowledge base documents")
public class KnowledgeController {
    
    private final KnowledgeService knowledgeService;
    
    @PostMapping("/upload")
    @Operation(summary = "Upload a document to the knowledge base")
    public ResponseEntity<Knowledge> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(required = false) List<String> tags) {
        
        Knowledge document = knowledgeService.uploadDocument(file, title, tags);
        return ResponseEntity.status(HttpStatus.CREATED).body(document);
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search the knowledge base")
    public ResponseEntity<List<Knowledge>> searchKnowledge(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        
        List<Knowledge> results = knowledgeService.searchKnowledge(query, limit);
        return ResponseEntity.ok(results);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get a document by ID")
    public ResponseEntity<Knowledge> getDocument(@PathVariable String id) {
        Knowledge document = knowledgeService.getDocumentById(id);
        return ResponseEntity.ok(document);
    }
    
    @GetMapping
    @Operation(summary = "List all documents with pagination")
    public ResponseEntity<Page<Knowledge>> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Page<Knowledge> documents = knowledgeService.listDocuments(PageRequest.of(page, size));
        return ResponseEntity.ok(documents);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        knowledgeService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}