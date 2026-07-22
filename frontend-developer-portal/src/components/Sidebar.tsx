import { NavLink } from 'react-router-dom';

const navItems = [
  { path: '/', label: 'Home' },
  { path: '/getting-started', label: 'Getting Started' },
  { path: '/authentication', label: 'Authentication' },
  { path: '/api-reference', label: 'API Reference' },
  { path: '/webhooks', label: 'Webhooks' },
];

function Sidebar() {
  return (
    <aside className="fixed top-0 left-0 h-screen w-64 bg-slate-900 text-white flex flex-col">
      <div className="px-6 py-6 border-b border-slate-700">
        <h1 className="text-xl font-bold tracking-tight">
          <span className="text-indigo-400">PayFlow</span> Docs
        </h1>
        <p className="text-xs text-slate-400 mt-1">Developer Portal</p>
      </div>
      <nav className="flex-1 px-4 py-6 space-y-1 overflow-y-auto">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            end={item.path === '/'}
            className={({ isActive }) =>
              `block px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-indigo-600 text-white'
                  : 'text-slate-300 hover:bg-slate-800 hover:text-white'
              }`
            }
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
      <div className="px-6 py-4 border-t border-slate-700">
        <p className="text-xs text-slate-500">v1.0.0</p>
      </div>
    </aside>
  );
}

export default Sidebar;
