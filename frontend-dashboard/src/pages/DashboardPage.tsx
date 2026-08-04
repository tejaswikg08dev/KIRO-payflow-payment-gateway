import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';

interface DashboardStats {
  totalPayments: number;
  successRate: number;
  totalRevenue: number;
  currency: string;
}

function DashboardPage() {
  const navigate = useNavigate();
  const [stats, setStats] = useState<DashboardStats>({
    totalPayments: 0,
    successRate: 0,
    totalRevenue: 0,
    currency: 'INR',
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const response = await api.get('/v1/merchant/dashboard/stats');
        setStats(response.data.data);
      } catch {
        // If unauthorized, api interceptor will redirect to login
      } finally {
        setLoading(false);
      }
    };
    fetchStats();
  }, []);

  const handleLogout = () => {
    localStorage.removeItem('payflow_token');
    navigate('/login');
  };

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-gray-500">Loading dashboard...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="border-b bg-white px-6 py-4 shadow-sm">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-bold text-gray-800">PayFlow Dashboard</h1>
          <button
            onClick={handleLogout}
            className="rounded px-4 py-2 text-sm text-gray-600 hover:bg-gray-100"
          >
            Logout
          </button>
        </div>
      </header>

      {/* Stats Cards */}
      <main className="mx-auto max-w-6xl p-6">
        <div className="grid gap-6 md:grid-cols-3">
          {/* Total Payments */}
          <div className="rounded-lg bg-white p-6 shadow">
            <p className="text-sm font-medium text-gray-500">Total Payments</p>
            <p className="mt-2 text-3xl font-bold text-gray-900">
              {stats.totalPayments.toLocaleString()}
            </p>
          </div>

          {/* Success Rate */}
          <div className="rounded-lg bg-white p-6 shadow">
            <p className="text-sm font-medium text-gray-500">Success Rate</p>
            <p className="mt-2 text-3xl font-bold text-green-600">
              {stats.successRate.toFixed(1)}%
            </p>
          </div>

          {/* Revenue */}
          <div className="rounded-lg bg-white p-6 shadow">
            <p className="text-sm font-medium text-gray-500">Total Revenue</p>
            <p className="mt-2 text-3xl font-bold text-gray-900">
              ₹{stats.totalRevenue.toLocaleString()}
            </p>
          </div>
        </div>

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
      </main>
    </div>
  );
}

export default DashboardPage;
