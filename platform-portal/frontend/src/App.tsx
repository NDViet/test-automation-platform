import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout'
import OrgOverview from './pages/OrgOverview'
import ProjectDetail from './pages/ProjectDetail'
import ProjectSettingsPage from './pages/ProjectSettingsPage'
import RunDetail from './pages/RunDetail'
import AlertsPage from './pages/AlertsPage'
import ApiKeysPage from './pages/ApiKeysPage'
import AiSettingsPage from './pages/AiSettingsPage'
import RequirementsPage from './pages/RequirementsPage'
import PRAnalysesPage from './pages/PRAnalysesPage'
import ImpactAnalysesPage from './pages/ImpactAnalysesPage'
import TestCasesPage from './pages/TestCasesPage'
import TestRunsPage from './pages/TestRunsPage'
import TestRunExecutionPage from './pages/TestRunExecutionPage'
import FlakyTestsPage from './pages/FlakyTestsPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<OrgOverview />} />
          <Route path="projects/:projectId" element={<ProjectDetail />} />
          <Route path="projects/:projectId/requirements" element={<RequirementsPage />} />
          <Route path="projects/:projectId/pr-analyses" element={<PRAnalysesPage />} />
          <Route path="projects/:projectId/impact-analyses" element={<ImpactAnalysesPage />} />
          <Route path="projects/:projectId/impact-analyses/:analysisId" element={<ImpactAnalysesPage />} />
          <Route path="projects/:projectId/settings" element={<ProjectSettingsPage />} />
          <Route path="projects/:projectId/test-cases" element={<TestCasesPage />} />
          <Route path="projects/:projectId/test-runs" element={<TestRunsPage />} />
          <Route path="projects/:projectId/test-runs/:runId" element={<TestRunExecutionPage />} />
          <Route path="projects/:projectId/flaky-tests" element={<FlakyTestsPage />} />
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
