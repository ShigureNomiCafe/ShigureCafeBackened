# Changelog

## [v1.3.0] - 2026-02-13

### Added
- Implemented centralized log storage system for enhanced system monitoring.
- Implemented log retrieval API with support for various log types and levels.
- Added cursor-based pagination and real-time polling for logs in the admin dashboard.

### Changed
- Refactored the internal logging system to improve performance and reliability.

## [v1.2.0] - 2026-02-08

### Added
- Added email notification system with Markdown support.
- Added templates for email notifications and verification codes.
- Added email notification endpoint for all active users.
- Added token validation endpoint.
- Integrated **Bucket4j** for enhanced rate limiting and security.

### Changed
- Refactored email service with better template management and configuration.
- Simplified network definition to use root network in Docker configurations.
- Renamed `API_KEY` to `CAFE_API_KEY` for consistency and updated database user configuration.

## [v1.1.1] - 2026-01-24

### Added
- Added `TURNSTILE_SECRET_KEY` environment variable support in `docker-compose.yml`.

### Changed
- Refactored service dependencies to improve maintainability.
- Enhanced user audit logic for better workflow reliability.

### Fixed
- Fixed a typo in the project package name (renamed `ShigureCafeBackened` to `ShigureCafeBackend`).

## [v1.1.0] - 2026-01-24

### Added
- Integrated Cloudflare Turnstile for CAPTCHA verification.
- Added database migration support with Flyway and initial migration script.

### Changed
- Updated `spring-boot-starter-aop` to 3.5.10.
- Configured JPA `ddl-auto` to `validate`.
- Updated dependencies and formatted `pom.xml`.

### Fixed
- Updated environment variables in `.env.example` and `README.md`.

## [v1.0.1] - 2026-01-22

### Fixed
- Allow deleting users who have associated audit information and notices.

### Changed
- Update Docker service restart policy to `unless-stopped`.
- Refactor port exposure configuration in `docker-compose.yml`.

## [v1.0.0] - 2026-01-19
- Initial release with Docker support.
- Minecraft chat synchronization and whitelist management.
- S3 storage integration.
- AOP-based rate limiting.
