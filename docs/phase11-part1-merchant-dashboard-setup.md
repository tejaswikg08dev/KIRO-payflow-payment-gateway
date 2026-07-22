# Hands-On Guide — Phase 11 Part 1: Merchant Dashboard — Setup

## Goal
- React project created with Vite + TypeScript + Tailwind CSS
- Project structure with pages, components, services folders
- Development server running on port 3000
- Git commit

---

## Create React Project

```cmd
cd payflow-payment-gateway
npm create vite@latest frontend-dashboard -- --template react-ts
cd frontend-dashboard
npm install
npm install tailwindcss @tailwindcss/vite react-router-dom @tanstack/react-query axios recharts react-hook-form
```

---

## Project Structure

```
frontend-dashboard/
├── src/
│   ├── App.tsx               ← Main app with router
│   ├── main.tsx              ← Entry point
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── RegisterPage.tsx
│   │   ├── DashboardPage.tsx ← Stats, charts
│   │   ├── TransactionsPage.tsx
│   │   ├── SettlementsPage.tsx
│   │   ├── ApiKeysPage.tsx
│   │   └── SettingsPage.tsx
│   ├── components/
│   │   ├── Layout.tsx        ← Sidebar + navbar + content area
│   │   ├── Sidebar.tsx
│   │   ├── StatsCard.tsx
│   │   └── DataTable.tsx
│   ├── services/
│   │   ├── api.ts            ← Axios instance with auth header
│   │   ├── authService.ts
│   │   ├── paymentService.ts
│   │   └── merchantService.ts
│   ├── hooks/
│   │   └── useAuth.ts
│   └── types/
│       └── index.ts          ← TypeScript interfaces
├── package.json
├── tsconfig.json
├── vite.config.ts
├── tailwind.config.js
└── index.html
```

---

## Run Development Server

```cmd
cd frontend-dashboard
npm run dev
# Opens: http://localhost:3000
```

---

## Phase 11 continues with Parts 2-8 covering:
- Authentication pages (login/register)
- Dashboard layout (sidebar, stats cards, charts)
- Transactions table with filters
- Settlements page
- API keys management
- Hosted checkout (separate app on port 3001)
- Payment forms (card, UPI, net banking)

---

## Next Step → Phase 11 Parts 2-8 (React pages)
