package cafe.shigure.ShigureCafeBackend.service;

import cafe.shigure.ShigureCafeBackend.model.SystemLog;
import cafe.shigure.ShigureCafeBackend.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LogService {
    private final SystemLogRepository systemLogRepository;

    public java.util.List<SystemLog> getLogsAfter(String level, String source, String search, Long afterId, int limit) {
        if (afterId == null) {
            org.springframework.data.domain.Pageable limitPageable = org.springframework.data.domain.PageRequest.of(0, limit, org.springframework.data.domain.Sort.by("id").descending());
            java.util.List<SystemLog> logs = systemLogRepository.findByFilters(level, source, search, limitPageable).getContent();
            // We want latest logs, but in ASCENDING order for the frontend to append or display correctly in terminal view
            java.util.List<SystemLog> mutableLogs = new java.util.ArrayList<>(logs);
            java.util.Collections.reverse(mutableLogs);
            return mutableLogs;
        }
        org.springframework.data.domain.Pageable limitPageable = org.springframework.data.domain.PageRequest.of(0, limit, org.springframework.data.domain.Sort.by("id").ascending());
        return systemLogRepository.findByFiltersAndIdGreaterThan(level, source, search, afterId, limitPageable);
    }

    public java.util.List<SystemLog> getLogsBefore(String level, String source, String search, Long beforeId, int limit) {
        org.springframework.data.domain.Pageable limitPageable = org.springframework.data.domain.PageRequest.of(0, limit, org.springframework.data.domain.Sort.by("id").descending());
        return systemLogRepository.findByFiltersAndIdLessThan(level, source, search, beforeId, limitPageable);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String level, String source, String content, Long timestamp) {
        SystemLog log = SystemLog.builder()
                .timestamp(timestamp != null ? timestamp : Instant.now().toEpochMilli())
                .level(level)
                .source(source != null ? source : "UNKNOWN")
                .content(content)
                .build();
        systemLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAll(java.util.List<cafe.shigure.ShigureCafeBackend.dto.LogRequest> requests) {
        java.util.List<SystemLog> logs = requests.stream()
                .map(request -> SystemLog.builder()
                        .timestamp(request.getTimestamp() != null ? request.getTimestamp() : Instant.now().toEpochMilli())
                        .level(request.getLevel() != null ? request.getLevel().toUpperCase() : "INFO")
                        .source(request.getSource() != null ? request.getSource() : "EXTERNAL")
                        .content(request.getContent())
                        .build())
                .collect(java.util.stream.Collectors.toList());
        systemLogRepository.saveAll(logs);
    }

    public void logInternal(String level, String module, String content) {
        String formattedContent = (module != null && !module.isEmpty()) 
                ? String.format("[%s] %s", module, content) 
                : content;
        log(level, "ShigureCafeBackend", formattedContent, null);
    }

    public void info(String module, String content) {
        logInternal("INFO", module, content);
    }

    public void warn(String module, String content) {
        logInternal("WARN", module, content);
    }

    public void error(String module, String content) {
        logInternal("ERROR", module, content);
    }
}
