package cafe.shigure.ShigureCafeBackend.service;

import cafe.shigure.ShigureCafeBackend.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogCleanupService {

    private final SystemLogRepository systemLogRepository;
    private final ApplicationContext applicationContext;

    @Value("${application.system.logs.retention-days:7}")
    private int retentionDays;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application started, performing initial log cleanup...");
        // Use applicationContext to get the proxy of this bean to ensure @Transactional works
        applicationContext.getBean(LogCleanupService.class).cleanupLogs();
    }

    @Scheduled(cron = "0 0 3 * * ?") // Run at 3 AM every day
    @Transactional
    public void cleanupLogs() {
        log.info("Starting log cleanup task, retention days: {}", retentionDays);
        try {
            long cutoffTimestamp = Instant.now()
                    .minus(retentionDays, ChronoUnit.DAYS)
                    .toEpochMilli();
            
            systemLogRepository.deleteByTimestampBefore(cutoffTimestamp);
            log.info("Log cleanup completed successfully.");
        } catch (Exception e) {
            log.error("Error occurred during log cleanup: ", e);
        }
    }
}
