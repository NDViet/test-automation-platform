import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout'
import ProjectLayout, { LegacyProjectRedirect } from './components/layout/ProjectLayout'
import OrgSelectPage from './pages/OrgSelectPage'
import OrgOverview from './pages/OrgOverview'
import ProjectDetail from './pages/ProjectDetail'
import ProjectSettingsPage from './pages/ProjectSettingsPage'
import RunDetail from './pages/RunDetail'
import AlertsPage from './pages/AlertsPage'
import ApiKeysPage from './pages/ApiKeysPage'
import AiSettingsPage from './pages/AiSettingsPage'
import AdminIntegrationsPage from './pages/AdminIntegrationsPage'
import MappingRulesPage from './pages/MappingRulesPage'
import RolesPage from './pages/RolesPage'
import OrgSettingsPage from './pages/OrgSettingsPage'
import RequirementsPage from './pages/RequirementsPage'
import AdoStructurePage from './pages/AdoStructurePage'
import QualityDashboardPage from './pages/QualityDashboardPage'
import ProductivityPage from './pages/ProductivityPage'
import CoverageMatrixPage from './pages/CoverageMatrixPage'
import AdoMappingPage from './pages/AdoMappingPage'
import PRAnalysesPage from './pages/PRAnalysesPage'
import ImpactAnalysesPage from './pages/ImpactAnalysesPage'
import TestCasesPage from './pages/TestCasesPage'
import TestRunExecutionPage from './pages/TestRunExecutionPage'
import TestExecutionPage from './pages/TestExecutionPage'
import ReleasesPage from './pages/ReleasesPage'
import SuitesPage from './pages/SuitesPage'
import FlakyTestsPage from './pages/FlakyTestsPage'
import ReviewQueuePage from './pages/ReviewQueuePage'
import AutomatedTestsPage from './pages/AutomatedTestsPage'
import GitHubWorkflowsPage from './pages/GitHubWorkflowsPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<OrgSelectPage />} />

          {/* Global (non-project) routes — static segments outrank the dynamic
              project route below, so these always win. */}
          <Route path="alerts" element={<AlertsPage />} />
          <Route path="settings/api-keys" element={<ApiKeysPage />} />
          <Route path="settings/ai" element={<AiSettingsPage />} />
          <Route path="settings/integrations" element={<AdminIntegrationsPage />} />
          <Route path="settings/mapping-rules" element={<MappingRulesPage />} />
          <Route path="settings/roles" element={<RolesPage />} />
          <Route path="settings/organization" element={<OrgSettingsPage />} />
          <Route path="runs/:runId" element={<RunDetail />} />

          {/* Back-compat: legacy UUID URLs redirect to slug URLs */}
          <Route path="projects/:projectId/*" element={<LegacyProjectRedirect />} />

          {/* Org-level overview: /:orgSlug */}
          <Route path=":orgSlug" element={<OrgOverview />} />

          {/* Human-readable project workspace: /:orgSlug/:projectSlug/… */}
          <Route path=":orgSlug/:projectSlug" element={<ProjectLayout />}>
            <Route index element={<ProjectDetail />} />
            <Route path="requirements" element={<RequirementsPage />} />
            <Route path="teams" element={<AdoStructurePage />} />
            <Route path="quality" element={<QualityDashboardPage />} />
            <Route path="productivity" element={<ProductivityPage />} />
            <Route path="coverage" element={<CoverageMatrixPage />} />
            <Route path="pr-analyses" element={<PRAnalysesPage />} />
            <Route path="impact-analyses" element={<ImpactAnalysesPage />} />
            <Route path="impact-analyses/:analysisId" element={<ImpactAnalysesPage />} />
            <Route path="settings" element={<ProjectSettingsPage />} />
            <Route path="settings/:section" element={<ProjectSettingsPage />} />
            <Route path="mapping" element={<AdoMappingPage />} />
            <Route path="test-cases" element={<TestCasesPage />} />
            <Route path="test-suites" element={<SuitesPage />} />
            <Route path="test-runs/:runId" element={<TestRunExecutionPage />} />
            <Route path="test-execution" element={<TestExecutionPage />} />
            <Route path="test-execution/manual/:runId" element={<TestRunExecutionPage />} />
            <Route path="runs/:runId" element={<RunDetail />} />
            <Route path="releases" element={<ReleasesPage />} />
            <Route path="flaky-tests" element={<FlakyTestsPage />} />
            <Route path="automated-tests" element={<AutomatedTestsPage />} />
            <Route path="github-workflows" element={<GitHubWorkflowsPage />} />
            <Route path="review-queue" element={<ReviewQueuePage />} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
