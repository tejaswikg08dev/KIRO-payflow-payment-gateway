# Phase 11 Part 5 — API Keys Management Page

## Goal
- Build an API keys page to generate, view, and revoke keys
- Show masked keys with copy-to-clipboard functionality
- Implement key regeneration with confirmation dialog

## Key Concept

```
┌─────────────────────────────────────────────────┐
│  API Keys Page                                  │
│ ┌─────────────────────────────────────────────┐ │
│ │ Live Keys                                   │ │
│ │ ┌─────────────────────────────────────────┐ │ │
│ │ │ API Key:  pk_pay_****3f2a  [Copy][👁]  │ │ │
│ │ │ Secret:   sk_pay_****8b1c  [Copy][👁]  │ │ │
│ │ │ Created:  2024-01-10                    │ │ │
│ │ │ [Regenerate]  [Revoke]                  │ │ │
│ │ └─────────────────────────────────────────┘ │ │
│ │                                             │ │
│ │ Test Keys                                   │ │
│ │ ┌─────────────────────────────────────────┐ │ │
│ │ │ API Key:  pk_tst_****9d4e  [Copy][👁]  │ │ │
│ │ └─────────────────────────────────────────┘ │ │
│ └─────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

## Prerequisites
- Phase 11 Part 4 completed
- Backend API key endpoints available at `/api/v1/api-keys`

## Step-by-Step

### 1. API Keys Service (`src/services/apiKeyService.ts`)

```typescript
import apiClient from './apiClient';

export interface ApiKey {
  id: string;
  keyPrefix: string;
  maskedKey: string;
  environment: 'LIVE' | 'TEST';
  createdAt: string;
  lastUsedAt: string | null;
}

export const apiKeyService = {
  async getKeys() {
    return apiClient.get('/api/v1/api-keys');
  },
  async generateKey(environment: string) {
    return apiClient.post('/api/v1/api-keys', { environment });
  },
  async revokeKey(keyId: string) {
    return apiClient.delete(`/api/v1/api-keys/${keyId}`);
  },
  async regenerateKey(keyId: string) {
    return apiClient.post(`/api/v1/api-keys/${keyId}/regenerate`);
  }
};
```

### 2. Confirmation Dialog (`src/components/ConfirmDialog.tsx`)

```tsx
interface Props {
  open: boolean;
  title: string;
  message: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmDialog({ open, title, message, onConfirm, onCancel }: Props) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white p-6 rounded-lg shadow-xl max-w-md">
        <h3 className="text-lg font-bold mb-2">{title}</h3>
        <p className="text-gray-600 mb-6">{message}</p>
        <div className="flex justify-end gap-3">
          <button onClick={onCancel} className="px-4 py-2 border rounded">Cancel</button>
          <button onClick={onConfirm} className="px-4 py-2 bg-red-600 text-white rounded">Confirm</button>
        </div>
      </div>
    </div>
  );
}
```

### 3. API Keys Page (`src/pages/ApiKeysPage.tsx`)

```tsx
import { useState, useEffect } from 'react';
import { apiKeyService, ApiKey } from '../services/apiKeyService';
import ConfirmDialog from '../components/ConfirmDialog';

export default function ApiKeysPage() {
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [newKey, setNewKey] = useState<string | null>(null);
  const [confirmAction, setConfirmAction] = useState<{id: string; action: string} | null>(null);

  useEffect(() => { loadKeys(); }, []);

  const loadKeys = async () => {
    const res = await apiKeyService.getKeys();
    setKeys(res.data.data);
  };

  const handleGenerate = async (env: string) => {
    const res = await apiKeyService.generateKey(env);
    setNewKey(res.data.data.fullKey); // Only shown once
    loadKeys();
  };

  const handleRevoke = async () => {
    if (!confirmAction) return;
    await apiKeyService.revokeKey(confirmAction.id);
    setConfirmAction(null);
    loadKeys();
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">API Keys</h1>
        <button onClick={() => handleGenerate('LIVE')}
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
          Generate New Key
        </button>
      </div>

      {newKey && (
        <div className="bg-yellow-50 border border-yellow-200 p-4 rounded-lg mb-6">
          <p className="font-semibold text-yellow-800">⚠️ Save this key now — it won't be shown again!</p>
          <code className="block mt-2 bg-white p-3 rounded font-mono text-sm">{newKey}</code>
          <button onClick={() => copyToClipboard(newKey)}
            className="mt-2 text-blue-600 text-sm">Copy to clipboard</button>
        </div>
      )}

      <div className="space-y-4">
        {keys.map(key => (
          <div key={key.id} className="bg-white p-6 rounded-lg shadow-sm border">
            <div className="flex justify-between items-center">
              <div>
                <span className={`px-2 py-1 rounded text-xs ${
                  key.environment === 'LIVE' ? 'bg-green-100 text-green-800' : 'bg-gray-100'
                }`}>{key.environment}</span>
                <p className="font-mono mt-2">{key.maskedKey}</p>
                <p className="text-sm text-gray-500 mt-1">Created: {new Date(key.createdAt).toLocaleDateString()}</p>
              </div>
              <div className="flex gap-2">
                <button onClick={() => copyToClipboard(key.maskedKey)}
                  className="px-3 py-1 border rounded text-sm">Copy</button>
                <button onClick={() => setConfirmAction({id: key.id, action: 'revoke'})}
                  className="px-3 py-1 border border-red-300 text-red-600 rounded text-sm">Revoke</button>
              </div>
            </div>
          </div>
        ))}
      </div>

      <ConfirmDialog open={!!confirmAction} title="Revoke API Key"
        message="This action cannot be undone. Any integrations using this key will stop working."
        onConfirm={handleRevoke} onCancel={() => setConfirmAction(null)} />
    </div>
  );
}
```

## Verification

```bash
npm run dev
# Navigate to /dashboard/api-keys
# Click "Generate New Key" — key shown in yellow banner
# Key appears in list with masked format
# Click "Revoke" — confirmation dialog appears
# Confirm revoke — key disappears from list
```

## Git Commit

```bash
git add merchant-portal/src/pages/ApiKeysPage.tsx merchant-portal/src/services/apiKeyService.ts merchant-portal/src/components/ConfirmDialog.tsx
git commit -m "feat(portal): add API keys management with generate/revoke"
```

## Next Step
→ **Phase 11 Part 6** — Hosted checkout separate React app setup
