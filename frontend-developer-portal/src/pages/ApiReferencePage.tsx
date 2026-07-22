const endpoints = [
  {
    category: 'Orders',
    description: 'Create and manage payment orders.',
    items: [
      { method: 'POST', path: '/v1/orders', description: 'Create a new payment order' },
      { method: 'GET', path: '/v1/orders', description: 'List all orders (paginated)' },
      { method: 'GET', path: '/v1/orders/:id', description: 'Retrieve a specific order' },
      { method: 'PATCH', path: '/v1/orders/:id/cancel', description: 'Cancel a pending order' },
    ],
  },
  {
    category: 'Payments',
    description: 'Process and manage individual payment transactions.',
    items: [
      { method: 'POST', path: '/v1/payments', description: 'Process a payment' },
      { method: 'GET', path: '/v1/payments/:id', description: 'Retrieve payment details' },
      { method: 'POST', path: '/v1/payments/:id/refund', description: 'Refund a payment (full or partial)' },
      { method: 'POST', path: '/v1/payments/:id/capture', description: 'Capture an authorized payment' },
    ],
  },
  {
    category: 'Merchants',
    description: 'Manage merchant accounts and configuration.',
    items: [
      { method: 'POST', path: '/v1/merchants', description: 'Onboard a new merchant' },
      { method: 'GET', path: '/v1/merchants/:id', description: 'Retrieve merchant details' },
      { method: 'PATCH', path: '/v1/merchants/:id', description: 'Update merchant configuration' },
      { method: 'GET', path: '/v1/merchants/:id/balance', description: 'Get merchant account balance' },
    ],
  },
  {
    category: 'Settlements',
    description: 'View settlement batches and payout history.',
    items: [
      { method: 'GET', path: '/v1/settlements', description: 'List all settlements' },
      { method: 'GET', path: '/v1/settlements/:id', description: 'Retrieve settlement details' },
      { method: 'GET', path: '/v1/settlements/:id/transactions', description: 'List transactions in a settlement' },
    ],
  },
  {
    category: 'Webhooks',
    description: 'Configure webhook endpoints for event notifications.',
    items: [
      { method: 'POST', path: '/v1/webhooks', description: 'Register a webhook endpoint' },
      { method: 'GET', path: '/v1/webhooks', description: 'List registered webhooks' },
      { method: 'DELETE', path: '/v1/webhooks/:id', description: 'Delete a webhook endpoint' },
      { method: 'POST', path: '/v1/webhooks/:id/test', description: 'Send a test event to a webhook' },
    ],
  },
];

function methodColor(method: string): string {
  switch (method) {
    case 'GET':
      return 'bg-green-100 text-green-700';
    case 'POST':
      return 'bg-blue-100 text-blue-700';
    case 'PATCH':
      return 'bg-yellow-100 text-yellow-700';
    case 'DELETE':
      return 'bg-red-100 text-red-700';
    default:
      return 'bg-slate-100 text-slate-700';
  }
}

function ApiReferencePage() {
  return (
    <div>
      <h1 className="text-3xl font-bold text-slate-900 mb-2">API Reference</h1>
      <p className="text-slate-600 mb-4">
        Complete reference for all PayFlow API endpoints. All requests require authentication via the{' '}
        <code className="bg-slate-100 px-1.5 py-0.5 rounded text-sm">X-Api-Key</code> header.
      </p>
      <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 mb-10">
        <p className="text-sm text-slate-600">
          <strong>Base URL:</strong>{' '}
          <code className="text-slate-800">https://api.payflow.io</code>
        </p>
      </div>

      {endpoints.map((section) => (
        <section key={section.category} className="mb-12">
          <h2 className="text-xl font-semibold text-slate-900 mb-1">{section.category}</h2>
          <p className="text-sm text-slate-500 mb-4">{section.description}</p>
          <div className="border border-slate-200 rounded-lg overflow-hidden">
            {section.items.map((endpoint, idx) => (
              <div
                key={idx}
                className={`flex items-center gap-4 px-4 py-3 ${
                  idx !== section.items.length - 1 ? 'border-b border-slate-100' : ''
                } hover:bg-slate-50 transition-colors`}
              >
                <span
                  className={`inline-block px-2 py-0.5 rounded text-xs font-bold uppercase w-16 text-center ${methodColor(
                    endpoint.method
                  )}`}
                >
                  {endpoint.method}
                </span>
                <code className="text-sm text-slate-800 font-medium">{endpoint.path}</code>
                <span className="text-sm text-slate-500 ml-auto">{endpoint.description}</span>
              </div>
            ))}
          </div>
        </section>
      ))}

      {/* Example Request */}
      <section className="border-t border-slate-200 pt-8">
        <h2 className="text-lg font-semibold text-slate-900 mb-4">Example Request</h2>
        <div className="bg-slate-800 rounded-lg p-4 overflow-x-auto">
          <pre className="text-sm text-green-300">
{`curl -X POST https://api.payflow.io/v1/orders \\
  -H "Content-Type: application/json" \\
  -H "X-Api-Key: pk_test_abc123def456" \\
  -d '{
    "amount": 2500,
    "currency": "GBP",
    "description": "Order #1234",
    "payment_method": "CARD"
  }'`}
          </pre>
        </div>
      </section>
    </div>
  );
}

export default ApiReferencePage;
