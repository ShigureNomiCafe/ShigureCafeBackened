package cafe.shigure.ShigureCafeBackend.controller;

import cafe.shigure.ShigureCafeBackend.dto.LogRequest;
import cafe.shigure.ShigureCafeBackend.model.SystemLog;
import cafe.shigure.ShigureCafeBackend.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {
    private final SystemLogRepository systemLogRepository;

    @PostMapping
    @PreAuthorize("hasAuthority('API_CLIENT')")
    public ResponseEntity<Void> addLog(@RequestBody LogRequest request) {
        SystemLog log = SystemLog.builder()
                .timestamp(request.getTimestamp() != null ? request.getTimestamp() : Instant.now().toEpochMilli())
                .level(request.getLevel() != null ? request.getLevel().toUpperCase() : "INFO")
                .source(request.getSource() != null ? request.getSource() : "EXTERNAL")
                .content(request.getContent())
                .build();
        systemLogRepository.save(log);
        return ResponseEntity.ok().build();
    }
}
