# Sprint 1, Part 17: Frontend Transactions Page

**Duration:** 1-2 hours  
**Prerequisites:** Part 16 completed, Dashboard page working

---

## 1. What We're Building

In this part, you'll create the **transactions page** that displays a table of payment history.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     TRANSACTIONS PAGE LAYOUT                                 │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Transactions                                      [ ← Dashboard ]   │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │                                                                      │   │
│  │  ┌──────────────────────────────────────────────────────────────┐  │   │
│  │  │ Payment ID    │ Order ID     │ Amount  │ Method │ Status │ Date│  │   │
│  │  ├───────────────┼──────────────┼─────────┼────────┼────────┼─────┤  │   │
│  │  │ pay_abc123    │ ord_xyz789   │ ₹1,000  │ card   │ ●captured│ ... │  │   │
│  │  │ pay_def456    │ ord_uvw456   │ ₹2,500  │ upi    │ ●settled │ ... │  │   │
│  │  │ pay_ghi789    │ ord_rst123   │ ₹500    │ card   │ ●failed  │ ... │  │   │
│  │  │ pay_jkl012    │ ord_opq890   │ ₹750    │ netbank│ ●process │ ... │  │   │
│  │  └──────────────────────────────────────────────────────────────┘  │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Features:                                                                  │
│  • Fetch paginated payment list from API                                   │
│  • Table with sortable columns                                             │
│  • Color-coded status badges                                               │
│  • Formatted currency and dates                                            │
│  • Empty state when no transactions                                        │
│  • Navigation back to dashboard                                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 Payment Status Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PAYMENT STATUS LIFECYCLE                                  │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                                                                     │    │
│  │    created → authorized → captured → settled                        │    │
│  │       │          │           │                                      │    │
│  │       │          │           └── Successfully completed             │    │
│  │       │          └── Funds reserved                                 │    │
│  │       └── Initial state                                             │    │
│  │                                                                     │    │
│  │    Or alternative paths:                                            │    │
│  │                                                                     │    │
│  │    created → processing → failed                                    │    │
│  │                 │            │                                      │    │
│  │                 │            └── Transaction declined               │    │
│  │                 └── In progress                                     │    │
│  │                                                                     │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  Status Badge Colors:                                                       │
│  ─────────────────────                                                      │
│  captured, settled  → Green  (success)                                     │
│  authorized         → Blue   (pending action)                              │
│  processing         → Yellow (in progress)                                 │
│  failed             → Red    (error)                                       │
│  default            → Gray   (unknown)                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Table Data Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DATA FLOW FOR TRANSACTIONS TABLE                          │
│                                                                              │
│  1. Component mounts                                                        │
│  2. useEffect triggers fetchPayments                                        │
│  3. API call: GET /v1/payments?page=0&size=20                              │
│  4. Response contains paginated data                                        │
│  5. Extract payments from response.data.data.content                       │
│  6. Render table rows with map()                                           │
│                                                                              │
│  API Response Structure:                                                    │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │  {                                                                  │    │
│  │    "success": true,                                                 │    │
│  │    "data": {                                                        │    │
│  │      "content": [           ← Array of payments                     │    │
│  │        { paymentId, orderId, amount, currency, status, ... },       │    │
│  │        ...                                                          │    │
│  │      ],                                                             │    │
│  │      "totalPages": 5,       ← For pagination                        │    │
│  │      "totalElements": 100,                                          │    │
│  │      "number": 0            ← Current page                          │    │
│  │    }                                                                │    │
│  │  }                                                                  │    │
│  └────────────────────────────────────────────────────────────────────┘    │
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

Verify you can navigate from Dashboard to Transactions.

---

## 4. Step-by-Step Implementation

### Step 4.1: Create Transactions Page Component

**File: `frontend-dashboard/src/pages/TransactionsPage.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';

interface Payment {
  paymentId: string;
  orderId: string;
  amount: number;
  currency: string;
  status: string;
  method: string;
  createdAt: string;
}

function TransactionsPage() {
  const navigate = useNavigate();
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchPayments = async () => {
      try {
        const response = await api.get('/v1/payments?page=0&size=20');
        setPayments(response.data.data.content || []);
      } catch {
        // Interceptor handles 401
      } finally {
        setLoading(false);
      }
    };
    fetchPayments();
  }, []);

  const statusColor = (status: string) => {
    switch (status) {
      case 'captured':
      case 'settled':
        return 'bg-green-100 text-green-800';
      case 'authorized':
        return 'bg-blue-100 text-blue-800';
      case 'failed':
        return 'bg-red-100 text-red-800';
      case 'processing':
        return 'bg-yellow-100 text-yellow-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="border-b bg-white px-6 py-4 shadow-sm">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-bold text-gray-800">Transactions</h1>
          <button
            onClick={() => navigate('/dashboard')}
            className="rounded px-4 py-2 text-sm text-gray-600 hover:bg-gray-100"
          >
            ← Dashboard
          </button>
        </div>
      </header>

      {/* Table */}
      <main className="mx-auto max-w-6xl p-6">
        {loading ? (
          <p className="text-gray-500">Loading transactions...</p>
        ) : payments.length === 0 ? (
          <p className="text-gray-500">No transactions found.</p>
        ) : (
          <div className="overflow-x-auto rounded-lg bg-white shadow">
            <table className="w-full text-left text-sm">
              <thead className="border-b bg-gray-50">
                <tr>
                  <th className="px-4 py-3 font-medium text-gray-600">Payment ID</th>
                  <th className="px-4 py-3 font-medium text-gray-600">Order ID</th>
                  <th className="px-4 py-3 font-medium text-gray-600">Amount</th>
                  <th className="px-4 py-3 font-medium text-gray-600">Method</th>
                  <th className="px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="px-4 py-3 font-medium text-gray-600">Date</th>
                </tr>
              </thead>
              <tbody>
                {payments.map((p) => (
                  <tr key={p.paymentId} className="border-b hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-xs">{p.paymentId}</td>
                    <td className="px-4 py-3 font-mono text-xs">{p.orderId}</td>
                    <td className="px-4 py-3">
                      {p.currency === 'INR' ? '₹' : p.currency}{' '}
                      {p.amount.toLocaleString()}
                    </td>
                    <td className="px-4 py-3 capitalize">{p.method}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-block rounded-full px-2 py-1 text-xs font-medium ${statusColor(p.status)}`}
                      >
                        {p.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500">
                      {new Date(p.createdAt).toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>
    </div>
  );
}

export default TransactionsPage;
```

**Code Breakdown:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TRANSACTIONS PAGE EXPLAINED                               │
│                                                                              │
│  Interface Payment:                                                         │
│  ──────────────────                                                         │
│  • paymentId: string    → Unique payment identifier (e.g., "pay_abc123")   │
│  • orderId: string      → Merchant's order reference                       │
│  • amount: number       → Amount in smallest currency unit                 │
│  • currency: string     → "INR", "USD", etc.                               │
│  • status: string       → "captured", "failed", etc.                       │
│  • method: string       → "card", "upi", "netbanking"                      │
│  • createdAt: string    → ISO date string                                  │
│                                                                              │
│  State Variables:                                                           │
│  ────────────────                                                           │
│  payments: Payment[]    → Array of payment records                         │
│  loading: boolean       → True while fetching                              │
│                                                                              │
│  statusColor Function:                                                      │
│  ─────────────────────                                                      │
│  Maps status string to Tailwind CSS classes                                │
│  Returns different bg/text color combinations                               │
│                                                                              │
│  Conditional Rendering:                                                     │
│  ──────────────────────                                                     │
│  loading === true       → "Loading transactions..."                        │
│  payments.length === 0  → "No transactions found."                         │
│  payments.length > 0    → Render table                                     │
│                                                                              │
│  Table Structure:                                                           │
│  ────────────────                                                           │
│  <table>                                                                    │
│    <thead> - Column headers (Payment ID, Order ID, etc.)                   │
│    <tbody> - Data rows, mapped from payments array                         │
│                                                                              │
│  Date Formatting:                                                           │
│  ────────────────                                                           │
│  new Date(p.createdAt).toLocaleString()                                    │
│  Converts ISO string to user's locale format                               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.2: Understanding the statusColor Helper

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    STATUS COLOR MAPPING                                      │
│                                                                              │
│  const statusColor = (status: string) => {                                 │
│    switch (status) {                                                        │
│      case 'captured':                                                       │
│      case 'settled':                                                        │
│        return 'bg-green-100 text-green-800';  ← Success states             │
│      case 'authorized':                                                     │
│        return 'bg-blue-100 text-blue-800';    ← Pending action             │
│      case 'failed':                                                         │
│        return 'bg-red-100 text-red-800';      ← Error state                │
│      case 'processing':                                                     │
│        return 'bg-yellow-100 text-yellow-800';← In progress                │
│      default:                                                               │
│        return 'bg-gray-100 text-gray-800';    ← Unknown                    │
│    }                                                                        │
│  };                                                                         │
│                                                                              │
│  Visual Result:                                                             │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                                                                     │    │
│  │  captured  → ┌─────────────┐  Light green bg, dark green text      │    │
│  │              │  captured   │                                        │    │
│  │              └─────────────┘                                        │    │
│  │                                                                     │    │
│  │  failed    → ┌─────────────┐  Light red bg, dark red text          │    │
│  │              │   failed    │                                        │    │
│  │              └─────────────┘                                        │    │
│  │                                                                     │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.3: Verify App.tsx Routes

Ensure `App.tsx` has the transactions route:

```tsx
import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import TransactionsPage from './pages/TransactionsPage';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/dashboard" element={<DashboardPage />} />
      <Route path="/transactions" element={<TransactionsPage />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}

export default App;
```

---

## 5. Verification

### Test the Transactions Page

1. Login and navigate to dashboard
2. Click "View Transactions" button
3. Verify URL changes to `/transactions`
4. Check table renders (may be empty if no payments)
5. Click "← Dashboard" to go back
6. Verify navigation works both ways

### Test with No Data

If there are no transactions, you should see:
```
"No transactions found."
```

This is the expected empty state, not an error.

---

## 6. File Structure

After this part, your frontend is complete:

```
frontend-dashboard/src/
├── pages/
│   ├── LoginPage.tsx         ← Part 15
│   ├── DashboardPage.tsx     ← Part 16
│   └── TransactionsPage.tsx  ← Created this part
├── services/
│   └── api.ts                ← Part 14
├── App.tsx                   ← Routes configuration
├── main.tsx                  ← Entry point
└── index.css                 ← Tailwind imports
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
│  │  Helper Functions      │  statusColor() for dynamic styling         │   │
│  │  Conditional Render    │  loading ? ... : empty ? ... : table      │   │
│  │  Table Rendering       │  map() over array to create rows          │   │
│  │  Date Formatting       │  new Date(str).toLocaleString()           │   │
│  │  Currency Display      │  Conditional ₹ symbol + toLocaleString    │   │
│  │  Status Badges         │  Rounded colored pill with text           │   │
│  │  Monospace Font        │  font-mono for IDs (better readability)   │   │
│  └────────────────────────┴────────────────────────────────────────────┘   │
│                                                                              │
│  Key Pattern: Use helper functions for repeated logic (statusColor).       │
│  Keeps JSX clean and logic testable.                                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

| Concept | What to Remember |
|---------|------------------|
| **Switch fallback** | Always have `default` case |
| **Empty array** | `[] || []` returns first `[]`, use proper check |
| **Key prop** | Use unique ID (`p.paymentId`) for list items |
| **CSS classes** | Template literal for dynamic class composition |

---

## 8. Common Issues and Solutions

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TROUBLESHOOTING GUIDE                                     │
│                                                                              │
│  Issue 1: "TypeError: Cannot read property 'map' of undefined"             │
│  ─────────────────────────────────────────────────────────────              │
│  Cause:   payments state is undefined instead of empty array               │
│  Fix:     Initialize with [] and use || [] fallback:                       │
│           setPayments(response.data.data.content || [])                    │
│                                                                              │
│  Issue 2: "Table rows don't update when data changes"                      │
│  ────────────────────────────────────────────────────                       │
│  Cause:   Missing or duplicate key prop                                    │
│  Fix:     Ensure each <tr> has unique key={p.paymentId}                   │
│                                                                              │
│  Issue 3: "Status badge shows wrong color"                                 │
│  ─────────────────────────────────────────                                  │
│  Cause:   Status string case mismatch (e.g., "Captured" vs "captured")    │
│  Fix:     Convert to lowercase: statusColor(p.status.toLowerCase())        │
│                                                                              │
│  Issue 4: "Date shows 'Invalid Date'"                                      │
│  ─────────────────────────────────────                                      │
│  Cause:   createdAt is not a valid date string                            │
│  Fix:     Check API response format, may need different parsing           │
│                                                                              │
│  Issue 5: "Table overflows on mobile"                                      │
│  ────────────────────────────────────                                       │
│  Cause:   Table too wide for screen                                        │
│  Fix:     overflow-x-auto class on container allows horizontal scroll     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Related Concepts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    RELATED TOPICS FOR DEEPER LEARNING                        │
│                                                                              │
│  React Patterns:                                                            │
│  ───────────────                                                            │
│  • List rendering     → Using map() with key prop                          │
│  • Conditional render → Ternary operator chains                            │
│  • Helper functions   → Extract logic outside component                    │
│                                                                              │
│  Tailwind CSS:                                                              │
│  ─────────────                                                              │
│  • Table styling      → w-full, text-left, text-sm                         │
│  • Overflow handling  → overflow-x-auto for horizontal scroll              │
│  • Badge styling      → rounded-full, px-2, py-1                           │
│  • Hover states       → hover:bg-gray-50 for row highlight                 │
│                                                                              │
│  Date/Time in JavaScript:                                                   │
│  ────────────────────────                                                   │
│  • new Date()         → Parse ISO string to Date object                    │
│  • toLocaleString()   → Format using user's locale settings                │
│  • toLocaleDateString → Date only (no time)                                │
│  • toLocaleTimeString → Time only (no date)                                │
│                                                                              │
│  Future Improvements:                                                       │
│  ────────────────────                                                       │
│  • Pagination         → Add page controls and fetch more data              │
│  • Sorting            → Click headers to sort columns                      │
│  • Filtering          → Filter by status, date range, etc.                 │
│  • Search             → Search by payment ID or order ID                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

The frontend dashboard is now complete with three pages:
- **Login** - Authentication
- **Dashboard** - Stats overview
- **Transactions** - Payment history

In the next part, you'll work on **Docker configuration** to containerize all services.

**Continue to:** [part-18-docker-services.md](./part-18-docker-services.md)

---

**End of Sprint 1, Part 17**
