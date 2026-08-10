# Sprint 1, Part 14: Frontend Dashboard Setup

**Duration:** 2-3 hours  
**Prerequisites:** Part 13 completed, Node.js 18+ installed

---

## 1. What We're Building

In this part, you'll create the **React frontend** for the PayFlow Merchant Dashboard.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     MERCHANT DASHBOARD OVERVIEW                              │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Tech Stack                                        │   │
│  │                                                                      │   │
│  │  • React 18.3.1 - UI library                                        │   │
│  │  • TypeScript 5.4.5 - Type safety                                   │   │
│  │  • Vite 5.3.1 - Build tool (fast!)                                  │   │
│  │  • React Router 6.23.1 - Navigation                                 │   │
│  │  • Axios 1.7.2 - HTTP client                                        │   │
│  │  • Tailwind CSS 3.4.4 - Styling                                     │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Folder Structure                                  │   │
│  │                                                                      │   │
│  │  frontend-dashboard/                                                │   │
│  │  ├── src/                                                           │   │
│  │  │   ├── pages/          # Route pages                              │   │
│  │  │   ├── services/       # API calls                                │   │
│  │  │   ├── App.tsx         # Root component + routing                 │   │
│  │  │   ├── main.tsx        # Entry point                              │   │
│  │  │   └── index.css       # Tailwind + global styles                 │   │
│  │  ├── index.html          # Entry HTML                               │   │
│  │  ├── package.json        # Dependencies                             │   │
│  │  ├── vite.config.ts      # Vite + proxy config                      │   │
│  │  └── tailwind.config.js  # Tailwind config                          │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 Why Vite over Create-React-App?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    VITE vs CREATE-REACT-APP                                  │
│                                                                              │
│  Create-React-App (CRA)           Vite                                      │
│  ─────────────────────           ──────                                     │
│  • Webpack-based                 • ESBuild + Rollup                         │
│  • Slow cold start (~30s)        • Fast cold start (~1s)                    │
│  • Slow HMR (~2s)                • Instant HMR (<50ms)                      │
│  • Heavy dependencies            • Lightweight                              │
│  • Deprecated (2023)             • Actively maintained                      │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                    VITE ARCHITECTURE                               │    │
│  │                                                                     │    │
│  │  Development:                                                       │    │
│  │  ─────────────                                                     │    │
│  │  Browser ──► Vite Server ──► ES Modules (native)                   │    │
│  │                    │                                                │    │
│  │                    ▼                                                │    │
│  │             ESBuild (fast)                                          │    │
│  │             • TypeScript → JavaScript                               │    │
│  │             • JSX → JavaScript                                      │    │
│  │                                                                     │    │
│  │  Production:                                                        │    │
│  │  ───────────                                                       │    │
│  │  Source ──► Rollup ──► Optimized bundle                            │    │
│  │                                                                     │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Vite Proxy Configuration

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    API PROXY SETUP                                           │
│                                                                              │
│  The frontend runs on localhost:3000, but the backend API is on 8080.       │
│  Vite's proxy solves CORS issues during development.                        │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │  Request: /api/v1/merchants                                        │    │
│  │                                                                     │    │
│  │  ┌─────────────┐         ┌─────────────┐         ┌─────────────┐  │    │
│  │  │   Browser   │ ──────► │ Vite Dev    │ ──────► │ API Gateway │  │    │
│  │  │ :3000       │         │ Server      │         │ :8080       │  │    │
│  │  └─────────────┘         └─────────────┘         └─────────────┘  │    │
│  │                              (rewrites)                            │    │
│  │                                                                     │    │
│  │  Browser calls:     /api/v1/merchants                              │    │
│  │  Vite rewrites to:  /v1/merchants                                  │    │
│  │  Backend receives:  http://localhost:8080/v1/merchants             │    │
│  │                                                                     │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  Configuration in vite.config.ts:                                           │
│  ─────────────────────────────────                                          │
│  server: {                                                                  │
│    port: 3000,                                                              │
│    proxy: {                                                                 │
│      '/api': {                                                              │
│        target: 'http://localhost:8080',                                     │
│        changeOrigin: true,                                                  │
│        rewrite: (path) => path.replace(/^\/api/, ''),                       │
│        secure: false,                                                       │
│      },                                                                     │
│    },                                                                       │
│  }                                                                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

```powershell
# Check Node.js version
node --version
# Expected: v18.x.x or higher

# Check npm version
npm --version
# Expected: 9.x.x or higher
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Create Vite Project

```powershell
# Navigate to project root
cd KIRO-payflow-payment-gateway

# Create React + TypeScript project with Vite
npm create vite@latest frontend-dashboard -- --template react-ts

# Navigate to project
cd frontend-dashboard
```

### Step 4.2: Install Dependencies

```powershell
# Install all dependencies
npm install

# Install additional dependencies
npm install react-router-dom axios

# Install development dependencies
npm install -D tailwindcss postcss autoprefixer

# Initialize Tailwind CSS
npx tailwindcss init -p
```

**Package Summary:**

| Package | Version | Purpose |
|---------|---------|---------|
| `react` | 18.3.1 | UI library |
| `react-dom` | 18.3.1 | React DOM rendering |
| `react-router-dom` | 6.23.1 | Client-side routing |
| `axios` | 1.7.2 | HTTP client |
| `tailwindcss` | 3.4.4 | Utility-first CSS |
| `typescript` | 5.4.5 | Type checking |
| `vite` | 5.3.1 | Build tool |

### Step 4.3: Configure package.json

**File: `frontend-dashboard/package.json`**

```json
{
  "name": "payflow-dashboard",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "axios": "1.7.2",
    "react": "18.3.1",
    "react-dom": "18.3.1",
    "react-router-dom": "6.23.1"
  },
  "devDependencies": {
    "@types/react": "18.3.3",
    "@types/react-dom": "18.3.0",
    "@vitejs/plugin-react": "4.3.1",
    "autoprefixer": "10.4.19",
    "postcss": "8.4.38",
    "tailwindcss": "3.4.4",
    "typescript": "5.4.5",
    "vite": "5.3.1"
  }
}
```

**Script Explanation:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    NPM SCRIPTS                                               │
│                                                                              │
│  npm run dev                                                                │
│  ─────────────                                                              │
│  • Starts Vite dev server on port 3000                                     │
│  • Hot Module Replacement (HMR) enabled                                    │
│  • Proxy configured for /api requests                                      │
│                                                                              │
│  npm run build                                                              │
│  ───────────────                                                            │
│  • Runs TypeScript compiler (tsc) first                                    │
│  • Then builds production bundle with Vite                                 │
│  • Output in dist/ folder                                                  │
│                                                                              │
│  npm run preview                                                            │
│  ─────────────────                                                          │
│  • Serves the production build locally                                     │
│  • For testing before deployment                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.4: Configure Vite with Proxy

**File: `frontend-dashboard/vite.config.ts`**

```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
        secure: false,
      },
    },
  },
});
```

**Configuration Breakdown:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    VITE CONFIG EXPLAINED                                     │
│                                                                              │
│  plugins: [react()]                                                         │
│  ─────────────────                                                          │
│  • Enables React support (JSX, Fast Refresh)                               │
│                                                                              │
│  server.port: 3000                                                          │
│  ─────────────────                                                          │
│  • Development server runs on http://localhost:3000                        │
│  • Different from Vite default (5173)                                      │
│                                                                              │
│  server.proxy                                                               │
│  ────────────                                                               │
│  • Proxies /api/* requests to backend                                      │
│  • target: API Gateway on port 8080                                        │
│  • changeOrigin: true (important for cookies/CORS)                         │
│  • rewrite: strips /api prefix before forwarding                           │
│  • secure: false (allows localhost without SSL)                            │
│                                                                              │
│  Example:                                                                   │
│  • Frontend calls: /api/v1/auth/login                                      │
│  • Vite rewrites to: /v1/auth/login                                        │
│  • Then proxies to: http://localhost:8080/v1/auth/login                    │
│                                                                              │
│  Why rewrite?                                                               │
│  ────────────                                                               │
│  • API Gateway routes don't include /api prefix                            │
│  • Frontend uses /api prefix for Vite to identify proxy requests           │
│  • rewrite removes it before forwarding to backend                         │
│                                                                              │
│  IMPORTANT: With Vite proxy, CORS configuration on backend is NOT          │
│  required for development - all requests appear same-origin!               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.5: Configure Tailwind CSS

**File: `frontend-dashboard/tailwind.config.js`**

```javascript
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: '#4f46e5',
        'primary-dark': '#4338ca',
      },
    },
  },
  plugins: [],
};
```

**Configuration Explanation:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TAILWIND CONFIG                                           │
│                                                                              │
│  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}']                    │
│  ──────────────────────────────────────────────────────                     │
│  • Tells Tailwind where to scan for class names                            │
│  • index.html - main HTML file                                             │
│  • ./src/**/* - all files in src folder, any depth                         │
│  • {js,ts,jsx,tsx} - JavaScript and TypeScript files                       │
│                                                                              │
│  theme.extend.colors                                                        │
│  ───────────────────                                                        │
│  • primary: #4f46e5 (Indigo)                                               │
│    Usage: bg-primary, text-primary, border-primary                         │
│                                                                              │
│  • primary-dark: #4338ca (Darker indigo)                                   │
│    Usage: hover:bg-primary-dark                                            │
│                                                                              │
│  Why extend instead of replace?                                             │
│  ──────────────────────────────                                             │
│  • extend ADDS to default colors (keeps gray, blue, red, etc.)             │
│  • Replacing would remove all defaults                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.6: Create CSS Entry Point

**File: `frontend-dashboard/src/index.css`**

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

body {
  @apply bg-gray-50 text-gray-900 antialiased;
}
```

**CSS Breakdown:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TAILWIND DIRECTIVES                                       │
│                                                                              │
│  @tailwind base;                                                            │
│  ───────────────                                                            │
│  • Injects Tailwind's base styles                                          │
│  • Normalizes browser defaults (margins, fonts)                            │
│  • Similar to CSS reset                                                    │
│                                                                              │
│  @tailwind components;                                                      │
│  ─────────────────────                                                      │
│  • Injects component classes                                               │
│  • Classes like .container                                                 │
│  • Your @layer components go here                                          │
│                                                                              │
│  @tailwind utilities;                                                       │
│  ────────────────────                                                       │
│  • Injects utility classes                                                 │
│  • All the bg-*, text-*, p-*, m-*, etc.                                    │
│  • Most of Tailwind's classes                                              │
│                                                                              │
│  body styles:                                                               │
│  ─────────────                                                              │
│  • bg-gray-50: Light gray background                                       │
│  • text-gray-900: Almost black text                                        │
│  • antialiased: Smooth font rendering                                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.7: Create Entry Point Files

**File: `frontend-dashboard/index.html`**

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/vite.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>PayFlow Dashboard</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

**File: `frontend-dashboard/src/main.tsx`**

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
```

**Entry Point Breakdown:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    MAIN.TSX EXPLAINED                                        │
│                                                                              │
│  import React from 'react';                                                 │
│  ──────────────────────────                                                 │
│  • Core React library                                                      │
│                                                                              │
│  import ReactDOM from 'react-dom/client';                                   │
│  ─────────────────────────────────────────                                  │
│  • React 18's new root API                                                 │
│  • Enables concurrent features                                             │
│                                                                              │
│  import { BrowserRouter } from 'react-router-dom';                          │
│  ──────────────────────────────────────────────                             │
│  • Router for client-side navigation                                       │
│  • Uses HTML5 history API                                                  │
│                                                                              │
│  ReactDOM.createRoot(document.getElementById('root')!).render(...)          │
│  ─────────────────────────────────────────────────────────────              │
│  • createRoot: New React 18 API                                            │
│  • getElementById('root'): Finds <div id="root">                           │
│  • !: TypeScript non-null assertion (we know it exists)                    │
│  • render: Mounts React app into DOM                                       │
│                                                                              │
│  <React.StrictMode>                                                         │
│  ─────────────────                                                          │
│  • Development-only checks                                                 │
│  • Warns about deprecated APIs                                             │
│  • Double-invokes effects to catch bugs                                    │
│                                                                              │
│  <BrowserRouter>                                                            │
│  ───────────────                                                            │
│  • Provides routing context                                                │
│  • Must wrap any component using useNavigate, Link, etc.                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.8: Create App Component with Routing

**File: `frontend-dashboard/src/App.tsx`**

```tsx
import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import MerchantOnboardingPage from './pages/MerchantOnboardingPage';
import DashboardPage from './pages/DashboardPage';
import TransactionsPage from './pages/TransactionsPage';
import ApiKeysPage from './pages/ApiKeysPage';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/onboarding" element={<MerchantOnboardingPage />} />
      <Route path="/dashboard" element={<DashboardPage />} />
      <Route path="/transactions" element={<TransactionsPage />} />
      <Route path="/api-keys" element={<ApiKeysPage />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}

export default App;
```

**Routing Breakdown:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    REACT ROUTER ROUTES                                       │
│                                                                              │
│  <Routes>                                                                   │
│  ────────                                                                   │
│  • Container for all Route definitions                                     │
│  • Renders first matching route                                            │
│                                                                              │
│  <Route path="/login" element={<LoginPage />} />                            │
│  ─────────────────────────────────────────────                              │
│  • When URL is /login, render LoginPage component                          │
│                                                                              │
│  <Route path="/register" element={<RegisterPage />} />                      │
│  ─────────────────────────────────────────────────                          │
│  • When URL is /register, render RegisterPage                              │
│  • New user account creation                                               │
│                                                                              │
│  <Route path="/onboarding" element={<MerchantOnboardingPage />} />          │
│  ─────────────────────────────────────────────────────────────              │
│  • When URL is /onboarding, render MerchantOnboardingPage                  │
│  • Business setup after registration                                       │
│                                                                              │
│  <Route path="/dashboard" element={<DashboardPage />} />                    │
│  ───────────────────────────────────────────────────                        │
│  • When URL is /dashboard, render DashboardPage                            │
│                                                                              │
│  <Route path="/transactions" element={<TransactionsPage />} />              │
│  ─────────────────────────────────────────────────────────                  │
│  • When URL is /transactions, render TransactionsPage                      │
│                                                                              │
│  <Route path="/api-keys" element={<ApiKeysPage />} />                       │
│  ─────────────────────────────────────────────────                          │
│  • When URL is /api-keys, render ApiKeysPage                               │
│  • API key management                                                      │
│                                                                              │
│  <Route path="*" element={<Navigate to="/login" replace />} />              │
│  ─────────────────────────────────────────────────────────                  │
│  • * matches any path not matched above                                    │
│  • Navigate redirects to /login                                            │
│  • replace: don't add to history (back button skips)                       │
│                                                                              │
│  Route Mapping:                                                             │
│  ──────────────                                                             │
│  /              → Redirects to /login                                      │
│  /login         → LoginPage                                                │
│  /register      → RegisterPage (new user signup)                           │
│  /onboarding    → MerchantOnboardingPage (business setup)                  │
│  /dashboard     → DashboardPage                                            │
│  /transactions  → TransactionsPage                                         │
│  /api-keys      → ApiKeysPage                                              │
│  /anything-else → Redirects to /login                                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.9: Create API Service

**File: `frontend-dashboard/src/services/api.ts`**

```typescript
import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to attach auth token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('payflow_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor to handle 401 (redirect to login)
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('payflow_token');
      localStorage.removeItem('payflow_refresh_token');
      localStorage.removeItem('payflow_user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
```

**API Service Breakdown:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AXIOS API CLIENT                                          │
│                                                                              │
│  axios.create({ baseURL: '/api' })                                          │
│  ─────────────────────────────────                                          │
│  • Creates reusable axios instance                                         │
│  • All requests prepend /api                                               │
│  • api.get('/v1/auth') → GET /api/v1/auth                                  │
│  • Vite proxy then strips /api and sends to localhost:8080                 │
│                                                                              │
│  Request Interceptor:                                                       │
│  ────────────────────                                                       │
│  • Runs BEFORE every request                                               │
│  • Reads token from localStorage                                           │
│  • Adds Authorization: Bearer <token> header                               │
│  • Every API call is automatically authenticated                           │
│                                                                              │
│  Response Interceptor:                                                      │
│  ─────────────────────                                                      │
│  • Runs AFTER every response                                               │
│  • On 401 Unauthorized:                                                    │
│    1. Removes all auth data from localStorage                              │
│       - payflow_token (access token)                                       │
│       - payflow_refresh_token (refresh token)                              │
│       - payflow_user (user info)                                           │
│    2. Redirects to /login                                                  │
│  • Handles auth expiration globally                                        │
│                                                                              │
│  Token Storage:                                                             │
│  ──────────────                                                             │
│  • 'payflow_token' - Access JWT token                                      │
│  • 'payflow_refresh_token' - Refresh JWT token                             │
│  • 'payflow_user' - User info JSON                                         │
│  • Stored in localStorage (persists across browser sessions)               │
│  • Set after successful login                                              │
│  • Removed on logout or 401                                                │
│                                                                              │
│  Flow Diagram:                                                              │
│  ─────────────                                                              │
│                                                                              │
│  api.get('/v1/merchants')                                                   │
│       │                                                                     │
│       ▼                                                                     │
│  ┌─────────────────────┐                                                   │
│  │ Request Interceptor │ ─► Add Authorization header                       │
│  └─────────────────────┘                                                   │
│       │                                                                     │
│       ▼                                                                     │
│  ┌─────────────────────┐                                                   │
│  │ Vite Proxy          │ ─► Forward to localhost:8080                      │
│  └─────────────────────┘                                                   │
│       │                                                                     │
│       ▼                                                                     │
│  ┌─────────────────────┐                                                   │
│  │ Backend Response    │                                                   │
│  └─────────────────────┘                                                   │
│       │                                                                     │
│       ▼                                                                     │
│  ┌─────────────────────┐                                                   │
│  │Response Interceptor │ ─► Check for 401, redirect if needed             │
│  └─────────────────────┘                                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Verification

### Start the Development Server

```powershell
cd frontend-dashboard
npm run dev
```

**Expected Output:**

```
  VITE v5.3.1  ready in xxx ms

  ➜  Local:   http://localhost:3000/
  ➜  Network: use --host to expose
  ➜  press h + enter to show help
```

### Verify in Browser

1. Open `http://localhost:3000`
2. You should be redirected to `/login`
3. Check browser console (F12) for any errors

---

## 6. File Structure

After completing this part, your structure should be:

```
frontend-dashboard/
├── node_modules/
├── src/
│   ├── pages/
│   │   ├── LoginPage.tsx          ← Created in Part 15
│   │   ├── RegisterPage.tsx       ← Created in Part 15
│   │   ├── MerchantOnboardingPage.tsx ← Created in Part 15
│   │   ├── DashboardPage.tsx      ← Created in Part 16
│   │   ├── TransactionsPage.tsx
│   │   └── ApiKeysPage.tsx
│   ├── services/
│   │   └── api.ts                 ← API client with interceptors
│   ├── App.tsx                    ← Routing configuration
│   ├── main.tsx                   ← Entry point
│   └── index.css                  ← Tailwind styles
├── index.html
├── package.json
├── postcss.config.js
├── tailwind.config.js
├── tsconfig.json
├── tsconfig.node.json
└── vite.config.ts
```

---

## 7. Key Takeaways

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         KEY LEARNINGS                                        │
│                                                                              │
│  1. Vite Configuration                                                      │
│  ─────────────────────                                                      │
│  • Port 3000 for development                                               │
│  • Proxy /api to backend on 8080 with path rewrite                         │
│  • rewrite: strips /api prefix before forwarding                           │
│  • secure: false for localhost development                                 │
│  • Fast HMR and ES module support                                          │
│  • With Vite proxy, CORS is NOT needed for development                     │
│                                                                              │
│  2. Tailwind Setup                                                          │
│  ────────────────                                                           │
│  • content array tells where to scan                                       │
│  • extend.colors adds custom colors                                        │
│  • Three directives in CSS: base, components, utilities                    │
│                                                                              │
│  3. React Router 6                                                          │
│  ─────────────────                                                          │
│  • BrowserRouter wraps app in main.tsx                                     │
│  • Routes + Route define paths                                             │
│  • Navigate for redirects                                                  │
│  • path="*" catches undefined routes                                       │
│  • User flow: /register → /onboarding → /dashboard                         │
│                                                                              │
│  4. Axios Interceptors                                                      │
│  ─────────────────────                                                      │
│  • Request: adds auth token automatically                                  │
│  • Response: handles 401 globally, clears all auth data                    │
│  • Token stored in localStorage:                                           │
│    - 'payflow_token' (access JWT)                                          │
│    - 'payflow_refresh_token' (refresh JWT)                                 │
│    - 'payflow_user' (user info JSON)                                       │
│                                                                              │
│  5. Token Storage in Browser                                                │
│  ───────────────────────────                                                │
│  • View in DevTools: F12 → Application → Local Storage → localhost:3000    │
│  • Keys: payflow_token, payflow_refresh_token, payflow_user                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Common Issues and Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| Port 3000 already in use | Another app using port | Kill process or change port in vite.config.ts |
| Tailwind classes not working | CSS not imported | Ensure `import './index.css'` in main.tsx |
| TypeScript errors | Type mismatch | Check tsconfig.json settings |
| API calls fail | Proxy not working | Verify vite.config.ts proxy settings |
| "Module not found" | Missing install | Run `npm install` |

### Debug Checklist

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TROUBLESHOOTING CHECKLIST                                 │
│                                                                              │
│  □ Node.js version >= 18?                                                   │
│    node --version                                                           │
│                                                                              │
│  □ Dependencies installed?                                                  │
│    npm install                                                              │
│                                                                              │
│  □ Dev server running?                                                      │
│    npm run dev                                                              │
│                                                                              │
│  □ Port 3000 accessible?                                                    │
│    Open http://localhost:3000 in browser                                   │
│                                                                              │
│  □ Browser console has errors?                                              │
│    Press F12 → Console tab                                                 │
│                                                                              │
│  □ Backend running for API calls?                                           │
│    curl http://localhost:8080/actuator/health                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Related Concepts

### Vite vs Other Build Tools

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    BUILD TOOL COMPARISON                                     │
│                                                                              │
│  Vite (what we use):                                                        │
│  ───────────────────                                                        │
│  • Uses native ES modules in dev                                           │
│  • ESBuild for fast transforms                                             │
│  • Rollup for production builds                                            │
│  • Framework-agnostic (React, Vue, Svelte)                                 │
│                                                                              │
│  Webpack (CRA, Next.js):                                                    │
│  ───────────────────────                                                    │
│  • Bundles everything in dev too                                           │
│  • Slower but more mature                                                  │
│  • Massive plugin ecosystem                                                │
│                                                                              │
│  Parcel:                                                                    │
│  ───────                                                                    │
│  • Zero config                                                             │
│  • Good for small projects                                                 │
│  • Less control                                                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

In the next part, we'll create the **Login Page**:

1. Create LoginPage component with form
2. Implement authentication API call
3. Store token and redirect on success
4. Handle error states

**Navigation:**
- [Previous: Part 13 - Merchant Swagger Testing](./part-13-merchant-swagger-testing.md)
- [Next: Part 15 - Frontend Login Page](./part-15-frontend-login-page.md)

---

## Quick Reference

### Commands

| Command | Purpose |
|---------|---------|
| `npm run dev` | Start development server |
| `npm run build` | Build for production |
| `npm run preview` | Preview production build |

### Configuration Files

| File | Purpose |
|------|---------|
| `vite.config.ts` | Vite settings + proxy |
| `tailwind.config.js` | Tailwind CSS settings |
| `tsconfig.json` | TypeScript settings |
| `package.json` | Dependencies + scripts |
