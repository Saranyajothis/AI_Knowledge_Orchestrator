package com.aiko.orchestrator.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class MongoPerformanceAspect {
    
    private final MeterRegistry meterRegistry;
    
    // Pointcut for MongoDB repository methods
    @Pointcut("execution(* org.springframework.data.mongodb.repository.MongoRepository+.*(..))")
    public void mongoRepositoryMethods() {}
    
    // Pointcut for methods with @Query annotation
    @Pointcut("@annotation(org.springframework.data.mongodb.repository.Query)")
    public void queryAnnotatedMethods() {}
    
    // Pointcut for MongoTemplate operations
    @Pointcut("execution(* org.springframework.data.mongodb.core.MongoTemplate.*(..))")
    public void mongoTemplateMethods() {}
    
    @Around("mongoRepositoryMethods() || queryAnnotatedMethods() || mongoTemplateMethods()")
    public Object measureMongoPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String operation = className + "." + methodName;
        
        // Start timing
        long startTime = System.currentTimeMillis();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Execute the actual method
            Object result = joinPoint.proceed();
            
            // Calculate duration
            long duration = System.currentTimeMillis() - startTime;
            
            // Record metrics
            sample.stop(Timer.builder("mongodb.query.duration")
                .tag("operation", operation)
                .tag("status", "success")
                .register(meterRegistry));
            
            meterRegistry.counter("mongodb.query.count",
                "operation", operation,
                "status", "success"
            ).increment();
            
            // Log slow queries (> 1 second)
            if (duration > 1000) {
                log.warn("Slow MongoDB query detected: {} took {}ms", operation, duration);
                meterRegistry.counter("mongodb.slow.queries",
                    "operation", operation
                ).increment();
            } else if (duration > 100) {
                log.debug("MongoDB query {} took {}ms", operation, duration);
            }
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            // Record failure metrics
            sample.stop(Timer.builder("mongodb.query.duration")
                .tag("operation", operation)
                .tag("status", "failure")
                .register(meterRegistry));
            
            meterRegistry.counter("mongodb.query.count",
                "operation", operation,
                "status", "failure"
            ).increment();
            
            meterRegistry.counter("mongodb.query.errors",
                "operation", operation,
                "exception", e.getClass().getSimpleName()
            ).increment();
            
            log.error("MongoDB operation {} failed after {}ms: {}", 
                     operation, duration, e.getMessage());
            
            throw e;
        }
    }
}
