# Environment Setup — Docker Desktop Installation

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## 1. What We're Building

In this guide, you'll install and configure **Docker Desktop** on Windows. Docker is essential for:
- Running PostgreSQL, Redis, and other infrastructure locally
- Testing microservices in isolated containers
- Simulating production environment on your laptop
- Building container images for AWS deployment

---

## 2. Concepts Deep Dive

### What is Docker?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Traditional vs Container Deployment                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Traditional (VM-based):              Container-based (Docker):            │
│   ┌─────────────────────┐              ┌─────────────────────┐              │
│   │    Application      │              │    Application      │              │
│   ├─────────────────────┤              ├─────────────────────┤              │
│   │   Guest OS (4GB)    │              │   Container (50MB)  │              │
│   ├─────────────────────┤              ├─────────────────────┤              │
│   │     Hypervisor      │              │    Docker Engine    │              │
│   ├─────────────────────┤              ├─────────────────────┤              │
│   │      Host OS        │              │      Host OS        │              │
│   ├─────────────────────┤              ├─────────────────────┤              │
│   │     Hardware        │              │     Hardware        │              │
│   └─────────────────────┘              └─────────────────────┘              │
│                                                                              │
│   Startup: 30+ seconds                 Startup: 1-2 seconds                 │
│   Size: 4-10 GB per VM                 Size: 50-500 MB per container        │
│   Isolation: Full OS                   Isolation: Process-level             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Docker Concepts

| Concept | What It Is | PayFlow Example |
|---------|------------|-----------------|
| **Image** | Read-only template with application code | `payflow/payment-service:1.0` |
| **Container** | Running instance of an image | Your running payment-service |
| **Dockerfile** | Recipe to build an image | Instructions to package Java app |
| **Docker Compose** | Run multiple containers together | All 11 services + databases |
| **Volume** | Persistent storage for containers | PostgreSQL data survives restart |
| **Network** | Virtual network for containers | Services communicate internally |

### How Docker Works

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Docker Architecture                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   You (Developer)                                                            │
│        │                                                                     │
│        │ docker build / docker run / docker-compose up                      │
│        ▼                                                                     │
│   ┌─────────────────┐                                                        │
│   │  Docker Client  │  ◄── CLI commands you type                            │
│   └────────┬────────┘                                                        │
│            │ REST API                                                        │
│            ▼                                                                 │
│   ┌─────────────────┐                                                        │
│   │  Docker Daemon  │  ◄── Background service (dockerd)                     │
│   └────────┬────────┘                                                        │
│            │                                                                 │
│    ┌───────┴───────┬─────────────┐                                          │
│    ▼               ▼             ▼                                          │
│ ┌──────┐      ┌──────┐      ┌──────┐                                        │
│ │Image │      │Image │      │Image │   ◄── Stored locally                   │
│ │ repo │      │ cache│      │builds│                                        │
│ └──┬───┘      └──────┘      └──────┘                                        │
│    │                                                                         │
│    ▼                                                                         │
│ ┌───────────────────────────────────────┐                                   │
│ │           Running Containers          │                                   │
│ │  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  │                                   │
│ │  │ DB  │  │Redis│  │ API │  │ Web │  │                                   │
│ │  └─────┘  └─────┘  └─────┘  └─────┘  │                                   │
│ └───────────────────────────────────────┘                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Why Docker for PayFlow?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        PayFlow Docker Usage                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   LOCAL DEVELOPMENT (docker-compose.yml):                                    │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │                     Docker Compose Stack                         │       │
│   │                                                                  │       │
│   │   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │       │
│   │   │PostgreSQL│  │  Redis   │  │ DynamoDB │  │   SQS    │       │       │
│   │   │ :5432    │  │  :6379   │  │  :8000   │  │  :9324   │       │       │
│   │   └──────────┘  └──────────┘  └──────────┘  └──────────┘       │       │
│   │                                                                  │       │
│   │   ┌──────────────────────────────────────────────────────┐      │       │
│   │   │              11 PayFlow Microservices                 │      │       │
│   │   │  service-registry │ config-server │ api-gateway │ ... │      │       │
│   │   └──────────────────────────────────────────────────────┘      │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   PRODUCTION (AWS ECS):                                                      │
│   Same Docker images → Deploy to AWS ECS Fargate                            │
│   "Works on my machine" → "Works everywhere"                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

Before installing Docker Desktop:

| Requirement | Check Command | Expected |
|-------------|---------------|----------|
| Windows 10/11 (64-bit) | `winver` | Version 1903+ |
| 4GB+ RAM (8GB recommended) | Task Manager | 8GB+ |
| Virtualization enabled | Task Manager → Performance | Enabled |
| WSL 2 installed | `wsl --version` | Version 2.x |

### Enable Virtualization (if needed)

1. Restart computer
2. Enter BIOS (usually F2, F10, or Del during boot)
3. Find **Virtualization Technology** or **VT-x**
4. Enable it
5. Save and exit

---

## 4. Step-by-Step Installation

### Step 4.1: Install WSL 2 (Windows Subsystem for Linux)

Docker Desktop uses WSL 2 for better performance.

**Open PowerShell as Administrator:**

```powershell
# Install WSL with Ubuntu (default)
wsl --install
```

**What this does:**
- Enables WSL feature
- Installs Ubuntu Linux distribution
- Sets WSL 2 as default

**After installation, restart your computer.**

**Verify WSL 2:**

```powershell
# Check WSL version
wsl --version
```

**Expected Output:**
```
WSL version: 2.0.x
Kernel version: 5.15.x
```

```powershell
# List installed distributions
wsl --list --verbose
```

**Expected Output:**
```
  NAME      STATE           VERSION
* Ubuntu    Running         2
```

---

### Step 4.2: Download Docker Desktop

1. Go to: https://www.docker.com/products/docker-desktop/
2. Click **Download for Windows**
3. Save the installer (approximately 500MB)

---

### Step 4.3: Install Docker Desktop

1. Run the downloaded **Docker Desktop Installer.exe**
2. On the configuration screen:
   - ✅ Check **Use WSL 2 instead of Hyper-V** (recommended)
   - ✅ Check **Add shortcut to desktop**
3. Click **Ok** and wait for installation
4. Click **Close and restart** when prompted

---

### Step 4.4: Initial Docker Setup

After restart:

1. Docker Desktop starts automatically
2. Accept the Service Agreement
3. Skip the sign-in (optional, not required)
4. Wait for Docker Engine to start (whale icon in system tray turns steady)

---

### Step 4.5: Configure Docker Settings

Open Docker Desktop → Settings (gear icon):

**General:**
- ✅ Start Docker Desktop when you sign in to Windows
- ✅ Use the WSL 2 based engine

**Resources → WSL Integration:**
- ✅ Enable integration with my default WSL distro
- ✅ Enable integration with: Ubuntu

**Resources → Advanced:**
- Memory: **4 GB minimum** (8 GB recommended for PayFlow)
- CPUs: **2 minimum** (4 recommended)
- Disk image size: **64 GB** (for all images)

Click **Apply & Restart**

---

## 5. Verification

### Verify Docker Installation

**Open a NEW terminal (PowerShell or CMD):**

```powershell
# Check Docker version
docker --version
```

**Expected Output:**
```
Docker version 24.x.x, build xxxxxxx
```

```powershell
# Check Docker Compose version
docker compose version
```

**Expected Output:**
```
Docker Compose version v2.x.x
```

```powershell
# Run hello-world container
docker run hello-world
```

**Expected Output:**
```
Hello from Docker!
This message shows that your installation appears to be working correctly.
...
```

### Verify Docker Compose

```powershell
# Create a test docker-compose.yml
mkdir docker-test
cd docker-test
```

Create a file named `docker-compose.yml`:

```yaml
# docker-compose.yml - Test file
version: '3.8'
services:
  web:
    image: nginx:alpine
    ports:
      - "8080:80"
```

```powershell
# Start the container
docker compose up -d

# Check running containers
docker ps

# Test the web server
curl http://localhost:8080
# Or open http://localhost:8080 in browser

# Stop and remove
docker compose down

# Clean up
cd ..
rmdir /s docker-test
```

### Run PostgreSQL Test

This is what we'll use in PayFlow:

```powershell
# Run PostgreSQL container
docker run -d ^
  --name test-postgres ^
  -e POSTGRES_USER=testuser ^
  -e POSTGRES_PASSWORD=testpass ^
  -e POSTGRES_DB=testdb ^
  -p 5432:5432 ^
  postgres:15-alpine

# Check it's running
docker ps

# View logs
docker logs test-postgres

# Connect (if you have psql installed)
# psql -h localhost -U testuser -d testdb

# Stop and remove
docker stop test-postgres
docker rm test-postgres
```

---

## 6. Understanding Docker Commands

### Essential Docker Commands

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Docker Command Reference                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   IMAGE COMMANDS:                                                            │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ docker images              # List all images                     │       │
│   │ docker pull nginx          # Download image from registry        │       │
│   │ docker build -t name .     # Build image from Dockerfile         │       │
│   │ docker rmi image_name      # Remove an image                     │       │
│   │ docker image prune         # Remove unused images                │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   CONTAINER COMMANDS:                                                        │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ docker ps                  # List running containers             │       │
│   │ docker ps -a               # List ALL containers                 │       │
│   │ docker run image           # Create and start container          │       │
│   │ docker start container     # Start stopped container             │       │
│   │ docker stop container      # Stop running container              │       │
│   │ docker rm container        # Remove stopped container            │       │
│   │ docker logs container      # View container logs                 │       │
│   │ docker exec -it container bash  # Enter container shell         │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   DOCKER COMPOSE COMMANDS:                                                   │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ docker compose up -d       # Start all services (detached)       │       │
│   │ docker compose down        # Stop and remove all services        │       │
│   │ docker compose ps          # List compose services               │       │
│   │ docker compose logs -f     # Follow logs of all services         │       │
│   │ docker compose build       # Rebuild all images                  │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   CLEANUP COMMANDS:                                                          │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ docker system prune        # Remove unused data                  │       │
│   │ docker volume prune        # Remove unused volumes               │       │
│   │ docker network prune       # Remove unused networks              │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Docker Run Flags Explained

```powershell
docker run -d -p 8080:80 --name myapp -e VAR=value -v /host:/container nginx
#          │  │          │            │             │                    │
#          │  │          │            │             │                    └── Image name
#          │  │          │            │             └── Volume mount
#          │  │          │            └── Environment variable
#          │  │          └── Container name
#          │  └── Port mapping (host:container)
#          └── Detached mode (run in background)
```

---

## 7. Key Takeaways

| Concept | Remember |
|---------|----------|
| **Docker** | Containers = lightweight VMs, package app + dependencies |
| **Image** | Blueprint/recipe, read-only, versioned |
| **Container** | Running instance of image, isolated, ephemeral |
| **Docker Compose** | Multi-container orchestration, one YAML file |
| **Volume** | Persistent data that survives container restart |
| **WSL 2** | Windows uses Linux kernel for Docker, better performance |

---

## 8. Q&A / Troubleshooting

### "Docker daemon is not running"

**Fix:**
1. Open Docker Desktop application
2. Wait for the whale icon to become steady
3. If stuck, restart Docker Desktop

### "WSL 2 installation is incomplete"

**Fix:**
```powershell
# Update WSL kernel
wsl --update

# Set WSL 2 as default
wsl --set-default-version 2
```

### "Port already in use"

**Fix:**
```powershell
# Find what's using the port
netstat -ano | findstr :5432

# Kill the process (replace PID)
taskkill /PID 12345 /F

# Or use a different port in docker-compose.yml
```

### Docker is slow or using too much RAM

**Fix:**
1. Docker Desktop → Settings → Resources
2. Reduce Memory limit to 4GB
3. Reduce CPUs to 2
4. Apply & Restart

### Cannot pull images (network error)

**Fix:**
1. Check internet connection
2. Docker Desktop → Settings → Docker Engine
3. Add DNS configuration:
```json
{
  "dns": ["8.8.8.8", "8.8.4.4"]
}
```

---

## 9. Related Concepts

| Concept | Relationship to Docker |
|---------|------------------------|
| **Kubernetes** | Container orchestration at scale (we use ECS instead) |
| **AWS ECS** | Amazon's container service (where we deploy) |
| **AWS ECR** | Amazon's Docker image registry |
| **Dockerfile** | Instructions to build images (Sprint 1+) |
| **docker-compose.yml** | Define multi-container apps (Sprint 0) |

---

## 10. Next Steps

**Continue to:** [06-git-setup.md](./06-git-setup.md)

In the next guide, you'll set up Git for version control.

**What you've accomplished:**
- ✅ Installed WSL 2
- ✅ Installed Docker Desktop
- ✅ Configured Docker settings
- ✅ Verified Docker works
- ✅ Understand Docker concepts
- ✅ Know essential Docker commands

---

**End of Docker Desktop Setup**
