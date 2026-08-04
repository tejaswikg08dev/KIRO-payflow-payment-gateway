# Environment Setup — IDE Setup (VS Code & IntelliJ IDEA)

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## 1. What We're Building

In this guide, you'll install and configure two IDEs:
- **Visual Studio Code** — For React frontend, Docker files, and quick edits
- **IntelliJ IDEA** — For Java/Spring Boot backend development

Both are free and work together seamlessly.

---

## 2. Concepts Deep Dive

### Why Two IDEs?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        IDE Comparison for PayFlow                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   VS Code (Lightweight)                IntelliJ IDEA (Full IDE)             │
│   ┌─────────────────────────┐          ┌─────────────────────────┐          │
│   │ ★ React/TypeScript      │          │ ★ Java/Spring Boot      │          │
│   │ ★ Docker/YAML files     │          │ ★ Multi-module Maven    │          │
│   │ ★ Quick text editing    │          │ ★ Advanced refactoring  │          │
│   │ ★ Git operations        │          │ ★ Database tools        │          │
│   │ ★ Terminal integration  │          │ ★ Debugging Java        │          │
│   │                         │          │ ★ Code analysis         │          │
│   │ RAM: ~300MB             │          │ RAM: ~1-2GB             │          │
│   │ Startup: 2-3 seconds    │          │ Startup: 10-30 seconds  │          │
│   └─────────────────────────┘          └─────────────────────────┘          │
│                                                                              │
│   USE VS CODE FOR:                     USE INTELLIJ FOR:                    │
│   • merchant-portal/                   • All Java services:                 │
│   • developer-portal/                    - api-gateway/                     │
│   • docker-compose.yml                   - payment-service/                 │
│   • .github/workflows/                   - identity-service/                │
│   • README files                         - etc.                             │
│   • Quick config edits                 • Debugging microservices            │
│                                        • Running tests                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### IDE Features Comparison

| Feature | VS Code | IntelliJ IDEA |
|---------|---------|---------------|
| **Price** | Free | Community (Free) / Ultimate (Paid) |
| **Java Support** | Basic (with extensions) | Excellent (native) |
| **TypeScript** | Excellent | Good |
| **Docker** | Excellent (extension) | Good (plugin) |
| **Git** | Excellent | Excellent |
| **Debugging** | Good | Excellent for Java |
| **Refactoring** | Basic | Advanced |
| **Database** | Extension needed | Built-in (Ultimate) |

---

## 3. Prerequisites

| Requirement | Status |
|-------------|--------|
| Windows 10/11 | Required |
| Java 17 installed | From previous guide |
| Node.js installed | From previous guide |
| Git installed | From previous guide |

---

## 4. Step-by-Step Installation

---

## Part A: Visual Studio Code

### Step A.1: Download VS Code

1. Go to: https://code.visualstudio.com/
2. Click **"Download for Windows"**
3. Save the installer

---

### Step A.2: Install VS Code

1. Run the downloaded installer
2. Accept the license agreement
3. Select destination folder (default is fine)
4. Select additional tasks:
   - ✅ Add "Open with Code" action to Windows Explorer file context menu
   - ✅ Add "Open with Code" action to Windows Explorer directory context menu
   - ✅ Register Code as an editor for supported file types
   - ✅ Add to PATH
5. Complete installation

---

### Step A.3: Verify VS Code Installation

```powershell
# Check VS Code version
code --version
```

**Expected Output:**
```
1.xx.x
<commit-hash>
x64
```

```powershell
# Open VS Code from terminal
code .
```

---

### Step A.4: Install Essential Extensions

Open VS Code and press `Ctrl+Shift+X` to open Extensions panel.

**Search and install these extensions:**

#### For React/TypeScript (Frontend):

| Extension | Publisher | Purpose |
|-----------|-----------|---------|
| **ES7+ React/Redux/React-Native snippets** | dsznajder | React code snippets |
| **Prettier - Code formatter** | Prettier | Auto-format code |
| **ESLint** | Microsoft | JavaScript linting |
| **TypeScript Hero** | rbbit | TypeScript imports |
| **Auto Rename Tag** | Jun Han | HTML/JSX tag rename |

#### For Docker:

| Extension | Publisher | Purpose |
|-----------|-----------|---------|
| **Docker** | Microsoft | Docker file support |
| **Docker Compose** | p1c2u | Compose file support |

#### For Git:

| Extension | Publisher | Purpose |
|-----------|-----------|---------|
| **GitLens** | GitKraken | Enhanced Git features |
| **Git Graph** | mhutchie | Visual branch graph |

#### For General Development:

| Extension | Publisher | Purpose |
|-----------|-----------|---------|
| **YAML** | Red Hat | YAML file support |
| **Thunder Client** | Thunder Client | API testing (like Postman) |
| **Error Lens** | Alexander | Inline error display |
| **Bracket Pair Color** | CoenraadS | Colored brackets |
| **Path Intellisense** | Christian Kohler | Path autocomplete |

**Install via command line (alternative):**

```powershell
# Install all extensions at once
code --install-extension dsznajder.es7-react-js-snippets
code --install-extension esbenp.prettier-vscode
code --install-extension dbaeumer.vscode-eslint
code --install-extension ms-azuretools.vscode-docker
code --install-extension eamodio.gitlens
code --install-extension mhutchie.git-graph
code --install-extension redhat.vscode-yaml
code --install-extension rangav.vscode-thunder-client
```

---

### Step A.5: Configure VS Code Settings

Press `Ctrl+,` to open Settings, then click the **Open Settings (JSON)** icon (top right).

Add these settings:

```json
{
  // Editor settings
  "editor.fontSize": 14,
  "editor.tabSize": 2,
  "editor.wordWrap": "on",
  "editor.formatOnSave": true,
  "editor.defaultFormatter": "esbenp.prettier-vscode",
  "editor.bracketPairColorization.enabled": true,
  "editor.guides.bracketPairs": true,
  
  // File settings
  "files.autoSave": "onFocusChange",
  "files.trimTrailingWhitespace": true,
  
  // Terminal settings
  "terminal.integrated.defaultProfile.windows": "PowerShell",
  "terminal.integrated.fontSize": 13,
  
  // Git settings
  "git.enableSmartCommit": true,
  "git.confirmSync": false,
  
  // TypeScript/JavaScript settings
  "typescript.updateImportsOnFileMove.enabled": "always",
  "javascript.updateImportsOnFileMove.enabled": "always",
  
  // Prettier settings
  "prettier.singleQuote": true,
  "prettier.trailingComma": "es5",
  
  // ESLint settings
  "eslint.validate": [
    "javascript",
    "javascriptreact",
    "typescript",
    "typescriptreact"
  ],
  
  // Emmet settings
  "emmet.includeLanguages": {
    "javascript": "javascriptreact",
    "typescript": "typescriptreact"
  }
}
```

---

### Step A.6: Learn VS Code Shortcuts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        VS Code Keyboard Shortcuts                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   GENERAL:                                                                   │
│   Ctrl+Shift+P       Command Palette (most important!)                      │
│   Ctrl+P             Quick Open file                                        │
│   Ctrl+,             Open Settings                                          │
│   Ctrl+`             Toggle Terminal                                        │
│   Ctrl+B             Toggle Sidebar                                         │
│                                                                              │
│   EDITING:                                                                   │
│   Ctrl+D             Select word (repeat for multiple)                      │
│   Ctrl+/             Toggle comment                                         │
│   Alt+Up/Down        Move line up/down                                      │
│   Ctrl+Shift+K       Delete line                                            │
│   Ctrl+Space         Trigger suggestions                                    │
│   F12                Go to Definition                                       │
│   Alt+F12            Peek Definition                                        │
│   Shift+Alt+F        Format document                                        │
│                                                                              │
│   SEARCH:                                                                    │
│   Ctrl+F             Find in file                                           │
│   Ctrl+H             Find and replace                                       │
│   Ctrl+Shift+F       Find in all files                                      │
│                                                                              │
│   GIT:                                                                       │
│   Ctrl+Shift+G       Open Source Control                                    │
│   Ctrl+Enter         Commit (in Source Control)                             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Part B: IntelliJ IDEA

### Step B.1: Download IntelliJ IDEA

1. Go to: https://www.jetbrains.com/idea/download/
2. Download **IntelliJ IDEA Community Edition** (Free)
   - Or Ultimate Edition if you have a license (recommended for full Spring support)
3. Save the installer

---

### Step B.2: Install IntelliJ IDEA

1. Run the downloaded installer
2. Select destination folder (default is fine)
3. Installation options:
   - ✅ 64-bit launcher
   - ✅ Add "Open Folder as Project"
   - ✅ .java association
   - ✅ Add launchers dir to PATH
4. Complete installation
5. Launch IntelliJ IDEA

---

### Step B.3: Initial IntelliJ Setup

On first launch:

1. **Import Settings:** Don't import (start fresh)
2. **UI Theme:** Select Dark (Darcula) or Light
3. **Plugins:** Continue with defaults for now
4. **Start using IntelliJ IDEA**

---

### Step B.4: Configure JDK

1. Open IntelliJ IDEA
2. Go to: **File → Project Structure** (Ctrl+Alt+Shift+S)
3. **Platform Settings → SDKs**
4. Click **+** → **Add JDK**
5. Navigate to Java 17 installation:
   - Usually: `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot`
6. Click **OK**

---

### Step B.5: Install Essential Plugins

Go to: **File → Settings → Plugins** (Ctrl+Alt+S)

**Search and install:**

| Plugin | Purpose |
|--------|---------|
| **Lombok** | Support for Lombok annotations |
| **Spring Boot** | Spring Boot support (Ultimate only, or community alternatives) |
| **Docker** | Docker support |
| **GitToolBox** | Enhanced Git features |
| **Rainbow Brackets** | Colored brackets |
| **Key Promoter X** | Learn keyboard shortcuts |
| **.ignore** | Gitignore support |
| **Markdown** | Markdown preview |

Click **Install** for each, then restart IDE.

---

### Step B.6: Configure IntelliJ Settings

Go to: **File → Settings** (Ctrl+Alt+S)

**Editor → General → Auto Import:**
- ✅ Add unambiguous imports on the fly
- ✅ Optimize imports on the fly

**Editor → Code Style → Java:**
- Tab size: 4
- Use tab character: ❌ (use spaces)

**Build, Execution, Deployment → Build Tools → Maven:**
- Maven home path: `C:\apache-maven-3.9.x` (your Maven installation)
- ✅ Use plugin registry

**Build, Execution, Deployment → Compiler → Annotation Processors:**
- ✅ Enable annotation processing (required for Lombok)

---

### Step B.7: Learn IntelliJ Shortcuts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       IntelliJ IDEA Keyboard Shortcuts                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   GENERAL:                                                                   │
│   Ctrl+Shift+A       Find Action (most important!)                          │
│   Shift+Shift        Search Everywhere                                      │
│   Ctrl+N             Open class                                             │
│   Ctrl+Shift+N       Open file                                              │
│   Alt+1              Project window                                         │
│   Alt+F12            Terminal                                               │
│                                                                              │
│   EDITING:                                                                   │
│   Ctrl+Space         Basic completion                                       │
│   Ctrl+Shift+Space   Smart completion                                       │
│   Alt+Enter          Show intention actions (quick fixes!)                  │
│   Ctrl+/             Line comment                                           │
│   Ctrl+Shift+/       Block comment                                          │
│   Ctrl+D             Duplicate line                                         │
│   Ctrl+Y             Delete line                                            │
│   Ctrl+W             Extend selection                                       │
│                                                                              │
│   NAVIGATION:                                                                │
│   Ctrl+B             Go to declaration                                      │
│   Ctrl+Alt+B         Go to implementation                                   │
│   Alt+F7             Find usages                                            │
│   Ctrl+F12           File structure                                         │
│   Ctrl+H             Type hierarchy                                         │
│                                                                              │
│   REFACTORING:                                                               │
│   Shift+F6           Rename                                                 │
│   Ctrl+Alt+M         Extract method                                         │
│   Ctrl+Alt+V         Extract variable                                       │
│   Ctrl+Alt+L         Reformat code                                          │
│   Ctrl+Alt+O         Optimize imports                                       │
│                                                                              │
│   RUN/DEBUG:                                                                 │
│   Shift+F10          Run                                                    │
│   Shift+F9           Debug                                                  │
│   F8                 Step over                                              │
│   F7                 Step into                                              │
│   Ctrl+F8            Toggle breakpoint                                      │
│                                                                              │
│   GIT:                                                                       │
│   Alt+9              Git window                                             │
│   Ctrl+K             Commit                                                 │
│   Ctrl+Shift+K       Push                                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Verification

### Test VS Code with React

```powershell
# Create test React app
cd ~
npx create-vite@latest test-react --template react-ts

# Open in VS Code
cd test-react
code .
```

In VS Code:
1. Open `src/App.tsx`
2. Try typing `rafce` + Tab (creates React component)
3. Save file - should auto-format
4. Open Terminal (Ctrl+`)
5. Run: `npm install && npm run dev`
6. Open http://localhost:5173

**Clean up:**
```powershell
cd ..
Remove-Item -Recurse -Force test-react
```

### Test IntelliJ with Java

1. Open IntelliJ IDEA
2. **New Project**
3. Select **Java** with Maven
4. Project SDK: Java 17
5. Create project
6. Create a test class:

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, PayFlow!");
    }
}
```

7. Click the green **Run** button
8. Verify "Hello, PayFlow!" prints in console

---

## 6. IDE Workflow for PayFlow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Typical Development Workflow                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   MORNING STARTUP:                                                           │
│   1. Open IntelliJ IDEA → Open PayFlow project                              │
│   2. Git pull latest changes                                                │
│   3. Run docker-compose up -d (databases)                                   │
│                                                                              │
│   BACKEND DEVELOPMENT (IntelliJ):                                           │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ 1. Open service (e.g., payment-service)                          │       │
│   │ 2. Write/modify Java code                                        │       │
│   │ 3. Run tests: Ctrl+Shift+F10                                     │       │
│   │ 4. Debug: Set breakpoint + Shift+F9                              │       │
│   │ 5. Hot reload: Spring DevTools auto-restarts                     │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   FRONTEND DEVELOPMENT (VS Code):                                           │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ 1. Open merchant-portal folder: code merchant-portal             │       │
│   │ 2. Run dev server: npm run dev                                   │       │
│   │ 3. Edit React components                                         │       │
│   │ 4. Hot reload: Changes appear instantly                          │       │
│   │ 5. Check browser console for errors                              │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   DOCKER/CONFIG (VS Code):                                                  │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ 1. Edit docker-compose.yml                                       │       │
│   │ 2. Edit application.yml                                          │       │
│   │ 3. Edit GitHub Actions workflows                                 │       │
│   │ 4. Edit README.md                                                │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   END OF DAY:                                                                │
│   1. git add . → git commit → git push                                      │
│   2. docker-compose down                                                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Key Takeaways

| IDE | Use For | Key Shortcut |
|-----|---------|--------------|
| **VS Code** | React, Docker, YAML, quick edits | Ctrl+Shift+P (Command Palette) |
| **IntelliJ** | Java, Spring Boot, debugging | Shift+Shift (Search Everywhere) |

| Concept | Remember |
|---------|----------|
| **Extensions/Plugins** | Both IDEs are highly customizable |
| **Keyboard shortcuts** | Learn them - 10x productivity |
| **Settings sync** | VS Code: Sync with GitHub account |
| **Workspace** | VS Code uses folders, IntelliJ uses projects |

---

## 8. Q&A / Troubleshooting

### VS Code: Extensions not working

**Fix:**
1. Reload window: Ctrl+Shift+P → "Reload Window"
2. Check extension is enabled for workspace
3. Reinstall extension

### IntelliJ: Cannot find JDK

**Fix:**
1. File → Project Structure → SDKs
2. Add JDK manually
3. Point to exact Java installation folder

### IntelliJ: Out of memory

**Fix:**
1. Help → Change Memory Settings
2. Increase to 2048 MB or more
3. Restart IntelliJ

### IntelliJ: Lombok not working

**Fix:**
1. Install Lombok plugin
2. Enable annotation processing:
   - Settings → Build → Compiler → Annotation Processors
   - ✅ Enable annotation processing
3. Rebuild project

### VS Code: Prettier not formatting

**Fix:**
1. Check Prettier is default formatter
2. Settings → Format On Save → ✅
3. Check .prettierrc file exists

---

## 9. Related Concepts

| Concept | Relationship |
|---------|--------------|
| **Maven** | IntelliJ has built-in Maven support |
| **npm** | VS Code terminal runs npm commands |
| **Docker Desktop** | Both IDEs can view container logs |
| **Git** | Both IDEs have Git integration |
| **Debugging** | IntelliJ excels at Java debugging |

---

## 10. Next Steps

**Continue to:** [08-postman-setup.md](./08-postman-setup.md)

In the next guide, you'll set up Postman for API testing.

**What you've accomplished:**
- ✅ Installed VS Code
- ✅ Configured VS Code extensions
- ✅ Installed IntelliJ IDEA
- ✅ Configured IntelliJ plugins
- ✅ Understand when to use each IDE
- ✅ Know essential shortcuts

---

**End of IDE Setup**
