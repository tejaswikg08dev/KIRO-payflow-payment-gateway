# Maven — Complete In-Depth Guide (Everything You Need to Know)

> This guide covers Maven from absolute zero to advanced multi-module project management.
> Written for first-time learners who want to understand every concept deeply.
> After reading this: you'll understand what Maven does, how it works internally,
> and how to use it confidently in real projects like PayFlow.

---

## Table of Contents

1. [What Is Maven?](#1-what-is-maven)
2. [Why Do We Need a Build Tool?](#2-why-do-we-need-a-build-tool)
3. [Installation (Windows Step by Step)](#3-installation-windows-step-by-step)
4. [Core Concepts](#4-core-concepts)
5. [The POM File (pom.xml) — Complete Anatomy](#5-the-pom-file-pomxml--complete-anatomy)
6. [Build Lifecycle — Every Phase Explained](#6-build-lifecycle--every-phase-explained)
7. [Dependency Management — How It Really Works](#7-dependency-management--how-it-really-works)
8. [Plugins — How Maven Actually Does Things](#8-plugins--how-maven-actually-does-things)
9. [Multi-Module Projects](#9-multi-module-projects)
10. [Profiles — Different Configs for Different Environments](#10-profiles--different-configs-for-different-environments)
11. [Maven Wrapper (mvnw)](#11-maven-wrapper-mvnw)
12. [Maven Repository System](#12-maven-repository-system)
13. [Maven in Your PayFlow Project](#13-maven-in-your-payflow-project)
14. [Troubleshooting Common Problems](#14-troubleshooting-common-problems)
15. [Commands Cheat Sheet](#15-commands-cheat-sheet)
16. [Interview Questions & Answers](#16-interview-questions--answers)

---

## 1. What Is Maven?

Maven is a **build automation and project management tool** for Java (and other JVM languages). Created by Apache in 2004, it's used by ~60% of all Java projects.

**One-line definition:** Maven takes your Java source code, downloads libraries it needs, compiles it, tests it, and packages it into a runnable JAR — all with one command.

```
WHAT MAVEN DOES:

┌─────────────────────────────────────────────────────────────┐
│                        MAVEN                                 │
│                                                              │
│  INPUT:                          OUTPUT:                     │
│  ├── Your .java source files     ├── Compiled .class files  │
│  ├── pom.xml (instructions)      ├── Runnable .jar file     │
│  └── Internet (Maven Central)    ├── Test reports           │
│                                  └── Documentation          │
│                                                              │
│  DOES AUTOMATICALLY:                                         │
│  ├── Downloads all libraries (and THEIR libraries)          │
│  ├── Compiles Java source to bytecode                       │
│  ├── Runs your unit tests                                   │
│  ├── Packages everything into a JAR/WAR                     │
│  ├── Installs JAR to local cache for other projects         │
│  └── Can deploy to remote servers                           │
└─────────────────────────────────────────────────────────────┘
```

**The name "Maven" means:** "accumulator of knowledge" (Yiddish). The idea is that Maven accumulates best practices for building Java projects.

---

## 2. Why Do We Need a Build Tool?

### Without Maven (Manual Process)

Imagine building a Spring Boot project without any build tool:

```
STEP 1: Download JARs manually
        → Go to spring.io, download spring-web-6.1.6.jar
        → Go to fasterxml.com, download jackson-databind-2.17.0.jar
        → Go to ... download 50 more JARs
        → But wait! spring-web needs spring-core... download that too
        → And spring-core needs spring-jcl... download THAT too
        → 150+ JARs later... maybe you got them all? 🤷

STEP 2: Compile your code
        javac -cp lib/spring-web.jar;lib/jackson.jar;lib/...;(150 more) \
              -d target/classes \
              src/main/java/com/payflow/**/*.java
        → Did you get the classpath right? Probably not on first try.

STEP 3: Run tests
        java -cp target/classes;lib/*;test-lib/* \
             org.junit.runner.JUnitCore \
             com.payflow.PaymentServiceTest
        → Different classpath for test dependencies...

STEP 4: Package into JAR
        jar -cf payment-service.jar -C target/classes .
        → But this doesn't include dependencies!
        → Customers need ALL 150 JARs too? 😱

STEP 5: Team collaboration
        → Developer B joins the team
        → "Which JARs do I need?" "What versions?" "Where do I get them?"
        → Spends 2 days setting up the environment

STEP 6: Update a library
        → spring-web 6.1.6 → 6.1.7
        → But spring-web 6.1.7 needs spring-core 6.1.7...
        → And spring-core 6.1.7 needs spring-jcl 6.1.7...
        → 20 JARs need updating? Which ones? 😭
```

### With Maven (Automated)

```
STEP 1: Write pom.xml (5 lines per dependency)
STEP 2: Run: mvn clean install
STEP 3: Done. ✅

Maven does EVERYTHING else:
├── Downloads all 150+ JARs (and their transitive dependencies)
├── Figures out which versions are compatible
├── Compiles with the correct classpath
├── Runs tests
├── Packages into a self-contained executable JAR
├── New developer joins? Just: git clone + mvn clean install
└── Update Spring Boot? Change ONE version number → Maven handles the cascade
```

---

## 3. Installation (Windows Step by Step)

### Prerequisites: Java 17

```
STEP 1: Check if Java is installed
        Open cmd → type: java -version

        If you see "java version 17.x.x" → SKIP to Maven installation
        If you see "'java' is not recognized" → Install Java:

STEP 2: Download JDK 17
        → Go to: https://adoptium.net/
        → Select:
           Operating System: Windows
           Architecture: x64
           Package Type: JDK
           Version: 17 (LTS)
        → Click: Latest release (.msi)

STEP 3: Run the installer
        → Double-click the .msi file
        → IMPORTANT: Check these boxes:
           ☑ Add to PATH
           ☑ Set JAVA_HOME variable
           ☑ Associate .jar files
        → Click Next → Install → Finish

STEP 4: Verify (open a NEW cmd window — old ones won't have the PATH)
        java -version       → Should show: openjdk version "17.0.x"
        echo %JAVA_HOME%   → Should show: C:\Program Files\Eclipse Adoptium\jdk-17.x.x
```

### Install Maven

```
STEP 1: Download Maven
        → Go to: https://maven.apache.org/download.cgi
        → Under "Files" → download: apache-maven-3.9.9-bin.zip
        → (Get the -bin.zip, NOT the -src.zip)

STEP 2: Extract the ZIP
        → Right-click the .zip → Extract All
        → Extract to: C:\tools\
        → Result: C:\tools\apache-maven-3.9.9\
        → Verify: C:\tools\apache-maven-3.9.9\bin\mvn.cmd exists

STEP 3: Set MAVEN_HOME environment variable
        → Press Windows key → type "Environment" → click "Edit the system environment variables"
        → Click "Environment Variables" button (bottom)
        → Under "System variables" → click "New"
           Variable name:  MAVEN_HOME
           Variable value: C:\tools\apache-maven-3.9.9
        → Click OK

STEP 4: Add Maven to PATH
        → Still in "System variables" → find "Path" → click "Edit"
        → Click "New"
        → Add: C:\tools\apache-maven-3.9.9\bin
        → Click OK → OK → OK (close all dialogs)

STEP 5: Verify (MUST open a NEW cmd window)
        mvn -version

        Expected output:
        ─────────────────────────────────────────
        Apache Maven 3.9.9 (...)
        Maven home: C:\tools\apache-maven-3.9.9
        Java version: 17.0.x, vendor: Eclipse Adoptium
        Default locale: en_US, platform encoding: UTF-8
        OS name: "windows 10", version: "10.0", arch: "amd64"
        ─────────────────────────────────────────

        If you see this → Maven is installed correctly ✅
```

### What Got Installed?

```
C:\tools\apache-maven-3.9.9\
├── bin\
│   ├── mvn.cmd           ← The Maven command (what you type in cmd)
│   └── mvnDebug.cmd      ← Maven with debug port open
├── boot\
│   └── plexus-classworlds-2.7.0.jar  ← Maven's bootstrap loader
├── conf\
│   └── settings.xml      ← Global Maven settings (proxy, mirrors, etc.)
├── lib\
│   └── (Maven's own JARs — you never touch these)
└── LICENSE, NOTICE, README.txt

After first use, Maven creates:
C:\Users\YourName\.m2\
├── repository\           ← LOCAL CACHE of all downloaded JARs
│   └── (thousands of JARs from all your projects)
└── settings.xml          ← User-level settings (optional, overrides global)
```

---

## 4. Core Concepts

### 4.1 Convention Over Configuration

Maven's #1 philosophy. Instead of configuring everything, Maven assumes a standard project structure:

```
Standard Maven project layout:
my-project/
├── pom.xml                              ← THE build file (required)
├── src/
│   ├── main/
│   │   ├── java/                        ← Your production Java source code
│   │   │   └── com/payflow/App.java
│   │   └── resources/                   ← Non-Java files (YAML, SQL, properties)
│   │       └── application.yml
│   └── test/
│       ├── java/                        ← Your test source code
│       │   └── com/payflow/AppTest.java
│       └── resources/                   ← Test-only resources
│           └── test-data.json
└── target/                              ← OUTPUT folder (Maven creates this)
    ├── classes/                          ← Compiled .class files
    ├── test-classes/                     ← Compiled test .class files
    ├── surefire-reports/                 ← Test result reports
    └── my-project-1.0.0.jar             ← The final packaged JAR
```

**If you follow this structure → zero configuration needed.**
Maven already knows:
- Source code is in `src/main/java/`
- Tests are in `src/test/java/`
- Resources are in `src/main/resources/`
- Output goes to `target/`

You don't need to tell Maven any of this. It's "convention."

### 4.2 Project Coordinates (GAV)

Every Maven artifact (JAR, POM, WAR) is identified by three coordinates:

```
┌─────────────────────────────────────────────────────────────┐
│  G.A.V. — The "Address" of Any Java Library                 │
│                                                              │
│  GroupId:    com.payflow                                     │
│             (Organization/company — like a reverse domain)   │
│                                                              │
│  ArtifactId: payment-service                                 │
│             (Project/module name — unique within the group)  │
│                                                              │
│  Version:   1.0.0-SNAPSHOT                                   │
│             (Release version)                                │
│                                                              │
│  Together they form a unique "coordinate" in the Maven       │
│  universe. No two JARs can have the same G:A:V.             │
└─────────────────────────────────────────────────────────────┘
```

**Real examples:**
| GroupId | ArtifactId | Version | What It Is |
|---------|-----------|---------|------------|
| `org.springframework.boot` | `spring-boot-starter-web` | `3.2.5` | Spring Boot Web starter |
| `com.fasterxml.jackson.core` | `jackson-databind` | `2.17.0` | JSON library |
| `org.postgresql` | `postgresql` | `42.7.3` | PostgreSQL JDBC driver |
| `com.payflow` | `common-lib` | `1.0.0-SNAPSHOT` | Our shared library |

### 4.3 SNAPSHOT vs RELEASE

```
VERSION TYPES:

1.0.0-SNAPSHOT
├── SNAPSHOT = "work in progress" (still developing)
├── Maven re-downloads it daily (might have changed)
├── Used during development
├── Can be overwritten in the repository
└── Example: you're still adding features to common-lib

1.0.0 (no SNAPSHOT)
├── RELEASE = "final, never changes"
├── Maven downloads once, caches forever
├── Used for production releases
├── Cannot be overwritten (immutable)
└── Example: Spring Boot 3.2.5 will always be exactly the same code
```

### 4.4 The Super POM

Every pom.xml secretly inherits from Maven's built-in "Super POM" — even if you don't declare a parent:

```
The inheritance chain:

YOUR pom.xml
    ↓ inherits from
spring-boot-starter-parent (if declared)
    ↓ inherits from
spring-boot-dependencies (BOM)
    ↓ inherits from
SUPER POM (built into Maven itself)
```

The Super POM defines defaults that every project uses:
- `src/main/java` as source directory
- `src/test/java` as test directory
- `target` as output directory
- Maven Central as default repository
- Standard plugin versions

That's why the convention works — the Super POM established it.

---

## 5. The POM File (pom.xml) — Complete Anatomy

POM = **P**roject **O**bject **M**odel. It's the single file that tells Maven everything about your project.

### 5.1 Minimal POM (Bare Minimum)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>hello-world</artifactId>
    <version>1.0.0</version>
</project>
```

That's it! Just 4 required elements:
- `modelVersion`: Always "4.0.0" (POM schema version)
- `groupId`: Your organization
- `artifactId`: Your project name
- `version`: Your version number

This compiles `src/main/java/*.java` into a JAR. No dependencies, no plugins — Maven defaults handle everything.

### 5.2 Full POM — Every Section Explained

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <!-- 
        modelVersion: ALWAYS 4.0.0. This has been the same since 2004.
        It tells Maven which version of the POM schema to use.
    -->

    <!-- ═══════════════════════════════════════════════════════════════ -->
    <!-- SECTION 1: PARENT (Optional — inherit from another POM)        -->
    <!-- ═══════════════════════════════════════════════════════════════ -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>
    <!--
        WHAT THIS DOES:
        - Inherit dependency versions managed by Spring Boot
        - Inherit plugin configurations (compiler, surefire, etc.)
        - Inherit default properties (Java version, encoding)
        
        <relativePath/> means: "Don't look in parent folder, download from Maven Central"
        Without relativePath: Maven looks for ../pom.xml first
        
        WHY: Spring Boot manages 400+ library versions for us.
        We don't need to specify versions for spring-web, jackson, etc.
    -->

    <!-- ═══════════════════════════════════════════════════════════════ -->
    <!-- SECTION 2: PROJECT COORDINATES (Who am I?)                     -->
    <!-- ═══════════════════════════════════════════════════════════════ -->
    <groupId>com.payflow</groupId>
    <artifactId>payment-service</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    <!--
        packaging options:
        - jar  (default) = Java library or executable Spring Boot app
        - war  = Web Application Archive (for servlet containers like Tomcat)
        - pom  = Parent/aggregator POM (has children, no code itself)
        - ear  = Enterprise Application Archive (legacy J2EE)
    -->

    <name>PayFlow Payment Service</name>
    <description>Handles payment processing, authorization, and capture</description>

    <!-- ═══════════════════════════════════════════════════════════════ -->
    <!-- SECTION 3: PROPERTIES (Variables you can reuse)                -->
    <!-- ═══════════════════════════════════════════════════════════════ -->
    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2023.0.1</spring-cloud.version>
        <mapstruct.version>1.5.5.Final</mapstruct.version>
    </properties>
    <!--
        Properties are VARIABLES. Use them with ${property.name} syntax.
        
        java.version=17:
        - Tells spring-boot-starter-parent to compile with Java 17
        - Sets both source and target compatibility
        
        Custom properties (spring-cloud.version, mapstruct.version):
        - Define once, use in multiple places
        - Change version in ONE place → applies everywhere
        
        Usage in dependencies:
        <version>${spring-cloud.version}</version>  → becomes "2023.0.1"
    -->

    <!-- ═══════════════════════════════════════════════════════════════ -->
    <!-- SECTION 4: DEPENDENCY MANAGEMENT (Version control, not import) -->
    <!-- ═══════════════════════════════════════════════════════════════ -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <!--
        CRITICAL DISTINCTION:
        
        <dependencyManagement> ≠ <dependencies>
        
        <dependencyManagement>:
        - Does NOT add any JAR to your project
        - Only DEFINES versions for when a child/this POM declares the dependency
        - Think of it as a "version catalog"
        
        <dependencies>:
        - Actually ADDS the JAR to your project classpath
        - Used below in Section 5
        
        The BOM import (type=pom, scope=import):
        - Imports Spring Cloud's entire version catalog
        - After this: any spring-cloud-* dependency can omit <version>
        - Spring Cloud manages: Eureka, Config, Gateway, LoadBalancer versions
    -->

    <!-- ═══════════════════════════════════════════════════════════════ -->
    <!-- SECTION 5: DEPENDENCIES (What JARs do I need?)                 -->
    <!-- ═══════════════════════════════════════════════════════════════ -->
    <dependencies>
        <!-- Compile scope (default): needed for compilation AND runtime -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <!-- No version! Inherited from spring-boot-starter-parent -->
        </dependency>

        <!-- Runtime scope: NOT needed for compilation, needed at runtime -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Provided scope: needed for compilation, NOT packaged (provided by runtime) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test scope: only available during testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <!-- ═══════════════════════════════════════════════════════════════ -->
    <!-- SECTION 6: BUILD (How to compile, what plugins to use)         -->
    <!-- ═══════════════════════════════════════════════════════════════ -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
    <!--
        spring-boot-maven-plugin:
        - Repackages the JAR into an executable "fat JAR"
        - Adds Spring Boot's custom class loader
        - Makes it runnable with: java -jar my-app.jar
        - exclude lombok: Lombok is compile-time only, not needed in final JAR
    -->
</project>
```

---

## 6. Build Lifecycle — Every Phase Explained

Maven has THREE built-in lifecycles. Each lifecycle contains phases that execute in order.

### 6.1 The Three Lifecycles

```
LIFECYCLE 1: CLEAN (clean up previous builds)
Phases: pre-clean → clean → post-clean

LIFECYCLE 2: DEFAULT (the main build process)
Phases: validate → compile → test → package → verify → install → deploy
(23 phases total — but these 7 are the important ones)

LIFECYCLE 3: SITE (generate project documentation)
Phases: pre-site → site → post-site → site-deploy
```

### 6.2 Default Lifecycle — All 23 Phases

When you run `mvn install`, ALL phases up to and including `install` execute in this order:

```
Phase #    Name                    What Happens
─────────────────────────────────────────────────────────────────
 1         validate                Validate POM is correct
 2         initialize              Initialize build state
 3         generate-sources        Generate any source code (e.g., from WSDL)
 4         process-sources         Process source code (filtering)
 5         generate-resources      Generate resource files
 6         process-resources       Copy resources to target/classes/
 7         compile                 ★ COMPILE .java → .class
 8         process-classes         Post-process (bytecode enhancement)
 9         generate-test-sources   Generate test source code
10         process-test-sources    Process test sources
11         generate-test-resources Generate test resources
12         process-test-resources  Copy test resources to target/test-classes/
13         test-compile            Compile test .java → .class
14         process-test-classes    Post-process test bytecode
15         test                    ★ RUN UNIT TESTS
16         prepare-package         Pre-packaging steps
17         package                 ★ CREATE JAR/WAR FILE
18         pre-integration-test    Setup for integration tests
19         integration-test        Run integration tests
20         post-integration-test   Cleanup after integration tests
21         verify                  ★ VERIFY (run checks/integration tests)
22         install                 ★ COPY JAR TO ~/.m2/repository
23         deploy                  ★ UPLOAD TO REMOTE REPOSITORY
```

**Key rule:** Running phase N automatically runs phases 1 through N-1 first.
- `mvn compile` → runs phases 1-7
- `mvn test` → runs phases 1-15
- `mvn package` → runs phases 1-17
- `mvn install` → runs phases 1-22


### 6.3 What Happens at Each Important Phase (File-Level Detail)

#### CLEAN Phase

```
BEFORE:                              AFTER:
project/                             project/
├── src/                             ├── src/         (unchanged)
├── target/          ← DELETED!      └── pom.xml     (unchanged)
│   ├── classes/
│   ├── test-classes/                 target/ is GONE
│   ├── surefire-reports/
│   └── my-app-1.0.0.jar
└── pom.xml
```

#### COMPILE Phase

```
INPUT FILES:
  src/main/java/com/payflow/payment/PaymentService.java
  src/main/java/com/payflow/payment/PaymentController.java
  src/main/java/com/payflow/payment/model/Payment.java

CLASSPATH (resolved from dependencies):
  ~/.m2/repository/org/springframework/boot/spring-boot-3.2.5.jar
  ~/.m2/repository/com/payflow/common-lib-1.0.0.jar
  ~/.m2/repository/... (50+ JARs)

COMMAND MAVEN RUNS (internally):
  javac -source 17 -target 17 \
    -cp (all dependency JARs joined with ;) \
    -d target/classes \
    -s target/generated-sources \
    src/main/java/com/payflow/payment/*.java

ANNOTATION PROCESSORS RUN:
  1. Lombok processor → reads @Data, @Builder → generates bytecode for getters/setters
  2. MapStruct processor → reads @Mapper → generates implementation classes

OUTPUT FILES:
  target/classes/com/payflow/payment/PaymentService.class
  target/classes/com/payflow/payment/PaymentController.class
  target/classes/com/payflow/payment/model/Payment.class
  target/generated-sources/annotations/com/payflow/payment/mapper/PaymentMapperImpl.java
```

#### TEST Phase

```
INPUT:
  target/test-classes/com/payflow/payment/PaymentServiceTest.class
  target/classes/ (your compiled code)
  All dependency JARs + test dependency JARs (JUnit, Mockito)

WHAT MAVEN DOES:
  1. Scans target/test-classes/ for classes matching: *Test, Test*, *Tests, *TestCase
  2. Starts a JVM with test classpath
  3. For EACH test class:
     a. Creates instance
     b. Runs @BeforeEach methods
     c. Runs each @Test method
     d. Runs @AfterEach methods
     e. Records: pass/fail/error/skip
  4. Generates reports

OUTPUT:
  target/surefire-reports/
  ├── com.payflow.payment.PaymentServiceTest.txt    (text report)
  ├── com.payflow.payment.PaymentServiceTest.xml    (XML for CI tools)
  └── ... (one per test class)

CONSOLE OUTPUT:
  [INFO] Running com.payflow.payment.PaymentServiceTest
  [INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
  [INFO] Running com.payflow.payment.PaymentControllerTest
  [INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 1
  [INFO]
  [INFO] Results:
  [INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 1
```

#### PACKAGE Phase

```
INPUT:
  target/classes/           (compiled code)
  target/resources/         (application.yml, etc.)
  All dependency JARs

OUTPUT (Spring Boot fat JAR):
  target/payment-service-1.0.0-SNAPSHOT.jar

INSIDE THE FAT JAR:
  payment-service-1.0.0-SNAPSHOT.jar
  ├── META-INF/
  │   └── MANIFEST.MF
  │       Main-Class: org.springframework.boot.loader.JarLauncher
  │       Start-Class: com.payflow.payment.PaymentServiceApplication
  ├── BOOT-INF/
  │   ├── classes/                    ← YOUR compiled code
  │   │   ├── com/payflow/payment/PaymentServiceApplication.class
  │   │   ├── com/payflow/payment/PaymentController.class
  │   │   └── application.yml
  │   └── lib/                        ← ALL dependency JARs (inside!)
  │       ├── spring-boot-3.2.5.jar
  │       ├── spring-web-6.1.6.jar
  │       ├── jackson-databind-2.17.0.jar
  │       ├── postgresql-42.7.3.jar
  │       └── ... (50+ JARs)
  └── org/springframework/boot/loader/  ← Spring Boot's JAR launcher
      └── JarLauncher.class

HOW IT RUNS:
  java -jar payment-service-1.0.0-SNAPSHOT.jar
  → JVM finds Main-Class: JarLauncher
  → JarLauncher finds Start-Class: PaymentServiceApplication
  → JarLauncher sets up classpath from BOOT-INF/lib/*.jar
  → Calls PaymentServiceApplication.main(args)
  → Spring Boot starts!
```

#### INSTALL Phase

```
ACTION:
  COPY target/payment-service-1.0.0-SNAPSHOT.jar
  TO   ~/.m2/repository/com/payflow/payment-service/1.0.0-SNAPSHOT/

ALSO COPIES:
  → payment-service-1.0.0-SNAPSHOT.pom (the pom.xml, renamed)

RESULT:
  ~/.m2/repository/
  └── com/
      └── payflow/
          └── payment-service/
              └── 1.0.0-SNAPSHOT/
                  ├── payment-service-1.0.0-SNAPSHOT.jar   ← The JAR
                  ├── payment-service-1.0.0-SNAPSHOT.pom   ← The POM
                  └── _remote.repositories                  ← Metadata

WHY THIS MATTERS:
  Other local projects can now declare:
  <dependency>
      <groupId>com.payflow</groupId>
      <artifactId>payment-service</artifactId>
      <version>1.0.0-SNAPSHOT</version>
  </dependency>
  Maven finds it in ~/.m2/ without needing to download from internet.
```

---

## 7. Dependency Management — How It Really Works

### 7.1 Dependency Scopes

```
┌──────────────────────────────────────────────────────────────────┐
│ SCOPE          │ COMPILE? │ TEST? │ IN FINAL JAR? │ EXAMPLE      │
├──────────────────────────────────────────────────────────────────┤
│ compile        │    ✅    │  ✅   │      ✅       │ spring-web   │
│ (default)      │          │       │               │              │
├──────────────────────────────────────────────────────────────────┤
│ provided       │    ✅    │  ✅   │      ❌       │ servlet-api  │
│                │          │       │ (server has it)│              │
├──────────────────────────────────────────────────────────────────┤
│ runtime        │    ❌    │  ✅   │      ✅       │ postgresql   │
│                │ (not imported)│   │               │ driver       │
├──────────────────────────────────────────────────────────────────┤
│ test           │    ❌    │  ✅   │      ❌       │ junit        │
│                │          │       │               │ mockito      │
├──────────────────────────────────────────────────────────────────┤
│ system         │    ✅    │  ✅   │      ❌       │ local JAR    │
│                │          │       │ (AVOID!)      │ (bad practice)│
└──────────────────────────────────────────────────────────────────┘
```

### 7.2 Transitive Dependencies

When you add ONE dependency, you get a TREE of dependencies:

```
YOU DECLARE: spring-boot-starter-data-jpa

MAVEN RESOLVES THE FULL TREE:
spring-boot-starter-data-jpa
├── spring-boot-starter-aop
│   ├── spring-aop
│   └── aspectjweaver
├── spring-boot-starter-jdbc
│   ├── spring-jdbc
│   └── HikariCP (connection pool)
├── spring-data-jpa
│   ├── spring-data-commons
│   └── spring-orm
├── hibernate-core (JPA implementation)
│   ├── hibernate-commons-annotations
│   ├── jboss-logging
│   ├── byte-buddy (bytecode generation)
│   └── antlr4-runtime (HQL parser)
├── jakarta.persistence-api
├── jakarta.transaction-api
└── spring-aspects

TOTAL: ~25 JARs from ONE dependency declaration!
```

### 7.3 Dependency Conflict Resolution

```
SCENARIO: Two dependencies need different versions of same library

your-project
├── library-A (needs jackson 2.17.0)
│   └── jackson-databind:2.17.0    ← depth 2
└── library-B (needs jackson 2.15.3)
    └── some-utility               
        └── jackson-databind:2.15.3  ← depth 3

MAVEN'S RULE: "Nearest definition wins" (shortest path from root)
RESULT: jackson-databind 2.17.0 is used (depth 2 beats depth 3)

IF SAME DEPTH:
your-project
├── library-A
│   └── jackson-databind:2.17.0    ← depth 2, declared FIRST in pom.xml → WINS
└── library-B
    └── jackson-databind:2.15.3    ← depth 2, declared SECOND → LOSES

TO FORCE A VERSION:
Option 1: Declare it directly in YOUR pom.xml (depth 1 = always wins)
Option 2: Put it in <dependencyManagement> (overrides all transitive versions)
Option 3: Use <exclusions> to block the unwanted version
```

### 7.4 Excluding a Transitive Dependency

Sometimes a transitive dependency causes problems. You can exclude it:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <!-- Exclude Tomcat — we'll use Jetty instead -->
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- Add Jetty instead -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jetty</artifactId>
</dependency>
```

### 7.5 BOM (Bill of Materials)

A BOM is a POM that ONLY defines versions for a set of related libraries:

```xml
<!-- Import Spring Cloud BOM — defines versions for ALL spring-cloud libraries -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2023.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Now in <dependencies>, no version needed for any spring-cloud library: -->
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        <!-- No <version>! BOM provides it. -->
    </dependency>
</dependencies>
```

**Why BOMs?** Spring Cloud has 30+ libraries that must be compatible with each other. The BOM guarantees all versions are tested together. You don't pick individual versions — you pick ONE BOM version and get guaranteed compatibility.

---

## 8. Plugins — How Maven Actually Does Things

Maven itself does NOTHING. Plugins do all the actual work. Each phase is bound to a plugin goal.

### 8.1 Default Plugin Bindings

When you run `mvn compile`, Maven actually runs:

```
Phase: compile  →  Plugin: maven-compiler-plugin  →  Goal: compile
Phase: test     →  Plugin: maven-surefire-plugin  →  Goal: test
Phase: package  →  Plugin: maven-jar-plugin       →  Goal: jar
Phase: install  →  Plugin: maven-install-plugin   →  Goal: install
```

You don't configure these — they're automatic. But you CAN override their settings:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <source>17</source>              <!-- Java source level -->
                <target>17</target>              <!-- Bytecode target level -->
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>1.18.32</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### 8.2 Common Plugins

| Plugin | What It Does | Phase |
|--------|-------------|-------|
| `maven-compiler-plugin` | Compiles .java → .class | compile |
| `maven-surefire-plugin` | Runs unit tests | test |
| `maven-failsafe-plugin` | Runs integration tests | verify |
| `maven-jar-plugin` | Creates JAR file | package |
| `maven-war-plugin` | Creates WAR file | package |
| `maven-install-plugin` | Copies JAR to ~/.m2 | install |
| `maven-deploy-plugin` | Uploads JAR to remote repo | deploy |
| `spring-boot-maven-plugin` | Creates executable fat JAR | package (repackage) |
| `maven-resources-plugin` | Copies resources to target/ | process-resources |
| `jacoco-maven-plugin` | Code coverage reports | test/verify |


---

## 9. Multi-Module Projects

### 9.1 What Is a Multi-Module Project?

Instead of ONE big project, you split into multiple modules (sub-projects). Each module:
- Has its own pom.xml
- Compiles independently
- Produces its own JAR
- Can depend on other modules

```
PayFlow uses multi-module architecture:

payflow-payment-gateway/          ← PARENT (packaging: pom)
├── pom.xml                       ← Declares modules, manages versions
├── common-lib/                   ← CHILD MODULE (library)
│   └── pom.xml
├── service-registry/             ← CHILD MODULE (runnable app)
│   └── pom.xml
├── config-server/                ← CHILD MODULE (runnable app)
│   └── pom.xml
├── api-gateway/                  ← CHILD MODULE (runnable app)
│   └── pom.xml
├── identity-service/             ← CHILD MODULE (runnable app)
│   └── pom.xml
└── payment-service/              ← CHILD MODULE (runnable app)
    └── pom.xml
```

### 9.2 Parent POM

The parent POM has `<packaging>pom</packaging>` and declares all modules:

```xml
<project>
    <groupId>com.payflow</groupId>
    <artifactId>payflow-payment-gateway</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>          ← This is a PARENT, not a JAR

    <modules>
        <module>common-lib</module>     ← Build order determined by Maven
        <module>service-registry</module>
        <module>config-server</module>
        <module>api-gateway</module>
        <module>identity-service</module>
        <module>payment-service</module>
    </modules>

    <dependencyManagement>
        <!-- Versions defined HERE, used by ALL children -->
    </dependencyManagement>
</project>
```

### 9.3 Child POM

Each child declares the parent and inherits everything:

```xml
<project>
    <parent>
        <groupId>com.payflow</groupId>
        <artifactId>payflow-payment-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>payment-service</artifactId>
    <!-- groupId and version INHERITED from parent (don't need to repeat) -->

    <dependencies>
        <!-- Versions inherited from parent's dependencyManagement -->
        <dependency>
            <groupId>com.payflow</groupId>
            <artifactId>common-lib</artifactId>
            <!-- Version inherited! -->
        </dependency>
    </dependencies>
</project>
```

### 9.4 Build Order (Reactor)

Maven's "Reactor" determines build order from inter-module dependencies:

```
Maven reads all pom.xml files → builds a dependency graph:

common-lib          → depends on nothing       → BUILD FIRST
service-registry    → depends on parent only   → BUILD SECOND (parallel)
config-server       → depends on parent only   → BUILD SECOND (parallel)
api-gateway         → depends on parent only   → BUILD SECOND (parallel)
identity-service    → depends on common-lib    → BUILD AFTER common-lib
payment-service     → depends on common-lib    → BUILD AFTER common-lib

Reactor Build Order:
[1] payflow-payment-gateway (parent POM - just validates)
[2] common-lib
[3] service-registry          ← can run in parallel with [4], [5]
[4] config-server
[5] api-gateway
[6] identity-service
[7] payment-service
```

### 9.5 Building Specific Modules

```cmd
# Build everything:
mvn clean install -DskipTests

# Build only payment-service (and its dependencies):
mvn clean install -DskipTests -pl payment-service -am

# Build only payment-service (WITHOUT dependencies):
mvn clean install -DskipTests -pl payment-service

# Build everything EXCEPT the slow integration tests module:
mvn clean install -pl !slow-tests-module
```

| Flag | Meaning |
|------|---------|
| `-pl module1,module2` | **P**roject **L**ist: only these modules |
| `-am` | **A**lso **M**ake: build dependencies of listed modules |
| `-amd` | **A**lso **M**ake **D**ependents: build modules that depend on listed modules |
| `!module` | Exclude this module from the build |

---

## 10. Profiles — Different Configs for Different Environments

Profiles let you have different build configurations for different situations:

```xml
<profiles>
    <!-- Activated by default (for local development) -->
    <profile>
        <id>dev</id>
        <activation>
            <activeByDefault>true</activeByDefault>
        </activation>
        <properties>
            <spring.profiles.active>dev</spring.profiles.active>
        </properties>
    </profile>

    <!-- Activated manually: mvn install -Pprod -->
    <profile>
        <id>prod</id>
        <properties>
            <spring.profiles.active>prod</spring.profiles.active>
        </properties>
        <dependencies>
            <!-- Extra monitoring dependency only in production -->
            <dependency>
                <groupId>io.micrometer</groupId>
                <artifactId>micrometer-registry-prometheus</artifactId>
            </dependency>
        </dependencies>
    </profile>

    <!-- Activated automatically when running on CI server -->
    <profile>
        <id>ci</id>
        <activation>
            <property>
                <name>env.CI</name>   <!-- Activated when CI=true env var exists -->
            </property>
        </activation>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.jacoco</groupId>
                    <artifactId>jacoco-maven-plugin</artifactId>
                    <!-- Enable code coverage reporting on CI -->
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

**Activate profiles:**
```cmd
mvn install -Pprod              # Activate "prod" profile
mvn install -Pprod,ci           # Activate multiple profiles
mvn install -P!dev              # Deactivate "dev" profile
```

---

## 11. Maven Wrapper (mvnw)

The Maven Wrapper ensures every developer uses the SAME Maven version:

```
project/
├── mvnw                        ← Unix/Mac script
├── mvnw.cmd                    ← Windows script
├── .mvn/
│   └── wrapper/
│       ├── maven-wrapper.jar   ← Downloads correct Maven version
│       └── maven-wrapper.properties   ← Specifies which version
└── pom.xml
```

**.mvn/wrapper/maven-wrapper.properties:**
```properties
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
```

**Usage (instead of `mvn`):**
```cmd
mvnw.cmd clean install -DskipTests     (Windows)
./mvnw clean install -DskipTests       (Linux/Mac)
```

**Why use the wrapper?**
- Developer A has Maven 3.8 → works
- Developer B has Maven 3.6 → different behavior!
- With wrapper: EVERYONE uses 3.9.9 → identical builds
- CI server doesn't need Maven pre-installed

**Generate the wrapper for a project:**
```cmd
mvn wrapper:wrapper -Dmaven=3.9.9
```

---

## 12. Maven Repository System

### 12.1 Three Types of Repositories

```
┌─────────────────────────────────────────────────────────────────────┐
│                    REPOSITORY HIERARCHY                               │
│                                                                       │
│  ┌─────────────────┐                                                 │
│  │ LOCAL REPOSITORY │  ~/.m2/repository/                             │
│  │  (your machine) │  • First place Maven checks                    │
│  │                  │  • Stores everything you've ever downloaded     │
│  │                  │  • Also stores your own installed modules       │
│  └────────┬─────────┘                                                │
│           │ not found locally?                                        │
│           ▼                                                           │
│  ┌─────────────────┐                                                 │
│  │ REMOTE REPOS    │  (configured in pom.xml or settings.xml)       │
│  │ (company/team)  │  • Nexus, Artifactory, GitHub Packages         │
│  │                  │  • Company's private JARs                       │
│  │                  │  • Caches Maven Central (proxy)                 │
│  └────────┬─────────┘                                                │
│           │ not found there?                                          │
│           ▼                                                           │
│  ┌─────────────────┐                                                 │
│  │ MAVEN CENTRAL   │  https://repo.maven.apache.org/maven2/         │
│  │ (internet)      │  • THE global public repository                 │
│  │                  │  • 10+ million artifacts                        │
│  │                  │  • Free, public, always available               │
│  └──────────────────┘                                                │
└─────────────────────────────────────────────────────────────────────┘
```

### 12.2 Local Repository Deep Dive

```
~/.m2/repository/
├── org/
│   └── springframework/
│       └── boot/
│           └── spring-boot/
│               └── 3.2.5/
│                   ├── spring-boot-3.2.5.jar            ← The actual library
│                   ├── spring-boot-3.2.5.pom            ← Its POM (lists dependencies)
│                   ├── spring-boot-3.2.5.jar.sha1       ← Checksum (verify integrity)
│                   └── _remote.repositories             ← Where it was downloaded from
├── com/
│   └── payflow/
│       └── common-lib/
│           └── 1.0.0-SNAPSHOT/
│               ├── common-lib-1.0.0-SNAPSHOT.jar        ← YOUR module
│               └── common-lib-1.0.0-SNAPSHOT.pom
└── ... (thousands of libraries)
```

### 12.3 How Maven Resolves a Dependency

```
STEP 1: You declare in pom.xml:
  <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <version>42.7.3</version>
  </dependency>

STEP 2: Maven converts G:A:V to a file path:
  org.postgresql : postgresql : 42.7.3
  →  org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar

STEP 3: Check local repository:
  Look for: ~/.m2/repository/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar
  Found? → Use it. DONE.

STEP 4: Not found locally → check remote repositories:
  Try: https://repo.maven.apache.org/maven2/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar
  Download → save to ~/.m2/repository/ → use it.

STEP 5: Downloaded the JAR. Now check ITS pom:
  Download: postgresql-42.7.3.pom
  Read it → find TRANSITIVE dependencies
  → checker-qual:3.42.0  (repeat from Step 2 for this one)
  
STEP 6: Repeat until entire dependency tree is resolved.
```

---

## 13. Maven in Your PayFlow Project

### 13.1 First Time Setup

```cmd
# 1. Clone the project
git clone <repository-url>
cd PayFlow-Payment-Gateway

# 2. Build everything (first time — downloads all dependencies, takes 5-10 min)
mvn clean install -DskipTests

# 3. Start infrastructure (PostgreSQL, Redis, etc.)
docker compose -f docker-compose-infra.yml up -d

# 4. Start services (in order!)
cd service-registry && mvn spring-boot:run
# (open new terminal)
cd config-server && mvn spring-boot:run
# (open new terminal)
cd api-gateway && mvn spring-boot:run
# (open new terminal)
cd identity-service && mvn spring-boot:run
```

### 13.2 Daily Development Workflow

```cmd
# Morning: pull latest code
git pull

# Rebuild (in case dependencies changed)
mvn clean install -DskipTests

# Work on identity-service — quick rebuild:
mvn clean install -DskipTests -pl identity-service -am

# Run the service you're working on:
cd identity-service
mvn spring-boot:run

# Run tests before pushing:
mvn test -pl identity-service

# Push your changes
git add .
git commit -m "feat: add password reset endpoint"
git push
```

### 13.3 Adding a New Module

```
STEP 1: Create the folder structure
  mkdir routing-service
  mkdir -p routing-service/src/main/java/com/payflow/routing
  mkdir -p routing-service/src/main/resources
  mkdir -p routing-service/src/test/java/com/payflow/routing

STEP 2: Create routing-service/pom.xml:
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.payflow</groupId>
        <artifactId>payflow-payment-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>routing-service</artifactId>
    <name>PayFlow Routing Service</name>

    <dependencies>
        <dependency>
            <groupId>com.payflow</groupId>
            <artifactId>common-lib</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

```
STEP 3: Add to parent pom.xml's <modules>:
  <module>routing-service</module>

STEP 4: Build:
  mvn clean install -DskipTests -pl routing-service -am
```

---

## 14. Troubleshooting Common Problems

| Problem | Cause | Fix |
|---------|-------|-----|
| "Could not resolve artifact com.payflow:common-lib" | common-lib not installed in .m2 | `mvn clean install -DskipTests -pl common-lib` |
| "Compilation failure" after git pull | Someone changed common-lib | `mvn clean install -DskipTests` (rebuild all) |
| "Port 8081 already in use" | Previous instance running | `netstat -ano \| findstr 8081` then `taskkill /PID <pid> /F` |
| Tests fail randomly | Test order dependency | Run: `mvn test -pl module -Dsurefire.runOrder=random` |
| "OutOfMemoryError" during build | Maven needs more RAM | Set: `set MAVEN_OPTS=-Xmx1024m` |
| "Cannot find symbol" | Missing dependency or import | Check pom.xml has the dependency, rebuild |
| Build is very slow | Re-downloading dependencies | Check internet, or run with `-o` (offline mode) |
| "Non-resolvable parent POM" | Parent not installed | Run from ROOT: `mvn clean install -N` (parent only) |
| Stale classes causing issues | Incremental compilation bug | `mvn clean` then rebuild |
| Plugin not found | Plugin version conflict | Specify explicit plugin version in `<pluginManagement>` |

### Clear all caches (nuclear option):

```cmd
# Delete local repository (re-downloads everything next build — slow but fixes everything)
rmdir /s /q %USERPROFILE%\.m2\repository

# Then rebuild:
mvn clean install -DskipTests
```

---

## 15. Commands Cheat Sheet

### Build Commands

```cmd
mvn clean                              # Delete target/ folders
mvn compile                            # Compile source code only
mvn test                               # Compile + run tests
mvn package                            # Compile + test + create JAR
mvn install                            # Compile + test + JAR + install to .m2
mvn clean install                      # Clean + full build
mvn clean install -DskipTests          # ★ MOST COMMON: full build, no tests
mvn clean package -DskipTests          # Build JAR without installing to .m2
```

### Run Commands

```cmd
mvn spring-boot:run                    # Start Spring Boot app
mvn spring-boot:run -Dspring-boot.run.profiles=docker  # With profile
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx512m"  # With JVM args
```

### Module-Specific Commands

```cmd
mvn clean install -pl common-lib                    # Build one module
mvn clean install -pl payment-service -am           # Module + dependencies
mvn clean install -pl payment-service -amd          # Module + dependents
mvn clean install -pl identity-service,payment-service -am  # Multiple modules
mvn test -pl payment-service                        # Test one module
```

### Dependency Commands

```cmd
mvn dependency:tree                                 # Show full dependency tree
mvn dependency:tree -pl payment-service             # For one module
mvn dependency:tree -Dincludes=com.fasterxml*       # Filter by group
mvn dependency:analyze                              # Find unused/undeclared dependencies
mvn versions:display-dependency-updates             # Check for newer versions
mvn versions:display-plugin-updates                 # Check for newer plugin versions
mvn dependency:resolve                              # Download all dependencies
mvn dependency:purge-local-repository               # Delete and re-download all
```

### Information Commands

```cmd
mvn help:effective-pom                  # Show the FULL merged POM (including inherited)
mvn help:effective-pom -pl payment-service   # For one module
mvn help:active-profiles                # Show which profiles are active
mvn help:describe -Dplugin=compiler     # Show plugin documentation
```

### Flags

```cmd
-DskipTests          # Skip test execution (still compiles tests)
-Dmaven.test.skip    # Skip test compilation AND execution
-U                   # Force update of SNAPSHOT dependencies
-o                   # Offline mode (don't check remote repos)
-X                   # Debug mode (verbose output)
-T 4                 # Build with 4 threads (parallel)
-N                   # Non-recursive (only parent POM, not modules)
-q                   # Quiet mode (less output)
```

---

## 16. Interview Questions & Answers

**Q: "What is Maven and why is it used?"**
> "Maven is a build automation tool for Java that manages dependencies, compiles code, runs tests, and packages applications. It solves the problem of manually managing library versions across large projects. With one command (`mvn clean install`), it downloads all required libraries, ensures version compatibility, compiles the code, and produces a deployable artifact."

**Q: "Explain the Maven build lifecycle."**
> "Maven has three lifecycles: Clean, Default, and Site. The Default lifecycle has phases like validate, compile, test, package, verify, install, and deploy — executed in that fixed order. Running any phase automatically executes all preceding phases. For example, `mvn package` runs validate → compile → test → package."

**Q: "What is the difference between dependency and dependencyManagement?"**
> "`<dependencies>` actually adds JARs to your classpath — your code can use them. `<dependencyManagement>` only defines VERSION numbers without adding anything. It's used in parent POMs so child modules can declare dependencies without repeating version numbers. The child just says 'I need spring-web' and the parent's dependencyManagement provides the version."

**Q: "How does Maven resolve dependency conflicts?"**
> "Maven uses the 'nearest definition' strategy. If two different paths lead to the same dependency with different versions, Maven picks the one closest to the project root (shortest path in the dependency tree). For same-depth conflicts, the first one declared in the POM wins. You can override this with explicit declarations or `<dependencyManagement>`."

**Q: "What is a BOM and how does it work?"**
> "BOM stands for Bill of Materials. It's a special POM that only defines version numbers for a set of related libraries. You import it in `<dependencyManagement>` with `<type>pom</type>` and `<scope>import</scope>`. After importing, any dependency from that BOM can be declared without a version — the BOM provides it. Spring Cloud uses this to ensure all its 30+ libraries are mutually compatible."

**Q: "What is the Maven Reactor?"**
> "The Reactor is Maven's multi-module build engine. When you run `mvn install` from a parent POM, the Reactor reads all module POMs, builds a dependency graph between them, determines the correct build order, and builds them sequentially (or in parallel with `-T`). It ensures common-lib builds before payment-service if payment-service depends on common-lib."

**Q: "Explain SNAPSHOT vs RELEASE versions."**
> "SNAPSHOT versions (e.g., 1.0.0-SNAPSHOT) are development versions that can change at any time. Maven re-checks for updates daily. RELEASE versions (e.g., 1.0.0) are immutable — once published, they never change. Maven caches them forever. Use SNAPSHOT during development and RELEASE for production deployments."

**Q: "What is a fat JAR and how does Spring Boot create one?"**
> "A fat JAR (or uber JAR) contains not just your compiled code but ALL dependency JARs inside it. Spring Boot's `spring-boot-maven-plugin` repackages the standard JAR during the `package` phase, embedding all libraries in a `BOOT-INF/lib/` folder. This makes the application self-contained — runnable with just `java -jar app.jar` with no external classpath needed."

**Q: "How do you handle different configurations for dev, staging, and production?"**
> "Using Maven Profiles. Each profile can activate different properties, dependencies, or plugins. Combined with Spring Profiles (spring.profiles.active), you can have dev/staging/prod configurations. Profiles are activated with `-P` flag (`mvn install -Pprod`), environment variables, or JDK/OS detection."

**Q: "What happens if `mvn clean install` fails?"**
> "I check the error message. Common causes: compilation error (fix the code), test failure (fix or skip with `-DskipTests`), dependency not found (run `mvn install` on the missing module first or check internet), plugin error (check plugin version compatibility). I use `mvn -X` for debug output and `mvn dependency:tree` to investigate dependency issues."

---

## Summary

```
MAVEN IN ONE PICTURE:

   pom.xml (WHAT you want)
       │
       ▼
   ┌─────────┐     ┌──────────────────┐
   │  MAVEN  │────►│ Maven Central    │ (downloads JARs)
   │         │     │ ~/.m2/repository │ (caches locally)
   └────┬────┘     └──────────────────┘
        │
        │ Executes lifecycle phases:
        │
        ├── validate  (check pom.xml)
        ├── compile   (javac: .java → .class)
        ├── test      (JUnit: run tests)
        ├── package   (jar: create JAR)
        ├── install   (copy to ~/.m2)
        └── deploy    (upload to remote)
        │
        ▼
   target/my-app-1.0.0.jar (DONE!)
       │
       ▼
   java -jar my-app.jar   (RUN!)
```

---

*End of document. You now know everything about Maven — from installation to multi-module builds to interview answers.*
