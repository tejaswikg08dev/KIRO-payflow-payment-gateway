import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';

interface UserInfo {
  userId: string;
  email: string;
  fullName: string;
  role: string;
}

function MerchantOnboardingPage() {
  const navigate = useNavigate();
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
  const [formData, setFormData] = useState({
    businessName: '',
    businessType: 'INDIVIDUAL',
    websiteUrl: '',
    gstNumber: '',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [checkingAuth, setCheckingAuth] = useState(true);

  useEffect(() => {
    // Check if user is authenticated
    const token = localStorage.getItem('payflow_token');
    if (!token) {
      navigate('/register');
      return;
    }

    // Get user info
    const fetchUserInfo = async () => {
      try {
        const response = await api.get('/v1/auth/profile');
        setUserInfo(response.data.data);

        // Check if user already has a merchant account
        try {
          const merchantResponse = await api.get(`/v1/merchants/by-user/${response.data.data.userId}`);
          if (merchantResponse.data.data) {
            // User already has merchant, go to dashboard
            navigate('/dashboard');
            return;
          }
        } catch {
          // No merchant exists, continue with onboarding
        }
      } catch {
        // Token invalid, redirect to login
        localStorage.removeItem('payflow_token');
        navigate('/login');
      } finally {
        setCheckingAuth(false);
      }
    };

    fetchUserInfo();
  }, [navigate]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError('');

    if (!userInfo) {
      setError('User information not loaded');
      return;
    }

    if (formData.businessName.trim().length < 2) {
      setError('Please enter a valid business name');
      return;
    }

    setLoading(true);

    try {
      await api.post('/v1/merchants', {
        userId: userInfo.userId,
        businessName: formData.businessName.trim(),
        businessType: formData.businessType,
        websiteUrl: formData.websiteUrl || null,
        gstNumber: formData.gstNumber || null,
      });

      // Merchant created, go to dashboard
      navigate('/dashboard');
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { message?: string } } };
      const message = errorResponse.response?.data?.message || 'Failed to create merchant. Please try again.';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  if (checkingAuth) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-100">
        <p className="text-gray-500">Loading...</p>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-100">
      <div className="w-full max-w-lg rounded-lg bg-white p-8 shadow-md">
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-800">
            Set Up Your Business
          </h1>
          <p className="mt-1 text-sm text-gray-500">
            Welcome, {userInfo?.fullName}! Let's set up your merchant account.
          </p>
        </div>

        {/* Progress indicator */}
        <div className="mb-6 flex items-center">
          <div className="flex items-center">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-green-500 text-sm text-white">
              ✓
            </div>
            <span className="ml-2 text-sm text-gray-600">Account Created</span>
          </div>
          <div className="mx-4 h-1 flex-1 bg-blue-500"></div>
          <div className="flex items-center">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-500 text-sm text-white">
              2
            </div>
            <span className="ml-2 text-sm font-medium text-gray-800">Business Setup</span>
          </div>
        </div>

        {error && (
          <div className="mb-4 rounded bg-red-50 p-3 text-sm text-red-600">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="businessName" className="block text-sm font-medium text-gray-700">
              Business Name *
            </label>
            <input
              id="businessName"
              name="businessName"
              type="text"
              required
              value={formData.businessName}
              onChange={handleChange}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="Acme Electronics"
            />
            <p className="mt-1 text-xs text-gray-500">This will be displayed on payment pages</p>
          </div>

          <div>
            <label htmlFor="businessType" className="block text-sm font-medium text-gray-700">
              Business Type *
            </label>
            <select
              id="businessType"
              name="businessType"
              value={formData.businessType}
              onChange={handleChange}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            >
              <option value="INDIVIDUAL">Individual / Sole Proprietor</option>
              <option value="PARTNERSHIP">Partnership</option>
              <option value="COMPANY">Private Limited Company</option>
              <option value="LLP">Limited Liability Partnership (LLP)</option>
              <option value="TRUST">Trust / NGO</option>
            </select>
          </div>

          <div>
            <label htmlFor="websiteUrl" className="block text-sm font-medium text-gray-700">
              Website URL (Optional)
            </label>
            <input
              id="websiteUrl"
              name="websiteUrl"
              type="url"
              value={formData.websiteUrl}
              onChange={handleChange}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="https://www.yoursite.com"
            />
          </div>

          <div>
            <label htmlFor="gstNumber" className="block text-sm font-medium text-gray-700">
              GST Number (Optional)
            </label>
            <input
              id="gstNumber"
              name="gstNumber"
              type="text"
              value={formData.gstNumber}
              onChange={handleChange}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="22AAAAA0000A1Z5"
            />
            <p className="mt-1 text-xs text-gray-500">15-character GST Identification Number</p>
          </div>

          <div className="rounded bg-blue-50 p-4">
            <h3 className="text-sm font-medium text-blue-800">What happens next?</h3>
            <ul className="mt-2 space-y-1 text-sm text-blue-700">
              <li>• Your merchant account will be created</li>
              <li>• You can generate API keys to integrate payments</li>
              <li>• Start accepting test payments immediately</li>
              <li>• Complete KYC verification to go live</li>
            </ul>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded bg-blue-600 py-3 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? 'Creating Merchant Account...' : 'Complete Setup'}
          </button>
        </form>

        <p className="mt-4 text-center text-xs text-gray-500">
          By continuing, you agree to PayFlow's Terms of Service and Privacy Policy.
        </p>
      </div>
    </div>
  );
}

export default MerchantOnboardingPage;
