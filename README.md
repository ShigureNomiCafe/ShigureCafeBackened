# Shigure Cafe Backend System

The backend service for ShigureCafe, a robust and secure user management and social system built with modern Java 25 and Spring Boot 4.

## Core Features

*   **Authentication & Security:**
    *   **Stateless JWT Authentication:** Secure, scalable token-based auth using `jjwt`.
    *   **Multi-Factor Authentication (MFA):** Supports both **Email 2FA** and **TOTP (Google Authenticator)**.
    *   **Redis-Powered Verification:** High-performance verification code storage and rate limiting using Redis.
    *   **Token Blacklisting:** Instant logout capability by blacklisting active JWTs.
    *   **Secure Passwords:** Strong hashing using BCrypt.
    *   **API Key Authentication:** Secure communication for internal plugins and services.
    *   **Rate Limiting:** AOP-based rate limiting to prevent brute-force and spam.
    *   **CAPTCHA Verification:** Integrated **Cloudflare Turnstile** for secure, user-friendly bot protection.
*   **Minecraft Integration:**
    *   **Real-time Chat Sync:** WebSocket-based synchronization between the web frontend and Minecraft server.
    *   **Account Binding:** Securely link Minecraft accounts using **Microsoft OAuth2**.
    *   **Whitelist Management:** Automated whitelist synchronization based on user audit status.
*   **Notice Board System:**
    *   **Markdown & KaTeX Support:** Create rich, formatted notices with mathematical expressions.
    *   **Notice Reaction System:** Users can react to announcements with a variety of emojis.
    *   **Pinned Notices:** Support for pinning important announcements to the top of the board.
*   **Storage & Resources:**
    *   **S3-Compatible Storage:** Integration with MinIO, Cloudflare R2, or AWS S3 for user avatars and resources.
    *   **Presigned URLs:** Secure, time-limited upload URLs for efficient resource management.
*   **User Registration & Audit Workflow:**
    *   **Two-Stage Registration:** New users are created with a `PENDING` status.
    *   **Audit Code System:** Administrators generate unique audit codes to approve/activate users.
    *   **Email Verification:** Integration with **Microsoft Graph API** for reliable email delivery.
*   **Architecture & Reliability:**
    *   **Global Exception Handling:** Standardized error responses.
    *   **Database Migrations:** Managed by **Flyway** for reliable schema versioning.
    *   **Scheduled Tasks:** Automatic cleanup of expired tokens, verification codes, and abandoned resources.
    *   **API Documentation:** Integrated **Springdoc OpenAPI (Swagger)**.

## Technical Stack

*   **Framework:** Spring Boot 4.0.1
*   **Language:** Java 25
*   **Security:** Spring Security & JJWT 0.13.0
*   **Messaging:** Spring WebSocket & Redis Pub/Sub
*   **Storage:** AWS SDK for Java (S3) 2.41.10
*   **Integration:** Microsoft OAuth2 & Microsoft Graph SDK 6.60.0
*   **Cache/Storage:** Redis (for verification codes, rate limiting, and messaging)
*   **Database:** MariaDB with Spring Data JPA & Hibernate
*   **Migrations:** Flyway
*   **MFA:** dev.samstevens.totp 1.7.1
*   **Documentation:** Springdoc OpenAPI 3.0.1

## Project Structure

```text
src/main/java/cafe/shigure/ShigureCafeBackened/
├── annotation/         # Custom annotations (e.g., @RateLimit)
├── aspect/             # AOP aspects for cross-cutting concerns
├── config/             # Security, JWT, S3, and App configurations
├── controller/         # REST Endpoints (Auth, Notices, Users, Minecraft)
├── dto/                # Data Transfer Objects (Requests/Responses)
├── event/              # Internal application events
├── exception/          # Global exception handling and custom exceptions
├── model/              # JPA Entities (User, Notice, ChatMessage, etc.)
├── repository/         # Spring Data JPA Repositories
├── service/            # Business logic (Security, Emails, Storage, Minecraft)
└── websocket/          # WebSocket handlers and interceptors
```

## Getting Started

### Prerequisites

*   **Java 25** or higher.
*   **Maven** (or use the provided `mvnw`).
*   **MariaDB** instance.
*   **Redis** instance.
*   **S3-Compatible Storage** (MinIO, R2, or AWS S3).

### Configuration

Fill out the `.env.example` file and source it. Here's a quick look:

```env
# MariaDB Configuration
DB_URL='jdbc:mariadb://localhost:3306/shigure_cafe'
DB_USER='shigure_cafe'
DB_PASSWORD='YourDBPasswordHere'
DB_ROOT_PASSWORD='YourRootPasswordHere'

# Redis Configuration
REDIS_HOST='localhost'
REDIS_PORT='6379'
REDIS_USERNAME=''
REDIS_PASSWORD='YourRedisPasswordHere'

# Mail and Minecraft OAuth2 Credentials
MAIL_TENANT_ID='xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
MAIL_CLIENT_ID='xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
MAIL_CLIENT_SECRET='YourSecretHere'
MAIL_FROM='noreply@example.com'
MINECRAFT_TENANT_ID='xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
MINECRAFT_CLIENT_ID='xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
MINECRAFT_CLIENT_SECRET='YourSecretHere'

# S3 Configuration
S3_ENDPOINT='https://your-s3-endpoint.com'
S3_ACCESS_KEY='YourAccessKeyHere'
S3_SECRET_KEY='YourSecretKeyHere'
S3_BUCKET='your-bucket-name'
S3_PUBLIC_URL='https://your-s3-endpoint.com/your-bucket-name'
S3_REGION='auto'

# The api key for interacting with ShigureCafePlugin
API_KEY='YourApiKeyHere'

# Cloudflare Turnstile Configuration
TURNSTILE_SECRET_KEY='YourTurnstileSecretKeyHere'
```

### Running the Application

Before running the application, ensure the environment variables are sourced:

```bash
# If using a .env file
export $(grep -v '^#' .env | xargs)

# Using Maven Wrapper
./mvnw spring-boot:run
```

The server will start on port `8080` by default. API documentation can be accessed at `/swagger-ui.html`.
