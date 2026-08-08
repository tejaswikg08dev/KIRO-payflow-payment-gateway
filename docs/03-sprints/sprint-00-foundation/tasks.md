# Sprint 0: Foundation — Tasks

**Sprint Duration:** 1 week  
**Total Parts:** 5 implementation parts

---

## Task Overview

| Part | Name | Duration | Status |
|------|------|----------|--------|
| 01 | Project Initialization | 2-3 hours | ⬜ |
| 02 | Common Library Setup | 2-3 hours | ⬜ |
| 03 | Docker Infrastructure | 2-3 hours | ⬜ |
| 04 | Git Workflow | 1-2 hours | ⬜ |
| 05 | Verification | 1 hour | ⬜ |

---

## Part 01: Project Initialization

**File:** [implementation/part-01-project-initialization.md](./implementation/part-01-project-initialization.md)

### Tasks

- [ ] Create root project folder `KIRO-payflow-payment-gateway`
- [ ] Create parent pom.xml with dependency management
- [ ] Configure Spring Boot 3.2.5 parent
- [ ] Configure Spring Cloud 2023.0.x BOM
- [ ] Add common dependencies (Lombok, validation)
- [ ] Create common-lib module folder structure
- [ ] Configure Maven compiler plugin (Java 17)
- [ ] Verify build: `mvn clean install`

### Verification

```powershell
mvn clean install -DskipTests
# Should output: BUILD SUCCESS
```

---

## Part 02: Common Library Setup

**File:** [implementation/part-02-common-lib-setup.md](./implementation/part-02-common-lib-setup.md)

### Tasks

- [ ] Create common-lib/pom.xml
- [ ] Create package structure: `com.payflow.common`
- [ ] Create dto package with:
  - [ ] ApiResponse.java (generic wrapper)
  - [ ] ErrorDetail.java
  - [ ] PagedResponse.java
- [ ] Create enums package with:
  - [ ] PaymentStatus.java
  - [ ] PaymentMethod.java
  - [ ] TransactionType.java
- [ ] Create exception package with:
  - [ ] PayflowException.java
  - [ ] ResourceNotFoundException.java
  - [ ] DuplicateResourceException.java
  - [ ] GlobalExceptionHandler.java
- [ ] Create util package with:
  - [ ] IdGenerator.java (unique ID generation)
- [ ] Add unit tests for IdGenerator
- [ ] Verify build: `mvn test`

### Verification

```powershell
cd common-lib
mvn test
# All tests should pass
```

---

## Part 03: Docker Infrastructure

**File:** [implementation/part-03-docker-infrastructure.md](./implementation/part-03-docker-infrastructure.md)

### Tasks

- [ ] Create docker-compose-infra.yml in project root
- [ ] Configure PostgreSQL 15 service:
  - [ ] Port 5432
  - [ ] Database: payflow
  - [ ] Volume for data persistence
- [ ] Configure Redis 7 service:
  - [ ] Port 6379
  - [ ] Volume for data persistence
- [ ] Configure LocalStack service (optional):
  - [ ] Port 4566
  - [ ] Services: SQS, SNS, DynamoDB
- [ ] Create docker/ folder
- [ ] Create docker/init-db.sql with 4 schemas:
  - [ ] identity schema
  - [ ] merchant schema
  - [ ] payment schema
  - [ ] settlement schema
- [ ] Create docker/init-localstack.sh (optional)
- [ ] Start and verify containers

### Verification

```powershell
docker compose -f docker-compose-infra.yml up -d
docker ps
# Should show: postgres, redis running

# Test PostgreSQL connection
docker exec -it payflow-postgres psql -U postgres -d payflow -c "\dn"
# Should show 4 schemas
```

---

## Part 04: Git Workflow

**File:** [implementation/part-04-git-workflow.md](./implementation/part-04-git-workflow.md)

### Tasks

- [ ] Create .gitignore file with:
  - [ ] target/
  - [ ] node_modules/
  - [ ] .env
  - [ ] *.log
  - [ ] IDE files (.idea/, .vscode/)
- [ ] Create/Update README.md with:
  - [ ] Project overview
  - [ ] Prerequisites
  - [ ] Quick start guide
  - [ ] Architecture overview
- [ ] Create CONTRIBUTING.md (optional)
- [ ] Initialize Git repository: `git init`
- [ ] Create initial commit
- [ ] Create develop branch
- [ ] Push to remote (GitHub)

### Verification

```powershell
git status
# Should show clean working tree

git log --oneline
# Should show initial commit

git branch
# Should show: main, develop
```

---

## Part 05: Verification

**File:** [implementation/part-05-verification.md](./implementation/part-05-verification.md)

### Tasks

- [ ] Verify Maven build from root
- [ ] Verify all Docker containers running
- [ ] Verify database schemas exist
- [ ] Verify Redis connection
- [ ] Document any issues found
- [ ] Update README if needed

### Verification Checklist

```powershell
# 1. Maven build
mvn clean install
# Expected: BUILD SUCCESS

# 2. Docker containers
docker compose -f docker-compose-infra.yml up -d
docker ps
# Expected: postgres, redis running

# 3. Database schemas
docker exec payflow-postgres psql -U postgres -d payflow -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('identity', 'merchant', 'payment', 'settlement');"
# Expected: 4 rows

# 4. Redis
docker exec payflow-redis redis-cli PING
# Expected: PONG
```

---

## Sprint Completion Checklist

### Build Verification
- [ ] `mvn clean install` succeeds from root
- [ ] No compilation errors
- [ ] All tests pass

### Docker Verification
- [ ] PostgreSQL accessible at localhost:5432
- [ ] Redis accessible at localhost:6379
- [ ] All 4 database schemas exist

### Git Verification
- [ ] .gitignore excludes target/, node_modules/, .env
- [ ] Initial commit exists
- [ ] Repository pushed to GitHub

---

## Time Estimate

| Part | Estimated Time |
|------|----------------|
| Part 01: Project Initialization | 2-3 hours |
| Part 02: Common Library Setup | 2-3 hours |
| Part 03: Docker Infrastructure | 2-3 hours |
| Part 04: Git Workflow | 1-2 hours |
| Part 05: Verification | 1 hour |
| **Total** | **8-12 hours** |

---

## Implementation Order

```
Start
  │
  ▼
Part 01: Project Initialization
  │ (creates Maven project structure)
  ▼
Part 02: Common Library Setup
  │ (shared code ready for use)
  ▼
Part 03: Docker Infrastructure
  │ (enables local databases)
  ▼
Part 04: Git Workflow
  │ (version control ready)
  ▼
Part 05: Verification
  │ (confirms everything works)
  ▼
Sprint 0 Complete! 🎉
  │
  ▼
Continue to Sprint 1
```

---

## File Structure After Sprint 0

```
KIRO-payflow-payment-gateway/
├── common-lib/
│   ├── pom.xml
│   └── src/main/java/com/payflow/common/
│       ├── dto/
│       │   ├── ApiResponse.java
│       │   ├── ErrorDetail.java
│       │   └── PagedResponse.java
│       ├── enums/
│       │   ├── PaymentStatus.java
│       │   ├── PaymentMethod.java
│       │   └── TransactionType.java
│       ├── exception/
│       │   ├── PayflowException.java
│       │   ├── ResourceNotFoundException.java
│       │   ├── DuplicateResourceException.java
│       │   └── GlobalExceptionHandler.java
│       └── util/
│           └── IdGenerator.java
├── docker/
│   ├── init-db.sql
│   └── init-localstack.sh
├── docker-compose-infra.yml
├── pom.xml
├── .gitignore
└── README.md
```

---

**Start Implementation:** [implementation/part-01-project-initialization.md](./implementation/part-01-project-initialization.md)
