# Phase 11 Part 3 — Dashboard Layout & Overview

## Goal
- Create a reusable layout component with sidebar navigation
- Build stats cards showing transaction metrics
- Add a revenue chart using Recharts

## Key Concept

```
┌──────────────────────────────────────────────────┐
│  DashboardLayout                                 │
│ ┌────────┐ ┌──────────────────────────────────┐  │
│ │Sidebar │ │  Header (user menu, logout)      │  │
│ │        │ ├──────────────────────────────────┤  │
│ │ • Home │ │                                  │  │
│ │ • Txns │ │  <Outlet /> (child routes)       │  │
│ │ • Keys │ │                                  │  │
│ │ • Sett │ │                                  │  │
│ └────────┘ └──────────────────────────────────┘  │
└──────────────────────────────────────────────────┘
```

## Prerequisites
- Phase 11 Part 2 completed (auth pages working)
- Install chart library: `npm install recharts`

## Step-by-Step

### 1. Create Sidebar (`src/components/Sidebar.tsx`)

```tsx
import { NavLink } from 'react-router-dom';
import { Home, CreditCard, Key, Settings, LogOut } from 'lucide-react';
import { authService } from '../services/authService';

const navItems = [
  { path: '/dashboard', icon: Home, label: 'Overview' },
  { path: '/dashboard/transactions', icon: CreditCard, label: 'Transactions' },
  { path: '/dashboard/api-keys', icon: Key, label: 'API Keys' },
  { path: '/dashboard/settings', icon: Settings, label: 'Settings' },
];

export default function Sidebar() {
  return (
    <aside className="w-64 bg-gray-900 text-white min-h-screen p-4">
      <h2 className="text-xl font-bold mb-8 px-4">PayFlow</h2>
      <nav className="space-y-2">
        {navItems.map(item => (
          <NavLink key={item.path} to={item.path}
            className={({isActive}) =>
              `flex items-center gap-3 px-4 py-3 rounded-lg ${isActive ? 'bg-blue-600' : 'hover:bg-gray-800'}`
            }>
            <item.icon size={20} />
            {item.label}
          </NavLink>
        ))}
      </nav>
      <button onClick={authService.logout}
        className="flex items-center gap-3 px-4 py-3 mt-8 hover:bg-gray-800 rounded-lg w-full">
        <LogOut size={20} /> Logout
      </button>
    </aside>
  );
}
```

### 2. Create Dashboard Layout (`src/layouts/DashboardLayout.tsx`)

```tsx
import { Outlet, Navigate } from 'react-router-dom';
import Sidebar from '../components/Sidebar';
import { authService } from '../services/authService';

export default function DashboardLayout() {
  if (!authService.getToken()) {
    return <Navigate to="/login" />;
  }

  return (
    <div className="flex">
      <Sidebar />
      <main className="flex-1 bg-gray-50 p-8 min-h-screen">
        <Outlet />
      </main>
    </div>
  );
}
```

### 3. Create Stats Card (`src/components/StatsCard.tsx`)

```tsx
interface StatsCardProps {
  title: string;
  value: string;
  change: string;
  positive: boolean;
}

export default function StatsCard({ title, value, change, positive }: StatsCardProps) {
  return (
    <div className="bg-white p-6 rounded-lg shadow-sm border">
      <p className="text-sm text-gray-500">{title}</p>
      <p className="text-2xl font-bold mt-2">{value}</p>
      <p className={`text-sm mt-2 ${positive ? 'text-green-600' : 'text-red-600'}`}>
        {positive ? '↑' : '↓'} {change} vs last month
      </p>
    </div>
  );
}
```

### 4. Create Overview Page (`src/pages/OverviewPage.tsx`)

```tsx
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import StatsCard from '../components/StatsCard';

const chartData = [
  { day: 'Mon', revenue: 4200 }, { day: 'Tue', revenue: 5800 },
  { day: 'Wed', revenue: 3900 }, { day: 'Thu', revenue: 7100 },
  { day: 'Fri', revenue: 6500 }, { day: 'Sat', revenue: 4800 },
  { day: 'Sun', revenue: 5200 },
];

export default function OverviewPage() {
  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Dashboard Overview</h1>
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
        <StatsCard title="Total Revenue" value="₹4,52,300" change="12%" positive={true} />
        <StatsCard title="Transactions" value="1,247" change="8%" positive={true} />
        <StatsCard title="Success Rate" value="96.4%" change="2.1%" positive={true} />
        <StatsCard title="Avg. Value" value="₹362" change="3%" positive={false} />
      </div>
      <div className="bg-white p-6 rounded-lg shadow-sm border">
        <h2 className="text-lg font-semibold mb-4">Revenue (Last 7 Days)</h2>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="day" />
            <YAxis />
            <Tooltip />
            <Line type="monotone" dataKey="revenue" stroke="#2563eb" strokeWidth={2} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
```

### 5. Update Routes

```tsx
// In App.tsx — add dashboard routes
<Route path="/dashboard" element={<DashboardLayout />}>
  <Route index element={<OverviewPage />} />
  <Route path="transactions" element={<TransactionsPage />} />
  <Route path="api-keys" element={<ApiKeysPage />} />
</Route>
```

## Verification

```bash
npm run dev
# Login → should redirect to /dashboard
# Sidebar navigation should highlight active page
# Stats cards and chart should render with mock data
```

## Git Commit

```bash
git add merchant-portal/src/components merchant-portal/src/layouts merchant-portal/src/pages/OverviewPage.tsx
git commit -m "feat(portal): add dashboard layout with sidebar, stats, and chart"
```

## Next Step
→ **Phase 11 Part 4** — Transactions list with filters and settlement page
