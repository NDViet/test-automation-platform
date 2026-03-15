import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout'
import OrgOverview from './pages/OrgOverview'
import ProjectDetail from './pages/ProjectDetail'
import RunDetail from './pages/RunDetail'
import AlertsPage from './pages/AlertsPage'
import ApiKeysPage from './pages/ApiKeysPage'
import AiSettingsPage from './pages/AiSettingsPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<OrgOverview />} />
          <Route path="projects/:projectId" element={<ProjectDetail />} />
          <Route path="runs/:runId" element={<RunDetail />} />
          <Route path="alerts" element={<AlertsPage />} />
          <Route path="settings/api-keys" element={<ApiKeysPage />} />
          <Route path="settings/ai" element={<AiSettingsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
