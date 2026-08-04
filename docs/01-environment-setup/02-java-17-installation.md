# Environment Setup — Java 17 Installation

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## Why Java 17?

- **LTS (Long-Term Support)** — Supported until 2029
- **Required by Spring Boot 3** — Won't work with older versions
- **Modern features** — Records, sealed classes, pattern matching

---

## Step 1: Download Java 17

### Option A: Eclipse Temurin (Recommended)

1. Go to: https://adoptium.net/
2. Click **"Latest LTS Release"**
3. Select:
   - Operating System: **Windows**
   - Architecture: **x64**
   - Package Type: **JDK**
4. Download the **.msi** installer

### Option B: Oracle JDK

1. Go to: https://www.oracle.com/java/technologies/downloads/
2. Select **Java 17**
3. Download Windows x64 Installer

---

## Step 2: Install Java

1. Run the downloaded **.msi** file
2. Click **Next** through the wizard
3. **Important:** Check "Set JAVA_HOME variable"
4. Complete installation

---

## Step 3: Set Environment Variables (Manual)

If the installer didn't set variables automatically:

### Open Environment Variables

1. Press `Win + R`
2. Type `sysdm.cpl` and press Enter
3. Go to **Advanced** tab
4. Click **Environment Variables**

### Set JAVA_HOME

1. Under **System variables**, click **New**
2. Variable name: `JAVA_HOME`
3. Variable value: `C:\Program Files\Eclipse Adoptium\jdk-17.0.x.x-hotspot`
   (Use your actual installation path)
4. Click **OK**

### Update PATH

1. Find **Path** in System variables
2. Click **Edit**
3. Click **New**
4. Add: `%JAVA_HOME%\bin`
5. Click **OK** on all dialogs

---

## Step 4: Verify Installation

**Open a NEW terminal** (PowerShell or CMD):

```powershell
# Check Java version
java -version
```

**Expected Output:**
```
openjdk version "17.0.x" 2024-xx-xx
OpenJDK Runtime Environment Temurin-17.0.x+x (build 17.0.x+x)
OpenJDK 64-Bit Server VM Temurin-17.0.x+x (build 17.0.x+x, mixed mode)
```

```powershell
# Check Java compiler
javac -version
```

**Expected Output:**
```
javac 17.0.x
```

```powershell
# Check JAVA_HOME
echo $env:JAVA_HOME
```

**Expected Output:**
```
C:\Program Files\Eclipse Adoptium\jdk-17.0.x.x-hotspot
```

---

## Troubleshooting

### "java is not recognized"

**Cause:** PATH not set correctly

**Fix:**
1. Close all terminals
2. Verify JAVA_HOME points to correct folder
3. Verify `%JAVA_HOME%\bin` is in PATH
4. Open NEW terminal and try again

### Wrong Java version

**Cause:** Multiple Java versions installed

**Fix:**
```powershell
# Check all Java installations
where java

# Remove old versions from Control Panel > Programs
# Or update PATH to prioritize Java 17
```

### JAVA_HOME not set

**Cause:** Installer didn't set it

**Fix:** Follow Step 3 above to set manually

---

## Quick Test: Run Java

Create a test file:

```powershell
# Create test file
echo 'public class Test { public static void main(String[] args) { System.out.println("Java 17 works!"); } }' > Test.java

# Compile
javac Test.java

# Run
java Test

# Cleanup
del Test.java Test.class
```

**Expected Output:**
```
Java 17 works!
```

---

## Next Steps

**Continue to:** [03-maven-installation.md](./03-maven-installation.md)

---

**End of Java 17 Installation**
