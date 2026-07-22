import { Routes, Route, Navigate } from 'react-router-dom';
import PaymentPage from './pages/PaymentPage';
import SuccessPage from './pages/SuccessPage';
import FailurePage from './pages/FailurePage';

function App() {
  return (
    <Routes>
      <Route path="/pay/:orderId" element={<PaymentPage />} />
      <Route path="/success" element={<SuccessPage />} />
      <Route path="/failure" element={<FailurePage />} />
      <Route path="*" element={<Navigate to="/failure" replace />} />
    </Routes>
  );
}

export default App;
