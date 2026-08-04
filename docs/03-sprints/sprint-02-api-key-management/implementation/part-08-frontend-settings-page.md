# Sprint 2, Part 08: Frontend Settings Page

**Duration:** 30 minutes  
**Prerequisites:** Part 07 completed  
**Goal:** The webhook configuration is already included in ApiKeysPage - this part covers future settings enhancements

---

## 1. Current Implementation

In Part 07, we included webhook configuration directly in the `ApiKeysPage.tsx`. This is the standard approach for Sprint 2.

---

## 2. Current Settings in ApiKeysPage

```tsx
{/* Webhook Configuration - Already in ApiKeysPage.tsx */}
<section className="rounded-lg bg-white p-6 shadow">
  <h2 className="text-lg font-semibold text-gray-800">Webhook Configuration</h2>
  {/* Webhook URL input */}
  {/* Webhook secret display */}
</section>
```

---

## 3. Future Settings Page (Sprint 4+)

In future sprints, you may want a dedicated Settings page with:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    FUTURE SETTINGS PAGE                                      │
│                                                                              │
│  Settings                                              ← Back to Dashboard   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Business Information                                     [Edit]     │   │
│  │                                                                      │   │
│  │  Business Name:     Awesome Store Pvt Ltd                           │   │
│  │  Business Type:     COMPANY                                         │   │
│  │  Registration No:   CIN123456789                                    │   │
│  │  GST Number:        27ABCDE1234F1Z5                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Bank Account                                             [Edit]     │   │
│  │                                                                      │   │
│  │  Account Number:    ****1234                                        │   │
│  │  IFSC Code:         HDFC0001234                                     │   │
│  │  Account Holder:    Awesome Store Pvt Ltd                           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Fees & Settlement                                                   │   │
│  │                                                                      │   │
│  │  MDR Rate:          2.00%                                           │   │
│  │  Settlement:        T+2 (2 business days)                           │   │
│  │                                                                      │   │
│  │  📞 Contact support to request rate changes                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Webhooks                                                 [Edit]     │   │
│  │                                                                      │   │
│  │  URL:     https://api.mystore.com/webhooks                          │   │
│  │  Secret:  ••••••••••••••••••••    [Show] [Copy]                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. File Structure Summary

For Sprint 2, the frontend structure is:

```
frontend-dashboard/src/
├── App.tsx                   # Routes: /login, /dashboard, /transactions, /api-keys
├── pages/
│   ├── LoginPage.tsx         # From Sprint 1
│   ├── DashboardPage.tsx     # From Sprint 1 + "Manage API Keys" button
│   ├── TransactionsPage.tsx  # From Sprint 1
│   └── ApiKeysPage.tsx       # NEW: API keys + webhook config
└── services/
    └── api.ts                # From Sprint 1
```

---

## 5. Key Takeaways

| Decision | Rationale |
|----------|-----------|
| No separate Settings page | Webhook config fits naturally with API keys |
| Future enhancement | Settings page can be added in Sprint 4+ |
| Current scope | Keep Sprint 2 focused on API key management |

---

## 6. Next Steps

**Continue to:** [part-09-docker-update.md](./part-09-docker-update.md)

In the next part, you'll verify Docker configuration for Sprint 2 changes.

---

**End of Sprint 2, Part 08**
