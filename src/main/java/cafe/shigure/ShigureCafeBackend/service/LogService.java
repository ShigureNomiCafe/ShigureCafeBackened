package cafe.shigure.ShigureCafeBackend.service;

import cafe.shigure.ShigureCafeBackend.model.SystemLog;
import cafe.shigure.ShigureCafeBackend.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LogService {
    private final SystemLogRepository systemLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String level, String originalSource, String content, Long timestamp) {
        String formattedContent = (originalSource != null && !originalSource.isEmpty()) 
                ? String.format("[%s] %s", originalSource, content) 
                : content;

        SystemLog log = SystemLog.builder()
                .timestamp(timestamp != null ? timestamp : Instant.now().toEpochMilli())
                .level(level)
                .source("ShigureCafeBackend")
                .content(formattedContent)
                .build();
        systemLogRepository.save(log);
    }

    public void log(String level, String source, String content) {
        log(level, source, content, null);
    }

    public void info(String source, String content) {
        log("INFO", source, content);
    }

    public void warn(String source, String content) {
        log("WARN", source, content);
    }

    public void error(String source, String content) {
        log("ERROR", source, content);
    }
}
