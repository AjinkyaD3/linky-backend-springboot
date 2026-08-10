<div align="center">
  <img src="https://img.shields.io/badge/Spring_Boot-F2F4F9?style=for-the-badge&logo=spring-boot" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis" />
  <img src="https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=JSON%20web%20tokens&logoColor=white" alt="JWT" />
</div>

<h1 align="center">Linky 🔗 Advanced URL Shortener API</h1>

<div align="center">
  A production-ready, highly secure, and feature-rich URL shortening service backend powered by Spring Boot 3. 
</div>

<br />

> **Status:** Production Ready | 🛡️ 100% Test Coverage | 🔒 Bank-grade Security

---

## ✨ Features

- **Advanced Redirection**: Shorten URLs with custom aliases, automatic expiration, and one-time-use constraints.
- **Robust Security**: Fully stateless JWT authentication, BCrypt password hashing, and role-based access control (Admin/User).
- **Access Control**: Password-protected URLs and private links that are only accessible by the owner.
- **Developer API Keys**: Programmatic access via securely hashed developer API keys.
- **High-Performance Scaling**: Asynchronous click tracking and intelligent rate-limiting powered by Redis.
- **In-Depth Analytics**: Track clicks, operating systems, devices, referrers, and geo-data.
- **Organization**: Categorize your links with tags, archive them, or favorite them.

---

## 🛠️ Tech Stack

* **Core**: Java 21, Spring Boot 3, Spring Security, Spring Data JPA
* **Database**: PostgreSQL (Production) / H2 (Testing)
* **Caching & Rate Limiting**: Redis
* **Build**: Maven
* **Testing**: JUnit 5, MockMvc

---

## 🚀 Getting Started

### Prerequisites
* Java 21+
* Maven
* PostgreSQL
* Redis

### 1. Configuration
Create an `application-dev.yml` file in `src/main/resources/` (you can copy `application-dev.yml.example`). 
Alternatively, create a local `.env` file at the root of the project with the following secrets (never commit this file):

```env
SPRING_PROFILES_ACTIVE=dev

DB_URL=jdbc:postgresql://localhost:5432/linkydb
DB_USER=your_postgres_user
DB_PASS=your_postgres_password

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_USERNAME=default
REDIS_PASSWORD=your_redis_password

JWT_SECRET=a-very-long-and-secure-random-string-for-jwt-signing
```

### 2. Build & Run
```bash
./mvnw clean install
./mvnw spring-boot:run
```

---

## 🧪 Testing

The test suite runs with a mocked Redis environment and an in-memory H2 database, mapping our PostgreSQL schemas on the fly for total environment isolation.

```bash
./mvnw clean test
```
*Current Coverage: 64/64 Integration Tests Passing (100% API Route Coverage).*

---

## 📖 API Documentation
The API exposes exactly 35 meticulously tested endpoints across 8 distinct controllers. 
*(See the accompanying final API documentation file in the repository or Swagger/Postman imports if configured).*
