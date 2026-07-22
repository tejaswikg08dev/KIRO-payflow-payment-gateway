function SuccessPage() {
  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f0fdf4' }}>
      <div style={{ textAlign: 'center', padding: 40 }}>
        {/* Green checkmark */}
        <div style={{ width: 80, height: 80, borderRadius: '50%', background: '#22c55e', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 24px' }}>
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="20 6 9 17 4 12" />
          </svg>
        </div>
        <h1 style={{ fontSize: 28, fontWeight: 700, color: '#166534', marginBottom: 8 }}>
          Payment Successful
        </h1>
        <p style={{ fontSize: 16, color: '#4b5563' }}>
          Your payment has been processed successfully.
        </p>
      </div>
    </div>
  );
}

export default SuccessPage;
