# Environment Setup — Maven Installation

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## What is Maven?

Maven is a **build automation tool** for Java projects. It handles:
- Dependency management (downloading libraries)
- Compiling code
- Running tests
- Packaging JARs/WARs
- Multi-module project structure

---

## Step 1: Download Maven

1. Go to: https://maven.apache.org/download.cgi
2. Download **Binary zip archive**: `apache-maven-3.9.x-bin.zip`

---

## Step 2: Extract Maven

1. Extract the ZIP to: `C:\Program Files\Apache\maven`
2. You should have: `C:\Program Files\Apache\maven\bin\mvn.cmd`

```powershell
# Create directory and extract (PowerShell)
mkdir "C:\Program Files\Apache"
# Extract ZIP to C:\Program Files\Apache\maven
```

---

## Step 3: Set Environment Variables

### Open Environment Variables

1. Press `Win + R`
2. Type `sysdm.cpl` and press Enter
3. Go to **Advanced** tab → **Environment Variables**

### Set M2_HOME

1. Under **System variables**, click **New**
2. Variable name: `M2_HOME`
3. Variable value: `C:\Program Files\Apache\maven`
4. Click **OK**

### Update PATH

1. Find **Path** in System variables
2. Click **Edit** → **New**
3. Add: `%M2_HOME%\bin`
4. Click **OK** on all dialogs

---

## Step 4: Verify Installation

**Open a NEW terminal:**

```powershell
mvn -version
```

**Expected Output:**
```
Apache Maven 3.9.x
Maven home: C:\Program Files\Apache\maven
Java version: 17.0.x, vendor: Eclipse Adoptium
```

---

## Step 5: Configure Maven Settings (Optional)

Create Maven settings file for better performance:

**File:** `C:\Users\<YourUsername>\.m2\settings.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0
          https://maven.apache.org/xsd/settings-1.2.0.xsd">
    
    <!-- Local repository location -->
    <localRepository>C:/Users/${user.name}/.m2/repository</localRepository>
    
    <!-- Mirror for faster downloads (optional) -->
    <mirrors>
        <mirror>
            <id>central-mirror</id>
            <mirrorOf>central</mirrorOf>
            <url>https://repo.maven.apache.org/maven2</url>
        </mirror>
    </mirrors>
    
</settings>
```

---

## Key Maven Commands

| Command | Purpose |
|---------|---------|
| `mvn clean` | Delete target folder |
| `mvn compile` | Compile source code |
| `mvn test` | Run unit tests |
| `mvn package` | Create JAR/WAR |
| `mvn install` | Install to local repo |
| `mvn spring-boot:run` | Run Spring Boot app |
| `mvn clean install -DskipTests` | Build without tests |

---

## Troubleshooting

### "mvn is not recognized"

**Fix:** 
1. Verify M2_HOME is set correctly
2. Verify `%M2_HOME%\bin` is in PATH
3. Open NEW terminal

### "JAVA_HOME not set"

**Fix:** Maven requires JAVA_HOME. See Java installation guide.

### Slow downloads

**Fix:** First build downloads many dependencies (~500MB). Be patient!

---

## Next Steps

**Continue to:** [04-nodejs-installation.md](./04-nodejs-installation.md)

---

**End of Maven Installation**
