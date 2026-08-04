# Sprint 0, Part 01: Project Initialization (Maven Setup)

**Duration:** 2-3 hours  
**Prerequisites:** Java 17, Maven installed

---

## 1. What We're Building

In this part, you'll create the **Maven multi-module project** structure for PayFlow. This is the foundation for all 11 microservices.

### Why Multi-Module?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Why Multi-Module Maven Project?                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   WITHOUT Multi-Module:              WITH Multi-Module:                     │
│                                                                              │
│   service-a/pom.xml                  payflow/                               │
│     └── spring-boot: 3.2.0           ├── pom.xml (parent)                  │
│   service-b/pom.xml                  │   └── spring-boot: 3.2.5            │
│     └── spring-boot: 3.2.1 ← MISMATCH│       (version in ONE place)        │
│   service-c/pom.xml                  ├── common-lib/                        │
│     └── spring-boot: 3.1.0 ← OLD     ├── identity-service/                  │
│                                      └── payment-service/                   │
│   Problems:                                                                  │
│   • Different versions everywhere    Benefits:                              │
│   • Duplicate dependency config      • Single source of truth               │
│   • Hard to maintain                 • Easy version upgrades                │
│   • No shared code                   • Shared common-lib                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### Maven POM Hierarchy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Maven POM Inheritance                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   spring-boot-starter-parent                                                │
│   (Spring provides this)                                                    │
│           │                                                                  │
│           │ inherits                                                        │
│           ▼                                                                  │
│   ┌───────────────────┐                                                     │
│   │  payflow/pom.xml  │  ◄── Parent POM (you create this)                  │
│   │  (Parent POM)     │      • Defines all dependency versions              │
│   │                   │      • Configures plugins                           │
│   │  <modules>        │      • Lists all child modules                      │
│   │    common-lib     │                                                     │
│   │    identity-svc   │                                                     │
│   │    payment-svc    │                                                     │
│   │  </modules>       │                                                     │
│   └─────────┬─────────┘                                                     │
│             │                                                                │
│      ┌──────┴──────┬────────────┐                                          │
│      │             │            │                                           │
│      ▼             ▼            ▼                                           │
│   ┌──────┐     ┌──────┐    ┌──────┐                                        │
│   │common│     │ident │    │paymnt│  ◄── Child modules                     │
│   │ lib  │     │ svc  │    │ svc  │      • Inherit from parent            │
│   └──────┘     └──────┘    └──────┘      • No version numbers needed       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Maven Concepts

| Concept | Purpose | Example |
|---------|---------|---------|
| `<parent>` | Inherit from another POM | spring-boot-starter-parent |
| `<modules>` | List child modules | common-lib, identity-service |
| `<dependencyManagement>` | Define versions (not include) | Spring Cloud BOM |
| `<dependencies>` | Actually include dependencies | Lombok, validation |
| `<properties>` | Reusable variables | java.version=17 |

---

## 3. Prerequisites

Before starting, verify:

```powershell
# Check Java version
java -version
# Expected: openjdk version "17.x.x"

# Check Maven version
mvn -version
# Expected: Apache Maven 3.9.x
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Create Project Root Folder

```powershell
# Navigate to your projects directory
cd C:\Projects  # or your preferred location

# Create project folder
mkdir payflow-payment-gateway
cd payflow-payment-gateway
```


### Step 4.2: Create Parent POM

Create `pom.xml` in the root folder:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 
        PARENT POM — This is the root of our multi-module Maven project.
        It defines:
        1. Common properties (Java version, Spring Boot version, etc.)
        2. All child modules (services)
        3. Dependency management (version control for ALL dependencies)
        4. Common plugins (compiler, Docker, etc.)
        
        Each child service inherits from this POM, so we manage versions in ONE place.
    -->

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.payflow</groupId>
    <artifactId>payflow-payment-gateway</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>PayFlow Payment Gateway</name>
    <description>Production-ready Payment Gateway with ISO 8583 - Microservices</description>

    <!-- All child modules (services) listed here -->
    <modules>
        <module>common-lib</module>
        <module>service-registry</module>
        <module>config-server</module>
        <module>api-gateway</module>
        <module>identity-service</module>
        <module>merchant-service</module>
        <module>payment-service</module>
        <module>routing-service</module>
        <module>settlement-service</module>
        <module>webhook-service</module>
        <module>notification-service</module>
        <module>bank-simulator</module>
    </modules>

    <!-- Centralized version management -->
    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2023.0.1</spring-cloud.version>
        <springdoc.version>2.3.0</springdoc.version>
        <mapstruct.version>1.5.5.Final</mapstruct.version>
        <lombok.version>1.18.32</lombok.version>
        <resilience4j.version>2.2.0</resilience4j.version>
    </properties>
```

**Line-by-Line Explanation:**

| Line | What It Does |
|------|--------------|
| `<parent>` | Inherits from Spring Boot 3.2.5 parent POM |
| `<groupId>` | Like a Java package name: com.payflow |
| `<artifactId>` | The project's name: payflow-payment-gateway |
| `<packaging>pom` | This is a parent POM, not a JAR |
| `<modules>` | Lists ALL 12 child projects |
| `<properties>` | Variables reused throughout the POM |


Continue the pom.xml (Dependency Management):

```xml
    <!-- 
        Dependency Management — Controls versions for ALL child modules.
        Child modules declare dependencies WITHOUT version numbers.
        Version is inherited from here. This prevents version conflicts.
    -->
    <dependencyManagement>
        <dependencies>
            <!-- Spring Cloud BOM (Bill of Materials) -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- SpringDoc OpenAPI (Swagger UI) -->
            <dependency>
                <groupId>org.springdoc</groupId>
                <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
                <version>${springdoc.version}</version>
            </dependency>

            <!-- MapStruct (DTO mapping) -->
            <dependency>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct</artifactId>
                <version>${mapstruct.version}</version>
            </dependency>
            <dependency>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>${mapstruct.version}</version>
            </dependency>

            <!-- Resilience4j -->
            <dependency>
                <groupId>io.github.resilience4j</groupId>
                <artifactId>resilience4j-spring-boot3</artifactId>
                <version>${resilience4j.version}</version>
            </dependency>

            <!-- Our common library -->
            <dependency>
                <groupId>com.payflow</groupId>
                <artifactId>common-lib</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
```

**What each dependency does:**

| Dependency | Purpose |
|------------|---------|
| `spring-cloud-dependencies` | BOM for Eureka, Config, Gateway, Feign |
| `springdoc-openapi` | Auto-generates Swagger UI documentation |
| `mapstruct` | Compile-time DTO ↔ Entity mapping |
| `resilience4j` | Circuit breaker, retry, rate limiter |
| `common-lib` | Our shared DTOs, exceptions, utilities |


Continue the pom.xml (Dependencies and Build):

```xml
    <!-- Dependencies shared by ALL child modules -->
    <dependencies>
        <!-- Lombok (available in all services) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Testing (available in all services) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Java compiler settings -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                        <path>
                            <groupId>org.mapstruct</groupId>
                            <artifactId>mapstruct-processor</artifactId>
                            <version>${mapstruct.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

**Build section explanation:**

| Section | Purpose |
|---------|---------|
| `annotationProcessorPaths` | Tells compiler to process Lombok and MapStruct annotations |
| Lombok processor | Generates getters, setters, builders at compile time |
| MapStruct processor | Generates mapper implementations at compile time |


### Step 4.3: Create common-lib Module

```powershell
# Create common-lib folder structure
mkdir common-lib
mkdir common-lib\src\main\java\com\payflow\common
mkdir common-lib\src\main\java\com\payflow\common\dto
mkdir common-lib\src\main\java\com\payflow\common\constant
mkdir common-lib\src\main\java\com\payflow\common\exception
mkdir common-lib\src\main\java\com\payflow\common\util
mkdir common-lib\src\test\java\com\payflow\common
```


### Step 4.4: Create common-lib/pom.xml

Create `common-lib/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.payflow</groupId>
        <artifactId>payflow-payment-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>common-lib</artifactId>
    <name>PayFlow Common Library</name>
    <description>Shared DTOs, exceptions, utilities, and constants used across all services</description>

    <!-- This module is a library (JAR), not a runnable app -->
    <packaging>jar</packaging>

    <dependencies>
        <!-- Spring Web (for annotations like @ResponseStatus) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Validation (for @NotNull, @Size, etc. on DTOs) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Jackson (JSON annotations for DTOs) -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-annotations</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Do NOT build as Spring Boot executable JAR (this is a library) -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <skip>true</skip>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

**Important points:**

| Section | Purpose |
|---------|---------|
| `<packaging>jar` | This is a library, not an executable |
| `spring-boot-maven-plugin skip` | Don't create executable JAR - other services include this as dependency |
| No `<version>` in dependencies | Versions inherited from parent |

---

## 5. Verification

### Build the Project

```powershell
# From root folder
mvn clean install
```

**Expected Output:**

```
[INFO] Reactor Build Order:
[INFO] 
[INFO] PayFlow Payment Gateway                            [pom]
[INFO] PayFlow Common Library                             [jar]
[INFO] 
[INFO] ----< com.payflow:payflow-payment-gateway >----
[INFO] Building PayFlow Payment Gateway 1.0.0-SNAPSHOT    [1/2]
...
[INFO] ----< com.payflow:common-lib >----
[INFO] Building PayFlow Common Library 1.0.0-SNAPSHOT     [2/2]
...
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary:
[INFO] 
[INFO] PayFlow Payment Gateway 1.0.0-SNAPSHOT ......... SUCCESS [  0.5 s]
[INFO] PayFlow Common Library 1.0.0-SNAPSHOT .......... SUCCESS [  3.2 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Verify Structure

```powershell
dir
```

**Expected:**
```
    Directory: C:\Projects\payflow-payment-gateway

Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d-----         8/4/2026   2:00 PM                common-lib
-a----         8/4/2026   2:00 PM           4500 pom.xml
```

---

## 6. File Structure After This Part

```
payflow-payment-gateway/
├── pom.xml                         # Parent POM (you just created)
└── common-lib/
    ├── pom.xml                     # Child module POM
    └── src/
        └── main/
            └── java/
                └── com/
                    └── payflow/
                        └── common/
                            ├── dto/
                            ├── constant/
                            ├── exception/
                            └── util/
```

---

## 7. Key Takeaways

| Concept | Remember |
|---------|----------|
| **Parent POM** | Manages versions for all 12 modules |
| **Multi-module** | `<modules>` lists all child projects |
| **dependencyManagement** | Defines versions, doesn't actually include |
| **dependencies** | Actually includes the dependency |
| **packaging: pom** | Parent POM, not a JAR |
| **${project.version}** | Refers to current project version (1.0.0-SNAPSHOT) |

---

## 8. Q&A / Troubleshooting

### "Cannot find parent POM"

**Cause:** Running Maven from wrong directory  
**Fix:** Make sure you're in `payflow-payment-gateway/` root folder

### "Dependency not found"

**Cause:** Typo in dependency coordinates  
**Fix:** Check spelling in `<dependencyManagement>` and ensure parent POM is installed first

### "Plugin not found: spring-boot-maven-plugin"

**Cause:** Parent POM not inheriting from spring-boot-starter-parent  
**Fix:** Verify `<parent>` section points to correct Spring Boot version

---

## 9. Related Concepts

| Topic | What to Learn Next | When Needed |
|-------|-------------------|-------------|
| Spring Cloud BOM | How version alignment works | Sprint 1 |
| MapStruct | Compile-time mapper generation | Sprint 1+ |
| Resilience4j | Circuit breakers | Sprint 2+ |
| Maven Profiles | Different configs for dev/prod | Phase 13 |

---

## 10. Next Steps

**Continue to:** [part-02-common-lib-setup.md](./part-02-common-lib-setup.md)

In the next part, you'll create the common library with shared DTOs, exceptions, and utilities that all microservices will use.
