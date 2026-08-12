# ============================================
# PayFlow Frontend Checkout - Multi-stage Build
# ============================================
# This Dockerfile builds the React/TypeScript hosted checkout page
# and serves it with Nginx for production.

# Stage 1: Build with Node.js
FROM node:18-alpine AS build
WORKDIR /app

# Accept build-time environment variables
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=${VITE_API_URL}

# Copy package files for dependency installation
COPY package*.json ./

# Install dependencies (using npm ci for reproducible builds)
RUN npm ci

# Copy source code
COPY . .

# Build the application
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:1.25-alpine AS runtime

# Copy custom nginx configuration
COPY --from=build /app/nginx.conf /etc/nginx/conf.d/default.conf 2>/dev/null || true

# Copy built assets from build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Create default nginx config if not provided
RUN if [ ! -f /etc/nginx/conf.d/default.conf ]; then \
    echo 'server { \
        listen 80; \
        server_name localhost; \
        root /usr/share/nginx/html; \
        index index.html; \
        location / { \
            try_files $uri $uri/ /index.html; \
        } \
        location /assets { \
            expires 1y; \
            add_header Cache-Control "public, immutable"; \
        } \
        gzip on; \
        gzip_types text/plain text/css application/json application/javascript text/xml application/xml; \
    }' > /etc/nginx/conf.d/default.conf; \
    fi

# Expose port 80
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:80/ || exit 1

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
