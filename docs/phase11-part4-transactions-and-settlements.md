# Phase 11 Part 4 — Transactions & Settlements Pages

## Goal
- Build a transaction list page with search, date range, and status filters
- Create a settlements page showing payout batches
- Implement paginated data fetching with Axios interceptors

## Key Concept

```
┌─────────────────────────────────────────────────┐
│  TransactionsPage                               │
│ ┌─────────────────────────────────────────────┐ │
│ │ Filters: [Status ▼] [Date Range] [Search]  │ │
│ ├─────────────────────────────────────────────┤ │
│ │ ID       | Amount | Status  | Date          │ │
│ │ txn_001  | ₹500   | SUCCESS | 2024-01-15    │ │
│ │ txn_002  | ₹1200  | PENDING | 2024-01-15    │ │
│ │ txn_003  | ₹800   | FAILED  | 2024-01-14    │ │
│ ├─────────────────────────────────────────────┤ │
│ │ < 1 2 3 ... 10 >  (Pagination)             │ │
│ └─────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

## Prerequisites
- Phase 11 Part 3 completed (dashboard layout ready)
- Install date picker: `npm install react-datepicker @types/react-datepicker`

## Step-by-Step

### 1. Create API Client with Interceptors (`src/services/apiClient.ts`)

```typescript
import axios from 'axios';
import { authService } from './authService';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
});

apiClient.interceptors.request.use(config => {
  const token = authService.getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

apiClient.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) authService.logout();
    return Promise.reject(error);
  }
);

export default apiClient;
```

### 2. Transaction Service (`src/services/transactionService.ts`)

```typescript
import apiClient from './apiClient';

export interface TransactionFilter {
  status?: string;
  startDate?: string;
  endDate?: string;
  search?: string;
  page: number;
  size: number;
}

export const transactionService = {
  async getTransactions(filter: TransactionFilter) {
    const params = new URLSearchParams();
    Object.entries(filter).forEach(([k, v]) => { if (v) params.append(k, String(v)); });
    return apiClient.get(`/api/v1/transactions?${params}`);
  },

  async getTransaction(id: string) {
    return apiClient.get(`/api/v1/transactions/${id}`);
  }
};
```

### 3. Transactions Page (`src/pages/TransactionsPage.tsx`)

```tsx
import { useState, useEffect } from 'react';
import { transactionService, TransactionFilter } from '../services/transactionService';

const STATUS_OPTIONS = ['ALL', 'SUCCESS', 'PENDING', 'FAILED', 'REFUNDED'];

export default function TransactionsPage() {
  const [transactions, setTransactions] = useState([]);
  const [filter, setFilter] = useState<TransactionFilter>({ page: 0, size: 20 });
  const [totalPages, setTotalPages] = useState(0);

  useEffect(() => {
    loadTransactions();
  }, [filter]);

  const loadTransactions = async () => {
    try {
      const res = await transactionService.getTransactions(filter);
      setTransactions(res.data.data.content);
      setTotalPages(res.data.data.totalPages);
    } catch (err) { console.error(err); }
  };

  const statusBadge = (status: string) => {
    const colors: Record<string, string> = {
      SUCCESS: 'bg-green-100 text-green-800',
      PENDING: 'bg-yellow-100 text-yellow-800',
      FAILED: 'bg-red-100 text-red-800',
    };
    return <span className={`px-2 py-1 rounded text-xs ${colors[status] || 'bg-gray-100'}`}>{status}</span>;
  };

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Transactions</h1>
      {/* Filters */}
      <div className="flex gap-4 mb-6">
        <select onChange={e => setFilter({...filter, status: e.target.value, page: 0})}
          className="border rounded p-2">
          {STATUS_OPTIONS.map(s => <option key={s} value={s === 'ALL' ? '' : s}>{s}</option>)}
        </select>
        <input type="date" onChange={e => setFilter({...filter, startDate: e.target.value})}
          className="border rounded p-2" />
        <input type="text" placeholder="Search by ID..."
          onChange={e => setFilter({...filter, search: e.target.value, page: 0})}
          className="border rounded p-2 flex-1" />
      </div>
      {/* Table */}
      <div className="bg-white rounded-lg shadow-sm border overflow-hidden">
        <table className="w-full text-left">
          <thead className="bg-gray-50 border-b">
            <tr>
              <th className="p-4">Transaction ID</th>
              <th className="p-4">Amount</th>
              <th className="p-4">Method</th>
              <th className="p-4">Status</th>
              <th className="p-4">Date</th>
            </tr>
          </thead>
          <tbody>
            {transactions.map((txn: any) => (
              <tr key={txn.id} className="border-b hover:bg-gray-50">
                <td className="p-4 font-mono text-sm">{txn.transactionId}</td>
                <td className="p-4">₹{txn.amount}</td>
                <td className="p-4">{txn.paymentMethod}</td>
                <td className="p-4">{statusBadge(txn.status)}</td>
                <td className="p-4">{new Date(txn.createdAt).toLocaleDateString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {/* Pagination */}
      <div className="flex justify-center gap-2 mt-4">
        <button disabled={filter.page === 0}
          onClick={() => setFilter({...filter, page: filter.page - 1})}
          className="px-4 py-2 border rounded disabled:opacity-50">Previous</button>
        <span className="px-4 py-2">Page {filter.page + 1} of {totalPages}</span>
        <button disabled={filter.page >= totalPages - 1}
          onClick={() => setFilter({...filter, page: filter.page + 1})}
          className="px-4 py-2 border rounded disabled:opacity-50">Next</button>
      </div>
    </div>
  );
}
```

### 4. Settlements Page (`src/pages/SettlementsPage.tsx`)

```tsx
import { useState, useEffect } from 'react';
import apiClient from '../services/apiClient';

export default function SettlementsPage() {
  const [settlements, setSettlements] = useState([]);

  useEffect(() => {
    apiClient.get('/api/v1/settlements').then(res => setSettlements(res.data.data.content));
  }, []);

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Settlements</h1>
      <div className="bg-white rounded-lg shadow-sm border overflow-hidden">
        <table className="w-full text-left">
          <thead className="bg-gray-50 border-b">
            <tr>
              <th className="p-4">Settlement ID</th>
              <th className="p-4">Amount</th>
              <th className="p-4">Transactions</th>
              <th className="p-4">Status</th>
              <th className="p-4">Settled On</th>
            </tr>
          </thead>
          <tbody>
            {settlements.map((s: any) => (
              <tr key={s.id} className="border-b hover:bg-gray-50">
                <td className="p-4 font-mono text-sm">{s.settlementId}</td>
                <td className="p-4">₹{s.totalAmount}</td>
                <td className="p-4">{s.transactionCount}</td>
                <td className="p-4">{s.status}</td>
                <td className="p-4">{new Date(s.settledAt).toLocaleDateString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
```

## Verification

```bash
npm run dev
# Navigate to /dashboard/transactions — table and filters render
# Change status filter — API call updates (check Network tab)
# Click pagination — page changes
# Navigate to /dashboard/settlements — settlement list renders
```

## Git Commit

```bash
git add merchant-portal/src/pages/TransactionsPage.tsx merchant-portal/src/pages/SettlementsPage.tsx merchant-portal/src/services
git commit -m "feat(portal): add transactions list with filters and settlements page"
```

## Next Step
→ **Phase 11 Part 5** — API keys management page
