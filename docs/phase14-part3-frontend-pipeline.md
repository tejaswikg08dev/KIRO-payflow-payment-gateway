# Phase 14 Part 3 — GitHub Actions Frontend Pipeline

## Goal
- Create CI workflow for React apps (lint, build, Docker push)
- Run TypeScript type checking and ESLint
- Build and push frontend Docker images

## Key Concept

```
┌──────────────────────────────────────────────────────┐
│  Frontend CI Pipeline                                │
│                                                      │
│  ┌────────┐  ┌──────┐  ┌──────┐  ┌──────────────┐   │
│  │Checkout│→│Install│→│ Lint │→│ Build & Push │   │
│  │  Code  │  │  npm │  │ +tsc │  │   Docker     │   │
│  └────────┘  └──────┘  └──────┘  └──────────────┘   │
│                                                      │
│  Runs on: push to main, PR (frontend paths only)     │
└──────────────────────────────────────────────────────┘
```

## Prerequisites
- Phase 14 Part 2 completed
- React apps have lint and build scripts in package.json

## Step-by-Step

### 1. Frontend Workflow (`.github/workflows/ci-frontend.yml`)

```yaml
name: Frontend CI

on:
  push:
    branches: [main, develop]
    paths:
      - 'merchant-portal/**'
      - 'hosted-checkout/**'
  pull_request:
    branches: [main]
    paths:
      - 'merchant-portal/**'
      - 'hosted-checkout/**'

jobs:
  lint-and-build:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        app: [merchant-portal, hosted-checkout]
    steps:
      - uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'
          cache: 'npm'
          cache-dependency-path: '${{ matrix.app }}/package-lock.json'

      - name: Install dependencies
        working-directory: ${{ matrix.app }}
        run: npm ci

      - name: TypeScript check
        working-directory: ${{ matrix.app }}
        run: npx tsc --noEmit

      - name: Lint
        working-directory: ${{ matrix.app }}
        run: npm run lint

      - name: Build
        working-directory: ${{ matrix.app }}
        run: npm run build
        env:
          VITE_API_URL: https://api.payflow.example.com

      - name: Upload build artifacts
        uses: actions/upload-artifact@v4
        with:
          name: ${{ matrix.app }}-dist
          path: ${{ matrix.app }}/dist

  docker-push:
    needs: lint-and-build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    strategy:
      matrix:
        app: [merchant-portal, hosted-checkout]
    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: ./${{ matrix.app }}
          push: true
          tags: |
            ghcr.io/${{ github.repository }}/${{ matrix.app }}:latest
            ghcr.io/${{ github.repository }}/${{ matrix.app }}:${{ github.sha }}
          build-args: |
            VITE_API_URL=https://api.payflow.example.com
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

### 2. Add ESLint Config (`merchant-portal/.eslintrc.cjs`)

```javascript
module.exports = {
  root: true,
  env: { browser: true, es2020: true },
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:react-hooks/recommended',
  ],
  ignorePatterns: ['dist', '.eslintrc.cjs'],
  parser: '@typescript-eslint/parser',
  plugins: ['react-refresh'],
  rules: {
    'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    '@typescript-eslint/no-explicit-any': 'warn',
  },
};
```

### 3. Add Script to package.json

```json
{
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "lint": "eslint . --ext ts,tsx --report-unused-disable-directives --max-warnings 0",
    "preview": "vite preview"
  }
}
```

## Verification

```bash
# Push frontend changes
git push origin main

# GitHub Actions → Frontend CI workflow triggers
# Expected:
# ✓ merchant-portal: lint, tsc, build (parallel)
# ✓ hosted-checkout: lint, tsc, build (parallel)
# ✓ Docker images pushed to GHCR

# Verify no lint errors locally first
cd merchant-portal && npm run lint
cd ../hosted-checkout && npm run lint
```

## Git Commit

```bash
git add .github/workflows/ci-frontend.yml
git add merchant-portal/.eslintrc.cjs hosted-checkout/.eslintrc.cjs
git commit -m "ci: add frontend CI pipeline with lint, build, and Docker push"
```

## Next Step
→ **Phase 14 Part 4** — Deployment automation with SSH
