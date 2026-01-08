# Shigure Cafe Backend System

The backend service for ShigureCafe, a robust and secure user management system built with modern Java technologies.

## Core Features

*   **Secure Registration Workflow:**
    *   Email verification powered by **Microsoft Graph API**.
    *   Mandatory administrator audit system with generated audit codes.
    *   Automatic fallback for nicknames (defaults to username if unspecified).
*   **Authentication & Security:**
    *   Stateless JWT-based authentication using **jjwt**.
    *   Verification code system with rate limiting (60s cooldown) and 5-minute expiry.
    *   BCrypt password encryption.
    *   Token blacklisting mechanism for secure logout.
*   **User Management:**
    *   Profile management (Nickname, Email updates).
    *   Validation logic (e.g., max 50 characters for nicknames).
*   **Administrative Tools:**
    *   Role-Based Access Control (`USER`, `ADMIN`).
    *   Lifecycle management: Edit, Delete (with self-deletion protection), and Password Reset.
    *   Real-time auditing of registration requests.
*   **API Documentation:** Integrated **Springdoc OpenAPI (Swagger)** for easy API exploration and testing.
*   **Global Exception Handling:** Consistent JSON error responses for business logic and data integrity violations.

## Technical Stack

*   **Framework:** Spring Boot 4.0.1
*   **Language:** Java 25
*   **Security:** Spring Security & JWT (0.12.6)
*   **Database:** MySQL with Spring Data JPA & Hibernate
*   **Integration:** Microsoft Graph SDK 6.19.0 (Email service)
*   **Environment Management:** Dotenv-java 3.1.0
*   **Documentation:** Springdoc OpenAPI 2.8.14

## Getting Started

### Prerequisites

*   **Java 25** or higher.
*   **Maven** (or use the provided `mvnw`).
*   **MySQL** instance.

### Configuration

Create a `.env` file in the root directory with the following variables:

```env
DB_URL=jdbc:mysql://localhost:3306/shigure_cafe?serverTimezone=UTC
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password
JWT_SECRET=your_long_secure_jwt_secret
AZURE_CLIENT_ID=your_azure_client_id
AZURE_TENANT_ID=your_azure_tenant_id
AZURE_CLIENT_SECRET=your_azure_client_secret
EMAIL_FROM=your_verified_email@domain.com
```

### Running the Application

```bash
./mvnw spring-boot:run
```

The server will start on port `8080` by default. API documentation can be accessed at `/swagger-ui.html`.
