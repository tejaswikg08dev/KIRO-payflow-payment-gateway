# Sprint 1, Part 16: Frontend Dashboard Page

**Duration:** 1-2 hours  
**Prerequisites:** Part 15 completed, Login page working

---

## 1. What We're Building

In this part, you'll create the **dashboard page** that displays merchant statistics after login.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     DASHBOARD PAGE LAYOUT                                    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ PayFlow Dashboard                               [ Logout ]          │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │                                                                      │   │
│  │  ┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐│   │
│  │  │ Total Payments    │  │ Success Rate      │  │ Total Revenue     ││   │
│  │  │                   │  │                   │  │                   ││   │
│  │  │     1,234         │  │     98.5%         │  │     ₹12,34,567   ││   │
│  │  │                   │  │                   │  │                   ││   │
│  │  └───────────────────┘  └───────────────────┘  └───────────────────┘│   │
│  │                                                                      │   │
│  │  ┌─────────────────────────────┐                                    │   │
│  │  │   View Transactions        │                                    │   │
│  │  └─────────────────────────────┘                                    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Features:                                                                  │
│  • Fetch dashboard stats from API                                          │
│  • Loading state while fetching                                            │
│  • Stats cards with formatted numbers                                      │
│  • Logout functionality                                                    │
│  • Navigation to transactions page                                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 Dashboard Data Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DASHBOARD DATA FLOW                                       │
│                                                                              │
│  1. User navigates to /dashboard                                            │
│  2. DashboardPage mounts                                                    │
│  3. useEffect triggers API call                                             │
│  4. api.get('/v1/merchant/dashboard/stats')                                 │
│  5. Interceptor adds Authorization header                                   │
│  6. Backend returns stats                                                   │
│  7. setStats() updates state                                                │
│  8. Component re-renders with data                                          │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                    DATA FLOW DIAGRAM                               │    │
│  │                                                                     │    │
│  │  DashboardPage                     Backend                          │    │
│  │  ─────────────                     ───────                          │    │
│  │      │                                │                             │    │
│  │      │  useEffect (mount)             │                             │    │
│  │      │                                │                             │    │
│  │      │  GET /v1/merchant/dashboard/stats                            │    │
│  │      │  Authorization: Bearer {token} │                             │    │
│  │      │ ───────────────────────────►   │                             │    │
│  │      │                                │                             │    │
│  │      │  { success: true,              │                             │    │
│  │      │    data: {                     │                             │    │
│  │      │      totalPayments: 1234,      │                             │    │
│  │      │      successRate: 98.5,        │                             │    │
│  │      │      totalRevenue: 1234567,    │                             │    │
│  │      │      currency: "INR"           │                             │    │
│  │      │    }                           │                             │    │
│  │      │  }                             │                             │    │
│  │      │ ◄───────────────────────────   │                             │    │
│  │      │                                │                             │    │
│  │      │  setStats(response.data.data)  │                             │    │
│  │      ▼                                │                             │    │
│  │                                                                     │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 useEffect for Data Fetching

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    useEffect EXPLAINED                                       │
│                                                                              │
│  useEffect(() => {                                                          │
│    const fetchStats = async () => {                                         │
│      // API call here                                                       │
│    };                                                                       │
│    fetchStats();                                                            │
│  }, []);   ← Empty dependency array = run once on mount                     │
│                                                                              │
│  Why async inside useEffect?                                                │
│  ───────────────────────────                                                │
│  useEffect callback cannot be async directly.                               │
│  We define an async function INSIDE and call it immediately.                │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────┐      │
│  │                                                                   │      │
│  │  ❌ WRONG:                                                        │      │
│  │  useEffect(async () => { ... }, [])                              │      │
│  │                                                                   │      │
│  │  ✅ CORRECT:                                                      │      │
│  │  useEffect(() => {                                                │      │
│  │    const fetchData = async () => { ... };                        │      │
│  │    fetchData();                                                   │      │
│  │  }, [])                                                           │      │
│  │                                                                   │      │
│  └──────────────────────────────────────────────────────────────────┘      │
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

Verify you can log in successfully from Part 15.

---

## 4. Step-by-Step Implementation

### Step 4.1: Create Dashboard Page Component

**File: `frontend-dashboard/src/pages/DashboardPage.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';

interface DashboardStats {
  totalPayments: number;
  successRate: number;
  totalRevenue: number;
  currency: string;
}

function DashboardPage() {
  const navigate = useNavigate();
  const [stats, setStats] = useState<DashboardStats>({
    totalPayments: 0,
    successRate: 0,
    totalRevenue: 0,
    currency: 'INR',
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const response = await api.get('/v1/merchant/dashboard/stats');
        setStats(response.data.data);
      } catch {
        // If unauthorized, api interceptor will redirect to login
      } finally {
        setLoading(false);
      }
    };
    fetchStats();
  }, []);

  const handleLogout = () => {
    localStorage.removeItem('payflow_token');
    navigate('/login');
  };

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-gray-500">Loading dashboard...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="border-b bg-white px-6 py-4 shadow-sm">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-bold text-gray-800">PayFlow Dashboard</h1>
          <button
            onClick={handleLogout}
            className="rounded px-4 py-2 text-sm text-gray-600 hover:bg-gray-100"
          >
            Logout
          </button>
        </div>
      </header>

      {/* Stats Cards */}
      <main className="mx-auto max-w-6xl p-6">
        <div className="grid gap-6 md:grid-cols-3">
          {/* Total Payments */}
          <div className="rounded-lg bg-white p-6 shadow">
            <p className="text-sm font-medium text-gray-500">Total Payments</p>
            <p className="mt-2 text-3xl font-bold text-gray-900">
              {stats.totalPayments.toLocaleString()}
            </p>
          </div>

          {/* Success Rate */}
          <div className="rounded-lg bg-white p-6 shadow">
            <p className="text-sm font-medium text-gray-500">Success Rate</p>
            <p className="mt-2 text-3xl font-bold text-green-600">
              {stats.successRate.toFixed(1)}%
            </p>
          </div>

          {/* Revenue */}
          <div className="rounded-lg bg-white p-6 shadow">
            <p className="text-sm font-medium text-gray-500">Total Revenue</p>
            <p className="mt-2 text-3xl font-bold text-gray-900">
              ₹{stats.totalRevenue.toLocaleString()}
            </p>
          </div>
        </div>

        {/* Quick Links */}
        <div className="mt-8">
          <button
            onClick={() => navigate('/transactions')}
            className="rounded bg-primary px-4 py-2 text-white hover:bg-primary-dark"
          >
            View Transactions
          </button>
        </div>
      </main>
    </div>
  );
}

export default DashboardPage;
```

**Code Breakdown:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DASHBOARD PAGE EXPLAINED                                  │
│                                                                              │
│  Interface DashboardStats:                                                  │
│  ─────────────────────────                                                  │
│  • totalPayments: number  → Count of all payments                          │
│  • successRate: number    → Percentage (0-100)                             │
│  • totalRevenue: number   → Sum in paisa (or smallest currency unit)       │
│  • currency: string       → "INR", "USD", etc.                             │
│                                                                              │
│  State Variables:                                                           │
│  ────────────────                                                           │
│  stats: DashboardStats    → The fetched statistics                         │
│  loading: boolean         → True while fetching data                       │
│                                                                              │
│  useEffect Pattern:                                                         │
│  ──────────────────                                                         │
│  1. Define async function fetchStats inside useEffect                      │
│  2. Call API with api.get()                                                │
│  3. setStats with response data                                            │
│  4. Empty catch block - interceptor handles 401                            │
│  5. finally sets loading to false                                          │
│                                                                              │
│  handleLogout Function:                                                     │
│  ──────────────────────                                                     │
│  1. Remove token from localStorage                                         │
│  2. Navigate to /login                                                     │
│  Note: Simple approach - no API call needed                                │
│                                                                              │
│  Loading State:                                                             │
│  ──────────────                                                             │
│  Return early with loading message if data not ready                       │
│                                                                              │
│  Number Formatting:                                                         │
│  ──────────────────                                                         │
│  • toLocaleString() → Adds commas: 1234567 → "1,234,567"                  │
│  • toFixed(1)       → One decimal: 98.54321 → "98.5"                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.2: Understanding the Component Structure

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    COMPONENT HIERARCHY                                       │
│                                                                              │
│  DashboardPage                                                              │
│  └── <div> (min-h-screen bg-gray-50)                                       │
│        ├── <header> (sticky header with title + logout)                    │
│        │     ├── <h1> "PayFlow Dashboard"                                  │
│        │     └── <button> "Logout"                                         │
│        │                                                                    │
│        └── <main> (content area)                                           │
│              ├── <div> (grid of 3 columns)                                 │
│              │     ├── Card 1: Total Payments                              │
│              │     ├── Card 2: Success Rate                                │
│              │     └── Card 3: Total Revenue                               │
│              │                                                              │
│              └── <div> (quick links)                                       │
│                    └── <button> "View Transactions"                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.3: API Response Structure

The `/v1/merchant/dashboard/stats` endpoint returns:

```json
{
  "success": true,
  "data": {
    "totalPayments": 1234,
    "successRate": 98.5,
    "totalRevenue": 1234567,
    "currency": "INR"
  }
}
```

We access the stats via `response.data.data` because:
- `response` - Axios response object
- `response.data` - The JSON body (`{success, data}`)
- `response.data.data` - The actual stats object

---

## 5. Verification

### Test the Dashboard

1. Start dev server: `npm run dev`
2. Login at `http://localhost:3000/login`
3. After successful login, you should be redirected to `/dashboard`
4. Verify the stats cards display (may show zeros if no data)
5. Click "View Transactions" and verify navigation
6. Click "Logout" and verify redirect to login

### Test Error Handling

1. In browser DevTools → Application → Local Storage
2. Delete `payflow_token`
3. Refresh the dashboard page
4. You should be redirected to login (handled by api interceptor)

---

## 6. File Structure

After this part:

```
frontend-dashboard/src/
├── pages/
│   ├── LoginPage.tsx        ← Part 15
│   ├── DashboardPage.tsx    ← Created this part
│   └── TransactionsPage.tsx ← Next part
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
│  ┌────────────────────────┬────────────────────────────────────────────┐   │
│  │  Concept               │  Implementation                            │   │
│  ├────────────────────────┼────────────────────────────────────────────┤   │
│  │  Data Fetching         │  useEffect with async function inside      │   │
│  │  Loading State         │  Show loading UI while fetching            │   │
│  │  Type Safety           │  Interface for API response shape          │   │
│  │  Number Formatting     │  toLocaleString() and toFixed()            │   │
│  │  Logout                │  Remove token + navigate to login          │   │
│  │  Error Handling        │  Let interceptor handle 401 errors         │   │
│  └────────────────────────┴────────────────────────────────────────────┘   │
│                                                                              │
│  Key Pattern: Empty catch block is OK here because the api interceptor     │
│  handles 401 errors globally - no need to duplicate that logic.            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

| Concept | What to Remember |
|---------|------------------|
| **useEffect** | Empty dependency array `[]` = run once on mount |
| **Async in useEffect** | Define async function inside, then call it |
| **Loading state** | Return early with loading UI |
| **Interceptor** | Handles auth errors globally |
| **Logout** | Just remove token and navigate |

---

## 8. Common Issues and Solutions

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TROUBLESHOOTING GUIDE                                     │
│                                                                              │
│  Issue 1: "Dashboard shows zeros for all stats"                            │
│  ───────────────────────────────────────────────                            │
│  Cause:   No transactions in database yet, or API returns empty data       │
│  Fix:     This is normal for new merchants with no payments                │
│           Stats will populate once payments are processed                   │
│                                                                              │
│  Issue 2: "Infinite redirect loop between dashboard and login"             │
│  ────────────────────────────────────────────────────────────               │
│  Cause:   Token invalid but not triggering 401, or race condition          │
│  Fix:     Clear localStorage completely and start fresh                    │
│           Check that API returns proper 401 status code                     │
│                                                                              │
│  Issue 3: "Stats cards not showing in 3 columns"                           │
│  ───────────────────────────────────────────────                            │
│  Cause:   Tailwind classes not being processed                             │
│  Fix:     Ensure tailwind.config.js includes src/**/*.tsx                  │
│           Restart dev server after config changes                           │
│                                                                              │
│  Issue 4: "Logout button doesn't do anything"                              │
│  ───────────────────────────────────────────────                            │
│  Cause:   onClick handler not attached properly                            │
│  Fix:     Check button has onClick={handleLogout}                          │
│           Not onClick={handleLogout()} (that calls it immediately!)        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Related Concepts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    RELATED TOPICS FOR DEEPER LEARNING                        │
│                                                                              │
│  React Hooks Used:                                                          │
│  ─────────────────                                                          │
│  • useState        → Managing component state (stats, loading)             │
│  • useEffect       → Side effects (API calls on mount)                     │
│  • useNavigate     → Programmatic routing                                  │
│                                                                              │
│  TypeScript Patterns:                                                       │
│  ────────────────────                                                       │
│  • Interface       → Define shape of DashboardStats                        │
│  • Type inference  → TypeScript knows setStats accepts DashboardStats      │
│                                                                              │
│  Tailwind CSS:                                                              │
│  ─────────────                                                              │
│  • Grid layout     → grid, gap-6, md:grid-cols-3                          │
│  • Responsive      → md: prefix for tablet+ breakpoint                     │
│  • Shadows         → shadow, shadow-sm for depth                           │
│  • Colors          → bg-primary, text-green-600 for emphasis               │
│                                                                              │
│  Error Handling Strategy:                                                   │
│  ────────────────────────                                                   │
│  • Global: api interceptor handles 401 → redirect to login                │
│  • Local: Component can handle specific errors if needed                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

In the next part, you'll build the **Transactions Page** that:
- Displays a table of payment transactions
- Shows payment ID, order ID, amount, status, and date
- Has status badges with color coding
- Navigates back to dashboard

**Continue to:** [part-17-frontend-transactions-page.md](./part-17-frontend-transactions-page.md)

---

**End of Sprint 1, Part 16**
