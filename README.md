# SecureNotes Manager

A production-grade, secure REST API for managing private notes with strict data isolation. Built for a university "Web Application Security" course project following OWASP guidelines.

## Features

- **JWT Authentication** with access and refresh tokens
- **Refresh Token Rotation** for enhanced security
- **Role-Based Access Control (RBAC)** - ROLE_USER and ROLE_ADMIN support
- **Strict Data Isolation** - Users can only access their own notes
- **Rate Limiting** on login endpoint to prevent brute force attacks
- **Security Headers** (CSP, HSTS, X-Frame-Options, etc.)
- **Input Validation** with safe error responses
- **Prepared Statements** - All database queries use parameterized queries to prevent SQL Injection
- **Comprehensive Logging** of security events
- **Integration Tests** with Testcontainers

## Technology Stack

- **Java 17**
- **Spring Boot 3.2.x**
- **PostgreSQL 16**
- **Flyway** for database migrations
- **JWT (jjwt 0.12.5)** for authentication
- **Bucket4j** for rate limiting
- **Testcontainers** for integration testing
- **Docker & Docker Compose** for containerization

## Quick Start

### Prerequisites

- Java 17+
- Docker & Docker Compose
- Maven 3.8+

### Option 1: Run with Docker Compose (Recommended)

This will start both PostgreSQL and the application in containers:

```bash
# Clone the repository
git clone <repository-url>
cd sowa

# Start all services
docker-compose up --build

# Or run in detached mode
docker-compose up -d --build
```

### Option 2: Run PostgreSQL in Docker, Application Locally

```bash
# Start only PostgreSQL
docker-compose up -d postgres

# Wait for PostgreSQL to be ready
docker-compose logs -f postgres
# (Wait until you see "database system is ready to accept connections")

# Run the application locally
./mvnw spring-boot:run

# Or on Windows
mvnw.cmd spring-boot:run
```

### Option 3: Run Everything Locally

If you have PostgreSQL installed locally:

```bash
# Create database
createdb securenotes

# Set environment variables
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/securenotes
export SPRING_DATASOURCE_USERNAME=your_username
export SPRING_DATASOURCE_PASSWORD=your_password

# Run the application
./mvnw spring-boot:run
```

### Stopping the Application

```bash
# Stop all Docker containers
docker-compose down

# Stop and remove volumes (clears database)
docker-compose down -v
```

### Access the Application

- **Frontend**: http://localhost:8080
- **API Base**: http://localhost:8080/api
- **Health Check**: http://localhost:8080/actuator/health

## API Endpoints

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/register` | Register new user |
| POST | `/auth/login` | Login and get tokens |
| POST | `/auth/refresh` | Refresh access token |
| POST | `/auth/logout` | Logout (revoke refresh token) |
| POST | `/auth/logout-all` | Logout from all devices |

### Notes (Requires Authentication)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/notes` | Get all user's notes |
| GET | `/api/notes/{id}` | Get specific note |
| POST | `/api/notes` | Create new note |
| PUT | `/api/notes/{id}` | Update note |
| DELETE | `/api/notes/{id}` | Delete note |

### Admin (Requires ROLE_ADMIN)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/dashboard` | Admin dashboard data |

## Security Features

### Authentication
- BCrypt password hashing
- Short-lived access tokens (15 min)
- Long-lived refresh tokens (7 days) with rotation
- Server-side token invalidation

### Security Headers
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`
- `Content-Security-Policy: script-src 'self'`
- `Strict-Transport-Security` (HSTS)

### Access Control
- **Role-Based Access Control (RBAC)** with ROLE_USER and ROLE_ADMIN
- All note operations verify ownership
- Admin endpoints restricted to ROLE_ADMIN only
- 404 returned for unauthorized access (prevents enumeration)
- Rate limiting on login (5 attempts/minute per IP)

### SQL Injection Prevention
- All database queries use **Prepared Statements** (parameterized queries)
- Native SQL queries with `@Query` annotation use `@Param` for safe parameter binding
- Spring Data JPA automatically handles parameter escaping

## Running Tests

```bash
# Unit tests
./mvnw test

# Integration tests (requires Docker)
./mvnw verify

# OWASP Dependency Check
./mvnw org.owasp:dependency-check-maven:check
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/securenotes` | Database URL |
| `SPRING_DATASOURCE_USERNAME` | `securenotes` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `securenotes123` | Database password |
| `JWT_SECRET` | (required) | Base64-encoded secret key (min 32 chars) |
| `JWT_ACCESS_EXPIRATION` | `900000` | Access token expiration (ms) |
| `JWT_REFRESH_EXPIRATION` | `604800000` | Refresh token expiration (ms) |

## Project Structure

```
src/
├── main/
│   ├── java/com/securenotes/
│   │   ├── config/          # Security, CORS, Rate limiting
│   │   ├── controller/      # REST endpoints (Auth, Notes, Admin)
│   │   ├── dto/             # Request/Response DTOs
│   │   ├── entity/          # JPA entities (User, Note, Role, RefreshToken)
│   │   ├── exception/       # Custom exceptions & GlobalExceptionHandler
│   │   ├── repository/      # Data access layer with native SQL queries
│   │   ├── security/        # JWT, filters, UserDetailsImpl
│   │   └── service/         # Business logic
│   └── resources/
│       ├── db/migration/    # Flyway migrations (V1: init, V2: roles)
│       ├── static/          # Frontend files
│       └── application.properties
└── test/
    └── java/com/securenotes/
        └── controller/      # Integration tests (AdminControllerIntegrationTest)
```

## Default Users

| Email | Password | Role |
|-------|----------|------|
| `admin@securenotes.com` | *(set during migration)* | ROLE_ADMIN |
| *(registered users)* | *(user defined)* | ROLE_USER |

> ⚠️ **Important**: Change the default admin password immediately in production!

## Database Schema

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   users     │     │ user_roles  │     │   roles     │
├─────────────┤     ├─────────────┤     ├─────────────┤
│ id (PK)     │◄────│ user_id(FK) │     │ id (PK)     │
│ username    │     │ role_id(FK) │────►│ name        │
│ email       │     └─────────────┘     └─────────────┘
│ password    │
│ created_at  │     ┌─────────────┐     ┌──────────────────┐
│ updated_at  │◄────│   notes     │     │ refresh_tokens   │
└─────────────┘     ├─────────────┤     ├──────────────────┤
                    │ id (PK)     │     │ id (PK)          │
                    │ title       │     │ token            │
                    │ content     │     │ user_id (FK)     │
                    │ user_id(FK) │     │ expires_at       │
                    └─────────────┘     │ revoked          │
                                        └──────────────────┘
```

## API Usage Examples

### Register a New User

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "email": "john@example.com",
    "password": "SecurePass123!"
  }'
```

### Login

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "SecurePass123!"
  }'
```

Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
  "refreshToken": "eyJhbGciOiJIUzM4NCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

### Create a Note (Authenticated)

```bash
curl -X POST http://localhost:8080/api/notes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{
    "title": "My First Note",
    "content": "This is the content of my note."
  }'
```

### Get All Notes (Authenticated)

```bash
curl -X GET http://localhost:8080/api/notes \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### Access Admin Dashboard (ROLE_ADMIN only)

```bash
curl -X GET http://localhost:8080/api/admin/dashboard \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>"
```

### Refresh Token

```bash
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<REFRESH_TOKEN>"
  }'
```

### Logout

```bash
curl -X POST http://localhost:8080/auth/logout \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<REFRESH_TOKEN>"
  }'
```

## Docker Configuration

### docker-compose.yml Services

| Service | Image | Port | Description |
|---------|-------|------|-------------|
| `postgres` | postgres:16-alpine | 5432 | PostgreSQL database |
| `app` | (built from Dockerfile) | 8080 | Spring Boot application |

### Docker Commands

```bash
# Build and start all services
docker-compose up --build

# Start in background
docker-compose up -d

# View logs
docker-compose logs -f

# View specific service logs
docker-compose logs -f app
docker-compose logs -f postgres

# Stop services
docker-compose down

# Stop and remove volumes
docker-compose down -v

# Rebuild without cache
docker-compose build --no-cache
```

### Production Deployment

For production, create a `.env` file:

```bash
# .env
POSTGRES_DB=securenotes_prod
POSTGRES_USER=securenotes_prod
POSTGRES_PASSWORD=<strong-password>
JWT_SECRET=<base64-encoded-secret-min-32-chars>
JWT_ACCESS_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=604800000
```

Generate a secure JWT secret:
```bash
# Generate a Base64-encoded secret
openssl rand -base64 32
```

Then run:
```bash
docker-compose --env-file .env up -d
```

## Troubleshooting

### Common Issues

**1. Database connection refused**
```bash
# Check if PostgreSQL is running
docker-compose ps

# Check PostgreSQL logs
docker-compose logs postgres
```

**2. JWT Secret error (500 on login)**
- Ensure `JWT_SECRET` is a valid Base64-encoded string
- Minimum 32 characters after decoding

**3. Port already in use**
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process
kill -9 <PID>
```

**4. Flyway migration failed**
```bash
# Reset database (WARNING: deletes all data)
docker-compose down -v
docker-compose up -d
```

## License

This project is for educational purposes as part of a Web Application Security course.
