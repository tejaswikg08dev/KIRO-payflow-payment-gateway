# Environment Setup — Node.js Installation

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## What is Node.js?

Node.js is a **JavaScript runtime** that lets you run JavaScript outside the browser. We use it for:
- React frontend development
- npm (package manager)
- Vite (build tool)

---

## Step 1: Download Node.js

1. Go to: https://nodejs.org/
2. Download **LTS version** (18.x or 20.x)
3. Download the Windows Installer (.msi)

---

## Step 2: Install Node.js

1. Run the downloaded **.msi** file
2. Accept the license agreement
3. Use default installation path
4. **Important:** Check "Automatically install necessary tools"
5. Complete installation

---

## Step 3: Verify Installation

**Open a NEW terminal:**

```powershell
# Check Node.js version
node -v
```

**Expected Output:**
```
v18.x.x  (or v20.x.x)
```

```powershell
# Check npm version
npm -v
```

**Expected Output:**
```
9.x.x  (or 10.x.x)
```

---

## Step 4: Configure npm (Optional)

Set npm to use a local cache:

```powershell
# Set cache location
npm config set cache C:\Users\%USERNAME%\.npm-cache --global

# View npm config
npm config list
```

---

## Key npm Commands

| Command | Purpose |
|---------|---------|
| `npm install` | Install dependencies |
| `npm run dev` | Start dev server |
| `npm run build` | Build for production |
| `npm run test` | Run tests |
| `npm install <package>` | Add new package |
| `npm update` | Update packages |

---

## Troubleshooting

### "node is not recognized"

**Fix:**
1. Restart terminal
2. Check PATH includes Node.js
3. Reinstall Node.js

### npm errors on Windows

**Fix:** Run as Administrator or:
```powershell
Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
```

---

## Next Steps

**Continue to:** [05-docker-desktop-setup.md](./05-docker-desktop-setup.md)

---

**End of Node.js Installation**
