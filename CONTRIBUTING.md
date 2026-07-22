# Contributing to PayFlow Payment Gateway

Thank you for considering contributing to PayFlow! This document outlines the guidelines and setup instructions to help you get started.

## Prerequisites

- **Java 17+** (Eclipse Temurin recommended)
- **Maven 3.9+**
- **Node.js 18+** and **npm 9+** (for frontend apps)
- **Docker** and **Docker Compose** (for local infrastructure)
- **Git** (obviously)

## Local Setup

### 1. Clone the repository

```bash
git clone https://github.com/your-org/payflow-payment-gateway.git
cd payflow-payment-gateway
```

### 2. Start infrastructure (PostgreSQL, Redis, Kafka)

```bash
docker compose up -d
```

### 3. Build all backend services

```bash
mvn clean install -DskipTests
```

### 4. Run a specific service

```bash
cd payment-service
mvn spring-boot:run
```

### 5. Run frontend dashboard

```bash
cd frontend-dashboard
npm install
npm run dev
```

## Project Structure

```
payflow-payment-gateway/
├── api-gateway/            # Spring Cloud Gateway (port 8080)
├── merchant-service/       # Merchant onboarding & API keys (port 8081)
├── payment-service/        # Core payment processing (port 8083)
├── routing-service/        # Bank routing & acquirer selection (port 8084)
├── webhook-service/        # Webhook dispatch & retries (port 8085)
├── notification-service/   # Email/SMS notifications (port 8086)
├── bank-simulator/         # Mock bank for testing (port 9090)
├── common-lib/             # Shared DTOs, exceptions, utilities
├── frontend-dashboard/     # Merchant dashboard (React + Vite)
├── frontend-checkout/      # Customer-facing checkout (React + Vite)
├── infra/                  # Docker Compose, Terraform, configs
└── docs/                   # Architecture diagrams, API specs
```

## Branch Strategy

| Branch       | Purpose                                |
|--------------|----------------------------------------|
| `main`       | Production-ready code                  |
| `develop`    | Integration branch for features        |
| `feature/*`  | New features (branch from `develop`)   |
| `bugfix/*`   | Bug fixes (branch from `develop`)      |
| `hotfix/*`   | Urgent production fixes (from `main`)  |
| `release/*`  | Release preparation                    |

### Workflow

1. Create a branch from `develop`: `git checkout -b feature/your-feature-name`
2. Make your changes with clear, atomic commits
3. Push and open a Pull Request targeting `develop`
4. Ensure CI passes (build + tests + lint)
5. Get at least one approval before merging

## Commit Message Format

We follow **Conventional Commits**:

```
<type>(<scope>): <short description>

[optional body]

[optional footer]
```

### Types

- `feat` – New feature
- `fix` – Bug fix
- `docs` – Documentation changes
- `refactor` – Code change that doesn't fix a bug or add a feature
- `test` – Adding or updating tests
- `chore` – Build scripts, CI, dependency updates
- `perf` – Performance improvements

### Examples

```
feat(payment): add UPI payment method support
fix(webhook): retry exponential backoff calculation
docs(readme): add local setup instructions
test(payment): add capture payment unit tests
```

## How to Add a New Service

1. **Create the module directory** at the project root (e.g., `my-new-service/`)

2. **Add pom.xml** with the parent reference:
   ```xml
   <parent>
       <groupId>com.payflow</groupId>
       <artifactId>payflow-parent</artifactId>
       <version>0.1.0-SNAPSHOT</version>
   </parent>
   ```

3. **Add the module** to the parent `pom.xml`:
   ```xml
   <module>my-new-service</module>
   ```

4. **Create the Spring Boot main class** under `src/main/java/com/payflow/myservice/`

5. **Add application.yml** with the service port and common config

6. **Create a Dockerfile** following the multi-stage pattern from existing services

7. **Add to docker-compose.yml** with correct port mapping and dependencies

8. **Register the route** in `api-gateway/src/main/resources/application.yml`

9. **Add CI workflow** or extend the existing `ci-backend.yml`

## Code Style

### Java Conventions

- **Formatting**: Use default IntelliJ/Eclipse formatting (4-space indent, 120 char line width)
- **Naming**: Standard Java conventions – `camelCase` for methods/fields, `PascalCase` for classes
- **Annotations**: Place `@Slf4j`, `@Service`, `@RestController` on class level; `@Operation`, `@Tag` for OpenAPI
- **Records**: Use Java records for DTOs/value objects where immutability is desired
- **Lombok**: Use `@Data`, `@Builder`, `@RequiredArgsConstructor` to reduce boilerplate
- **Null Safety**: Prefer `Optional<T>` return types over nullable returns
- **Error Handling**: Throw domain exceptions (`PayflowException`, `ResourceNotFoundException`); never return raw error strings
- **Testing**: JUnit 5 + Mockito for unit tests; use `@ExtendWith(MockitoExtension.class)`
- **Logging**: Use SLF4J via Lombok's `@Slf4j`; include correlation IDs in log messages

### General

- Keep controllers thin – business logic belongs in `@Service` classes
- Every public REST endpoint must have `@Operation` and `@Tag` annotations for OpenAPI docs
- Wrap all responses in `ApiResponse<T>` from `common-lib`
- Use `BigDecimal` for all monetary amounts – never use `float` or `double`
