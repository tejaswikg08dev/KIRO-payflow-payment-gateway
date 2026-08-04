# Environment Setup — Windows Overview

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## Overview

This guide walks you through setting up your Windows development environment for PayFlow. By the end, you'll have all tools installed and verified.

---

## Tools to Install

| Tool | Version | Purpose | Install Order |
|------|---------|---------|---------------|
| Java 17 | 17.x (LTS) | Backend development | 1 |
| Maven | 3.9+ | Build tool | 2 |
| Node.js | 18+ | Frontend development | 3 |
| Docker Desktop | Latest | Containers | 4 |
| Git | Latest | Version control | 5 |
| VS Code / IntelliJ | Latest | IDE | 6 |
| Postman | Latest | API testing | 7 |

---

## System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| OS | Windows 10 64-bit | Windows 11 64-bit |
| RAM | 8 GB | 16 GB |
| Disk | 50 GB free | 100 GB SSD |
| CPU | 4 cores | 8 cores |

---

## Installation Checklist

Use this checklist as you go through each setup document:

- [ ] Java 17 installed and JAVA_HOME set
- [ ] Maven installed and M2_HOME set
- [ ] Node.js 18+ installed
- [ ] Docker Desktop running with WSL2
- [ ] Git configured with SSH keys
- [ ] IDE installed with extensions
- [ ] Postman installed

---

## Folder Structure

Create this folder for all your work:

```
C:\
└── dev\
    └── payflow\
        └── (project will be cloned here)
```

**Create it now:**
```powershell
mkdir C:\dev\payflow
```

---

## Next Steps

**Continue to:** [02-java-17-installation.md](./02-java-17-installation.md)

Let's start with Java installation.

---

**End of Windows Overview**
