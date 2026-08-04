# Phase 13 Part 2 — Dockerfiles for React Frontends

## Goal
- Create multi-stage Dockerfiles for merchant-portal and hosted-checkout
- Use Node for building and Nginx for serving static files
- Optimize image size with layer caching

## Key Concept

```
┌────────────────────────────────────────────────────┐
│  Multi-Stage Build                                 │
│                                                    │
│  Stage 1: BUILD (node:18-alpine)                   │
│  ┌──────────────────────────────────┐              │
│  │ COPY package*.json               │              │
│  │ RUN npm ci                       │ ← cached     │
│  │ COPY src/                        │              │
│  │ RUN npm run build                │ → dist/      │
│  └──────────────────────────────────┘              │
│                     │                              │
│                     ▼                              │
│  Stage 2: SERVE (nginx:alpine)                     │
│  ┌──────────────────────────────────┐              │
│  │ COPY --from=build dist/ → html/  │              │
│  │ COPY nginx.conf                  │              │
│  │ EXPOSE 80                        │              │
│  └──────────────────────────────────┘              │
│                                                    │
│  Final image: ~25MB (vs ~1GB with node)            │
└────────────────────────────────────────────────────┘
```

## Prerequisites
- React apps build successfully with `npm run build`
- Docker installed

## Step-by-Step

### 1. Merchant Portal Dockerfile (`merchant-portal/Dockerfile`)

```dockerfile
# Stage 1: Build
FROM node:18-alpine AS build
WORKDIR /app

# Install dependencies (cached layer)
COPY package.json package-lock.json ./
RUN npm ci --silent

# Copy source and build
COPY . .
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### 2. Nginx Config (`merchant-portal/nginx.conf`)

```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    # SPA routing — all paths serve index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Cache static assets
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2?)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
}
```

### 3. Hosted Checkout Dockerfile (`hosted-checkout/Dockerfile`)

```dockerfile
# Stage 1: Build
FROM node:18-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --silent

COPY . .
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# Stage 2: Serve
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### 4. Docker Ignore Files (`.dockerignore`)

```
node_modules
dist
.git
.env
*.md
```

### 5. Build and Test

```bash
# Build merchant portal
cd merchant-portal
docker build --build-arg VITE_API_URL=http://api.payflow.local -t payflow/merchant-portal:latest .

# Build hosted checkout
cd ../hosted-checkout
docker build --build-arg VITE_API_URL=http://api.payflow.local -t payflow/hosted-checkout:latest .

# Check image sizes
docker images | grep payflow
# payflow/merchant-portal   latest   25MB
# payflow/hosted-checkout   latest   23MB
```

## Verification

```bash
# Run merchant portal
docker run -d -p 3000:80 --name portal payflow/merchant-portal:latest
curl -I http://localhost:3000
# HTTP/1.1 200 OK
# Content-Type: text/html

# Test SPA routing
curl -I http://localhost:3000/dashboard
# HTTP/1.1 200 OK (serves index.html, not 404)

# Test static caching
curl -I http://localhost:3000/assets/index-abc123.js
# Cache-Control: public, immutable

# Cleanup
docker stop portal && docker rm portal
```

## Git Commit

```bash
git add merchant-portal/Dockerfile merchant-portal/nginx.conf merchant-portal/.dockerignore
git add hosted-checkout/Dockerfile hosted-checkout/nginx.conf hosted-checkout/.dockerignore
git commit -m "build(docker): add multi-stage Dockerfiles for React frontends"
```

## Next Step
→ **Phase 13 Part 3** — Full-stack docker-compose with all services
