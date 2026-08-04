# Environment Setup — Git Installation & Configuration

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## 1. What We're Building

In this guide, you'll install and configure **Git** for version control. Git is essential for:
- Tracking code changes across sprints
- Collaborating with team members
- Creating branches for each feature
- Managing code history and reverting mistakes
- Integrating with GitHub for CI/CD pipelines

---

## 2. Concepts Deep Dive

### What is Git?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Version Control with Git                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   WITHOUT VERSION CONTROL:              WITH GIT:                           │
│                                                                              │
│   project_v1/                           project/                            │
│   project_v2/                              └── .git/  ◄── All history here  │
│   project_v2_final/                                                         │
│   project_v2_final_REAL/                                                    │
│   project_v2_final_REAL_v2/                                                 │
│                                                                              │
│   Problems:                             Benefits:                            │
│   • Files everywhere                    • Single folder                     │
│   • Which is latest?                    • Full history preserved            │
│   • Can't undo changes                  • Easy rollback to any point        │
│   • No collaboration                    • Multiple people, no conflicts     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Git Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Git Workflow Areas                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Working Directory          Staging Area            Local Repository       │
│   (Your files)              (Index)                  (.git folder)          │
│   ┌─────────────┐           ┌─────────────┐         ┌─────────────┐        │
│   │             │  git add  │             │ git     │             │        │
│   │  Edit       │ ───────►  │  Stage      │ commit  │   Save      │        │
│   │  Files      │           │  Changes    │ ──────► │   History   │        │
│   │             │           │             │         │             │        │
│   └─────────────┘           └─────────────┘         └─────────────┘        │
│         │                                                   │               │
│         │                     git push                      │               │
│         │                    ───────────────────────────────┼───►          │
│         │                                                   │    Remote    │
│         │◄──────────────────────────────────────────────────│    (GitHub)  │
│                               git pull                                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Git Branching Strategy for PayFlow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        PayFlow Branching Model                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   main (production-ready)                                                   │
│   ════════════════════════════════════════════════════════════════►        │
│        │                    │                    │                          │
│        │ merge              │ merge              │ merge                    │
│        │                    │                    │                          │
│   develop (integration)                                                     │
│   ─────●────────●────────●────────●────────●────────●──────────────►        │
│        │\       │\       │       /│       /│       /                        │
│        │ \      │ \      │      / │      / │      /                         │
│   feature/auth  │  \     │     /  │     /  │     /                          │
│   ──────●───────●   \    │    /   │    /   │    /                           │
│              sprint-1\   │   /    │   /    │   /                            │
│                        \ │  /     │  /     │  /                             │
│   feature/api-keys      \│ /      │ /      │ /                              │
│   ────────────────●──────●/       │/       │/                               │
│                    sprint-2      │        │                                 │
│                                  │        │                                 │
│   feature/orders                 │        │                                 │
│   ────────────────────────●──────●        │                                 │
│                           sprint-3       │                                  │
│                                          │                                  │
│   Branch Naming:                        │                                   │
│   • feature/auth-login                  │                                   │
│   • feature/payment-card                │                                   │
│   • bugfix/null-pointer-fix             │                                   │
│   • hotfix/security-patch               │                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Git Concepts

| Concept | What It Is | Example |
|---------|------------|---------|
| **Repository** | Project folder with .git history | PayFlow project |
| **Commit** | Snapshot of changes with message | "Add login API endpoint" |
| **Branch** | Parallel line of development | feature/auth-login |
| **Merge** | Combine branches together | feature → develop |
| **Pull Request** | Request to merge (GitHub) | Code review before merge |
| **Clone** | Copy remote repo to local | Get PayFlow from GitHub |
| **Push** | Upload local commits to remote | Share your work |
| **Pull** | Download remote commits to local | Get team's work |

---

## 3. Prerequisites

| Requirement | Status |
|-------------|--------|
| Windows 10/11 | Required |
| GitHub account | Create at github.com |
| Terminal access | PowerShell or Git Bash |

---

## 4. Step-by-Step Installation

### Step 4.1: Download Git

1. Go to: https://git-scm.com/download/windows
2. Click **"Click here to download"** (64-bit)
3. Save the installer

---

### Step 4.2: Install Git

Run the downloaded installer with these settings:

**Select Components:**
- ✅ Windows Explorer integration
- ✅ Git Bash Here
- ✅ Git GUI Here
- ✅ Associate .git* files with default text editor

**Default Editor:**
- Select **Visual Studio Code** (or your preferred editor)

**Initial Branch Name:**
- Select **"Override the default branch name for new repositories"**
- Enter: `main`

**PATH Environment:**
- Select **"Git from the command line and also from 3rd-party software"**

**SSH:**
- Select **"Use bundled OpenSSH"**

**HTTPS Transport:**
- Select **"Use the native Windows Secure Channel library"**

**Line Ending:**
- Select **"Checkout Windows-style, commit Unix-style line endings"**

**Terminal:**
- Select **"Use MinTTY (the default terminal of MSYS2)"**

**Default pull behavior:**
- Select **"Default (fast-forward or merge)"**

**Credential Helper:**
- Select **"Git Credential Manager"**

Complete the installation.

---

### Step 4.3: Verify Installation

**Open a NEW terminal:**

```powershell
# Check Git version
git --version
```

**Expected Output:**
```
git version 2.42.x.windows.x
```

```powershell
# Check Git Bash
# Right-click on desktop → "Git Bash Here"
# Or search "Git Bash" in Start menu
```

---

### Step 4.4: Configure Git Identity

Git needs to know who you are for commit history:

```powershell
# Set your name (appears in commits)
git config --global user.name "Your Full Name"

# Set your email (use GitHub email)
git config --global user.email "your.email@example.com"

# Verify configuration
git config --list
```

**Expected Output:**
```
user.name=Your Full Name
user.email=your.email@example.com
...
```

---

### Step 4.5: Configure Git Settings

```powershell
# Set default branch name to 'main'
git config --global init.defaultBranch main

# Set VS Code as default editor
git config --global core.editor "code --wait"

# Enable colored output
git config --global color.ui auto

# Set line ending handling (Windows)
git config --global core.autocrlf true

# Set credential helper (remembers passwords)
git config --global credential.helper manager
```

---

### Step 4.6: Generate SSH Key (Recommended)

SSH keys let you push to GitHub without entering passwords:

```powershell
# Generate SSH key pair
ssh-keygen -t ed25519 -C "your.email@example.com"
```

**Prompts:**
- File location: Press **Enter** (use default)
- Passphrase: Enter a secure passphrase (or press Enter for none)

```powershell
# Start SSH agent
Get-Service ssh-agent | Set-Service -StartupType Automatic
Start-Service ssh-agent

# Add your key to the agent
ssh-add ~/.ssh/id_ed25519

# Copy public key to clipboard
Get-Content ~/.ssh/id_ed25519.pub | Set-Clipboard
```

---

### Step 4.7: Add SSH Key to GitHub

1. Go to: https://github.com/settings/keys
2. Click **"New SSH key"**
3. Title: "My Windows Laptop" (or descriptive name)
4. Key type: **Authentication Key**
5. Key: **Paste** your copied key
6. Click **"Add SSH key"**

**Verify SSH connection:**

```powershell
# Test SSH connection to GitHub
ssh -T git@github.com
```

**Expected Output:**
```
Hi username! You've successfully authenticated, but GitHub does not provide shell access.
```

---

## 5. Verification

### Create a Test Repository

```powershell
# Create test directory
mkdir git-test
cd git-test

# Initialize Git repository
git init

# Check status
git status
```

**Expected Output:**
```
Initialized empty Git repository in C:/Users/.../git-test/.git/
On branch main
No commits yet
nothing to commit (create/copy files and use "git add" to track)
```

### Make Your First Commit

```powershell
# Create a test file
echo "# My Test Project" > README.md

# Check status (file is untracked)
git status

# Stage the file
git add README.md

# Check status (file is staged)
git status

# Commit with message
git commit -m "Initial commit: Add README"

# View commit history
git log --oneline
```

**Expected Output:**
```
abc1234 (HEAD -> main) Initial commit: Add README
```

### Test Branching

```powershell
# Create and switch to new branch
git checkout -b feature/test-feature

# Check current branch
git branch

# Make a change
echo "New feature content" >> README.md

# Commit the change
git add .
git commit -m "Add new feature content"

# Switch back to main
git checkout main

# Merge feature branch
git merge feature/test-feature

# Delete feature branch
git branch -d feature/test-feature

# View history
git log --oneline --graph
```

### Clean Up

```powershell
# Go back to parent directory
cd ..

# Remove test directory
Remove-Item -Recurse -Force git-test
```

---

## 6. Understanding Git Commands

### Essential Git Commands

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Git Command Reference                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   SETUP & INIT:                                                              │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ git init                   # Create new repo in current folder   │       │
│   │ git clone url              # Copy remote repo to local           │       │
│   │ git config --list          # View all settings                   │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   DAILY WORKFLOW:                                                            │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ git status                 # Check what's changed                │       │
│   │ git add <file>             # Stage specific file                 │       │
│   │ git add .                  # Stage all changes                   │       │
│   │ git commit -m "message"    # Commit staged changes               │       │
│   │ git push                   # Upload to remote                    │       │
│   │ git pull                   # Download from remote                │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   BRANCHING:                                                                 │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ git branch                 # List branches                       │       │
│   │ git branch name            # Create new branch                   │       │
│   │ git checkout name          # Switch to branch                    │       │
│   │ git checkout -b name       # Create and switch (shortcut)        │       │
│   │ git merge branch           # Merge branch into current           │       │
│   │ git branch -d name         # Delete branch                       │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   HISTORY & UNDO:                                                            │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ git log                    # View commit history                 │       │
│   │ git log --oneline          # Compact history view                │       │
│   │ git diff                   # Show unstaged changes               │       │
│   │ git diff --staged          # Show staged changes                 │       │
│   │ git restore <file>         # Discard changes in file             │       │
│   │ git reset HEAD~1           # Undo last commit (keep changes)     │       │
│   │ git revert <commit>        # Create new commit that undoes       │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   REMOTE:                                                                    │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ git remote -v              # List remote repositories            │       │
│   │ git remote add origin url  # Add remote                          │       │
│   │ git fetch                  # Download without merging            │       │
│   │ git push -u origin main    # Push and set upstream               │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Commit Message Best Practices

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Good Commit Messages                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   FORMAT:                                                                    │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ <type>: <short description>                                      │       │
│   │                                                                  │       │
│   │ [optional body with more details]                                │       │
│   │                                                                  │       │
│   │ [optional footer with issue references]                          │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   TYPES:                                                                     │
│   • feat:     New feature                                                   │
│   • fix:      Bug fix                                                       │
│   • docs:     Documentation only                                            │
│   • style:    Formatting, no code change                                    │
│   • refactor: Code change, no new feature or fix                            │
│   • test:     Adding tests                                                  │
│   • chore:    Maintenance tasks                                             │
│                                                                              │
│   EXAMPLES:                                                                  │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ feat: Add user registration API endpoint                         │       │
│   │ fix: Resolve null pointer in payment validation                  │       │
│   │ docs: Update API documentation for orders                        │       │
│   │ refactor: Extract payment validation to separate class           │       │
│   │ test: Add unit tests for fraud detection rules                   │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   BAD EXAMPLES (avoid these):                                               │
│   • "fix"                                                                   │
│   • "update"                                                                │
│   • "WIP"                                                                   │
│   • "asdfasdf"                                                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Key Takeaways

| Concept | Remember |
|---------|----------|
| **Git** | Distributed version control, tracks all changes |
| **Commit** | Save point with message, can always go back |
| **Branch** | Parallel development, isolate features |
| **main** | Production-ready code only |
| **develop** | Integration branch for features |
| **feature/** | Branch prefix for new features |
| **SSH key** | Secure authentication without passwords |

---

## 8. Q&A / Troubleshooting

### "git is not recognized"

**Fix:**
1. Restart terminal
2. Check PATH includes Git:
```powershell
$env:PATH -split ';' | Select-String -Pattern 'Git'
```
3. Reinstall Git

### "Permission denied (publickey)"

**Fix:**
1. Check SSH key exists:
```powershell
ls ~/.ssh/
```
2. Regenerate SSH key
3. Add to GitHub again

### "Failed to push, rejected"

**Fix:**
```powershell
# Pull remote changes first
git pull --rebase

# Then push
git push
```

### Merge conflicts

**Fix:**
1. Open conflicting files
2. Look for conflict markers:
```
<<<<<<< HEAD
Your changes
=======
Their changes
>>>>>>> branch-name
```
3. Edit to keep desired code
4. Remove conflict markers
5. Stage and commit

### Accidentally committed sensitive data

**Fix:**
```powershell
# Remove from history (careful!)
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch <file>" \
  --prune-empty --tag-name-filter cat -- --all

# Force push
git push origin --force --all

# Change any exposed credentials immediately!
```

---

## 9. Related Concepts

| Concept | Relationship to Git |
|---------|---------------------|
| **GitHub** | Remote hosting for Git repositories |
| **GitHub Actions** | CI/CD that triggers on git push |
| **Pull Request** | Code review before merging |
| **.gitignore** | Files Git should ignore |
| **GitHub CLI** | Command-line tool for GitHub |

---

## 10. Next Steps

**Continue to:** [07-ide-setup.md](./07-ide-setup.md)

In the next guide, you'll set up VS Code and IntelliJ IDEA.

**What you've accomplished:**
- ✅ Installed Git
- ✅ Configured Git identity
- ✅ Generated SSH key
- ✅ Connected to GitHub
- ✅ Understand Git workflow
- ✅ Know essential Git commands

---

**End of Git Setup**
