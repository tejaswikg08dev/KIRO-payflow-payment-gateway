# Phase 11 Part 6 — Hosted Checkout App Setup

## Goal
- Create a separate React app for hosted checkout (customer-facing)
- Set up URL-based session validation (checkout links)
- Build the checkout layout with merchant branding

## Key Concept

```
┌──────────────────────────────────────────────────────────┐
│  Checkout Flow                                           │
│                                                          │
│  Merchant Backend                    PayFlow             │
│       │                                 │                │
│       ├── POST /checkout/sessions ─────►│                │
│       │                                 │                │
│       │◄── { checkoutUrl } ─────────────┤                │
│       │                                 │                │
│  Customer Browser                       │                │
│       ├── GET /checkout/{sessionId} ───►│                │
│       │                                 │                │
│       │◄── Hosted Checkout Page ────────┤                │
│       │    (card form, UPI, etc.)       │                │
└──────────────────────────────────────────────────────────┘
```

## Prerequisites
- Node.js 18+, npm installed
- Payment service running with checkout session endpoints

## Step-by-Step

### 1. Scaffold the Checkout App

```bash
cd payflow-payment-gateway
npm create vite@latest hosted-checkout -- --template react-ts
cd hosted-checkout
npm install react-router-dom axios tailwindcss postcss autoprefixer
npx tailwindcss init -p
```

### 2. Configure Tailwind (`tailwind.config.js`)

```javascript
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: { 50: '#eff6ff', 600: '#2563eb', 700: '#1d4ed8' }
      }
    },
  },
  plugins: [],
};
```

### 3. Checkout Session Service (`src/services/checkoutService.ts`)

```typescript
import axios from 'axios';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export interface CheckoutSession {
  sessionId: string;
  merchantName: string;
  merchantLogo: string | null;
  amount: number;
  currency: string;
  description: string;
  allowedMethods: string[];
  expiresAt: string;
  status: 'ACTIVE' | 'EXPIRED' | 'COMPLETED';
}

export const checkoutService = {
  async getSession(sessionId: string): Promise<CheckoutSession> {
    const res = await axios.get(`${API_URL}/api/v1/checkout/sessions/${sessionId}`);
    return res.data.data;
  },

  async submitPayment(sessionId: string, paymentData: any) {
    return axios.post(`${API_URL}/api/v1/checkout/sessions/${sessionId}/pay`, paymentData);
  }
};
```

### 4. Checkout Layout (`src/layouts/CheckoutLayout.tsx`)

```tsx
import { Outlet } from 'react-router-dom';

interface Props {
  merchantName?: string;
  amount?: number;
  currency?: string;
}

export default function CheckoutLayout({ merchantName, amount, currency }: Props) {
  return (
    <div className="min-h-screen bg-gray-100 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-lg w-full max-w-md overflow-hidden">
        {/* Header */}
        <div className="bg-blue-600 text-white p-6">
          <p className="text-sm opacity-80">Paying to</p>
          <h1 className="text-lg font-bold">{merchantName || 'Merchant'}</h1>
          {amount && (
            <p className="text-3xl font-bold mt-2">
              {currency === 'INR' ? '₹' : '$'}{amount.toLocaleString()}
            </p>
          )}
        </div>
        {/* Content */}
        <div className="p-6">
          <Outlet />
        </div>
        {/* Footer */}
        <div className="border-t p-4 text-center text-xs text-gray-400">
          Secured by PayFlow • 256-bit encryption
        </div>
      </div>
    </div>
  );
}
```

### 5. Session Loader Page (`src/pages/CheckoutPage.tsx`)

```tsx
import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { checkoutService, CheckoutSession } from '../services/checkoutService';
import CheckoutLayout from '../layouts/CheckoutLayout';
import PaymentMethodSelector from '../components/PaymentMethodSelector';

export default function CheckoutPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const [session, setSession] = useState<CheckoutSession | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!sessionId) return;
    checkoutService.getSession(sessionId)
      .then(setSession)
      .catch(() => setError('Invalid or expired checkout link'));
  }, [sessionId]);

  if (error) return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-center">
        <h1 className="text-2xl font-bold text-red-600">Link Expired</h1>
        <p className="mt-2 text-gray-600">{error}</p>
      </div>
    </div>
  );

  if (!session) return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="animate-spin h-8 w-8 border-4 border-blue-600 border-t-transparent rounded-full" />
    </div>
  );

  return (
    <CheckoutLayout merchantName={session.merchantName} amount={session.amount} currency={session.currency}>
      <PaymentMethodSelector methods={session.allowedMethods} sessionId={session.sessionId} />
    </CheckoutLayout>
  );
}
```

### 6. App Routes (`src/App.tsx`)

```tsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import CheckoutPage from './pages/CheckoutPage';
import SuccessPage from './pages/SuccessPage';
import FailurePage from './pages/FailurePage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/checkout/:sessionId" element={<CheckoutPage />} />
        <Route path="/checkout/:sessionId/success" element={<SuccessPage />} />
        <Route path="/checkout/:sessionId/failure" element={<FailurePage />} />
      </Routes>
    </BrowserRouter>
  );
}
export default App;
```

## Verification

```bash
cd hosted-checkout
npm run dev
# Open http://localhost:5174/checkout/test-session-123
# Should show loading → then session data or error
# Layout should show merchant name, amount, and PayFlow branding
```

## Git Commit

```bash
git add hosted-checkout/
git commit -m "feat(checkout): scaffold hosted checkout React app with session loading"
```

## Next Step
→ **Phase 11 Part 7** — Payment forms (card, UPI, net banking)
