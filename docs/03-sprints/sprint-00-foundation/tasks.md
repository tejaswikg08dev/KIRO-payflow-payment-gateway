# Sprint 0: Foundation — Tasks

**Sprint Duration:** 1 week  
**Total Parts:** 4 implementation parts

---

## Task Overview

| Part | Name | Duration | Status |
|------|------|----------|--------|
| 01 | Maven Project Setup | 2-3 hours | ⬜ |
| 02 | Docker Infrastructure | 2-3 hours | ⬜ |
| 03 | Common Library | 2-3 hours | ⬜ |
| 04 | Git & Documentation | 1-2 hours | ⬜ |

---

## Part 01: Maven Project Setup

**File:** [implementation/part-01-maven-setup.md](./implementation/part-01-maven-setup.md)

### Tasks

- [ ] Create root project folder
- [ ] Create parent pom.xml with dependency management
- [ ] Configure Spring Boot 3.2.x parent
- [ ] Configure Spring Cloud 2023.0.x BOM
- [ ] Add common dependencies (Lombok, validation)
- [ ] Create common-lib module folder
- [ ] Create common-lib/pom.xml
- [ ] Verify build: `mvn clean install`

### Verification

```powershell
mvn clean install
# Should output: BUILD SUCCESS
```

---

## Part 02: Docker Infrastructure

**File:** [implementation/part-02-docker-infrastructure.md](./implementation/part-02-docker-infrastructure.md)

### Tasks

- [ ] Create docker-compose-infra.yml
- [ ] Configure PostgreSQL service
- [ ] Configure Redis service
- [ ] Configure LocalStack service
- [ ] Create docker/ folder
- [ ] Create docker/init-db.sql (4 schemas)
- [ ] Create docker/init-localstack.sh
- [ ] Start and verify containers

### Verification

```powershell
docker compose -f docker-compose-infra.yml up -d
docker ps
# Should show 3 running containers
```

---

## Part 03: Common Library

**File:** [implementation/part-03-common-library.md](./implementation/part-03-common-library.md)

### Tasks

- [ ] Create package structure
- [ ] Create ApiResponse.java
- [ ] Create ErrorDetail.java
- [ ] Create PagedResponse.java
- [ ] Create PaymentStatus.java enum
- [ ] Create PaymentMethod.java enum
- [ ] Create PayflowException.java
- [ ] Create GlobalExceptionHandler.java
- [ ] Create IdGenerator.java
- [ ] Add unit tests
- [ ] Verify build

### Verification

```powershell
cd common-lib
mvn test
# All tests should pass
```

---

## Part 04: Git & Documentation

**File:** [implementation/part-04-git-documentation.md](./implementation/part-04-git-documentation.md)

### Tasks

- [ ] Create .gitignore file
- [ ] Update README.md
- [ ] Create CONTRIBUTING.md
- [ ] Initialize Git repository
- [ ] Create initial commit
- [ ] Create develop branch
- [ ] Push to remote (if applicable)

### Verification

```powershell
git status
# Should show clean working tree
git log --oneline
# Should show initial commit
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
- [ ] LocalStack accessible at localhost:4566
- [ ] All 4 database schemas exist

### Git Verification
- [ ] .gitignore excludes target/, node_modules/, .env
- [ ] Initial commit exists
- [ ] develop branch created

---

## Time Estimate

| Part | Estimated Time |
|------|----------------|
| Part 01 | 2-3 hours |
| Part 02 | 2-3 hours |
| Part 03 | 2-3 hours |
| Part 04 | 1-2 hours |
| **Total** | **7-11 hours** |

---

## Implementation Order

```
Start
  │
  ▼
Part 01: Maven Setup
  │ (creates project structure)
  ▼
Part 02: Docker Infrastructure
  │ (enables local databases)
  ▼
Part 03: Common Library
  │ (shared code ready)
  ▼
Part 04: Git & Documentation
  │ (version control ready)
  ▼
Sprint 0 Complete! 🎉
  │
  ▼
Continue to Sprint 1
```

---

**Start Implementation:** [implementation/part-01-maven-setup.md](./implementation/part-01-maven-setup.md)
