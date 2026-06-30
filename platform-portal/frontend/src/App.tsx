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
import AgentsPage from './pages/AgentsPage'
import TaskAgentsPage from './pages/TaskAgentsPage'
import AdminIntegrationsPage from './pages/AdminIntegrationsPage'
import MappingRulesPage from './pages/MappingRulesPage'
import UsersPage from './pages/UsersPage'
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
import LoginPage from './pages/LoginPage'
import ChangePasswordPage from './pages/ChangePasswordPage'
import LoadingSpinner from './components/LoadingSpinner'
import { AuthProvider, useAuth } from './context/AuthContext'
import { RequireCap } from './components/Can'
import ForbiddenToast from './components/ForbiddenToast'
import type { ReactNode } from 'react'

/** Gate the app behind authentication: loading → spinner, no user → login, must-change → change. */
function AuthGate({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (user === undefined)
    return (
      <div className="min-h-screen flex items-center justify-center">
        <LoadingSpinner message="Loading…" />
      </div>
    )
  if (user === null) return <LoginPage />
  if (user.mustChangePassword) return <ChangePasswordPage forced />
  return <>{children}</>
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AuthGate>
          <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<OrgSelectPage />} />

          {/* Global (non-project) routes — static segments outrank the dynamic
              project route below, so these always win. */}
          <Route path="alerts" element={<AlertsPage />} />
          <Route
            path="settings/api-keys"
            element={<RequireCap cap="MANAGE_ORG"><ApiKeysPage /></RequireCap>}
          />
          <Route
            path="settings/ai"
            element={<RequireCap cap="MANAGE_AI_GATEWAY"><AiSettingsPage /></RequireCap>}
          />
          <Route
            path="settings/agents"
            element={<RequireCap cap="OPERATE_QUALITY"><AgentsPage /></RequireCap>}
          />
          <Route
            path="settings/task-agents"
            element={<RequireCap cap="OPERATE_QUALITY"><TaskAgentsPage /></RequireCap>}
          />
          <Route
            path="settings/integrations"
            element={<RequireCap cap="MANAGE_ORG"><AdminIntegrationsPage /></RequireCap>}
          />
          <Route
            path="settings/mapping-rules"
            element={<RequireCap cap="MANAGE_ORG"><MappingRulesPage /></RequireCap>}
          />
          <Route
            path="settings/users"
            element={<RequireCap cap="MANAGE_ORG"><UsersPage /></RequireCap>}
          />
          <Route
            path="settings/organization"
            element={<RequireCap cap="MANAGE_ORG"><OrgSettingsPage /></RequireCap>}
          />
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
            <Route path="test-cases/:tcId" element={<TestCasesPage />} />
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
          <ForbiddenToast />
        </AuthGate>
      </AuthProvider>
    </BrowserRouter>
  )
}
