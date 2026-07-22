import { Routes, Route } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import HomePage from './pages/HomePage';
import GettingStartedPage from './pages/GettingStartedPage';
import ApiReferencePage from './pages/ApiReferencePage';
import AuthenticationPage from './pages/AuthenticationPage';
import WebhooksPage from './pages/WebhooksPage';

function App() {
  return (
    <div className="flex min-h-screen">
      <Sidebar />
      <main className="flex-1 ml-64 bg-white">
        <div className="max-w-4xl mx-auto px-8 py-12">
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/getting-started" element={<GettingStartedPage />} />
            <Route path="/api-reference" element={<ApiReferencePage />} />
            <Route path="/authentication" element={<AuthenticationPage />} />
            <Route path="/webhooks" element={<WebhooksPage />} />
          </Routes>
        </div>
      </main>
    </div>
  );
}

export default App;
