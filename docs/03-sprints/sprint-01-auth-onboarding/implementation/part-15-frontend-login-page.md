# Sprint 1, Part 15: Frontend Login, Register & Merchant Onboarding Pages

**Duration:** 2-3 hours  
**Prerequisites:** Part 14 completed, React project running

---

## 1. What We're Building

In this part, you'll create the **complete authentication flow** with three pages:

1. **RegisterPage** - New user registration
2. **MerchantOnboardingPage** - Business setup after registration  
3. **LoginPage** - Existing user sign-in

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     COMPLETE AUTH FLOW                                       │
│                                                                              │
│  ┌──────────────┐      ┌────────────────────┐      ┌─────────────────┐     │
│  │              │      │                    │      │                 │     │
│  │   Register   │ ───► │ Merchant Onboarding│ ───► │    Dashboard    │     │
│  │    Page      │      │       Page         │      │      Page       │     │
│  │              │      │                    │      │                 │     │
│  └──────────────┘      └────────────────────┘      └─────────────────┘     │
│         ▲                                                    │              │
│         │                                                    │              │
│         │            ┌──────────────┐                       │              │
│         │            │              │                       │              │
│         └─────────── │    Login     │ ◄─────────────────────┘              │
│                      │    Page      │    (Existing User)                   │
│                      │              │                                       │
│                      └──────────────┘                                       │
│                                                                              │
│  New User Flow:     /register → /onboarding → /dashboard                   │
│  Existing User Flow: /login → /dashboard                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 Two-Step Onboarding Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHY TWO-STEP ONBOARDING?                                  │
│                                                                              │
│  Step 1: User Registration                                                  │
│  ─────────────────────────                                                  │
│  • Creates identity.users record                                            │
│  • Returns JWT token with userId                                            │
│  • Minimal info: email, password, name                                      │
│                                                                              │
│  Step 2: Merchant Onboarding                                                │
│  ─────────────────────────────                                              │
│  • Creates merchant.merchants record                                        │
│  • Links to userId from Step 1                                              │
│  • Business details: name, type, GST, website                               │
│                                                                              │
│  Benefits:                                                                   │
│  • Separation of concerns (identity vs business)                            │
│  • Can pause onboarding and resume later                                    │
│  • Different services handle different data                                 │
│  • Consistent with production payment gateways                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Token Storage Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TOKEN STORAGE                                             │
│                                                                              │
│  Key: 'payflow_token'                                                       │
│  Storage: localStorage                                                      │
│                                                                              │
│  Why localStorage?                                                          │
│  • Persists across tabs and browser refresh                                 │
│  • Simple API for read/write                                                │
│  • Acceptable for development/learning                                      │
│                                                                              │
│  In production, consider:                                                   │
│  • HTTP-only cookies (more secure against XSS)                              │
│  • Refresh token rotation                                                   │
│  • Token expiration handling                                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

```powershell
# Ensure frontend dev server is ready
cd frontend-dashboard
npm install

# Verify API service exists
ls src/services/api.ts
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Create Register Page

**File: `frontend-dashboard/src/pages/RegisterPage.tsx`**

```tsx
import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../services/api';

function RegisterPage() {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    confirmPassword: '',
    fullName: '',
    phone: '',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const validateForm = (): string | null => {
    if (formData.password.length < 8) {
      return 'Password must be at least 8 characters';
    }
    if (formData.password !== formData.confirmPassword) {
      return 'Passwords do not match';
    }
    if (!formData.email.includes('@')) {
      return 'Please enter a valid email';
    }
    if (formData.fullName.trim().length < 2) {
      return 'Please enter your full name';
    }
    return null;
  };

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError('');

    const validationError = validateForm();
    if (validationError) {
      setError(validationError);
      return;
    }

    setLoading(true);

    try {
      const response = await api.post('/v1/auth/register', {
        email: formData.email,
        password: formData.password,
        fullName: formData.fullName,
        phone: formData.phone || null,
        role: 'MERCHANT',
      });

      const { accessToken, refreshToken, user } = response.data.data;

      // Store both tokens
      localStorage.setItem('payflow_token', accessToken);
      localStorage.setItem('payflow_refresh_token', refreshToken);
      localStorage.setItem('payflow_user', JSON.stringify(user));

      // Navigate to merchant onboarding
      navigate('/onboarding');
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { message?: string } } };
      const message = errorResponse.response?.data?.message || 'Registration failed. Please try again.';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-100">
      <div className="w-full max-w-md rounded-lg bg-white p-8 shadow-md">
        <h1 className="mb-2 text-center text-2xl font-bold text-gray-800">
          Create Account
        </h1>
        <p className="mb-6 text-center text-sm text-gray-500">
          Sign up to start accepting payments with PayFlow
        </p>

        {error && (
          <div className="mb-4 rounded bg-red-50 p-3 text-sm text-red-600">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="fullName" className="block text-sm font-medium text-gray-700">
              Full Name
            </label>
            <input
              id="fullName"
              name="fullName"
              type="text"
              required
              value={formData.fullName}
              onChange={handleChange}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="John Doe"
            />
          </div>

          <div>
            <label htmlFor="email" className="block text-sm font-medium text-gray-700">
              Email
            </label>
            <input
              id="email"
              name="email"
              type="email"
              required
              value={formData.email}
              onChange={handleChange}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="john@example.com"
            />
          </div>

          <div>
            <label htmlFor="phone" className="block text-sm font-medium text-gray-700">
              Phone (Optional)
            </label>
            <input
              id="phone"
              name="phone"
              type="tel"
              value={formData.phone}
              onChange={handleChange}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="+91 9876543210"
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-700">
              Password
            </label>
            <input
              id="password"
              name="password"
              type="password"
              required
              value={formData.password}
              onChange={handleChange}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="••••••••"
            />
            <p className="mt-1 text-xs text-gray-500">Minimum 8 characters</p>
          </div>

          <div>
            <label htmlFor="confirmPassword" className="block text-sm font-medium text-gray-700">
              Confirm Password
            </label>
            <input
              id="confirmPassword"
              name="confirmPassword"
              type="password"
              required
              value={formData.confirmPassword}
              onChange={handleChange}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="••••••••"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded bg-blue-600 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? 'Creating Account...' : 'Create Account'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-gray-500">
          Already have an account?{' '}
          <Link to="/login" className="text-blue-600 hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}

export default RegisterPage;
```

---

### Step 4.2: Create Merchant Onboarding Page

**File: `frontend-dashboard/src/pages/MerchantOnboardingPage.tsx`**

```tsx
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';

interface UserInfo {
  userId: string;
  email: string;
  fullName: string;
  role: string;
}

function MerchantOnboardingPage() {
  const navigate = useNavigate();
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
  const [formData, setFormData] = useState({
    businessName: '',
    businessType: 'INDIVIDUAL',
    websiteUrl: '',
    gstNumber: '',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [checkingAuth, setCheckingAuth] = useState(true);

  useEffect(() => {
    // Check if user is authenticated
    const token = localStorage.getItem('payflow_token');
    if (!token) {
      navigate('/register');
      return;
    }

    // Get user info from profile endpoint
    const fetchUserInfo = async () => {
      try {
        const response = await api.get('/v1/auth/profile');
        setUserInfo(response.data.data);

        // Check if user already has a merchant account
        try {
          const merchantResponse = await api.get(`/v1/merchants/by-user/${response.data.data.userId}`);
          if (merchantResponse.data.data) {
            // User already has merchant, go to dashboard
            navigate('/dashboard');
            return;
          }
        } catch {
          // No merchant exists, continue with onboarding
        }
      } catch {
        // Token invalid, redirect to login
        localStorage.removeItem('payflow_token');
        navigate('/login');
      } finally {
        setCheckingAuth(false);
      }
    };

    fetchUserInfo();
  }, [navigate]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError('');

    if (!userInfo) {
      setError('User information not loaded');
      return;
    }

    if (formData.businessName.trim().length < 2) {
      setError('Please enter a valid business name');
      return;
    }

    setLoading(true);

    try {
      await api.post('/v1/merchants', {
        userId: userInfo.userId,
        businessName: formData.businessName.trim(),
        businessType: formData.businessType,
        websiteUrl: formData.websiteUrl || null,
        gstNumber: formData.gstNumber || null,
      });

      // Merchant created, go to dashboard
      navigate('/dashboard');
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { message?: string } } };
      const message = errorResponse.response?.data?.message || 'Failed to create merchant. Please try again.';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  if (checkingAuth) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-100">
        <p className="text-gray-500">Loading...</p>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-100">
      <div className="w-full max-w-lg rounded-lg bg-white p-8 shadow-md">
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-800">
            Set Up Your Business
          </h1>
          <p className="mt-1 text-sm text-gray-500">
            Welcome, {userInfo?.fullName}! Let's set up your merchant account.
          </p>
        </div>

        {/* Progress indicator */}
        <div className="mb-6 flex items-center">
          <div className="flex items-center">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-green-500 text-sm text-white">
              ✓
            </div>
            <span className="ml-2 text-sm text-gray-600">Account Created</span>
          </div>
          <div className="mx-4 h-1 flex-1 bg-blue-500"></div>
          <div className="flex items-center">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-500 text-sm text-white">
              2
            </div>
            <span className="ml-2 text-sm font-medium text-gray-800">Business Setup</span>
          </div>
        </div>

        {error && (
          <div className="mb-4 rounded bg-red-50 p-3 text-sm text-red-600">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="businessName" className="block text-sm font-medium text-gray-700">
              Business Name *
            </label>
            <input
              id="businessName"
              name="businessName"
              type="text"
              required
              value={formData.businessName}
              onChange={handleChange}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="Acme Electronics"
            />
            <p className="mt-1 text-xs text-gray-500">This will be displayed on payment pages</p>
          </div>

          <div>
            <label htmlFor="businessType" className="block text-sm font-medium text-gray-700">
              Business Type *
            </label>
            <select
              id="businessType"
              name="businessType"
              value={formData.businessType}
              onChange={handleChange}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            >
              <option value="INDIVIDUAL">Individual / Sole Proprietor</option>
              <option value="PARTNERSHIP">Partnership</option>
              <option value="COMPANY">Private Limited Company</option>
              <option value="LLP">Limited Liability Partnership (LLP)</option>
              <option value="TRUST">Trust / NGO</option>
            </select>
          </div>

          <div>
            <label htmlFor="websiteUrl" className="block text-sm font-medium text-gray-700">
              Website URL (Optional)
            </label>
            <input
              id="websiteUrl"
              name="websiteUrl"
              type="url"
              value={formData.websiteUrl}
              onChange={handleChange}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="https://www.yoursite.com"
            />
          </div>

          <div>
            <label htmlFor="gstNumber" className="block text-sm font-medium text-gray-700">
              GST Number (Optional)
            </label>
            <input
              id="gstNumber"
              name="gstNumber"
              type="text"
              value={formData.gstNumber}
              onChange={handleChange}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="22AAAAA0000A1Z5"
            />
            <p className="mt-1 text-xs text-gray-500">15-character GST Identification Number</p>
          </div>

          <div className="rounded bg-blue-50 p-4">
            <h3 className="text-sm font-medium text-blue-800">What happens next?</h3>
            <ul className="mt-2 space-y-1 text-sm text-blue-700">
              <li>• Your merchant account will be created</li>
              <li>• You can generate API keys to integrate payments</li>
              <li>• Start accepting test payments immediately</li>
              <li>• Complete KYC verification to go live</li>
            </ul>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded bg-blue-600 py-3 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? 'Creating Merchant Account...' : 'Complete Setup'}
          </button>
        </form>

        <p className="mt-4 text-center text-xs text-gray-500">
          By continuing, you agree to PayFlow's Terms of Service and Privacy Policy.
        </p>
      </div>
    </div>
  );
}

export default MerchantOnboardingPage;
```

---

### Step 4.3: Create Login Page

**File: `frontend-dashboard/src/pages/LoginPage.tsx`**

```tsx
import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../services/api';

function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await api.post('/v1/auth/login', { email, password });
      const { accessToken, refreshToken, user } = response.data.data;
      
      // Store both tokens
      localStorage.setItem('payflow_token', accessToken);
      localStorage.setItem('payflow_refresh_token', refreshToken);
      localStorage.setItem('payflow_user', JSON.stringify(user));
      
      navigate('/dashboard');
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { message?: string } } })
          .response?.data?.message || 'Login failed. Please try again.';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-100">
      <div className="w-full max-w-md rounded-lg bg-white p-8 shadow-md">
        <h1 className="mb-6 text-center text-2xl font-bold text-gray-800">
          PayFlow Dashboard
        </h1>

        {error && (
          <div className="mb-4 rounded bg-red-50 p-3 text-sm text-red-600">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-gray-700">
              Email
            </label>
            <input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="admin@payflow.com"
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-700">
              Password
            </label>
            <input
              id="password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="••••••••"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded bg-blue-600 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-gray-500">
          Don't have an account?{' '}
          <Link to="/register" className="text-blue-600 hover:underline">
            Create one
          </Link>
        </p>
      </div>
    </div>
  );
}

export default LoginPage;
```

---

### Step 4.4: Update App.tsx Routes

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

---

### Step 4.5: Configure Vite Proxy

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

**Important:** The `rewrite` function removes the `/api` prefix because our backend endpoints are at `/v1/auth/...` not `/api/v1/auth/...`. The `secure: false` option allows proxying to HTTP targets.

---

## 5. Backend Endpoints Required

These endpoints must exist for the frontend to work:

### Identity Service (Port 8081)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/auth/register` | Create new user account |
| POST | `/v1/auth/login` | Authenticate and get JWT |
| GET | `/v1/auth/profile` | Get user info from JWT |

### Merchant Service (Port 8082)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/merchants` | Create merchant record |
| GET | `/v1/merchants/by-user/{userId}` | Get merchant by user ID |

---

## 6. Verification

### Start the Development Server

```powershell
cd frontend-dashboard
npm run dev
```

### Test Complete Flow

1. **Register New User:**
   - Open `http://localhost:3000/register`
   - Fill in all fields
   - Click "Create Account"
   - Should redirect to `/onboarding`

2. **Complete Onboarding:**
   - Enter business name and select type
   - Click "Complete Setup"
   - Should redirect to `/dashboard`

3. **Test Login:**
   - Logout (clear localStorage or click logout)
   - Go to `/login`
   - Enter registered credentials
   - Should redirect to `/dashboard`

4. **Verify Token Storage:**
   - Open DevTools → Application → Local Storage
   - Check `payflow_token` exists
   - Check `payflow_user` has user data

---

## 7. File Structure

```
frontend-dashboard/src/
├── pages/
│   ├── LoginPage.tsx            ← Sign in existing users
│   ├── RegisterPage.tsx         ← Create new users
│   ├── MerchantOnboardingPage.tsx ← Business setup
│   ├── DashboardPage.tsx        ← Main dashboard
│   ├── TransactionsPage.tsx     ← Transaction list
│   └── ApiKeysPage.tsx          ← API key management
├── services/
│   └── api.ts                   ← Axios instance with interceptors
├── App.tsx                      ← Route configuration
├── main.tsx                     ← React entry point
└── index.css                    ← Tailwind CSS
```

---

## 8. Key Takeaways

| Concept | Implementation |
|---------|----------------|
| **Form State** | useState with object for multiple fields |
| **Form Validation** | validateForm() returns error string or null |
| **Error Handling** | try/catch with setError() |
| **Loading State** | Disable button + show loading text |
| **Token Storage** | `localStorage.setItem('payflow_token', ...)` and `payflow_refresh_token` |
| **Navigation** | `useNavigate()` from react-router-dom |
| **Auth Check** | useEffect to verify token on page load |
| **Conditional Rendering** | checkingAuth state for loading |
| **React Types** | Use `React.FormEvent<HTMLFormElement>` instead of deprecated `FormEvent` |

---

## 9. Common Issues and Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| "Network Error" | Backend not running | Start identity-service and merchant-service |
| "401 Unauthorized" | Token expired/invalid | Clear localStorage, re-register |
| Redirect loop | Missing merchant check | Verify /v1/merchants/by-user endpoint |
| Form doesn't submit | Missing e.preventDefault() | Add to handleSubmit |
| Styles not working | Tailwind not configured | Check tailwind.config.js |

---

## 10. Next Steps

In the next part, you'll build the **Dashboard Page** that:
- Displays merchant statistics
- Shows navigation sidebar
- Includes logout functionality

**Continue to:** [part-16-frontend-dashboard-page.md](./part-16-frontend-dashboard-page.md)

---

**End of Sprint 1, Part 15**
