package cafe.shigure.ShigureCafeBackend.config;

import cafe.shigure.ShigureCafeBackend.model.SystemLog;
import cafe.shigure.ShigureCafeBackend.repository.SystemLogRepository;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;

public class DatabaseLogAppender extends AppenderBase<ILoggingEvent> {

    private static final ThreadLocal<Boolean> IS_LOGGING = ThreadLocal.withInitial(() -> false);

    @Override
    protected void append(ILoggingEvent eventObject) {
        // Prevent circular logging
        if (IS_LOGGING.get()) {
            return;
        }

        // Restore blacklist to exclude noisy infrastructure logs
        if (eventObject.getLoggerName().contains("hibernate") || 
            eventObject.getLoggerName().contains("springframe") ||
            eventObject.getLoggerName().contains("mariadb")) {
            return;
        }

        IS_LOGGING.set(true);
        try {
            // Do not log if context is not yet initialized or is closing
            if (ApplicationContextHolder.getContext() == null) {
                return;
            }

            SystemLogRepository repository = ApplicationContextHolder.getBean(SystemLogRepository.class);
            if (repository != null) {
                StringBuilder contentBuilder = new StringBuilder();
                contentBuilder.append(String.format("[%s] %s", 
                        eventObject.getLoggerName(), 
                        eventObject.getFormattedMessage()));

                // Handle multi-line logs (stack traces)
                IThrowableProxy tp = eventObject.getThrowableProxy();
                if (tp != null) {
                    contentBuilder.append("\n").append(ThrowableProxyUtil.asString(tp));
                }
                
                SystemLog log = SystemLog.builder()
                        .timestamp(Instant.now().toEpochMilli())
                        .level(eventObject.getLevel().toString())
                        .source("ShigureCafeBackend")
                        .content(contentBuilder.toString())
                        .build();
                repository.save(log);
            }
        } catch (Exception e) {
            // Fallback to console or just ignore to prevent application crash
            addError("Failed to save log to database", e);
        } finally {
            IS_LOGGING.set(false);
        }
    }
}
