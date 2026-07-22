# Phase 11 Part 8 — Checkout Success & Failure Pages

## Goal
- Create a success page showing payment confirmation details
- Create a failure page with retry option
- Add auto-redirect back to merchant after timeout

## Key Concept

```
┌─────────────────────────────────────────┐
│  Payment Result Flow                    │
│                                         │
│  submitPayment()                        │
│       │                                 │
│       ├── 200 OK ──► /success           │
│       │              • Show ✓           │
│       │              • Transaction ID   │
│       │              • Auto-redirect    │
│       │                                 │
│       └── Error ───► /failure           │
│                      • Show ✗           │
│                      • Error reason     │
│                      • Retry button     │
└─────────────────────────────────────────┘
```

## Prerequisites
- Phase 11 Part 7 completed (payment forms submitting)

## Step-by-Step

### 1. Success Page (`src/pages/SuccessPage.tsx`)

```tsx
import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';

export default function SuccessPage() {
  const { sessionId } = useParams();
  const [searchParams] = useSearchParams();
  const [countdown, setCountdown] = useState(10);

  const transactionId = searchParams.get('txnId') || 'TXN_' + Date.now();
  const returnUrl = searchParams.get('returnUrl');

  useEffect(() => {
    if (!returnUrl) return;
    const timer = setInterval(() => {
      setCountdown(prev => {
        if (prev <= 1) {
          clearInterval(timer);
          window.location.href = returnUrl;
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [returnUrl]);

  return (
    <div className="min-h-screen bg-gray-100 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-lg w-full max-w-md p-8 text-center">
        {/* Success Icon */}
        <div className="w-20 h-20 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-6">
          <svg className="w-10 h-10 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
          </svg>
        </div>

        <h1 className="text-2xl font-bold text-green-800 mb-2">Payment Successful!</h1>
        <p className="text-gray-600 mb-6">Your payment has been processed successfully.</p>

        {/* Transaction Details */}
        <div className="bg-gray-50 rounded-lg p-4 mb-6 text-left">
          <div className="flex justify-between py-2 border-b">
            <span className="text-gray-500">Transaction ID</span>
            <span className="font-mono text-sm">{transactionId}</span>
          </div>
          <div className="flex justify-between py-2 border-b">
            <span className="text-gray-500">Status</span>
            <span className="text-green-600 font-semibold">Completed</span>
          </div>
          <div className="flex justify-between py-2">
            <span className="text-gray-500">Session</span>
            <span className="font-mono text-sm">{sessionId?.slice(0, 12)}...</span>
          </div>
        </div>

        {returnUrl && (
          <p className="text-sm text-gray-500">
            Redirecting back to merchant in <strong>{countdown}s</strong>
          </p>
        )}

        {returnUrl && (
          <a href={returnUrl}
            className="inline-block mt-4 text-blue-600 hover:underline text-sm">
            Return to merchant now →
          </a>
        )}
      </div>
    </div>
  );
}
```

### 2. Failure Page (`src/pages/FailurePage.tsx`)

```tsx
import { useParams, useSearchParams, useNavigate } from 'react-router-dom';

export default function FailurePage() {
  const { sessionId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const errorCode = searchParams.get('error') || 'PAYMENT_FAILED';
  const errorMessage = searchParams.get('message') || 'Your payment could not be processed.';

  const errorMessages: Record<string, string> = {
    PAYMENT_FAILED: 'The payment was declined by the bank.',
    TIMEOUT: 'The payment request timed out. Please try again.',
    INSUFFICIENT_FUNDS: 'Insufficient funds in your account.',
    CARD_EXPIRED: 'The card has expired. Please use a different card.',
    SESSION_EXPIRED: 'This checkout session has expired.',
  };

  const handleRetry = () => {
    navigate(`/checkout/${sessionId}`);
  };

  return (
    <div className="min-h-screen bg-gray-100 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-lg w-full max-w-md p-8 text-center">
        {/* Failure Icon */}
        <div className="w-20 h-20 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-6">
          <svg className="w-10 h-10 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </div>

        <h1 className="text-2xl font-bold text-red-800 mb-2">Payment Failed</h1>
        <p className="text-gray-600 mb-6">{errorMessages[errorCode] || errorMessage}</p>

        {/* Error Details */}
        <div className="bg-red-50 rounded-lg p-4 mb-6 text-left">
          <div className="flex justify-between py-2">
            <span className="text-gray-500">Error Code</span>
            <span className="font-mono text-sm text-red-700">{errorCode}</span>
          </div>
        </div>

        {/* Actions */}
        <div className="space-y-3">
          <button onClick={handleRetry}
            className="w-full bg-blue-600 text-white p-4 rounded-lg font-semibold hover:bg-blue-700">
            Try Again
          </button>
          <button onClick={() => window.history.back()}
            className="w-full border p-4 rounded-lg font-semibold text-gray-700 hover:bg-gray-50">
            Go Back
          </button>
        </div>
      </div>
    </div>
  );
}
```

### 3. Update Card Form to Navigate with Params

```tsx
// In CardForm.tsx handleSubmit success block:
const result = await checkoutService.submitPayment(sessionId, payload);
navigate(`/checkout/${sessionId}/success?txnId=${result.data.data.transactionId}&returnUrl=${encodeURIComponent(result.data.data.returnUrl || '')}`);

// In catch block:
navigate(`/checkout/${sessionId}/failure?error=${err.response?.data?.code || 'PAYMENT_FAILED'}`);
```

## Verification

```bash
cd hosted-checkout && npm run dev
# After successful payment → redirects to success page
# Shows green checkmark, transaction ID, countdown timer
# After failed payment → redirects to failure page
# Shows red X, error message, retry button
# Click "Try Again" → goes back to checkout form
# Countdown reaches 0 → redirects to merchant returnUrl
```

## Git Commit

```bash
git add hosted-checkout/src/pages/SuccessPage.tsx hosted-checkout/src/pages/FailurePage.tsx
git commit -m "feat(checkout): add success and failure result pages with auto-redirect"
```

## Next Step
→ **Phase 12 Part 2** — Unit tests for the service layer with JUnit 5 and Mockito
