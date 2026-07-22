function GettingStartedPage() {
  return (
    <div>
      <h1 className="text-3xl font-bold text-slate-900 mb-2">Getting Started</h1>
      <p className="text-slate-600 mb-10">
        Follow these steps to start processing payments with PayFlow in minutes.
      </p>

      {/* Step 1 */}
      <section className="mb-12">
        <div className="flex items-center gap-3 mb-4">
          <span className="flex items-center justify-center w-8 h-8 rounded-full bg-indigo-100 text-indigo-700 text-sm font-bold">
            1
          </span>
          <h2 className="text-xl font-semibold text-slate-900">Create an Account</h2>
        </div>
        <p className="text-slate-600 ml-11 mb-4">
          Sign up at{' '}
          <code className="bg-slate-100 px-1.5 py-0.5 rounded text-sm">
            https://dashboard.payflow.io/register
          </code>{' '}
          to get access to your merchant dashboard. You'll receive a test environment
          immediately upon registration.
        </p>
      </section>

      {/* Step 2 */}
      <section className="mb-12">
        <div className="flex items-center gap-3 mb-4">
          <span className="flex items-center justify-center w-8 h-8 rounded-full bg-indigo-100 text-indigo-700 text-sm font-bold">
            2
          </span>
          <h2 className="text-xl font-semibold text-slate-900">Get API Keys</h2>
        </div>
        <p className="text-slate-600 ml-11 mb-4">
          Navigate to <strong>Settings → API Keys</strong> in your dashboard. You'll find
          both test and live keys. Use test keys for development — no real charges are made.
        </p>
        <div className="ml-11 bg-slate-800 rounded-lg p-4 overflow-x-auto">
          <pre className="text-sm text-green-300">
{`# Your API keys look like this:
Test key: pk_test_abc123def456...
Live key: pk_live_xyz789ghi012...`}
          </pre>
        </div>
      </section>

      {/* Step 3 */}
      <section className="mb-12">
        <div className="flex items-center gap-3 mb-4">
          <span className="flex items-center justify-center w-8 h-8 rounded-full bg-indigo-100 text-indigo-700 text-sm font-bold">
            3
          </span>
          <h2 className="text-xl font-semibold text-slate-900">Make Your First Payment</h2>
        </div>
        <p className="text-slate-600 ml-11 mb-4">
          Create a payment order using the curl command below. This example creates a
          £10.00 payment in the test environment.
        </p>
        <div className="ml-11 bg-slate-800 rounded-lg p-4 overflow-x-auto">
          <pre className="text-sm text-green-300">
{`curl -X POST https://api.payflow.io/v1/orders \\
  -H "Content-Type: application/json" \\
  -H "X-Api-Key: pk_test_abc123def456" \\
  -d '{
    "amount": 1000,
    "currency": "GBP",
    "description": "Test payment",
    "customer_email": "customer@example.com",
    "payment_method": "CARD",
    "card": {
      "number": "4242424242424242",
      "exp_month": 12,
      "exp_year": 2025,
      "cvv": "123"
    }
  }'`}
          </pre>
        </div>
        <div className="ml-11 mt-4">
          <p className="text-sm text-slate-500">Example response:</p>
          <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 mt-2 overflow-x-auto">
            <pre className="text-sm text-slate-700">
{`{
  "id": "ord_7f3a2b1c4d5e6f",
  "status": "COMPLETED",
  "amount": 1000,
  "currency": "GBP",
  "description": "Test payment",
  "created_at": "2024-01-15T10:30:00Z"
}`}
            </pre>
          </div>
        </div>
      </section>

      {/* Next Steps */}
      <section className="border-t border-slate-200 pt-8">
        <h2 className="text-lg font-semibold text-slate-900 mb-3">Next Steps</h2>
        <ul className="space-y-2 text-slate-600">
          <li className="flex items-center gap-2">
            <span className="text-indigo-500">→</span>
            Learn about <a href="/authentication" className="text-indigo-600 hover:underline">Authentication</a>
          </li>
          <li className="flex items-center gap-2">
            <span className="text-indigo-500">→</span>
            Explore the full <a href="/api-reference" className="text-indigo-600 hover:underline">API Reference</a>
          </li>
          <li className="flex items-center gap-2">
            <span className="text-indigo-500">→</span>
            Set up <a href="/webhooks" className="text-indigo-600 hover:underline">Webhooks</a> for real-time notifications
          </li>
        </ul>
      </section>
    </div>
  );
}

export default GettingStartedPage;
