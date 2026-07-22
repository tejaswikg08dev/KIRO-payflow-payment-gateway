import { useNavigate } from 'react-router-dom';

function FailurePage() {
  const navigate = useNavigate();

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#fef2f2' }}>
      <div style={{ textAlign: 'center', padding: 40 }}>
        {/* Red X */}
        <div style={{ width: 80, height: 80, borderRadius: '50%', background: '#ef4444', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 24px' }}>
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </div>
        <h1 style={{ fontSize: 28, fontWeight: 700, color: '#991b1b', marginBottom: 8 }}>
          Payment Failed
        </h1>
        <p style={{ fontSize: 16, color: '#4b5563', marginBottom: 24 }}>
          Something went wrong. Your card was not charged.
        </p>
        <button
          onClick={() => navigate(-1)}
          style={{
            padding: '12px 24px',
            background: '#4f46e5',
            color: '#fff',
            border: 'none',
            borderRadius: 8,
            fontSize: 16,
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          Retry Payment
        </button>
      </div>
    </div>
  );
}

export default FailurePage;
