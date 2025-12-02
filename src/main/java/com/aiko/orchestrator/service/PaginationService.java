package com.aiko.orchestrator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaginationService {
    
    private final MongoTemplate mongoTemplate;
    
    /**
     * Cursor-based pagination - More efficient for large datasets
     * Uses the last document's ID as a cursor for the next page
     */
    public <T> CursorPage<T> findWithCursor(String lastId, int pageSize, 
                                           Class<T> entityClass, 
                                           Query baseQuery) {
        log.debug("Cursor pagination for {} with lastId: {}, pageSize: {}", 
                 entityClass.getSimpleName(), lastId, pageSize);
        
        Query query;
        if (baseQuery != null) {
            // Create a new Query object properly
            query = new Query();
            if (baseQuery.getQueryObject() != null && !baseQuery.getQueryObject().isEmpty()) {
                baseQuery.getQueryObject().forEach((key, value) -> 
                    query.addCriteria(Criteria.where(key).is(value))
                );
            }
        } else {
            query = new Query();
        }
        
        // If we have a cursor (lastId), add it to the query
        if (lastId != null && !lastId.isEmpty()) {
            query.addCriteria(Criteria.where("_id").gt(new ObjectId(lastId)));
        }
        
        // Limit results to pageSize + 1 to check if there's a next page
        query.limit(pageSize + 1);
        query.with(Sort.by(Sort.Direction.ASC, "_id"));
        
        List<T> results = mongoTemplate.find(query, entityClass);
        
        boolean hasNext = results.size() > pageSize;
        if (hasNext) {
            results = results.subList(0, pageSize); // Remove the extra element
        }
        
        String nextCursor = null;
        if (hasNext && !results.isEmpty()) {
            T lastItem = results.get(results.size() - 1);
            nextCursor = extractId(lastItem);
        }
        
        return new CursorPage<>(results, nextCursor, hasNext, pageSize);
    }
    
    /**
     * Offset-based pagination - Traditional approach
     * Good for small to medium datasets with UI pagination
     */
    public <T> Page<T> findWithOffset(int page, int size, 
                                      Class<T> entityClass, 
                                      Query baseQuery,
                                      Sort sort) {
        log.debug("Offset pagination for {} - page: {}, size: {}", 
                 entityClass.getSimpleName(), page, size);
        
        Pageable pageable = PageRequest.of(page, size, 
            sort != null ? sort : Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Query query;
        if (baseQuery != null) {
            query = new Query();
            if (baseQuery.getQueryObject() != null && !baseQuery.getQueryObject().isEmpty()) {
                baseQuery.getQueryObject().forEach((key, value) -> 
                    query.addCriteria(Criteria.where(key).is(value))
                );
            }
        } else {
            query = new Query();
        }
        
        query.with(pageable);
        
        // Get total count for pagination metadata
        Query countQuery = Query.of(query).skip(-1).limit(-1);
        long total = mongoTemplate.count(countQuery, entityClass);
        
        // Get the actual page data
        List<T> content = mongoTemplate.find(query, entityClass);
        
        return new PageImpl<>(content, pageable, total);
    }
    
    /**
     * Keyset pagination - Efficient for sorted results
     * Uses a combination of fields as a key for pagination
     */
    public <T> KeysetPage<T> findWithKeyset(Object[] lastKey, 
                                            int pageSize, 
                                            Class<T> entityClass,
                                            String[] keyFields,
                                            Sort.Direction[] directions) {
        log.debug("Keyset pagination for {} with pageSize: {}", 
                 entityClass.getSimpleName(), pageSize);
        
        Query query = new Query();
        
        // Build the keyset criteria
        if (lastKey != null && lastKey.length == keyFields.length) {
            Criteria criteria = buildKeysetCriteria(keyFields, lastKey, directions);
            query.addCriteria(criteria);
        }
        
        // Apply sorting
        Sort sort = buildSort(keyFields, directions);
        query.with(sort);
        query.limit(pageSize + 1);
        
        List<T> results = mongoTemplate.find(query, entityClass);
        
        boolean hasNext = results.size() > pageSize;
        if (hasNext) {
            results = results.subList(0, pageSize);
        }
        
        Object[] nextKey = null;
        if (hasNext && !results.isEmpty()) {
            nextKey = extractKeyValues(results.get(results.size() - 1), keyFields);
        }
        
        return new KeysetPage<>(results, nextKey, hasNext, pageSize);
    }
    
    /**
     * Aggregation-based pagination for complex queries
     */
    public <T> Page<T> findWithAggregation(int page, int size, 
                                          Class<T> entityClass,
                                          List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> operations) {
        log.debug("Aggregation pagination for {} - page: {}, size: {}", 
                 entityClass.getSimpleName(), page, size);
        
        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> pipelineOps = 
            new ArrayList<>(operations);
        
        // Add pagination stages
        long skip = (long) page * size;
        pipelineOps.add(org.springframework.data.mongodb.core.aggregation.Aggregation.skip(skip));
        pipelineOps.add(org.springframework.data.mongodb.core.aggregation.Aggregation.limit(size));
        
        org.springframework.data.mongodb.core.aggregation.Aggregation aggregation = 
            org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(pipelineOps);
        
        List<T> results = mongoTemplate.aggregate(aggregation, entityClass, entityClass)
            .getMappedResults();
        
        // Get total count (expensive for aggregations)
        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> countOps = 
            new ArrayList<>(operations);
        countOps.add(org.springframework.data.mongodb.core.aggregation.Aggregation.count().as("total"));
        
        org.springframework.data.mongodb.core.aggregation.Aggregation countAggregation = 
            org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(countOps);
        
        long total = 0;
        List<CountResult> countResults = mongoTemplate.aggregate(
            countAggregation, entityClass, CountResult.class).getMappedResults();
        
        if (!countResults.isEmpty()) {
            total = countResults.get(0).getTotal();
        }
        
        Pageable pageable = PageRequest.of(page, size);
        return new PageImpl<>(results, pageable, total);
    }
    
    /**
     * Stream large result sets efficiently
     */
    public <T> void streamResults(Class<T> entityClass, 
                                  Query query,
                                  java.util.function.Consumer<T> processor) {
        log.debug("Streaming results for {}", entityClass.getSimpleName());
        
        // Use cursor with batch size for efficient streaming
        query.cursorBatchSize(100);
        
        // Since stream returns a Stream<T>, we need to process it
        try (java.util.stream.Stream<T> stream = mongoTemplate.stream(query, entityClass)) {
            stream.forEach(processor);
        }
    }
    
    // Helper methods
    private String extractId(Object entity) {
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object id = idField.get(entity);
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            log.error("Error extracting ID from entity", e);
            return null;
        }
    }
    
    private Criteria buildKeysetCriteria(String[] fields, Object[] values, Sort.Direction[] directions) {
        Criteria criteria = new Criteria();
        List<Criteria> orCriteria = new ArrayList<>();
        
        for (int i = 0; i < fields.length; i++) {
            List<Criteria> andCriteria = new ArrayList<>();
            
            // Add equality criteria for all fields before this one
            for (int j = 0; j < i; j++) {
                andCriteria.add(Criteria.where(fields[j]).is(values[j]));
            }
            
            // Add comparison criteria for this field
            if (directions[i] == Sort.Direction.ASC) {
                andCriteria.add(Criteria.where(fields[i]).gt(values[i]));
            } else {
                andCriteria.add(Criteria.where(fields[i]).lt(values[i]));
            }
            
            if (!andCriteria.isEmpty()) {
                orCriteria.add(new Criteria().andOperator(andCriteria.toArray(new Criteria[0])));
            }
        }
        
        if (!orCriteria.isEmpty()) {
            criteria.orOperator(orCriteria.toArray(new Criteria[0]));
        }
        
        return criteria;
    }
    
    private Sort buildSort(String[] fields, Sort.Direction[] directions) {
        List<Sort.Order> orders = new ArrayList<>();
        for (int i = 0; i < fields.length; i++) {
            orders.add(new Sort.Order(directions[i], fields[i]));
        }
        return Sort.by(orders);
    }
    
    private Object[] extractKeyValues(Object entity, String[] fields) {
        Object[] values = new Object[fields.length];
        try {
            for (int i = 0; i < fields.length; i++) {
                java.lang.reflect.Field field = entity.getClass().getDeclaredField(fields[i]);
                field.setAccessible(true);
                values[i] = field.get(entity);
            }
        } catch (Exception e) {
            log.error("Error extracting key values from entity", e);
        }
        return values;
    }
    
    // Response classes
    public static class CursorPage<T> {
        private final List<T> content;
        private final String nextCursor;
        private final boolean hasNext;
        private final int pageSize;
        
        public CursorPage(List<T> content, String nextCursor, boolean hasNext, int pageSize) {
            this.content = content;
            this.nextCursor = nextCursor;
            this.hasNext = hasNext;
            this.pageSize = pageSize;
        }
        
        // Getters
        public List<T> getContent() { return content; }
        public String getNextCursor() { return nextCursor; }
        public boolean isHasNext() { return hasNext; }
        public int getPageSize() { return pageSize; }
    }
    
    public static class KeysetPage<T> {
        private final List<T> content;
        private final Object[] nextKey;
        private final boolean hasNext;
        private final int pageSize;
        
        public KeysetPage(List<T> content, Object[] nextKey, boolean hasNext, int pageSize) {
            this.content = content;
            this.nextKey = nextKey;
            this.hasNext = hasNext;
            this.pageSize = pageSize;
        }
        
        // Getters
        public List<T> getContent() { return content; }
        public Object[] getNextKey() { return nextKey; }
        public boolean isHasNext() { return hasNext; }
        public int getPageSize() { return pageSize; }
    }
    
    // Helper class for count results
    private static class CountResult {
        private long total;
        
        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
    }
}
