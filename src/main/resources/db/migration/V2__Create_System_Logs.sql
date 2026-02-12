CREATE TABLE system_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp BIGINT NOT NULL,
    level VARCHAR(10) NOT NULL,
    source VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    INDEX idx_system_logs_timestamp (timestamp),
    INDEX idx_system_logs_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
