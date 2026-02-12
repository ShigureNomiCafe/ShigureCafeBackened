package cafe.shigure.ShigureCafeBackend.config;

import cafe.shigure.ShigureCafeBackend.model.SystemLog;
import cafe.shigure.ShigureCafeBackend.repository.SystemLogRepository;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;

public class DatabaseLogAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent eventObject) {
        // Prevent circular logging
        if (eventObject.getLoggerName().contains("hibernate") || 
            eventObject.getLoggerName().contains("springframe") ||
            eventObject.getLoggerName().contains("mariadb")) {
            return;
        }

        try {
            // Do not log if context is not yet initialized or is closing
            if (ApplicationContextHolder.getContext() == null) {
                return;
            }

            SystemLogRepository repository = ApplicationContextHolder.getBean(SystemLogRepository.class);
            if (repository != null) {
                String formattedContent = String.format("[%s] %s", 
                        eventObject.getLoggerName(), 
                        eventObject.getFormattedMessage());
                
                SystemLog log = SystemLog.builder()
                        .timestamp(Instant.now().toEpochMilli())
                        .level(eventObject.getLevel().toString())
                        .source("ShigureCafeBackend")
                        .content(formattedContent)
                        .build();
                repository.save(log);
            }
        } catch (Exception e) {
            // Fallback to console or just ignore to prevent application crash
            addError("Failed to save log to database", e);
        }
    }
}
