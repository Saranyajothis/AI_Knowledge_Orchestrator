package com.aiko.orchestrator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MongoBackupService {
    
    @Value("${mongodb.backup.enabled:true}")
    private boolean backupEnabled;
    
    @Value("${mongodb.backup.path:./backup/mongodb}")
    private String backupPath;
    
    @Value("${spring.data.mongodb.uri:mongodb://localhost:27017/ai_orchestrator}")
    private String mongoUri;
    
    @Value("${mongodb.backup.retention.days:7}")
    private int retentionDays;
    
    @Value("${mongodb.backup.compression:true}")
    private boolean useCompression;
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * Scheduled backup task - runs daily at 2 AM
     */
    @Scheduled(cron = "${mongodb.backup.cron:0 0 2 * * ?}")
    public void performScheduledBackup() {
        if (!backupEnabled) {
            log.debug("MongoDB backup is disabled");
            return;
        }
        
        try {
            BackupResult result = performBackup("scheduled");
            if (result.isSuccess()) {
                log.info("Scheduled backup completed successfully: {}", result.getBackupPath());
                cleanOldBackups();
            } else {
                log.error("Scheduled backup failed: {}", result.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Error during scheduled backup", e);
        }
    }
    
    /**
     * Perform a manual backup
     */
    public BackupResult performBackup(String backupType) {
        String timestamp = LocalDateTime.now().format(DATE_FORMAT);
        String backupDir = String.format("%s/%s_%s", backupPath, backupType, timestamp);
        
        log.info("Starting {} backup to: {}", backupType, backupDir);
        
        try {
            // Create backup directory
            Files.createDirectories(Paths.get(backupDir));
            
            // Build mongodump command
            List<String> command = buildBackupCommand(backupDir);
            
            // Execute backup
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // Capture output
            String output = captureProcessOutput(process);
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                long backupSize = calculateBackupSize(backupDir);
                log.info("Backup successful. Size: {} bytes, Path: {}", backupSize, backupDir);
                
                return BackupResult.success(backupDir, backupSize, timestamp);
            } else {
                log.error("Backup failed with exit code: {}. Output: {}", exitCode, output);
                return BackupResult.failure("Backup failed with exit code: " + exitCode);
            }
            
        } catch (Exception e) {
            log.error("Error performing backup", e);
            return BackupResult.failure(e.getMessage());
        }
    }
    
    /**
     * Restore from a backup
     */
    public RestoreResult restoreBackup(String backupTimestamp) {
        String backupDir = findBackupDirectory(backupTimestamp);
        
        if (backupDir == null) {
            return RestoreResult.failure("Backup not found for timestamp: " + backupTimestamp);
        }
        
        log.info("Starting restore from: {}", backupDir);
        
        try {
            // Build mongorestore command
            List<String> command = buildRestoreCommand(backupDir);
            
            // Execute restore
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // Capture output
            String output = captureProcessOutput(process);
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.info("Restore successful from: {}", backupDir);
                return RestoreResult.success(backupDir, backupTimestamp);
            } else {
                log.error("Restore failed with exit code: {}. Output: {}", exitCode, output);
                return RestoreResult.failure("Restore failed with exit code: " + exitCode);
            }
            
        } catch (Exception e) {
            log.error("Error performing restore", e);
            return RestoreResult.failure(e.getMessage());
        }
    }
    
    /**
     * List available backups
     */
    public List<BackupInfo> listBackups() {
        List<BackupInfo> backups = new ArrayList<>();
        
        try {
            Path backupBasePath = Paths.get(backupPath);
            if (!Files.exists(backupBasePath)) {
                return backups;
            }
            
            try (Stream<Path> paths = Files.list(backupBasePath)) {
                backups = paths
                    .filter(Files::isDirectory)
                    .map(this::createBackupInfo)
                    .filter(info -> info != null)
                    .sorted(Comparator.comparing(BackupInfo::getCreatedAt).reversed())
                    .collect(Collectors.toList());
            }
        } catch (IOException e) {
            log.error("Error listing backups", e);
        }
        
        return backups;
    }
    
    /**
     * Delete a specific backup
     */
    public boolean deleteBackup(String backupTimestamp) {
        String backupDir = findBackupDirectory(backupTimestamp);
        
        if (backupDir == null) {
            log.warn("Backup not found for timestamp: {}", backupTimestamp);
            return false;
        }
        
        try {
            deleteDirectory(Paths.get(backupDir));
            log.info("Deleted backup: {}", backupDir);
            return true;
        } catch (IOException e) {
            log.error("Error deleting backup: {}", backupDir, e);
            return false;
        }
    }
    
    /**
     * Clean old backups based on retention policy
     */
    private void cleanOldBackups() {
        log.debug("Cleaning old backups older than {} days", retentionDays);
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        
        List<BackupInfo> backups = listBackups();
        
        for (BackupInfo backup : backups) {
            if (backup.getCreatedAt().isBefore(cutoffDate)) {
                try {
                    deleteDirectory(Paths.get(backup.getPath()));
                    log.info("Deleted old backup: {}", backup.getPath());
                } catch (IOException e) {
                    log.error("Error deleting old backup: {}", backup.getPath(), e);
                }
            }
        }
    }
    
    private List<String> buildBackupCommand(String backupDir) {
        List<String> command = new ArrayList<>();
        command.add("mongodump");
        command.add("--uri");
        command.add(mongoUri);
        command.add("--out");
        command.add(backupDir);
        
        if (useCompression) {
            command.add("--gzip");
        }
        
        // Add additional options
        command.add("--forceTableScan"); // Ensure complete backup
        
        return command;
    }
    
    private List<String> buildRestoreCommand(String backupDir) {
        List<String> command = new ArrayList<>();
        command.add("mongorestore");
        command.add("--uri");
        command.add(mongoUri);
        command.add("--dir");
        command.add(backupDir);
        command.add("--drop"); // Drop collections before restore
        
        if (useCompression) {
            command.add("--gzip");
        }
        
        return command;
    }
    
    private String captureProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug("Process output: {}", line);
            }
        }
        return output.toString();
    }
    
    private long calculateBackupSize(String backupDir) {
        try {
            Path path = Paths.get(backupDir);
            return Files.walk(path)
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0L;
                    }
                })
                .sum();
        } catch (IOException e) {
            log.error("Error calculating backup size", e);
            return 0L;
        }
    }
    
    private String findBackupDirectory(String timestamp) {
        try {
            Path backupBasePath = Paths.get(backupPath);
            if (!Files.exists(backupBasePath)) {
                return null;
            }
            
            try (Stream<Path> paths = Files.list(backupBasePath)) {
                return paths
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().contains(timestamp))
                    .map(Path::toString)
                    .findFirst()
                    .orElse(null);
            }
        } catch (IOException e) {
            log.error("Error finding backup directory", e);
            return null;
        }
    }
    
    private BackupInfo createBackupInfo(Path path) {
        try {
            String fileName = path.getFileName().toString();
            String[] parts = fileName.split("_");
            
            if (parts.length >= 2) {
                String type = parts[0];
                String timestamp = parts[1] + (parts.length > 2 ? "_" + parts[2] : "");
                
                BackupInfo info = new BackupInfo();
                info.setPath(path.toString());
                info.setType(type);
                info.setTimestamp(timestamp);
                info.setSize(calculateBackupSize(path.toString()));
                
                // Parse timestamp
                try {
                    info.setCreatedAt(LocalDateTime.parse(timestamp, DATE_FORMAT));
                } catch (Exception e) {
                    info.setCreatedAt(Files.getLastModifiedTime(path)
                        .toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime());
                }
                
                return info;
            }
        } catch (Exception e) {
            log.error("Error creating backup info for path: {}", path, e);
        }
        return null;
    }
    
    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }
    
    // Result classes
    public static class BackupResult {
        private final boolean success;
        private final String backupPath;
        private final long backupSize;
        private final String timestamp;
        private final String errorMessage;
        
        private BackupResult(boolean success, String backupPath, long backupSize, 
                           String timestamp, String errorMessage) {
            this.success = success;
            this.backupPath = backupPath;
            this.backupSize = backupSize;
            this.timestamp = timestamp;
            this.errorMessage = errorMessage;
        }
        
        public static BackupResult success(String path, long size, String timestamp) {
            return new BackupResult(true, path, size, timestamp, null);
        }
        
        public static BackupResult failure(String errorMessage) {
            return new BackupResult(false, null, 0, null, errorMessage);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getBackupPath() { return backupPath; }
        public long getBackupSize() { return backupSize; }
        public String getTimestamp() { return timestamp; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class RestoreResult {
        private final boolean success;
        private final String backupPath;
        private final String timestamp;
        private final String errorMessage;
        
        private RestoreResult(boolean success, String backupPath, String timestamp, String errorMessage) {
            this.success = success;
            this.backupPath = backupPath;
            this.timestamp = timestamp;
            this.errorMessage = errorMessage;
        }
        
        public static RestoreResult success(String path, String timestamp) {
            return new RestoreResult(true, path, timestamp, null);
        }
        
        public static RestoreResult failure(String errorMessage) {
            return new RestoreResult(false, null, null, errorMessage);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getBackupPath() { return backupPath; }
        public String getTimestamp() { return timestamp; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class BackupInfo {
        private String path;
        private String type;
        private String timestamp;
        private long size;
        private LocalDateTime createdAt;
        
        // Getters and setters
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}
