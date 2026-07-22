import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';

interface Payment {
  paymentId: string;
  orderId: string;
  amount: number;
  currency: string;
  status: string;
  method: string;
  createdAt: string;
}

function TransactionsPage() {
  const navigate = useNavigate();
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchPayments = async () => {
      try {
        const response = await api.get('/v1/payments?page=0&size=20');
        setPayments(response.data.data.content || []);
      } catch {
        // Interceptor handles 401
      } finally {
        setLoading(false);
      }
    };
    fetchPayments();
  }, []);

  const statusColor = (status: string) => {
    switch (status) {
      case 'captured':
      case 'settled':
        return 'bg-green-100 text-green-800';
      case 'authorized':
        return 'bg-blue-100 text-blue-800';
      case 'failed':
        return 'bg-red-100 text-red-800';
      case 'processing':
        return 'bg-yellow-100 text-yellow-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="border-b bg-white px-6 py-4 shadow-sm">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-bold text-gray-800">Transactions</h1>
          <button
            onClick={() => navigate('/dashboard')}
            className="rounded px-4 py-2 text-sm text-gray-600 hover:bg-gray-100"
          >
            ← Dashboard
          </button>
        </div>
      </header>

      {/* Table */}
      <main className="mx-auto max-w-6xl p-6">
        {loading ? (
          <p className="text-gray-500">Loading transactions...</p>
        ) : payments.length === 0 ? (
          <p className="text-gray-500">No transactions found.</p>
        ) : (
          <div className="overflow-x-auto rounded-lg bg-white shadow">
            <table className="w-full text-left text-sm">
              <thead className="border-b bg-gray-50">
                <tr>
                  <th className="px-4 py-3 font-medium text-gray-600">Payment ID</th>
                  <th className="px-4 py-3 font-medium text-gray-600">Order ID</th>
                  <th className="px-4 py-3 font-medium text-gray-600">Amount</th>
                  <th className="px-4 py-3 font-medium text-gray-600">Method</th>
                  <th className="px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="px-4 py-3 font-medium text-gray-600">Date</th>
                </tr>
              </thead>
              <tbody>
                {payments.map((p) => (
                  <tr key={p.paymentId} className="border-b hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-xs">{p.paymentId}</td>
                    <td className="px-4 py-3 font-mono text-xs">{p.orderId}</td>
                    <td className="px-4 py-3">
                      {p.currency === 'INR' ? '₹' : p.currency}{' '}
                      {p.amount.toLocaleString()}
                    </td>
                    <td className="px-4 py-3 capitalize">{p.method}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-block rounded-full px-2 py-1 text-xs font-medium ${statusColor(p.status)}`}
                      >
                        {p.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500">
                      {new Date(p.createdAt).toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>
    </div>
  );
}

export default TransactionsPage;
