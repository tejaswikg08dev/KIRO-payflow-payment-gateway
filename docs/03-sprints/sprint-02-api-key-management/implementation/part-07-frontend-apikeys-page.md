# Sprint 2, Part 07: Frontend API Keys Page

**Duration:** 2 hours  
**Prerequisites:** Part 06 completed  
**Goal:** Create a React page for managing API keys

---

## 1. Learning Objectives

By the end of this part, you will:
- Create the `ApiKeysPage.tsx` component
- Implement API key generation, listing, and revocation
- Handle the one-time secret key display pattern
- Update App.tsx with the new route

---

## 2. Page Layout

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    API KEYS PAGE LAYOUT                                      │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  API Keys & Webhooks                            ← Back to Dashboard  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  🎉 New API Key Generated                                            │   │
│  │                                                                      │   │
│  │  Public Key:  pk_test_5G8nK2mPq9vX3hJ7...       [Copy]              │   │
│  │  Secret Key:  sk_test_xxxxxxxxxxxxxxxxxxx...   [Copy]               │   │
│  │                                                                      │   │
│  │  ⚠️ Save the secret key now! It will NOT be shown again.           │   │
│  │                                                                      │   │
│  │  [I've saved my secret key]                                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Generate New API Key                                                │   │
│  │                                                                      │   │
│  │  [Generate TEST Key]    [Generate LIVE Key]                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Your API Keys                                                       │   │
│  │                                                                      │   │
│  │  Type   Key Prefix      Status   Created        Last Used   Actions │   │
│  │  ──────────────────────────────────────────────────────────────────│   │
│  │  TEST   sk_test_abc1... ACTIVE   Jan 15, 2024   Never       [Revoke]│   │
│  │  LIVE   sk_live_def4... ACTIVE   Jan 15, 2024   Jan 16      [Revoke]│   │
│  │  TEST   sk_test_xyz9... REVOKED  Jan 10, 2024   Jan 12              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Create ApiKeysPage.tsx

**File:** `frontend-dashboard/src/pages/ApiKeysPage.tsx`

```tsx
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';

interface ApiKey {
  keyId: string;
  keyType: 'TEST' | 'LIVE';
  publicKey: string;
  keyPrefix: string;
  status: 'ACTIVE' | 'REVOKED';
  lastUsedAt: string | null;
  createdAt: string;
}

interface WebhookConfig {
  webhookUrl: string | null;
  webhookSecret: string;
}

interface NewKeyResult {
  key_id: string;
  key_type: string;
  public_key: string;
  secret_key: string;
  note: string;
}

function ApiKeysPage() {
  const navigate = useNavigate();
  const [apiKeys, setApiKeys] = useState<ApiKey[]>([]);
  const [webhookConfig, setWebhookConfig] = useState<WebhookConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [newKey, setNewKey] = useState<NewKeyResult | null>(null);
  const [webhookUrl, setWebhookUrl] = useState('');
  const [showWebhookSecret, setShowWebhookSecret] = useState(false);
  const [merchantId, setMerchantId] = useState<string>('');

  useEffect(() => {
    const fetchData = async () => {
      try {
        // Get merchant ID from token claims or profile
        const profileResponse = await api.get('/v1/auth/profile');
        const merchId = profileResponse.data.data.merchantId;
        setMerchantId(merchId);

        // Fetch API keys
        const keysResponse = await api.get(`/v1/merchants/${merchId}/api-keys`);
        setApiKeys(keysResponse.data.data);

        // Fetch webhook config
        const webhookResponse = await api.get(`/v1/merchants/${merchId}/webhook`);
        setWebhookConfig(webhookResponse.data.data);
        setWebhookUrl(webhookResponse.data.data.webhookUrl || '');
      } catch {
        // If unauthorized, api interceptor will redirect to login
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const generateKey = async (keyType: 'TEST' | 'LIVE') => {
    try {
      const response = await api.post(
        `/v1/merchants/${merchantId}/api-keys?keyType=${keyType}`
      );
      setNewKey(response.data.data);
      
      // Refresh the keys list
      const keysResponse = await api.get(`/v1/merchants/${merchantId}/api-keys`);
      setApiKeys(keysResponse.data.data);
    } catch (error) {
      console.error('Failed to generate key:', error);
      alert('Failed to generate API key. Please try again.');
    }
  };

  const revokeKey = async (keyId: string) => {
    if (!confirm('Are you sure you want to revoke this API key? This cannot be undone.')) {
      return;
    }
    
    try {
      await api.delete(`/v1/merchants/${merchantId}/api-keys/${keyId}`);
      
      // Refresh the keys list
      const keysResponse = await api.get(`/v1/merchants/${merchantId}/api-keys`);
      setApiKeys(keysResponse.data.data);
    } catch (error) {
      console.error('Failed to revoke key:', error);
      alert('Failed to revoke API key. Please try again.');
    }
  };

  const updateWebhook = async () => {
    try {
      const response = await api.put(`/v1/merchants/${merchantId}/webhook`, {
        webhookUrl: webhookUrl,
      });
      setWebhookConfig(response.data.data);
      setShowWebhookSecret(true);
      alert('Webhook URL updated successfully. A new secret has been generated.');
    } catch (error) {
      console.error('Failed to update webhook:', error);
      alert('Failed to update webhook. Please try again.');
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    alert('Copied to clipboard!');
  };

  const formatDate = (dateString: string | null) => {
    if (!dateString) return 'Never';
    return new Date(dateString).toLocaleDateString('en-IN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-gray-500">Loading API keys...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="border-b bg-white px-6 py-4 shadow-sm">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-bold text-gray-800">API Keys & Webhooks</h1>
          <button
            onClick={() => navigate('/dashboard')}
            className="rounded px-4 py-2 text-sm text-gray-600 hover:bg-gray-100"
          >
            ← Back to Dashboard
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-4xl p-6">
        {/* New Key Modal */}
        {newKey && (
          <div className="mb-6 rounded-lg border-2 border-green-500 bg-green-50 p-6">
            <h3 className="text-lg font-bold text-green-800">
              🎉 New API Key Generated
            </h3>
            <p className="mt-2 text-sm text-green-700">
              Save the secret key now. It will NOT be shown again!
            </p>
            <div className="mt-4 space-y-3">
              <div>
                <label className="text-sm font-medium text-gray-600">Public Key</label>
                <div className="flex items-center gap-2">
                  <code className="flex-1 rounded bg-white p-2 font-mono text-sm">
                    {newKey.public_key}
                  </code>
                  <button
                    onClick={() => copyToClipboard(newKey.public_key)}
                    className="rounded bg-gray-200 px-3 py-2 text-sm hover:bg-gray-300"
                  >
                    Copy
                  </button>
                </div>
              </div>
              <div>
                <label className="text-sm font-medium text-gray-600">
                  Secret Key (copy now!)
                </label>
                <div className="flex items-center gap-2">
                  <code className="flex-1 rounded bg-yellow-100 p-2 font-mono text-sm text-yellow-800">
                    {newKey.secret_key}
                  </code>
                  <button
                    onClick={() => copyToClipboard(newKey.secret_key)}
                    className="rounded bg-yellow-500 px-3 py-2 text-sm text-white hover:bg-yellow-600"
                  >
                    Copy
                  </button>
                </div>
              </div>
            </div>
            <button
              onClick={() => setNewKey(null)}
              className="mt-4 rounded bg-green-600 px-4 py-2 text-white hover:bg-green-700"
            >
              I've saved my secret key
            </button>
          </div>
        )}

        {/* Generate Keys Section */}
        <section className="mb-8 rounded-lg bg-white p-6 shadow">
          <h2 className="text-lg font-semibold text-gray-800">Generate New API Key</h2>
          <p className="mt-1 text-sm text-gray-500">
            Create API keys to authenticate your API requests.
          </p>
          <div className="mt-4 flex gap-4">
            <button
              onClick={() => generateKey('TEST')}
              className="rounded border border-blue-500 bg-blue-50 px-4 py-2 text-blue-700 hover:bg-blue-100"
            >
              Generate TEST Key
            </button>
            <button
              onClick={() => generateKey('LIVE')}
              className="rounded border border-green-500 bg-green-50 px-4 py-2 text-green-700 hover:bg-green-100"
            >
              Generate LIVE Key
            </button>
          </div>
        </section>

        {/* API Keys List */}
        <section className="mb-8 rounded-lg bg-white p-6 shadow">
          <h2 className="text-lg font-semibold text-gray-800">Your API Keys</h2>
          <div className="mt-4">
            {apiKeys.length === 0 ? (
              <p className="text-gray-500">No API keys yet. Generate one above.</p>
            ) : (
              <table className="w-full">
                <thead>
                  <tr className="border-b text-left text-sm text-gray-500">
                    <th className="pb-2">Type</th>
                    <th className="pb-2">Key Prefix</th>
                    <th className="pb-2">Status</th>
                    <th className="pb-2">Created</th>
                    <th className="pb-2">Last Used</th>
                    <th className="pb-2">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {apiKeys.map((key) => (
                    <tr key={key.keyId} className="border-b">
                      <td className="py-3">
                        <span
                          className={`inline-block rounded px-2 py-1 text-xs font-medium ${
                            key.keyType === 'LIVE'
                              ? 'bg-green-100 text-green-800'
                              : 'bg-blue-100 text-blue-800'
                          }`}
                        >
                          {key.keyType}
                        </span>
                      </td>
                      <td className="py-3 font-mono text-sm">{key.keyPrefix}...</td>
                      <td className="py-3">
                        <span
                          className={`inline-block rounded px-2 py-1 text-xs ${
                            key.status === 'ACTIVE'
                              ? 'bg-green-100 text-green-800'
                              : 'bg-red-100 text-red-800'
                          }`}
                        >
                          {key.status}
                        </span>
                      </td>
                      <td className="py-3 text-sm text-gray-500">
                        {formatDate(key.createdAt)}
                      </td>
                      <td className="py-3 text-sm text-gray-500">
                        {formatDate(key.lastUsedAt)}
                      </td>
                      <td className="py-3">
                        {key.status === 'ACTIVE' && (
                          <button
                            onClick={() => revokeKey(key.keyId)}
                            className="rounded bg-red-100 px-3 py-1 text-sm text-red-700 hover:bg-red-200"
                          >
                            Revoke
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </section>

        {/* Webhook Configuration */}
        <section className="rounded-lg bg-white p-6 shadow">
          <h2 className="text-lg font-semibold text-gray-800">Webhook Configuration</h2>
          <p className="mt-1 text-sm text-gray-500">
            Configure where PayFlow sends payment event notifications.
          </p>
          
          <div className="mt-4">
            <label className="text-sm font-medium text-gray-600">Webhook URL</label>
            <div className="mt-1 flex gap-2">
              <input
                type="url"
                value={webhookUrl}
                onChange={(e) => setWebhookUrl(e.target.value)}
                placeholder="https://api.yoursite.com/webhooks/payflow"
                className="flex-1 rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none"
              />
              <button
                onClick={updateWebhook}
                className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
              >
                Save
              </button>
            </div>
          </div>

          {webhookConfig && (
            <div className="mt-4">
              <label className="text-sm font-medium text-gray-600">
                Webhook Secret
              </label>
              <p className="text-xs text-gray-500">
                Use this to verify that webhooks came from PayFlow.
              </p>
              <div className="mt-1 flex items-center gap-2">
                <code className="flex-1 rounded bg-gray-100 p-2 font-mono text-sm">
                  {showWebhookSecret
                    ? webhookConfig.webhookSecret
                    : '••••••••••••••••••••••••••••••••'}
                </code>
                <button
                  onClick={() => setShowWebhookSecret(!showWebhookSecret)}
                  className="rounded bg-gray-200 px-3 py-2 text-sm hover:bg-gray-300"
                >
                  {showWebhookSecret ? 'Hide' : 'Show'}
                </button>
                {showWebhookSecret && (
                  <button
                    onClick={() => copyToClipboard(webhookConfig.webhookSecret)}
                    className="rounded bg-gray-200 px-3 py-2 text-sm hover:bg-gray-300"
                  >
                    Copy
                  </button>
                )}
              </div>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}

export default ApiKeysPage;
```

---

## 4. Update App.tsx

**File:** `frontend-dashboard/src/App.tsx`

```tsx
import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import TransactionsPage from './pages/TransactionsPage';
import ApiKeysPage from './pages/ApiKeysPage';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/dashboard" element={<DashboardPage />} />
      <Route path="/transactions" element={<TransactionsPage />} />
      <Route path="/api-keys" element={<ApiKeysPage />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}

export default App;
```

---

## 5. Update Dashboard with Link

**File:** `frontend-dashboard/src/pages/DashboardPage.tsx`

Update the Quick Links section:

```tsx
{/* Quick Links */}
<div className="mt-8 flex gap-4">
  <button
    onClick={() => navigate('/transactions')}
    className="rounded bg-primary px-4 py-2 text-white hover:bg-primary-dark"
  >
    View Transactions
  </button>
  <button
    onClick={() => navigate('/api-keys')}
    className="rounded border border-gray-300 px-4 py-2 text-gray-700 hover:bg-gray-100"
  >
    Manage API Keys
  </button>
</div>
```

---

## 6. Testing

### 6.1 Start Frontend

```powershell
cd frontend-dashboard
npm run dev
```

### 6.2 Test Flow

1. Navigate to `http://localhost:3000/login`
2. Login with merchant credentials
3. Click "Manage API Keys" on dashboard
4. Generate TEST key → Verify secret shown with yellow highlight
5. Copy secret key → Verify clipboard works
6. Click "I've saved my secret key" → Card disappears
7. Generate LIVE key → Verify appears in table
8. Revoke TEST key → Confirm dialog → Status changes to REVOKED
9. Update webhook URL → New secret generated

---

## 7. Key Takeaways

| Concept | Implementation |
|---------|----------------|
| One-time secret | Yellow highlight, dismiss button |
| Revocation | Confirmation dialog before action |
| Webhook secret | Hidden by default, toggle to show |
| Date formatting | Indian locale (en-IN) |

---

## 8. Next Steps

**Continue to:** [part-08-frontend-settings-page.md](./part-08-frontend-settings-page.md)

In the next part, you'll create the Settings page for webhook and other configurations.

---

**End of Sprint 2, Part 07**
