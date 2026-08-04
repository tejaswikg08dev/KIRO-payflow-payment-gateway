# Phase 11 Part 7 — Checkout Payment Forms

## Goal
- Build a card payment form with Luhn validation and formatting
- Create a UPI payment form with VPA input
- Add net banking form with bank selector

## Key Concept

```
┌─────────────────────────────────────┐
│  PaymentMethodSelector              │
│  ┌───────┐ ┌─────┐ ┌────────────┐  │
│  │ Card  │ │ UPI │ │ Net Banking│  │
│  └───┬───┘ └──┬──┘ └─────┬──────┘  │
│      │        │           │         │
│      ▼        ▼           ▼         │
│  CardForm  UpiForm   BankForm       │
│      │        │           │         │
│      └────────┴───────────┘         │
│              │                      │
│              ▼                      │
│    checkoutService.submitPayment()  │
└─────────────────────────────────────┘
```

## Prerequisites
- Phase 11 Part 6 completed (hosted checkout app scaffolded)

## Step-by-Step

### 1. Payment Method Selector (`src/components/PaymentMethodSelector.tsx`)

```tsx
import { useState } from 'react';
import CardForm from './CardForm';
import UpiForm from './UpiForm';
import BankForm from './BankForm';

interface Props {
  methods: string[];
  sessionId: string;
}

export default function PaymentMethodSelector({ methods, sessionId }: Props) {
  const [active, setActive] = useState(methods[0] || 'CARD');

  const tabs = [
    { id: 'CARD', label: '💳 Card' },
    { id: 'UPI', label: '📱 UPI' },
    { id: 'NET_BANKING', label: '🏦 Net Banking' },
  ].filter(t => methods.includes(t.id));

  return (
    <div>
      <div className="flex border-b mb-6">
        {tabs.map(tab => (
          <button key={tab.id} onClick={() => setActive(tab.id)}
            className={`px-4 py-3 text-sm font-medium border-b-2 ${
              active === tab.id ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500'
            }`}>
            {tab.label}
          </button>
        ))}
      </div>
      {active === 'CARD' && <CardForm sessionId={sessionId} />}
      {active === 'UPI' && <UpiForm sessionId={sessionId} />}
      {active === 'NET_BANKING' && <BankForm sessionId={sessionId} />}
    </div>
  );
}
```

### 2. Card Form with Validation (`src/components/CardForm.tsx`)

```tsx
import { useState, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { checkoutService } from '../services/checkoutService';

function luhnCheck(num: string): boolean {
  const digits = num.replace(/\s/g, '').split('').map(Number);
  let sum = 0;
  for (let i = digits.length - 1; i >= 0; i--) {
    let d = digits[i];
    if ((digits.length - 1 - i) % 2 === 1) { d *= 2; if (d > 9) d -= 9; }
    sum += d;
  }
  return sum % 10 === 0;
}

function formatCard(value: string): string {
  return value.replace(/\s/g, '').replace(/(\d{4})/g, '$1 ').trim();
}

export default function CardForm({ sessionId }: { sessionId: string }) {
  const [card, setCard] = useState({ number: '', expiry: '', cvv: '', name: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const validate = () => {
    const errs: Record<string, string> = {};
    if (!luhnCheck(card.number)) errs.number = 'Invalid card number';
    if (!/^\d{2}\/\d{2}$/.test(card.expiry)) errs.expiry = 'Use MM/YY format';
    if (!/^\d{3,4}$/.test(card.cvv)) errs.cvv = 'Invalid CVV';
    if (card.name.length < 2) errs.name = 'Name required';
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setLoading(true);
    try {
      await checkoutService.submitPayment(sessionId, {
        method: 'CARD',
        cardNumber: card.number.replace(/\s/g, ''),
        expiryMonth: card.expiry.split('/')[0],
        expiryYear: '20' + card.expiry.split('/')[1],
        cvv: card.cvv,
        cardholderName: card.name,
      });
      navigate(`/checkout/${sessionId}/success`);
    } catch {
      navigate(`/checkout/${sessionId}/failure`);
    } finally { setLoading(false); }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <input type="text" placeholder="Card Number" maxLength={19}
          value={card.number} onChange={e => setCard({...card, number: formatCard(e.target.value)})}
          className="w-full p-3 border rounded" />
        {errors.number && <p className="text-red-500 text-xs mt-1">{errors.number}</p>}
      </div>
      <div className="flex gap-4">
        <div className="flex-1">
          <input type="text" placeholder="MM/YY" maxLength={5}
            value={card.expiry} onChange={e => setCard({...card, expiry: e.target.value})}
            className="w-full p-3 border rounded" />
          {errors.expiry && <p className="text-red-500 text-xs mt-1">{errors.expiry}</p>}
        </div>
        <div className="flex-1">
          <input type="password" placeholder="CVV" maxLength={4}
            value={card.cvv} onChange={e => setCard({...card, cvv: e.target.value})}
            className="w-full p-3 border rounded" />
          {errors.cvv && <p className="text-red-500 text-xs mt-1">{errors.cvv}</p>}
        </div>
      </div>
      <input type="text" placeholder="Cardholder Name"
        value={card.name} onChange={e => setCard({...card, name: e.target.value})}
        className="w-full p-3 border rounded" />
      <button type="submit" disabled={loading}
        className="w-full bg-blue-600 text-white p-4 rounded-lg font-semibold hover:bg-blue-700">
        {loading ? 'Processing...' : 'Pay Now'}
      </button>
    </form>
  );
}
```

### 3. UPI Form (`src/components/UpiForm.tsx`)

```tsx
import { useState, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { checkoutService } from '../services/checkoutService';

export default function UpiForm({ sessionId }: { sessionId: string }) {
  const [vpa, setVpa] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!/^[\w.-]+@[\w]+$/.test(vpa)) return;
    setLoading(true);
    try {
      await checkoutService.submitPayment(sessionId, { method: 'UPI', vpa });
      navigate(`/checkout/${sessionId}/success`);
    } catch {
      navigate(`/checkout/${sessionId}/failure`);
    } finally { setLoading(false); }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <input type="text" placeholder="Enter UPI ID (e.g. name@upi)"
        value={vpa} onChange={e => setVpa(e.target.value)}
        className="w-full p-3 border rounded" />
      <button type="submit" disabled={loading}
        className="w-full bg-blue-600 text-white p-4 rounded-lg font-semibold">
        {loading ? 'Verifying...' : 'Pay with UPI'}
      </button>
    </form>
  );
}
```

### 4. Net Banking Form (`src/components/BankForm.tsx`)

```tsx
import { useState, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { checkoutService } from '../services/checkoutService';

const BANKS = [
  { code: 'SBI', name: 'State Bank of India' },
  { code: 'HDFC', name: 'HDFC Bank' },
  { code: 'ICICI', name: 'ICICI Bank' },
  { code: 'AXIS', name: 'Axis Bank' },
  { code: 'KOTAK', name: 'Kotak Mahindra Bank' },
];

export default function BankForm({ sessionId }: { sessionId: string }) {
  const [bank, setBank] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!bank) return;
    setLoading(true);
    try {
      await checkoutService.submitPayment(sessionId, { method: 'NET_BANKING', bankCode: bank });
      navigate(`/checkout/${sessionId}/success`);
    } catch {
      navigate(`/checkout/${sessionId}/failure`);
    } finally { setLoading(false); }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="space-y-2">
        {BANKS.map(b => (
          <label key={b.code} className={`flex items-center p-3 border rounded cursor-pointer ${
            bank === b.code ? 'border-blue-600 bg-blue-50' : ''
          }`}>
            <input type="radio" name="bank" value={b.code}
              onChange={e => setBank(e.target.value)} className="mr-3" />
            {b.name}
          </label>
        ))}
      </div>
      <button type="submit" disabled={!bank || loading}
        className="w-full bg-blue-600 text-white p-4 rounded-lg font-semibold disabled:opacity-50">
        {loading ? 'Redirecting...' : 'Continue to Bank'}
      </button>
    </form>
  );
}
```

## Verification

```bash
cd hosted-checkout && npm run dev
# Open a checkout session URL
# Switch between Card / UPI / Net Banking tabs
# Card: Enter invalid number → Luhn validation error shown
# Card: Enter 4242 4242 4242 4242, 12/25, 123 → submits
# UPI: Enter invalid VPA → form doesn't submit
# Net Banking: Select bank → redirects to success/failure
```

## Git Commit

```bash
git add hosted-checkout/src/components/
git commit -m "feat(checkout): add card, UPI, and net banking payment forms"
```

## Next Step
→ **Phase 11 Part 8** — Success and failure result pages
