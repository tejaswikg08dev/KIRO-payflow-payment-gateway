import { useState, FormEvent } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';

function PaymentPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();

  const [cardNumber, setCardNumber] = useState('');
  const [expiry, setExpiry] = useState('');
  const [cvv, setCvv] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    const [expiryMonth, expiryYear] = expiry.split('/').map(Number);

    try {
      await axios.post('/api/v1/payments', {
        orderId,
        amount: 1000, // Amount comes from order context in real flow
        method: 'card',
        card: {
          number: cardNumber.replace(/\s/g, ''),
          expiryMonth,
          expiryYear: 2000 + expiryYear,
          cvv,
        },
      });
      navigate('/success');
    } catch {
      navigate('/failure');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f9fafb' }}>
      <div style={{ width: '100%', maxWidth: 400, background: '#fff', borderRadius: 12, padding: 32, boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}>
        <h1 style={{ fontSize: 20, fontWeight: 700, marginBottom: 8, textAlign: 'center' }}>
          Secure Payment
        </h1>
        <p style={{ fontSize: 14, color: '#6b7280', marginBottom: 24, textAlign: 'center' }}>
          Order: {orderId}
        </p>

        {error && (
          <div style={{ background: '#fef2f2', color: '#dc2626', padding: 12, borderRadius: 8, marginBottom: 16, fontSize: 14 }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: 16 }}>
            <label htmlFor="cardNumber" style={{ display: 'block', fontSize: 14, fontWeight: 500, marginBottom: 4 }}>
              Card Number
            </label>
            <input
              id="cardNumber"
              type="text"
              required
              maxLength={19}
              placeholder="4111 1111 1111 1111"
              value={cardNumber}
              onChange={(e) => setCardNumber(e.target.value)}
              style={{ width: '100%', padding: '10px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 16 }}
            />
          </div>

          <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
            <div style={{ flex: 1 }}>
              <label htmlFor="expiry" style={{ display: 'block', fontSize: 14, fontWeight: 500, marginBottom: 4 }}>
                Expiry
              </label>
              <input
                id="expiry"
                type="text"
                required
                maxLength={5}
                placeholder="MM/YY"
                value={expiry}
                onChange={(e) => setExpiry(e.target.value)}
                style={{ width: '100%', padding: '10px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 16 }}
              />
            </div>
            <div style={{ flex: 1 }}>
              <label htmlFor="cvv" style={{ display: 'block', fontSize: 14, fontWeight: 500, marginBottom: 4 }}>
                CVV
              </label>
              <input
                id="cvv"
                type="password"
                required
                maxLength={4}
                placeholder="•••"
                value={cvv}
                onChange={(e) => setCvv(e.target.value)}
                style={{ width: '100%', padding: '10px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 16 }}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            style={{
              width: '100%',
              padding: 12,
              background: loading ? '#9ca3af' : '#4f46e5',
              color: '#fff',
              border: 'none',
              borderRadius: 8,
              fontSize: 16,
              fontWeight: 600,
              cursor: loading ? 'not-allowed' : 'pointer',
            }}
          >
            {loading ? 'Processing...' : 'Pay Now'}
          </button>
        </form>
      </div>
    </div>
  );
}

export default PaymentPage;
