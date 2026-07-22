# Phase 11 Part 2 — Authentication Pages (Login & Register)

## Goal
- Build a React login page with email/password form handling
- Build a register page with validation feedback
- Connect forms to the identity-service API via Axios

## Key Concept

```
┌─────────────────────────────────────────────┐
│           React Auth Flow                    │
│                                             │
│  LoginPage ──► authService.login() ──► API  │
│       │                                │    │
│       ▼                                ▼    │
│  Store JWT in localStorage      Return JWT  │
│       │                                     │
│       ▼                                     │
│  Redirect to /dashboard                     │
└─────────────────────────────────────────────┘
```

## Prerequisites
- Phase 11 Part 1 completed (React project scaffolded with Vite + React Router)
- Identity service running on port 8081

## Step-by-Step

### 1. Create Auth Service (`src/services/authService.ts`)

```typescript
import axios from 'axios';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  merchantName: string;
}

export const authService = {
  async login(data: LoginRequest) {
    const response = await axios.post(`${API_URL}/api/v1/auth/login`, data);
    localStorage.setItem('token', response.data.data.accessToken);
    return response.data;
  },

  async register(data: RegisterRequest) {
    const response = await axios.post(`${API_URL}/api/v1/auth/register`, data);
    return response.data;
  },

  logout() {
    localStorage.removeItem('token');
    window.location.href = '/login';
  },

  getToken() {
    return localStorage.getItem('token');
  }
};
```

### 2. Create Login Page (`src/pages/LoginPage.tsx`)

```tsx
import { useState, FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authService } from '../services/authService';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await authService.login({ email, password });
      navigate('/dashboard');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <form onSubmit={handleSubmit} className="bg-white p-8 rounded-lg shadow-md w-96">
        <h1 className="text-2xl font-bold mb-6">Sign In to PayFlow</h1>
        {error && <div className="bg-red-100 text-red-700 p-3 rounded mb-4">{error}</div>}
        <input type="email" placeholder="Email" value={email}
          onChange={e => setEmail(e.target.value)} required
          className="w-full p-3 border rounded mb-4" />
        <input type="password" placeholder="Password" value={password}
          onChange={e => setPassword(e.target.value)} required
          className="w-full p-3 border rounded mb-4" />
        <button type="submit" disabled={loading}
          className="w-full bg-blue-600 text-white p-3 rounded hover:bg-blue-700">
          {loading ? 'Signing in...' : 'Sign In'}
        </button>
        <p className="mt-4 text-center text-sm">
          Don't have an account? <Link to="/register" className="text-blue-600">Register</Link>
        </p>
      </form>
    </div>
  );
}
```

### 3. Create Register Page (`src/pages/RegisterPage.tsx`)

```tsx
import { useState, FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authService } from '../services/authService';

export default function RegisterPage() {
  const [form, setForm] = useState({ email: '', password: '', merchantName: '' });
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      await authService.register(form);
      navigate('/login');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Registration failed');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <form onSubmit={handleSubmit} className="bg-white p-8 rounded-lg shadow-md w-96">
        <h1 className="text-2xl font-bold mb-6">Create Account</h1>
        {error && <div className="bg-red-100 text-red-700 p-3 rounded mb-4">{error}</div>}
        <input type="text" placeholder="Merchant Name"
          onChange={e => setForm({...form, merchantName: e.target.value})} required
          className="w-full p-3 border rounded mb-4" />
        <input type="email" placeholder="Email"
          onChange={e => setForm({...form, email: e.target.value})} required
          className="w-full p-3 border rounded mb-4" />
        <input type="password" placeholder="Password (min 8 chars)"
          onChange={e => setForm({...form, password: e.target.value})} required minLength={8}
          className="w-full p-3 border rounded mb-4" />
        <button type="submit" className="w-full bg-blue-600 text-white p-3 rounded hover:bg-blue-700">
          Register
        </button>
        <p className="mt-4 text-center text-sm">
          Already have an account? <Link to="/login" className="text-blue-600">Sign In</Link>
        </p>
      </form>
    </div>
  );
}
```

### 4. Update Routes (`src/App.tsx`)

```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/" element={<Navigate to="/login" />} />
      </Routes>
    </BrowserRouter>
  );
}
export default App;
```

## Verification

```bash
cd merchant-portal
npm run dev
# Open http://localhost:5173/login — form should render
# Open http://localhost:5173/register — registration form should render
# Submit login with invalid creds — error message should appear
```

## Git Commit

```bash
git add merchant-portal/src/pages merchant-portal/src/services
git commit -m "feat(portal): add login and register pages with auth service"
```

## Next Step
→ **Phase 11 Part 3** — Dashboard layout with sidebar navigation and overview stats
