# Sprint 1, Part 15: Frontend Login Page

**Duration:** 1-2 hours  
**Prerequisites:** Part 14 completed, React project running

---

## 1. What We're Building

In this part, you'll create the **login page** with form handling and API integration.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     LOGIN PAGE COMPONENTS                                    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                                                                      │   │
│  │                    ┌───────────────────────────┐                    │   │
│  │                    │    PayFlow Dashboard      │                    │   │
│  │                    │                           │                    │   │
│  │                    │  ┌───────────────────┐   │                    │   │
│  │                    │  │ Email             │   │                    │   │
│  │                    │  └───────────────────┘   │                    │   │
│  │                    │                           │                    │   │
│  │                    │  ┌───────────────────┐   │                    │   │
│  │                    │  │ Password          │   │                    │   │
│  │                    │  └───────────────────┘   │                    │   │
│  │                    │                           │                    │   │
│  │                    │  ┌───────────────────┐   │                    │   │
│  │                    │  │     Sign In       │   │                    │   │
│  │                    │  └───────────────────┘   │                    │   │
│  │                    │                           │                    │   │
│  │                    └───────────────────────────┘                    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Features:                                                                  │
│  • Email and password form fields                                          │
│  • Loading state during API call                                           │
│  • Error message display                                                   │
│  • Token storage in localStorage                                           │
│  • Redirect to dashboard after login                                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 Simple Authentication Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AUTHENTICATION FLOW                                       │
│                                                                              │
│  1. User enters email and password                                          │
│  2. Frontend calls POST /api/v1/auth/login                                  │
│  3. Backend returns JWT token                                               │
│  4. Frontend stores token in localStorage                                   │
│  5. Subsequent API calls include token in header                            │
│  6. User is redirected to dashboard                                         │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                    LOGIN FLOW DIAGRAM                              │    │
│  │                                                                     │    │
│  │  LoginPage                    Backend                               │    │
│  │  ──────────                   ───────                               │    │
│  │      │                           │                                  │    │
│  │      │  POST /api/v1/auth/login  │                                  │    │
│  │      │  { email, password }      │                                  │    │
│  │      │ ──────────────────────►   │                                  │    │
│  │      │                           │                                  │    │
│  │      │  { success: true,         │                                  │    │
│  │      │    data: { token: "..." }}│                                  │    │
│  │      │ ◄──────────────────────   │                                  │    │
│  │      │                           │                                  │    │
│  │      │  Store token              │                                  │    │
│  │      │  localStorage.setItem()   │                                  │    │
│  │      │                           │                                  │    │
│  │      │  navigate('/dashboard')   │                                  │    │
│  │      ▼                           │                                  │    │
│  │                                                                     │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Token Storage Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TOKEN STORAGE COMPARISON                                  │
│                                                                              │
│  Option         │ Pros                    │ Cons                            │
│  ───────────────┼─────────────────────────┼────────────────────────────────│
│  localStorage   │ Persists across tabs    │ Vulnerable to XSS              │
│                 │ Simple API              │ Accessible by any JS            │
│                 │ Persists on refresh     │                                 │
│                 │                         │                                 │
│  sessionStorage │ Cleared on tab close    │ Lost when tab closes           │
│                 │ Per-tab isolation       │ Can't share across tabs         │
│                 │                         │                                 │
│  HTTP-only      │ Not accessible by JS    │ Need backend changes           │
│  Cookie         │ Auto-sent with requests │ More complex setup              │
│                 │ XSS protection          │                                 │
│                                                                              │
│  Our approach: localStorage with key 'payflow_token'                        │
│  Simple and effective for learning purposes.                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

```powershell
# Ensure dev server is running
cd frontend-dashboard
npm run dev
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Create Login Page Component

**File: `frontend-dashboard/src/pages/LoginPage.tsx`**

```tsx
import { useState, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';

function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await api.post('/v1/auth/login', { email, password });
      localStorage.setItem('payflow_token', response.data.data.token);
      navigate('/dashboard');
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { error?: { message?: string } } } })
          .response?.data?.error?.message || 'Login failed. Please try again.';
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
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
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
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              placeholder="••••••••"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded bg-primary py-2 font-medium text-white hover:bg-primary-dark disabled:opacity-50"
          >
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  );
}

export default LoginPage;
```

**Code Breakdown:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    LOGIN PAGE EXPLAINED                                      │
│                                                                              │
│  Imports:                                                                   │
│  ────────                                                                   │
│  useState, FormEvent    → React hooks and types                            │
│  useNavigate            → React Router hook for navigation                 │
│  api                    → Axios instance with interceptors                 │
│                                                                              │
│  State Variables:                                                           │
│  ────────────────                                                           │
│  email: string          → Email input value                                │
│  password: string       → Password input value                             │
│  error: string          → Error message to display                         │
│  loading: boolean       → Submit button loading state                      │
│                                                                              │
│  handleSubmit Function:                                                     │
│  ─────────────────────                                                      │
│  1. e.preventDefault()         → Prevent form default submit               │
│  2. setError('')               → Clear previous errors                     │
│  3. setLoading(true)           → Show loading state                        │
│  4. api.post('/v1/auth/login') → Call login API                            │
│  5. localStorage.setItem()     → Store token as 'payflow_token'            │
│  6. navigate('/dashboard')     → Redirect on success                       │
│  7. catch → setError()         → Show error message                        │
│  8. finally → setLoading(false)→ Reset loading state                       │
│                                                                              │
│  Token Storage:                                                             │
│  ──────────────                                                             │
│  localStorage.setItem('payflow_token', response.data.data.token);          │
│  • Key: 'payflow_token'                                                    │
│  • The api interceptor reads this key for Authorization header             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.2: Understanding the API Response

The login endpoint returns this structure:

```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "user": {
      "id": "usr_abc123",
      "email": "admin@payflow.com",
      "fullName": "Admin User",
      "role": "MERCHANT"
    }
  }
}
```

We extract `response.data.data.token` and store it.

### Step 4.3: How Authentication Works with API Interceptor

Remember from Part 14, the api service has interceptors:

```typescript
// Request interceptor adds token to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('payflow_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor handles 401 errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('payflow_token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

This means:
1. After login, token is stored in localStorage
2. Every subsequent API call automatically includes the token
3. If token expires (401), user is redirected to login

---

## 5. Verification

### Start the Development Server

```powershell
cd frontend-dashboard
npm run dev
```

### Test Login Flow

1. Open `http://localhost:3000/login`
2. You should see the login form
3. Enter valid credentials
4. Check browser DevTools:
   - Network tab: See the POST request
   - Application tab: See `payflow_token` in localStorage
5. After successful login, you're redirected to `/dashboard`

### Test Error Handling

1. Enter invalid credentials
2. You should see error message displayed
3. Form should be re-enabled after error

---

## 6. File Structure

After this part, your pages folder should have:

```
frontend-dashboard/src/
├── pages/
│   ├── LoginPage.tsx        ← Created this part
│   ├── DashboardPage.tsx    ← Next part
│   └── TransactionsPage.tsx
├── services/
│   └── api.ts
├── App.tsx
├── main.tsx
└── index.css
```

---

## 7. Key Takeaways

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHAT YOU LEARNED                                          │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Concept               │  Implementation                            │   │
│  ├────────────────────────┼────────────────────────────────────────────┤   │
│  │  Form State            │  useState for each field (email, password)│   │
│  │  Form Submission       │  async handleSubmit with e.preventDefault │   │
│  │  Error Handling        │  try/catch with setError()                │   │
│  │  Loading State         │  setLoading(true/false) + disabled button │   │
│  │  Token Storage         │  localStorage.setItem('payflow_token')    │   │
│  │  Navigation            │  useNavigate() from react-router-dom      │   │
│  │  API Integration       │  Axios instance with interceptors         │   │
│  └────────────────────────┴────────────────────────────────────────────┘   │
│                                                                              │
│  Key Pattern: The api interceptor automatically adds token to requests     │
│  and handles 401 errors - you don't need to do it in every component!      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

| Concept | What to Remember |
|---------|------------------|
| **Token Key** | Always use `'payflow_token'` - matches api interceptor |
| **API Response** | Access token at `response.data.data.token` |
| **Error Message** | Extract from `error.response?.data?.error?.message` |
| **Loading State** | Disable button and show "Signing in..." text |
| **Navigation** | Use `navigate('/dashboard')` after successful login |

---

## 8. Common Issues and Solutions

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TROUBLESHOOTING GUIDE                                     │
│                                                                              │
│  Issue 1: "Login fails but no error shown"                                 │
│  ───────────────────────────────────────────                                │
│  Cause:   Backend might not be running                                      │
│  Fix:     Check backend services are running at http://localhost:8080      │
│           Use browser DevTools → Network tab to see actual error           │
│                                                                              │
│  Issue 2: "Token not being sent in subsequent requests"                    │
│  ─────────────────────────────────────────────────────                      │
│  Cause:   Token key mismatch or interceptor issue                          │
│  Fix:     Verify localStorage key is exactly 'payflow_token'               │
│           Check api.ts interceptor is configured correctly                 │
│                                                                              │
│  Issue 3: "Redirect to login after successful login"                       │
│  ───────────────────────────────────────────────────                        │
│  Cause:   Token not stored before navigate, or dashboard making            │
│           API call before token is saved                                    │
│  Fix:     Ensure localStorage.setItem runs BEFORE navigate()               │
│                                                                              │
│  Issue 4: "CORS error when calling API"                                    │
│  ─────────────────────────────────────                                      │
│  Cause:   Vite proxy not configured correctly                              │
│  Fix:     Check vite.config.ts has proxy for '/api' to backend             │
│                                                                              │
│  Issue 5: "Form submits but page reloads"                                  │
│  ─────────────────────────────────────────                                  │
│  Cause:   Missing e.preventDefault() in handleSubmit                       │
│  Fix:     Ensure handleSubmit starts with e.preventDefault()               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Related Concepts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    RELATED TOPICS FOR DEEPER LEARNING                        │
│                                                                              │
│  React Concepts Used:                                                       │
│  ─────────────────────                                                      │
│  • useState Hook       → Managing form field values and UI state           │
│  • FormEvent Type      → TypeScript type for form submission events        │
│  • Controlled Inputs   → Input values controlled by React state            │
│                                                                              │
│  Authentication Patterns:                                                   │
│  ────────────────────────                                                   │
│  • JWT (JSON Web Token)  → Stateless authentication token                  │
│  • Bearer Token          → Authorization header format                     │
│  • Token Expiry          → Handled by api interceptor (401 → /login)       │
│                                                                              │
│  React Router:                                                              │
│  ─────────────                                                              │
│  • useNavigate           → Programmatic navigation                         │
│  • Route components      → Defined in App.tsx                              │
│  • No ProtectedRoute     → We rely on api interceptor for auth checks      │
│                                                                              │
│  Axios Interceptors:                                                        │
│  ───────────────────                                                        │
│  • Request interceptor   → Adds token to every request                     │
│  • Response interceptor  → Handles 401 globally (redirect to login)        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

In the next part, you'll build the **Dashboard Page** that:
- Displays merchant statistics
- Shows total payments, success rate, and revenue
- Has navigation to transactions page
- Includes a logout button

**Continue to:** [part-16-frontend-dashboard-page.md](./part-16-frontend-dashboard-page.md)

---

**End of Sprint 1, Part 15**
