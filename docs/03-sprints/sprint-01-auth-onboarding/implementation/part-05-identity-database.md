# Sprint 1, Part 05: Identity Database

**Duration:** 1.5-2 hours  
**Prerequisites:** Part 04 completed, PostgreSQL running, Identity Service building successfully

---

## 1. What We're Building

In this part, you'll create the **database layer** for the Identity Service - entities, migrations, and repositories.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     DATABASE LAYER COMPONENTS                                │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                                                                      │   │
│  │  Controller → Service → Repository → Database                       │   │
│  │                              │                                       │   │
│  │              ┌───────────────┴───────────────┐                      │   │
│  │              │                               │                      │   │
│  │         ┌────▼────┐                   ┌─────▼─────┐                │   │
│  │         │ ENTITY  │                   │ MIGRATION │                │   │
│  │         │         │                   │           │                │   │
│  │         │ User.java                   │ V1__create                │   │
│  │         │ (in model                   │ _users.sql│                │   │
│  │         │ package)                    │           │                │   │
│  │         │         │                   │ SQL that                  │   │
│  │         │ Java class                  │ creates                   │   │
│  │         │ maps to                     │ tables                    │   │
│  │         │ database                    │           │                │   │
│  │         │ table                       │           │                │   │
│  │         └─────────┘                   └───────────┘                │   │
│  │                                                                      │   │
│  │  WHAT EACH DOES:                                                    │   │
│  │  ───────────────                                                    │   │
│  │  Entity: Java class representing a database row                     │   │
│  │  Repository: Interface for CRUD operations (no SQL needed!)        │   │
│  │  Migration: SQL scripts that create/modify schema                  │   │
│  │  DTO: Data Transfer Objects for API request/response               │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 JPA and Hibernate

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    JPA / HIBERNATE EXPLAINED                                 │
│                                                                              │
│  JPA (Java Persistence API) = Specification (interface)                     │
│  Hibernate = Implementation (actual code)                                   │
│                                                                              │
│  Like:  JDBC = specification,  PostgreSQL driver = implementation          │
│                                                                              │
│  WHY USE JPA/HIBERNATE?                                                     │
│  ─────────────────────                                                      │
│                                                                              │
│  Without JPA (raw JDBC):                                                    │
│  ────────────────────────                                                   │
│  String sql = "SELECT * FROM users WHERE email = ?";                       │
│  PreparedStatement ps = conn.prepareStatement(sql);                        │
│  ps.setString(1, email);                                                   │
│  ResultSet rs = ps.executeQuery();                                         │
│  while (rs.next()) {                                                       │
│      user.setId(rs.getString("id"));                                      │
│      user.setEmail(rs.getString("email"));                                │
│      // ... map every column manually                                      │
│  }                                                                          │
│                                                                              │
│  With JPA:                                                                   │
│  ─────────                                                                  │
│  User user = userRepository.findByEmail(email);                            │
│                                                                              │
│  JPA Benefits:                                                               │
│  • No SQL for basic CRUD                                                    │
│  • Automatic mapping (column → field)                                       │
│  • Database agnostic (switch databases easily)                             │
│  • Relationship management (OneToMany, etc.)                               │
│  • Caching                                                                  │
│  • Lazy loading                                                             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 2.2 Flyway Database Migrations

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    FLYWAY MIGRATIONS                                         │
│                                                                              │
│  Problem: How do you manage database schema changes across:                 │
│  • Multiple developers                                                       │
│  • Multiple environments (dev, staging, production)                         │
│  • Over time (weeks, months, years of changes)                              │
│                                                                              │
│  Solution: Version-controlled migration scripts                              │
│                                                                              │
│  MIGRATION FILE NAMING:                                                      │
│  ─────────────────────                                                      │
│  V1__create_users_table.sql                                                 │
│  │ │          │                                                             │
│  │ │          └── Description (underscores = spaces)                       │
│  │ └──────────── Double underscore separator                               │
│  └────────────── Version number (must be unique, sequential)               │
│                                                                              │
│  HOW IT WORKS:                                                               │
│  ─────────────                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ On application startup:                                              │   │
│  │                                                                      │   │
│  │ 1. Flyway checks flyway_schema_history table                        │   │
│  │    (creates if doesn't exist)                                       │   │
│  │                                                                      │   │
│  │ 2. Compares applied versions vs available scripts                   │   │
│  │    Applied: V1                                                      │   │
│  │    Available: V1, V2, V3                                            │   │
│  │    Pending: V2, V3                                                  │   │
│  │                                                                      │   │
│  │ 3. Runs pending migrations in order                                 │   │
│  │    Executing V2__add_new_column.sql...                             │   │
│  │    Executing V3__add_index.sql...                                  │   │
│  │                                                                      │   │
│  │ 4. Records success in history table                                 │   │
│  │    | version | description      | success |                        │   │
│  │    | 2       | add new column   | true    |                        │   │
│  │    | 3       | add index        | true    |                        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  GOLDEN RULES:                                                               │
│  • NEVER modify an applied migration                                        │
│  • Always create a new migration for changes                               │
│  • Always test migrations on a copy of production data                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 2.3 Repository Pattern

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SPRING DATA JPA REPOSITORIES                              │
│                                                                              │
│  Spring Data JPA generates implementations automatically!                    │
│                                                                              │
│  You write:                          Spring generates:                       │
│  ───────────                          ─────────────────                      │
│  interface UserRepository             SELECT * FROM users                   │
│    extends JpaRepository              INSERT INTO users                     │
│                                       UPDATE users                          │
│  findByEmail(email)          →        WHERE email = ?                       │
│                                                                              │
│  METHOD NAMING CONVENTION:                                                  │
│  ────────────────────────                                                   │
│  findBy + FieldName                   WHERE field_name = ?                  │
│  findByFieldNameAndOtherField         WHERE field_name = ? AND other = ?   │
│  findByFieldNameOrOtherField          WHERE field_name = ? OR other = ?    │
│  findByFieldNameOrderByOther          ORDER BY other                       │
│  findByFieldNameContaining            WHERE field_name LIKE %?%            │
│  findByFieldNameStartingWith          WHERE field_name LIKE ?%             │
│  existsByFieldName                    SELECT COUNT(*) > 0                  │
│  countByFieldName                     SELECT COUNT(*)                      │
│  deleteByFieldName                    DELETE FROM table                    │
│                                                                              │
│  BUILT-IN METHODS (from JpaRepository):                                     │
│  • save(entity) - Insert or update                                         │
│  • findById(id) - Find by primary key                                      │
│  • findAll() - Get all records                                             │
│  • delete(entity) - Delete record                                          │
│  • count() - Count records                                                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

```powershell
# PostgreSQL running with identity schema created
docker exec -it payflow-postgres psql -U payflow -d payflow -c "SELECT 1 FROM identity.users LIMIT 0"
# If schema doesn't exist yet, that's OK - Flyway will create it

# Identity Service builds successfully
cd identity-service
mvn clean compile
# Expected: BUILD SUCCESS
```

---

## 4. Step-by-Step Implementation


### Step 4.1: Create First Migration (Users Table)

**File: `identity-service/src/main/resources/db/migration/V1__create_users_table.sql`**

```sql
-- V1: Create users table for identity service
-- This migration runs automatically via Flyway on first startup

CREATE TABLE IF NOT EXISTS identity.users (
    id              VARCHAR(50) PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20),
    role            VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON identity.users(email);
CREATE INDEX idx_users_status ON identity.users(status);
```

**Understanding the Schema:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    USERS TABLE SCHEMA                                        │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ COLUMN           │ TYPE         │ PURPOSE                          │   │
│  ├──────────────────┼──────────────┼──────────────────────────────────┤   │
│  │ id               │ VARCHAR(50)  │ Primary key (10-char random ID)  │   │
│  │ email            │ VARCHAR(255) │ Login identifier (unique)        │   │
│  │ password_hash    │ VARCHAR(255) │ BCrypt hash (NEVER plain text!)  │   │
│  │ full_name        │ VARCHAR(100) │ User's display name              │   │
│  │ phone            │ VARCHAR(20)  │ Optional phone number            │   │
│  │ role             │ VARCHAR(20)  │ CUSTOMER, MERCHANT, or ADMIN     │   │
│  │ email_verified   │ BOOLEAN      │ Has user verified email?         │   │
│  │ status           │ VARCHAR(20)  │ ACTIVE, SUSPENDED, or DELETED    │   │
│  │ last_login_at    │ TIMESTAMP    │ Last successful login            │   │
│  │ created_at       │ TIMESTAMP    │ When account was created         │   │
│  │ updated_at       │ TIMESTAMP    │ When account was last modified   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  WHY VARCHAR(50) FOR ID INSTEAD OF UUID?                                    │
│  ────────────────────────────────────────                                   │
│  • PayFlow uses 10-character alphanumeric IDs (e.g., "a1B2c3D4e5")         │
│  • Generated using SecureRandom for security                               │
│  • Shorter than UUID (36 chars) but still unique                          │
│  • VARCHAR(50) gives room for future changes                              │
│                                                                              │
│  WHY USE identity SCHEMA?                                                   │
│  ────────────────────────                                                   │
│  • Each microservice has its own schema in PostgreSQL                      │
│  • Keeps tables organized and isolated                                     │
│  • identity.users, merchant.merchants, payment.transactions, etc.          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---


### Step 4.2: Create User Entity

**File: `identity-service/src/main/java/com/payflow/identity/model/User.java`**

> **Note:** The entity is in the `model` package, NOT `entity` package.

```java
package com.payflow.identity.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "users", schema = "identity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Role {
        CUSTOMER, MERCHANT, ADMIN
    }

    public enum UserStatus {
        ACTIVE, SUSPENDED, DELETED
    }
}
```


**Understanding the Entity:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    USER ENTITY ANNOTATIONS EXPLAINED                         │
│                                                                              │
│  CLASS-LEVEL ANNOTATIONS:                                                   │
│  ────────────────────────                                                   │
│  @Entity           → Marks this class as a JPA entity                      │
│  @Table(name="users", schema="identity")                                   │
│                    → Maps to identity.users table                          │
│  @Data             → Lombok: generates getters, setters, toString, etc.    │
│  @Builder          → Lombok: enables builder pattern                       │
│  @NoArgsConstructor→ Lombok: required by JPA                               │
│  @AllArgsConstructor→ Lombok: all-args constructor                         │
│                                                                              │
│  FIELD ANNOTATIONS:                                                         │
│  ─────────────────                                                          │
│  @Id               → Marks 'id' as primary key                             │
│  @Column(length=50)→ Column properties (max length)                        │
│  @Column(name="password_hash")                                             │
│                    → Maps Java 'passwordHash' to SQL 'password_hash'       │
│  @Enumerated(STRING)→ Stores enum name, not ordinal (safer!)               │
│  @Builder.Default  → Sets default value when using builder                 │
│  @CreationTimestamp→ Hibernate auto-sets on INSERT                         │
│  @UpdateTimestamp  → Hibernate auto-sets on UPDATE                         │
│                                                                              │
│  WHY STRING ID (NOT UUID)?                                                  │
│  ─────────────────────────                                                  │
│  PayFlow uses a custom IdGenerator that creates 10-character               │
│  alphanumeric IDs using SecureRandom. The ID is set by the                │
│  service layer before saving, NOT auto-generated by JPA.                   │
│                                                                              │
│  WHY INNER ENUMS?                                                           │
│  ────────────────                                                           │
│  Role and UserStatus are defined inside User class because:                │
│  • They're tightly coupled to the User entity                              │
│  • Reduces number of files                                                 │
│  • Access as: User.Role.CUSTOMER, User.UserStatus.ACTIVE                   │
│                                                                              │
│  WHY Instant INSTEAD OF LocalDateTime?                                      │
│  ─────────────────────────────────────                                      │
│  • Instant represents a point on the UTC timeline                          │
│  • No timezone ambiguity                                                   │
│  • Better for distributed systems                                          │
│  • Hibernate handles Instant <-> TIMESTAMP conversion                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---


### Step 4.3: Create User Repository

**File: `identity-service/src/main/java/com/payflow/identity/repository/UserRepository.java`**

```java
package com.payflow.identity.repository;

import com.payflow.identity.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
```

**Understanding the Repository:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    USER REPOSITORY EXPLAINED                                 │
│                                                                              │
│  JpaRepository<User, String>                                                │
│  ───────────────────────────                                                │
│  • User = The entity type this repository manages                          │
│  • String = The type of the primary key (id field)                         │
│                                                                              │
│  INHERITED METHODS (free from JpaRepository):                               │
│  ────────────────────────────────────────────                               │
│  • save(user)      → INSERT or UPDATE                                      │
│  • findById(id)    → SELECT * WHERE id = ?                                 │
│  • findAll()       → SELECT * FROM users                                   │
│  • deleteById(id)  → DELETE FROM users WHERE id = ?                        │
│  • count()         → SELECT COUNT(*) FROM users                            │
│  • existsById(id)  → SELECT COUNT(*) > 0 WHERE id = ?                      │
│                                                                              │
│  CUSTOM METHODS (Spring generates SQL from method name):                    │
│  ───────────────────────────────────────────────────────                    │
│  findByEmail(email)                                                         │
│  └─→ SELECT * FROM identity.users WHERE email = ?                          │
│                                                                              │
│  existsByEmail(email)                                                       │
│  └─→ SELECT COUNT(*) > 0 FROM identity.users WHERE email = ?               │
│                                                                              │
│  WHY Optional<User>?                                                        │
│  ─────────────────                                                          │
│  • findByEmail might not find a user                                       │
│  • Optional forces caller to handle null case                              │
│  • Prevents NullPointerException                                           │
│  • Usage: userRepo.findByEmail(email).orElseThrow(...)                     │
│                                                                              │
│  WHY ONLY TWO CUSTOM METHODS?                                               │
│  ───────────────────────────                                                │
│  • Simple is better - add methods as needed                                │
│  • findByEmail: Required for login (lookup by email)                       │
│  • existsByEmail: Required for registration (check duplicates)             │
│  • More methods can be added in future migrations                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---


### Step 4.4: Create DTOs (Data Transfer Objects)

**File: `identity-service/src/main/java/com/payflow/identity/dto/RegisterRequest.java`**

```java
package com.payflow.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    private String phone;

    @NotBlank(message = "Role is required")
    private String role; // CUSTOMER, MERCHANT
}
```

**Understanding RegisterRequest:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    REGISTER REQUEST DTO                                      │
│                                                                              │
│  FIELD           │ VALIDATION                 │ PURPOSE                     │
│  ─────────────────────────────────────────────────────────────────────────  │
│  email           │ @NotBlank, @Email          │ Login identifier            │
│  password        │ @NotBlank, @Size(8-100)    │ Plain text (hashed later)   │
│  fullName        │ @NotBlank, @Size(max=100)  │ Display name                │
│  phone           │ (optional)                 │ Contact number              │
│  role            │ @NotBlank                  │ CUSTOMER or MERCHANT        │
│                                                                              │
│  NOTE: This DTO has fullName (NOT firstName + lastName)                     │
│  The User entity also uses single fullName field.                           │
│                                                                              │
│  VALIDATION HAPPENS AT CONTROLLER LEVEL:                                    │
│  ───────────────────────────────────────                                    │
│  @PostMapping("/register")                                                  │
│  public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) │
│                                        ^^^^                                 │
│                                    This triggers validation                 │
│                                                                              │
│  If validation fails, Spring returns 400 Bad Request with error details.   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---


**File: `identity-service/src/main/java/com/payflow/identity/dto/LoginRequest.java`**

```java
package com.payflow.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
```

---

**File: `identity-service/src/main/java/com/payflow/identity/dto/AuthResponse.java`**

```java
package com.payflow.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn; // seconds
    private UserInfo user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String userId;
        private String email;
        private String fullName;
        private String role;
    }
}
```


**Understanding AuthResponse:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AUTH RESPONSE DTO                                         │
│                                                                              │
│  RESPONSE STRUCTURE:                                                        │
│  ──────────────────                                                         │
│  {                                                                          │
│    "accessToken": "eyJhbGc...",                                            │
│    "refreshToken": "dGhpcyBpcyB...",                                       │
│    "tokenType": "Bearer",                                                  │
│    "expiresIn": 3600,                                                      │
│    "user": {                                                               │
│      "userId": "a1B2c3D4e5",                                              │
│      "email": "user@example.com",                                         │
│      "fullName": "John Doe",                                              │
│      "role": "MERCHANT"                                                   │
│    }                                                                       │
│  }                                                                          │
│                                                                              │
│  WHY NESTED UserInfo CLASS?                                                 │
│  ──────────────────────────                                                 │
│  • Groups user-related fields together                                     │
│  • Makes JSON structure cleaner                                            │
│  • Static inner class keeps it in same file                                │
│  • Never exposes sensitive data (no passwordHash!)                         │
│                                                                              │
│  FIELD EXPLANATIONS:                                                        │
│  ───────────────────                                                        │
│  accessToken  → JWT for API authentication (short-lived: 1 hour)           │
│  refreshToken → Used to get new access tokens (long-lived: 7 days)         │
│  tokenType    → Always "Bearer" (standard for JWT)                         │
│  expiresIn    → Seconds until access token expires                         │
│  user         → Basic user info for frontend display                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Verification

### 5.1 Build and Run

```powershell
cd identity-service
mvn clean package -DskipTests
mvn spring-boot:run
```

**Expected output:**
```
INFO  --- Flyway : Migrating schema "identity" to version "1 - create users table"
INFO  --- Flyway : Successfully applied 1 migration
INFO  --- Started IdentityServiceApplication in X.XXX seconds
```


### 5.2 Verify Tables Created

```powershell
# Check tables exist
docker exec -it payflow-postgres psql -U payflow -d payflow -c "\dt identity.*"

# Expected output:
#              List of relations
#  Schema   |         Name         | Type  | Owner
# ----------+----------------------+-------+---------
#  identity | flyway_schema_history| table | payflow
#  identity | users                | table | payflow

# Check users table structure
docker exec -it payflow-postgres psql -U payflow -d payflow -c "\d identity.users"
```

---

## 6. File Structure

After completing this part:

```
identity-service/
├── src/
│   ├── main/
│   │   ├── java/com/payflow/identity/
│   │   │   ├── dto/
│   │   │   │   ├── AuthResponse.java      (with nested UserInfo)
│   │   │   │   ├── LoginRequest.java
│   │   │   │   └── RegisterRequest.java
│   │   │   ├── model/                     (NOT entity/)
│   │   │   │   └── User.java              (with Role & UserStatus enums)
│   │   │   └── repository/
│   │   │       └── UserRepository.java
│   │   └── resources/
│   │       └── db/migration/
│   │           └── V1__create_users_table.sql
```

**Important Differences from Generic Tutorials:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PAYFLOW-SPECIFIC DESIGN CHOICES                           │
│                                                                              │
│  WHAT GENERIC TUTORIALS SHOW     │ WHAT PAYFLOW USES                       │
│  ─────────────────────────────────────────────────────────────────────────  │
│  UUID primary key                 │ VARCHAR(50) with custom ID generator   │
│  package: entity/                 │ package: model/                         │
│  firstName, lastName separate     │ fullName combined                       │
│  Role.java separate file          │ Role as inner enum in User.java        │
│  LocalDateTime                    │ Instant (timezone-safe)                 │
│  @Getter @Setter                  │ @Data (combines both + more)            │
│  RefreshToken entity              │ Not implemented yet                     │
│  Complex account locking          │ Simple status: ACTIVE/SUSPENDED/DELETED │
│                                                                              │
│  These choices are intentional and match PayFlow's design decisions.        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Key Takeaways


```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHAT YOU LEARNED                                          │
│                                                                              │
│  ✅ JPA Entities                                                            │
│     • @Entity maps class to database table                                 │
│     • @Table with schema for namespace isolation                          │
│     • @Column defines column properties                                    │
│     • @Id marks primary key (String, not UUID)                             │
│     • @Enumerated(STRING) for enums                                       │
│     • Inner enums for Role and UserStatus                                 │
│                                                                              │
│  ✅ Flyway Migrations                                                       │
│     • Version-controlled database schema                                   │
│     • V1__description.sql naming convention                               │
│     • Migrations are immutable once applied                               │
│     • flyway_schema_history tracks applied migrations                     │
│     • Uses identity schema for isolation                                  │
│                                                                              │
│  ✅ Spring Data JPA Repositories                                           │
│     • Interface-based, implementations auto-generated                      │
│     • JpaRepository<User, String> for String primary key                  │
│     • Method naming convention generates SQL                              │
│     • Only add methods you need                                           │
│                                                                              │
│  ✅ DTOs vs Entities                                                       │
│     • Entities: Database representation                                    │
│     • DTOs: API request/response objects                                  │
│     • Never expose entities directly in API                               │
│     • Nested classes for related data (UserInfo in AuthResponse)          │
│                                                                              │
│  ✅ Validation Annotations                                                 │
│     • @NotBlank, @Email, @Size                                            │
│     • Validated at controller level with @Valid                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Q&A / Troubleshooting

### Q1: "Migration checksum mismatch" error

**Cause:** You modified an already-applied migration file.

**Fix:**
```powershell
# Option 1: Reset database (dev only!)
docker exec -it payflow-postgres psql -U payflow -c "DROP SCHEMA identity CASCADE;"
docker exec -it payflow-postgres psql -U payflow -c "CREATE SCHEMA identity;"

# Option 2: Repair Flyway (use if you really need to keep data)
mvn flyway:repair -Dflyway.url=jdbc:postgresql://localhost:5432/payflow \
    -Dflyway.user=payflow -Dflyway.password=payflow_secret \
    -Dflyway.schemas=identity
```

### Q2: "Entity not found for query" errors

**Cause:** Entity class not matching table structure.

**Fix:**
1. Compare entity fields with table columns
2. Check @Column name attributes match database column names
3. Verify schema name in @Table annotation
4. Run with `spring.jpa.hibernate.ddl-auto=validate` to catch mismatches

### Q3: Lombok getters/setters not working

**Cause:** Lombok not configured in IDE.

**Fix:**
1. Install Lombok plugin in your IDE
2. Enable annotation processing in IDE settings
3. Rebuild project

### Q4: "Cannot find User.Role" or "Cannot find User.UserStatus"

**Cause:** Role and UserStatus are inner enums.

**Fix:**
```java
// Wrong:
import com.payflow.identity.model.Role;

// Correct:
import com.payflow.identity.model.User;
// Then use: User.Role.CUSTOMER, User.UserStatus.ACTIVE
```

---


## 9. Related Concepts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    RELATED TOPICS TO EXPLORE                                 │
│                                                                              │
│  Custom ID Generation                                                       │
│  ────────────────────                                                       │
│  PayFlow uses IdGenerator (in common-lib) to create 10-character           │
│  alphanumeric IDs. Unlike auto-increment or UUID, these are:               │
│  • Short and readable                                                       │
│  • Generated using SecureRandom                                            │
│  • Set before entity is saved                                              │
│                                                                              │
│  Schema Isolation                                                           │
│  ────────────────                                                           │
│  Each microservice has its own PostgreSQL schema:                          │
│  • identity.users                                                          │
│  • merchant.merchants                                                      │
│  • payment.transactions                                                    │
│  This provides logical separation while using one database.                │
│                                                                              │
│  Soft Delete Pattern                                                        │
│  ─────────────────                                                         │
│  PayFlow uses UserStatus.DELETED instead of actually deleting rows.        │
│  This preserves audit trail and allows recovery.                           │
│                                                                              │
│  Optimistic vs Pessimistic Locking                                         │
│  ─────────────────────────────────                                         │
│  Handle concurrent updates. @Version for optimistic locking.               │
│  @Lock for pessimistic locking. (Future enhancement)                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHAT'S NEXT                                               │
│                                                                              │
│  ✅ Part 05 COMPLETE: Identity Database                                     │
│                                                                              │
│  NEXT: Part 06 - JWT Authentication                                         │
│  ──────────────────────────────────                                         │
│  In Part 06, we'll create:                                                  │
│  • JwtService for token generation                                         │
│  • AuthService for login/register logic                                    │
│  • Token signing with HMAC secret (HS256)                                  │
│  • Password hashing with BCrypt                                            │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  IDENTITY SERVICE BUILD PROGRESS                                    │   │
│  │                                                                      │   │
│  │  Part 04: Setup ✅                                                  │   │
│  │  Part 05: Database ✅      - Entity, migration, repository          │   │
│  │  Part 06: JWT Auth         - Token generation                       │   │
│  │  Part 07: Controllers      - REST endpoints                         │   │
│  │  Part 08: Swagger          - API docs                               │   │
│  │  Part 09: Testing          - Tests                                  │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Continue to: part-06-jwt-authentication.md                                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

**Part 05 Complete!** 🎉

You now have:
- User entity with Role and UserStatus enums
- Flyway migration creating identity.users table
- UserRepository for data access
- DTOs for API communication (RegisterRequest, LoginRequest, AuthResponse)
