function AuthenticationPage() {
  return (
    <div>
      <h1 className="text-3xl font-bold text-slate-900 mb-2">Authentication</h1>
      <p className="text-slate-600 mb-10">
        All API requests must be authenticated using an API key. PayFlow uses key-based
        authentication to identify your account and authorize requests.
      </p>

      {/* How API Keys Work */}
      <section className="mb-12">
        <h2 className="text-xl font-semibold text-slate-900 mb-4">How API Keys Work</h2>
        <p className="text-slate-600 mb-4">
          Every request to the PayFlow API must include your API key in the{' '}
          <code className="bg-slate-100 px-1.5 py-0.5 rounded text-sm">X-Api-Key</code> header.
          The key identifies your merchant account and determines which environment (test or live)
          the request targets.
        </p>
        <div className="bg-slate-800 rounded-lg p-4 overflow-x-auto mb-4">
          <pre className="text-sm text-green-300">
{`curl https://api.payflow.io/v1/orders \\
  -H "X-Api-Key: pk_test_abc123def456"`}
          </pre>
        </div>
        <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
          <p className="text-sm text-amber-800">
            <strong>⚠️ Security:</strong> Never expose your API keys in client-side code,
            public repositories, or logs. Store them as environment variables or in a secure
            secrets manager.
          </p>
        </div>
      </section>

      {/* Test vs Live Keys */}
      <section className="mb-12">
        <h2 className="text-xl font-semibold text-slate-900 mb-4">Test vs Live Keys</h2>
        <p className="text-slate-600 mb-4">
          PayFlow provides two sets of API keys for each merchant account:
        </p>
        <div className="border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left px-4 py-3 font-semibold text-slate-700">Key Type</th>
                <th className="text-left px-4 py-3 font-semibold text-slate-700">Prefix</th>
                <th className="text-left px-4 py-3 font-semibold text-slate-700">Environment</th>
                <th className="text-left px-4 py-3 font-semibold text-slate-700">Real Charges</th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-t border-slate-100">
                <td className="px-4 py-3 text-slate-800">Test Key</td>
                <td className="px-4 py-3">
                  <code className="bg-slate-100 px-1.5 py-0.5 rounded text-xs">pk_test_</code>
                </td>
                <td className="px-4 py-3 text-slate-600">Sandbox</td>
                <td className="px-4 py-3">
                  <span className="text-green-600 font-medium">No</span>
                </td>
              </tr>
              <tr className="border-t border-slate-100">
                <td className="px-4 py-3 text-slate-800">Live Key</td>
                <td className="px-4 py-3">
                  <code className="bg-slate-100 px-1.5 py-0.5 rounded text-xs">pk_live_</code>
                </td>
                <td className="px-4 py-3 text-slate-600">Production</td>
                <td className="px-4 py-3">
                  <span className="text-red-600 font-medium">Yes</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      {/* Request Headers */}
      <section className="mb-12">
        <h2 className="text-xl font-semibold text-slate-900 mb-4">Required Headers</h2>
        <p className="text-slate-600 mb-4">
          Include these headers in every API request:
        </p>
        <div className="bg-slate-800 rounded-lg p-4 overflow-x-auto">
          <pre className="text-sm text-green-300">
{`X-Api-Key: pk_test_abc123def456
Content-Type: application/json
Accept: application/json`}
          </pre>
        </div>
      </section>

      {/* Error Responses */}
      <section className="mb-12">
        <h2 className="text-xl font-semibold text-slate-900 mb-4">Authentication Errors</h2>
        <p className="text-slate-600 mb-4">
          If authentication fails, the API returns one of the following errors:
        </p>
        <div className="space-y-3">
          <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <span className="bg-red-100 text-red-700 text-xs font-bold px-2 py-0.5 rounded">401</span>
              <span className="text-sm font-medium text-slate-800">Unauthorized</span>
            </div>
            <p className="text-sm text-slate-600">
              Missing or invalid API key. Verify the key is correct and included in the{' '}
              <code className="bg-slate-100 px-1 py-0.5 rounded text-xs">X-Api-Key</code> header.
            </p>
          </div>
          <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <span className="bg-red-100 text-red-700 text-xs font-bold px-2 py-0.5 rounded">403</span>
              <span className="text-sm font-medium text-slate-800">Forbidden</span>
            </div>
            <p className="text-sm text-slate-600">
              The API key is valid but lacks permission for the requested resource or action.
            </p>
          </div>
          <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <span className="bg-yellow-100 text-yellow-700 text-xs font-bold px-2 py-0.5 rounded">429</span>
              <span className="text-sm font-medium text-slate-800">Rate Limited</span>
            </div>
            <p className="text-sm text-slate-600">
              Too many requests. Default limit is 100 requests/minute per API key.
              Retry after the <code className="bg-slate-100 px-1 py-0.5 rounded text-xs">Retry-After</code> header value.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
}

export default AuthenticationPage;
