# Sprint 1, Part 05: React Frontend

**Duration:** 4-5 hours  
**Prerequisites:** Parts 01-04 completed, Node.js installed

---

## 1. What We're Building

A React + TypeScript merchant portal with:

| Page | Purpose |
|------|---------|
| Login | User authentication |
| Register | New user signup |
| Dashboard | Main merchant dashboard |
| Merchant Setup | Business onboarding form |

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     MERCHANT PORTAL OVERVIEW                                 │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        merchant-portal/                              │   │
│  │                      (React + TypeScript)                            │   │
│  │                                                                      │   │
│  │  Public Routes:              Protected Routes:                       │   │
│  │  ┌─────────────────┐        ┌─────────────────────────────────┐     │   │
│  │  │ /login          │        │ /dashboard                      │     │   │
│  │  │ /register       │        │ /merchant/setup                 │     │   │
│  │  └─────────────────┘        │ /settings (future)              │     │   │
│  │                             └─────────────────────────────────┘     │   │
│  │                                                                      │   │
│  │  Auth Flow:                                                          │   │
│  │  Login/Register → Store JWT → Redirect to Dashboard                 │   │
│  │  Protected routes check JWT → No token? Redirect to Login           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                              │
│                              │ API Calls                                    │
│                              ▼                                              │
│                    ┌──────────────────┐                                    │
│                    │   API Gateway    │                                    │
│                    │    :8080         │                                    │
│                    └──────────────────┘                                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 React Project Structure

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    RECOMMENDED REACT STRUCTURE                               │
│                                                                              │
│  merchant-portal/                                                            │
│  ├── public/                    ← Static assets (favicon, etc.)             │
│  ├── src/                                                                    │
│  │   ├── main.tsx               ← Entry point                               │
│  │   ├── App.tsx                ← Router setup                              │
│  │   │                                                                       │
│  │   ├── pages/                 ← Page components (one per route)           │
│  │   │   ├── Login.tsx                                                      │
│  │   │   ├── Register.tsx                                                   │
│  │   │   ├── Dashboard.tsx                                                  │
│  │   │   └── MerchantSetup.tsx                                              │
│  │   │                                                                       │
│  │   ├── components/            ← Reusable UI components                    │
│  │   │   ├── Layout.tsx         ← Dashboard layout with sidebar             │
│  │   │   ├── Sidebar.tsx        ← Navigation sidebar                        │
│  │   │   ├── Input.tsx          ← Styled input component                    │
│  │   │   ├── Button.tsx         ← Styled button component                   │
│  │   │   └── ProtectedRoute.tsx ← Auth guard component                      │
│  │   │                                                                       │
│  │   ├── api/                   ← API client functions                      │
│  │   │   ├── client.ts          ← Axios instance with interceptors          │
│  │   │   ├── auth.ts            ← Auth API (login, register)                │
│  │   │   └── merchant.ts        ← Merchant API                              │
│  │   │                                                                       │
│  │   ├── store/                 ← State management (Zustand)                │
│  │   │   └── authStore.ts       ← Auth state (user, token)                  │
│  │   │                                                                       │
│  │   ├── hooks/                 ← Custom React hooks                        │
│  │   │   └── useAuth.ts         ← Auth helper hook                          │
│  │   │                                                                       │
│  │   ├── types/                 ← TypeScript type definitions               │
│  │   │   └── index.ts                                                       │
│  │   │                                                                       │
│  │   └── styles/                ← Global styles                             │
│  │       └── index.css          ← Tailwind imports                          │
│  │                                                                           │
│  ├── package.json                                                            │
│  ├── tsconfig.json              ← TypeScript config                         │
│  ├── tailwind.config.js         ← Tailwind CSS config                       │
│  └── vite.config.ts             ← Vite bundler config                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Authentication Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    FRONTEND AUTH FLOW                                        │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                        LOGIN FLOW                                      │ │
│  │                                                                        │ │
│  │  1. User enters credentials                                           │ │
│  │     ┌─────────────────────┐                                           │ │
│  │     │ Email: [_________]  │                                           │ │
│  │     │ Password: [______]  │                                           │ │
│  │     │ [Login Button]      │                                           │ │
│  │     └─────────────────────┘                                           │ │
│  │              │                                                         │ │
│  │              ▼                                                         │ │
│  │  2. Call API: POST /v1/auth/login                                     │ │
│  │              │                                                         │ │
│  │              ▼                                                         │ │
│  │  3. Receive: { accessToken, user }                                    │ │
│  │              │                                                         │ │
│  │              ▼                                                         │ │
│  │  4. Store token: localStorage.setItem('token', accessToken)           │ │
│  │              │                                                         │ │
│  │              ▼                                                         │ │
│  │  5. Update store: authStore.setUser(user)                             │ │
│  │              │                                                         │ │
│  │              ▼                                                         │ │
│  │  6. Navigate to /dashboard                                             │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                    PROTECTED ROUTE CHECK                               │ │
│  │                                                                        │ │
│  │  User visits /dashboard                                                │ │
│  │         │                                                              │ │
│  │         ▼                                                              │ │
│  │  ProtectedRoute component:                                             │ │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │ │
│  │  │ const token = localStorage.getItem('token');                    │  │ │
│  │  │ if (!token) {                                                   │  │ │
│  │  │   return <Navigate to="/login" />;  // Redirect to login        │  │ │
│  │  │ }                                                               │  │ │
│  │  │ return <Outlet />;  // Render the protected page                │  │ │
│  │  └─────────────────────────────────────────────────────────────────┘  │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Step-by-Step Implementation

### Step 3.1: Create React Project

```powershell
# Navigate to project root
cd C:\payflow-payment-gateway

# Create React project with Vite
npm create vite@latest merchant-portal -- --template react-ts

# Navigate to project
cd merchant-portal

# Install dependencies
npm install
```

### Step 3.2: Install Additional Dependencies

```powershell
# Routing
npm install react-router-dom

# HTTP client
npm install axios

# State management
npm install zustand

# Form handling
npm install react-hook-form

# Tailwind CSS
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p

# Icons
npm install lucide-react
```

### Step 3.3: Configure Tailwind CSS

Update `tailwind.config.js`:

```javascript
/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#f0f9ff',
          100: '#e0f2fe',
          500: '#0ea5e9',
          600: '#0284c7',
          700: '#0369a1',
        },
      },
    },
  },
  plugins: [],
}
```

Update `src/index.css`:

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

/* Custom styles */
body {
  @apply bg-gray-50 text-gray-900;
}
```

### Step 3.4: Create Types

Create `src/types/index.ts`:

```typescript
// ═══════════════════════════════════════════════════════════════════════════
// TypeScript Types
// ═══════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────
// API Response Types
// ─────────────────────────────────────────────────────────────────────────────

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// User Types
// ─────────────────────────────────────────────────────────────────────────────

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: 'MERCHANT' | 'ADMIN';
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED';
  createdAt: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Auth Types
// ─────────────────────────────────────────────────────────────────────────────

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

// ─────────────────────────────────────────────────────────────────────────────
// Merchant Types
// ─────────────────────────────────────────────────────────────────────────────

export interface Merchant {
  id: string;
  userId: string;
  businessName: string;
  businessType: 'INDIVIDUAL' | 'COMPANY' | 'PARTNERSHIP' | 'NON_PROFIT';
  country: string;
  status: 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'REJECTED';
  webhookUrl?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateMerchantRequest {
  businessName: string;
  businessType: string;
  country: string;
}
```


### Step 3.5: Create API Client

Create `src/api/client.ts`:

```typescript
import axios from 'axios';

/**
 * Axios instance with base configuration
 * 
 * Features:
 * - Base URL pointing to API Gateway
 * - Automatic JWT token injection
 * - Response error handling
 */
const apiClient = axios.create({
  baseURL: 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json',
  },
});

// ═══════════════════════════════════════════════════════════════════════════
// Request Interceptor: Add JWT token to every request
// ═══════════════════════════════════════════════════════════════════════════
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// ═══════════════════════════════════════════════════════════════════════════
// Response Interceptor: Handle errors globally
// ═══════════════════════════════════════════════════════════════════════════
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Handle 401 Unauthorized - redirect to login
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default apiClient;
```

Create `src/api/auth.ts`:

```typescript
import apiClient from './client';
import { ApiResponse, AuthResponse, LoginRequest, RegisterRequest, User } from '../types';

/**
 * Auth API functions
 */
export const authApi = {
  /**
   * Register new user
   */
  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<ApiResponse<AuthResponse>>(
      '/v1/auth/register',
      data
    );
    return response.data.data!;
  },

  /**
   * Login user
   */
  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<ApiResponse<AuthResponse>>(
      '/v1/auth/login',
      data
    );
    return response.data.data!;
  },

  /**
   * Get current user
   */
  getMe: async (): Promise<User> => {
    const response = await apiClient.get<ApiResponse<User>>('/v1/auth/me');
    return response.data.data!;
  },
};
```

Create `src/api/merchant.ts`:

```typescript
import apiClient from './client';
import { ApiResponse, Merchant, CreateMerchantRequest } from '../types';

/**
 * Merchant API functions
 */
export const merchantApi = {
  /**
   * Create merchant
   */
  create: async (data: CreateMerchantRequest): Promise<Merchant> => {
    const response = await apiClient.post<ApiResponse<Merchant>>(
      '/v1/merchants',
      data
    );
    return response.data.data!;
  },

  /**
   * Get my merchant
   */
  getMyMerchant: async (): Promise<Merchant> => {
    const response = await apiClient.get<ApiResponse<Merchant>>(
      '/v1/merchants/me'
    );
    return response.data.data!;
  },

  /**
   * Get merchant by ID
   */
  getById: async (id: string): Promise<Merchant> => {
    const response = await apiClient.get<ApiResponse<Merchant>>(
      `/v1/merchants/${id}`
    );
    return response.data.data!;
  },
};
```

### Step 3.6: Create Auth Store

Create `src/store/authStore.ts`:

```typescript
import { create } from 'zustand';
import { User } from '../types';

/**
 * Auth State Store
 * 
 * Uses Zustand for simple, lightweight state management.
 * Stores user info and provides auth actions.
 */
interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  
  // Actions
  setAuth: (user: User, token: string) => void;
  logout: () => void;
  loadFromStorage: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  isAuthenticated: false,

  /**
   * Set auth after login/register
   */
  setAuth: (user: User, token: string) => {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(user));
    set({ user, token, isAuthenticated: true });
  },

  /**
   * Clear auth on logout
   */
  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    set({ user: null, token: null, isAuthenticated: false });
  },

  /**
   * Load auth from localStorage on app start
   */
  loadFromStorage: () => {
    const token = localStorage.getItem('token');
    const userStr = localStorage.getItem('user');
    
    if (token && userStr) {
      try {
        const user = JSON.parse(userStr);
        set({ user, token, isAuthenticated: true });
      } catch {
        // Invalid stored data, clear it
        localStorage.removeItem('token');
        localStorage.removeItem('user');
      }
    }
  },
}));
```

### Step 3.7: Create Components

Create `src/components/ProtectedRoute.tsx`:

```tsx
import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';

/**
 * Protected Route Component
 * 
 * Wraps routes that require authentication.
 * Redirects to login if no token found.
 */
export function ProtectedRoute() {
  const { isAuthenticated } = useAuthStore();
  const token = localStorage.getItem('token');

  // Check both store and localStorage
  if (!isAuthenticated && !token) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}
```

Create `src/components/Layout.tsx`:

```tsx
import { Outlet, Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { 
  Home, 
  Store, 
  Settings, 
  LogOut,
  CreditCard 
} from 'lucide-react';

/**
 * Dashboard Layout
 * 
 * Provides sidebar navigation and header for authenticated pages.
 */
export function Layout() {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="flex h-screen bg-gray-100">
      {/* Sidebar */}
      <aside className="w-64 bg-white shadow-lg">
        <div className="p-6">
          <h1 className="text-2xl font-bold text-primary-600">PayFlow</h1>
        </div>
        
        <nav className="mt-6">
          <Link
            to="/dashboard"
            className="flex items-center px-6 py-3 text-gray-700 hover:bg-gray-100"
          >
            <Home className="w-5 h-5 mr-3" />
            Dashboard
          </Link>
          
          <Link
            to="/merchant/setup"
            className="flex items-center px-6 py-3 text-gray-700 hover:bg-gray-100"
          >
            <Store className="w-5 h-5 mr-3" />
            Merchant Setup
          </Link>
          
          <Link
            to="/payments"
            className="flex items-center px-6 py-3 text-gray-700 hover:bg-gray-100"
          >
            <CreditCard className="w-5 h-5 mr-3" />
            Payments
          </Link>
          
          <Link
            to="/settings"
            className="flex items-center px-6 py-3 text-gray-700 hover:bg-gray-100"
          >
            <Settings className="w-5 h-5 mr-3" />
            Settings
          </Link>
        </nav>

        {/* User section */}
        <div className="absolute bottom-0 w-64 p-4 border-t">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium">{user?.fullName}</p>
              <p className="text-xs text-gray-500">{user?.email}</p>
            </div>
            <button
              onClick={handleLogout}
              className="p-2 text-gray-500 hover:text-red-500"
            >
              <LogOut className="w-5 h-5" />
            </button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-y-auto">
        <div className="p-8">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
```

### Step 3.8: Create Pages

Create `src/pages/Login.tsx`:

```tsx
import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { authApi } from '../api/auth';
import { useAuthStore } from '../store/authStore';
import { LoginRequest } from '../types';

/**
 * Login Page
 */
export function Login() {
  const navigate = useNavigate();
  const { setAuth } = useAuthStore();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const { register, handleSubmit, formState: { errors } } = useForm<LoginRequest>();

  const onSubmit = async (data: LoginRequest) => {
    setLoading(true);
    setError('');

    try {
      const response = await authApi.login(data);
      setAuth(response.user, response.accessToken);
      navigate('/dashboard');
    } catch (err: any) {
      setError(err.response?.data?.error?.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md w-full space-y-8 p-8 bg-white rounded-xl shadow-lg">
        {/* Header */}
        <div className="text-center">
          <h1 className="text-3xl font-bold text-primary-600">PayFlow</h1>
          <h2 className="mt-4 text-xl text-gray-900">Sign in to your account</h2>
        </div>

        {/* Error message */}
        {error && (
          <div className="p-4 bg-red-50 border border-red-200 rounded-lg">
            <p className="text-red-600 text-sm">{error}</p>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
          <div>
            <label className="block text-sm font-medium text-gray-700">
              Email
            </label>
            <input
              type="email"
              {...register('email', { required: 'Email is required' })}
              className="mt-1 block w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-primary-500 focus:border-primary-500"
              placeholder="you@example.com"
            />
            {errors.email && (
              <p className="mt-1 text-sm text-red-500">{errors.email.message}</p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700">
              Password
            </label>
            <input
              type="password"
              {...register('password', { required: 'Password is required' })}
              className="mt-1 block w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-primary-500 focus:border-primary-500"
              placeholder="••••••••"
            />
            {errors.password && (
              <p className="mt-1 text-sm text-red-500">{errors.password.message}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3 px-4 bg-primary-600 text-white font-medium rounded-lg hover:bg-primary-700 disabled:opacity-50"
          >
            {loading ? 'Signing in...' : 'Sign in'}
          </button>
        </form>

        {/* Register link */}
        <p className="text-center text-sm text-gray-600">
          Don't have an account?{' '}
          <Link to="/register" className="text-primary-600 hover:underline">
            Register here
          </Link>
        </p>
      </div>
    </div>
  );
}
```

Create `src/pages/Register.tsx`:

```tsx
import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { authApi } from '../api/auth';
import { useAuthStore } from '../store/authStore';
import { RegisterRequest } from '../types';

/**
 * Register Page
 */
export function Register() {
  const navigate = useNavigate();
  const { setAuth } = useAuthStore();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const { register, handleSubmit, formState: { errors } } = useForm<RegisterRequest>();

  const onSubmit = async (data: RegisterRequest) => {
    setLoading(true);
    setError('');

    try {
      const response = await authApi.register(data);
      setAuth(response.user, response.accessToken);
      navigate('/dashboard');
    } catch (err: any) {
      setError(err.response?.data?.error?.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md w-full space-y-8 p-8 bg-white rounded-xl shadow-lg">
        <div className="text-center">
          <h1 className="text-3xl font-bold text-primary-600">PayFlow</h1>
          <h2 className="mt-4 text-xl text-gray-900">Create your account</h2>
        </div>

        {error && (
          <div className="p-4 bg-red-50 border border-red-200 rounded-lg">
            <p className="text-red-600 text-sm">{error}</p>
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
          <div>
            <label className="block text-sm font-medium text-gray-700">
              Full Name
            </label>
            <input
              type="text"
              {...register('fullName', { 
                required: 'Name is required',
                minLength: { value: 2, message: 'Name too short' }
              })}
              className="mt-1 block w-full px-4 py-3 border border-gray-300 rounded-lg"
              placeholder="John Doe"
            />
            {errors.fullName && (
              <p className="mt-1 text-sm text-red-500">{errors.fullName.message}</p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700">
              Email
            </label>
            <input
              type="email"
              {...register('email', { required: 'Email is required' })}
              className="mt-1 block w-full px-4 py-3 border border-gray-300 rounded-lg"
              placeholder="you@example.com"
            />
            {errors.email && (
              <p className="mt-1 text-sm text-red-500">{errors.email.message}</p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700">
              Password
            </label>
            <input
              type="password"
              {...register('password', { 
                required: 'Password is required',
                minLength: { value: 8, message: 'Password must be 8+ characters' },
                pattern: {
                  value: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/,
                  message: 'Must include uppercase, lowercase, and number'
                }
              })}
              className="mt-1 block w-full px-4 py-3 border border-gray-300 rounded-lg"
              placeholder="••••••••"
            />
            {errors.password && (
              <p className="mt-1 text-sm text-red-500">{errors.password.message}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3 px-4 bg-primary-600 text-white font-medium rounded-lg hover:bg-primary-700 disabled:opacity-50"
          >
            {loading ? 'Creating account...' : 'Create account'}
          </button>
        </form>

        <p className="text-center text-sm text-gray-600">
          Already have an account?{' '}
          <Link to="/login" className="text-primary-600 hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
```


Create `src/pages/Dashboard.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { merchantApi } from '../api/merchant';
import { Merchant } from '../types';
import { Store, ArrowRight } from 'lucide-react';

/**
 * Dashboard Page
 */
export function Dashboard() {
  const { user } = useAuthStore();
  const [merchant, setMerchant] = useState<Merchant | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadMerchant();
  }, []);

  const loadMerchant = async () => {
    try {
      const data = await merchantApi.getMyMerchant();
      setMerchant(data);
    } catch {
      // User doesn't have a merchant yet
      setMerchant(null);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
      </div>
    );
  }

  return (
    <div>
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">
          Welcome back, {user?.fullName}!
        </h1>
        <p className="text-gray-600">Here's what's happening with your account.</p>
      </div>

      {/* Merchant setup card */}
      {!merchant ? (
        <div className="bg-white rounded-xl shadow-md p-6 mb-6">
          <div className="flex items-center">
            <div className="p-3 bg-primary-100 rounded-lg">
              <Store className="w-6 h-6 text-primary-600" />
            </div>
            <div className="ml-4 flex-1">
              <h2 className="text-lg font-semibold">Complete your merchant setup</h2>
              <p className="text-gray-600">Set up your business to start accepting payments.</p>
            </div>
            <Link
              to="/merchant/setup"
              className="flex items-center px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700"
            >
              Get started
              <ArrowRight className="w-4 h-4 ml-2" />
            </Link>
          </div>
        </div>
      ) : (
        <div className="bg-white rounded-xl shadow-md p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4">Merchant Details</h2>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="text-sm text-gray-500">Business Name</p>
              <p className="font-medium">{merchant.businessName}</p>
            </div>
            <div>
              <p className="text-sm text-gray-500">Merchant ID</p>
              <p className="font-mono text-sm">{merchant.id}</p>
            </div>
            <div>
              <p className="text-sm text-gray-500">Status</p>
              <span className={`inline-flex px-2 py-1 text-xs rounded-full ${
                merchant.status === 'ACTIVE' 
                  ? 'bg-green-100 text-green-800' 
                  : 'bg-yellow-100 text-yellow-800'
              }`}>
                {merchant.status}
              </span>
            </div>
            <div>
              <p className="text-sm text-gray-500">Country</p>
              <p className="font-medium">{merchant.country}</p>
            </div>
          </div>
        </div>
      )}

      {/* Stats cards (placeholder) */}
      <div className="grid grid-cols-3 gap-6">
        <div className="bg-white rounded-xl shadow-md p-6">
          <p className="text-sm text-gray-500">Total Revenue</p>
          <p className="text-2xl font-bold">₹0.00</p>
          <p className="text-xs text-gray-400 mt-1">Coming in Sprint 4</p>
        </div>
        <div className="bg-white rounded-xl shadow-md p-6">
          <p className="text-sm text-gray-500">Transactions</p>
          <p className="text-2xl font-bold">0</p>
          <p className="text-xs text-gray-400 mt-1">Coming in Sprint 4</p>
        </div>
        <div className="bg-white rounded-xl shadow-md p-6">
          <p className="text-sm text-gray-500">Success Rate</p>
          <p className="text-2xl font-bold">--</p>
          <p className="text-xs text-gray-400 mt-1">Coming in Sprint 4</p>
        </div>
      </div>
    </div>
  );
}
```

Create `src/pages/MerchantSetup.tsx`:

```tsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { merchantApi } from '../api/merchant';
import { CreateMerchantRequest } from '../types';

/**
 * Merchant Setup Page
 */
export function MerchantSetup() {
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const { register, handleSubmit, formState: { errors } } = useForm<CreateMerchantRequest>();

  const onSubmit = async (data: CreateMerchantRequest) => {
    setLoading(true);
    setError('');

    try {
      await merchantApi.create(data);
      navigate('/dashboard');
    } catch (err: any) {
      setError(err.response?.data?.error?.message || 'Failed to create merchant');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-gray-900 mb-2">Merchant Setup</h1>
      <p className="text-gray-600 mb-8">Tell us about your business.</p>

      {error && (
        <div className="p-4 bg-red-50 border border-red-200 rounded-lg mb-6">
          <p className="text-red-600 text-sm">{error}</p>
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        <div className="bg-white rounded-xl shadow-md p-6 space-y-6">
          <div>
            <label className="block text-sm font-medium text-gray-700">
              Business Name
            </label>
            <input
              type="text"
              {...register('businessName', { required: 'Business name is required' })}
              className="mt-1 block w-full px-4 py-3 border border-gray-300 rounded-lg"
              placeholder="Acme Store"
            />
            {errors.businessName && (
              <p className="mt-1 text-sm text-red-500">{errors.businessName.message}</p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700">
              Business Type
            </label>
            <select
              {...register('businessType', { required: 'Business type is required' })}
              className="mt-1 block w-full px-4 py-3 border border-gray-300 rounded-lg"
            >
              <option value="">Select type...</option>
              <option value="INDIVIDUAL">Individual / Sole Proprietor</option>
              <option value="COMPANY">Company</option>
              <option value="PARTNERSHIP">Partnership</option>
              <option value="NON_PROFIT">Non-Profit Organization</option>
            </select>
            {errors.businessType && (
              <p className="mt-1 text-sm text-red-500">{errors.businessType.message}</p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700">
              Country
            </label>
            <select
              {...register('country', { required: 'Country is required' })}
              className="mt-1 block w-full px-4 py-3 border border-gray-300 rounded-lg"
            >
              <option value="">Select country...</option>
              <option value="IN">India</option>
              <option value="US">United States</option>
              <option value="GB">United Kingdom</option>
              <option value="DE">Germany</option>
              <option value="SG">Singapore</option>
            </select>
            {errors.country && (
              <p className="mt-1 text-sm text-red-500">{errors.country.message}</p>
            )}
          </div>
        </div>

        <div className="flex justify-end">
          <button
            type="submit"
            disabled={loading}
            className="px-6 py-3 bg-primary-600 text-white font-medium rounded-lg hover:bg-primary-700 disabled:opacity-50"
          >
            {loading ? 'Creating...' : 'Create Merchant Account'}
          </button>
        </div>
      </form>
    </div>
  );
}
```

### Step 3.9: Create App Router

Update `src/App.tsx`:

```tsx
import { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';

// Pages
import { Login } from './pages/Login';
import { Register } from './pages/Register';
import { Dashboard } from './pages/Dashboard';
import { MerchantSetup } from './pages/MerchantSetup';

// Components
import { ProtectedRoute } from './components/ProtectedRoute';
import { Layout } from './components/Layout';

function App() {
  const { loadFromStorage } = useAuthStore();

  // Load auth from localStorage on app start
  useEffect(() => {
    loadFromStorage();
  }, [loadFromStorage]);

  return (
    <BrowserRouter>
      <Routes>
        {/* Public routes */}
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        {/* Protected routes */}
        <Route element={<ProtectedRoute />}>
          <Route element={<Layout />}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/merchant/setup" element={<MerchantSetup />} />
          </Route>
        </Route>

        {/* Default redirect */}
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
```

### Step 3.10: Update main.tsx

Update `src/main.tsx`:

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

---

## 4. Verification

### 4.1 Run the Frontend

```powershell
cd merchant-portal
npm run dev
```

**Expected output:**
```
  VITE v5.x.x  ready in xxx ms

  ➜  Local:   http://localhost:5173/
  ➜  Network: http://192.168.x.x:5173/
```

### 4.2 Test the Application

1. **Open browser:** http://localhost:5173
2. **Register:** Click "Register here", fill form, submit
3. **Dashboard:** Should redirect to dashboard
4. **Merchant Setup:** Click "Get started", fill form, submit
5. **Logout:** Click logout icon in sidebar

### 4.3 Verification Checklist

| Check | Test | Expected |
|-------|------|----------|
| Login page | Visit /login | Form displays |
| Register page | Visit /register | Form displays |
| Registration | Submit register form | Redirect to dashboard |
| Login | Submit login form | Redirect to dashboard |
| Protected route | Visit /dashboard without token | Redirect to login |
| Merchant setup | Complete setup form | Merchant created, redirect |
| Logout | Click logout | Redirect to login |

---

## 5. File Structure

```
merchant-portal/
├── package.json
├── vite.config.ts
├── tailwind.config.js
├── tsconfig.json
├── index.html
└── src/
    ├── main.tsx
    ├── App.tsx
    ├── index.css
    ├── api/
    │   ├── client.ts
    │   ├── auth.ts
    │   └── merchant.ts
    ├── components/
    │   ├── Layout.tsx
    │   └── ProtectedRoute.tsx
    ├── pages/
    │   ├── Login.tsx
    │   ├── Register.tsx
    │   ├── Dashboard.tsx
    │   └── MerchantSetup.tsx
    ├── store/
    │   └── authStore.ts
    └── types/
        └── index.ts
```

---

## 6. Key Takeaways

| Concept | What We Learned |
|---------|-----------------|
| React Router | Route protection with `<Outlet />` |
| Zustand | Lightweight state management |
| Axios Interceptors | Auto-add JWT to requests |
| TypeScript | Type safety for API data |
| Tailwind CSS | Utility-first styling |

---

## 7. Next Steps

**React Frontend complete!** You now have:
- ✅ Login and Register pages
- ✅ Protected route handling
- ✅ Dashboard with merchant info
- ✅ Merchant setup form

**Continue to:** [Part 06: Docker & CI/CD](./part-06-docker-cicd.md)

In Part 06, you'll:
- Create Dockerfiles for all services
- Set up docker-compose.yml
- Create GitHub Actions CI pipeline

---

**End of Sprint 1, Part 05**

*Next: Docker & CI/CD for Containerization*
