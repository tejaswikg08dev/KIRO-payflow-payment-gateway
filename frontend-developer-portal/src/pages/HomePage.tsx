import { Link } from 'react-router-dom';

const quickLinks = [
  {
    title: 'Getting Started',
    description: 'Create an account, get your API keys, and make your first payment in minutes.',
    path: '/getting-started',
    icon: '🚀',
  },
  {
    title: 'Authentication',
    description: 'Learn how to authenticate requests using API keys and manage environments.',
    path: '/authentication',
    icon: '🔐',
  },
  {
    title: 'API Reference',
    description: 'Explore all available endpoints for orders, payments, merchants, and more.',
    path: '/api-reference',
    icon: '📖',
  },
  {
    title: 'Webhooks',
    description: 'Receive real-time event notifications and verify webhook signatures.',
    path: '/webhooks',
    icon: '🔔',
  },
];

function HomePage() {
  return (
    <div>
      <div className="mb-12">
        <h1 className="text-4xl font-bold text-slate-900 mb-4">
          Welcome to PayFlow API
        </h1>
        <p className="text-lg text-slate-600 leading-relaxed max-w-2xl">
          PayFlow provides a complete payment processing platform. Use our APIs to accept
          payments, manage orders, handle settlements, and receive real-time notifications
          via webhooks.
        </p>
      </div>

      <div className="mb-12">
        <h2 className="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-4">
          Base URL
        </h2>
        <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
          <code className="text-sm text-slate-800">https://api.payflow.io/v1</code>
        </div>
      </div>

      <div>
        <h2 className="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-6">
          Quick Links
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {quickLinks.map((link) => (
            <Link
              key={link.path}
              to={link.path}
              className="block p-6 border border-slate-200 rounded-lg hover:border-indigo-300 hover:shadow-md transition-all group"
            >
              <div className="text-2xl mb-3">{link.icon}</div>
              <h3 className="text-base font-semibold text-slate-900 group-hover:text-indigo-600 transition-colors">
                {link.title}
              </h3>
              <p className="text-sm text-slate-500 mt-1">{link.description}</p>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}

export default HomePage;
