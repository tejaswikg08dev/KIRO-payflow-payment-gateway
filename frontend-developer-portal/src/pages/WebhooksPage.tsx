function WebhooksPage() {
  return (
    <div>
      <h1 className="text-3xl font-bold text-slate-900 mb-2">Webhooks</h1>
      <p className="text-slate-600 mb-10">
        Webhooks allow you to receive real-time HTTP notifications when events occur in your
        PayFlow account. Configure endpoints to listen for specific events like successful
        payments, refunds, and settlement completions.
      </p>

      {/* Event Types */}
      <section className="mb-12">
        <h2 className="text-xl font-semibold text-slate-900 mb-4">Event Types</h2>
        <div className="border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left px-4 py-3 font-semibold text-slate-700">Event</th>
                <th className="text-left px-4 py-3 font-semibold text-slate-700">Description</th>
              </tr>
            </thead>
            <tbody>
              {[
                ['payment.completed', 'A payment was successfully processed'],
                ['payment.failed', 'A payment attempt failed'],
                ['payment.refunded', 'A payment was refunded (full or partial)'],
                ['order.created', 'A new order was created'],
                ['order.cancelled', 'An order was cancelled'],
                ['settlement.completed', 'A settlement batch was processed'],
                ['merchant.updated', 'Merchant configuration was updated'],
                ['webhook.test', 'Test event sent via the API'],
              ].map(([event, desc], idx) => (
                <tr key={idx} className="border-t border-slate-100">
                  <td className="px-4 py-3">
                    <code className="bg-slate-100 px-1.5 py-0.5 rounded text-xs">{event}</code>
                  </td>
                  <td className="px-4 py-3 text-slate-600">{desc}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Payload Format */}
      <section className="mb-12">
        <h2 className="text-xl font-semibold text-slate-900 mb-4">Payload Format</h2>
        <p className="text-slate-600 mb-4">
          Each webhook delivery sends a JSON payload to your configured endpoint via HTTP POST:
        </p>
        <div className="bg-slate-800 rounded-lg p-4 overflow-x-auto">
          <pre className="text-sm text-green-300">
{`POST /your-webhook-endpoint HTTP/1.1
Content-Type: application/json
X-PayFlow-Signature: sha256=a1b2c3d4e5f6...
X-PayFlow-Event: payment.completed
X-PayFlow-Delivery-Id: evt_7f3a2b1c4d5e

{
  "id": "evt_7f3a2b1c4d5e",
  "type": "payment.completed",
  "created_at": "2024-01-15T10:30:00Z",
  "data": {
    "payment_id": "pay_abc123",
    "order_id": "ord_xyz789",
    "amount": 2500,
    "currency": "GBP",
    "status": "COMPLETED"
  }
}`}
          </pre>
        </div>
      </section>

      {/* HMAC Verification */}
      <section className="mb-12">
        <h2 className="text-xl font-semibold text-slate-900 mb-4">Signature Verification (HMAC)</h2>
        <p className="text-slate-600 mb-4">
          Every webhook includes an <code className="bg-slate-100 px-1.5 py-0.5 rounded text-sm">X-PayFlow-Signature</code>{' '}
          header. Verify this signature to ensure the request is genuinely from PayFlow and hasn't
          been tampered with. The signature is computed using HMAC-SHA256 with your webhook secret.
        </p>

        {/* Java */}
        <div className="mb-6">
          <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Java</h3>
          <div className="bg-slate-800 rounded-lg p-4 overflow-x-auto">
            <pre className="text-sm text-green-300">
{`import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

public class WebhookVerifier {
    public static boolean verify(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes("UTF-8"), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes("UTF-8"));
            String computed = "sha256=" + HexFormat.of().formatHex(hash);
            return computed.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
}`}
            </pre>
          </div>
        </div>

        {/* Python */}
        <div className="mb-6">
          <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Python</h3>
          <div className="bg-slate-800 rounded-lg p-4 overflow-x-auto">
            <pre className="text-sm text-green-300">
{`import hmac
import hashlib

def verify_webhook(payload: bytes, signature: str, secret: str) -> bool:
    computed = "sha256=" + hmac.new(
        secret.encode("utf-8"),
        payload,
        hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(computed, signature)`}
            </pre>
          </div>
        </div>

        {/* Node.js */}
        <div className="mb-6">
          <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Node.js</h3>
          <div className="bg-slate-800 rounded-lg p-4 overflow-x-auto">
            <pre className="text-sm text-green-300">
{`const crypto = require('crypto');

function verifyWebhook(payload, signature, secret) {
  const computed = 'sha256=' + crypto
    .createHmac('sha256', secret)
    .update(payload, 'utf-8')
    .digest('hex');

  return crypto.timingSafeEqual(
    Buffer.from(computed),
    Buffer.from(signature)
  );
}`}
            </pre>
          </div>
        </div>
      </section>

      {/* Best Practices */}
      <section className="border-t border-slate-200 pt-8">
        <h2 className="text-lg font-semibold text-slate-900 mb-4">Best Practices</h2>
        <ul className="space-y-3 text-slate-600 text-sm">
          <li className="flex items-start gap-2">
            <span className="text-indigo-500 mt-0.5">•</span>
            <span>Always verify the <code className="bg-slate-100 px-1 py-0.5 rounded text-xs">X-PayFlow-Signature</code> header before processing events.</span>
          </li>
          <li className="flex items-start gap-2">
            <span className="text-indigo-500 mt-0.5">•</span>
            <span>Return a <code className="bg-slate-100 px-1 py-0.5 rounded text-xs">200</code> status quickly. Process the event asynchronously if needed.</span>
          </li>
          <li className="flex items-start gap-2">
            <span className="text-indigo-500 mt-0.5">•</span>
            <span>Handle duplicate deliveries — use the <code className="bg-slate-100 px-1 py-0.5 rounded text-xs">X-PayFlow-Delivery-Id</code> for idempotency.</span>
          </li>
          <li className="flex items-start gap-2">
            <span className="text-indigo-500 mt-0.5">•</span>
            <span>PayFlow retries failed deliveries up to 5 times with exponential backoff.</span>
          </li>
          <li className="flex items-start gap-2">
            <span className="text-indigo-500 mt-0.5">•</span>
            <span>Use HTTPS endpoints only. HTTP endpoints are not supported in production.</span>
          </li>
        </ul>
      </section>
    </div>
  );
}

export default WebhooksPage;
