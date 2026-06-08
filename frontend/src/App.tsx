import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Route, Routes, Navigate } from "react-router-dom";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AppLayout } from "@/components/AppLayout";
import { AdminLayout } from "@/components/admin/AdminLayout";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { AdminRoute } from "@/components/AdminRoute";
import { AuthProvider } from "@/lib/auth-context";
import { MovementToast } from "@/components/MovementToast";
import Index from "./pages/Index";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Dashboard from "./pages/Dashboard";
import ZoneDetail from "./pages/ZoneDetail";
import Logs from "./pages/Logs";
import Assets from "./pages/Assets";
import AssetDetail from "./pages/AssetDetail";
import NotFound from "./pages/NotFound";
import AdminOverview from "./pages/admin/AdminOverview";
import TenantsPage from "./pages/admin/TenantsPage";
import AuditLogPage from "./pages/admin/AuditLogPage";
import AdminSettings from "./pages/admin/AdminSettings";
import TenantDetailLayout from "./pages/admin/TenantDetailLayout";
import TenantOverviewTab from "./pages/admin/tenant/TenantOverviewTab";
import TenantDevicesTab from "./pages/admin/tenant/TenantDevicesTab";
import TenantUsersTab from "./pages/admin/tenant/TenantUsersTab";
import TenantAuditTab from "./pages/admin/tenant/TenantAuditTab";
import TenantSettingsTab from "./pages/admin/tenant/TenantSettingsTab";

const queryClient = new QueryClient();

const App = () => (
  <QueryClientProvider client={queryClient}>
    <TooltipProvider>
      <Toaster />
      <Sonner />
      <BrowserRouter basename="/blex">
        <AuthProvider>
          <Routes>
            {/* Public routes — no AppLayout */}
            <Route path="/" element={<Index />} />
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />

            {/* Protected user routes — wrapped in AppLayout */}
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <AppLayout>
                    <Dashboard />
                  </AppLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/zones/:zoneId"
              element={
                <ProtectedRoute>
                  <AppLayout>
                    <ZoneDetail />
                  </AppLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/logs"
              element={
                <ProtectedRoute>
                  <AppLayout>
                    <Logs />
                  </AppLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/assets"
              element={
                <ProtectedRoute>
                  <AppLayout>
                    <Assets />
                  </AppLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/assets/:assetId"
              element={
                <ProtectedRoute>
                  <AppLayout>
                    <AssetDetail />
                  </AppLayout>
                </ProtectedRoute>
              }
            />

            {/* Admin routes — separate AdminLayout (sidebar) */}
            <Route
              path="/admin"
              element={
                <AdminRoute>
                  <AdminLayout>
                    <AdminOverview />
                  </AdminLayout>
                </AdminRoute>
              }
            />
            <Route
              path="/admin/tenants"
              element={
                <AdminRoute>
                  <AdminLayout>
                    <TenantsPage />
                  </AdminLayout>
                </AdminRoute>
              }
            />
            <Route
              path="/admin/tenants/:tenantId"
              element={
                <AdminRoute>
                  <AdminLayout>
                    <TenantDetailLayout />
                  </AdminLayout>
                </AdminRoute>
              }
            >
              <Route index element={<Navigate to="overview" replace />} />
              <Route path="overview" element={<TenantOverviewTab />} />
              <Route path="devices" element={<TenantDevicesTab />} />
              <Route path="users" element={<TenantUsersTab />} />
              <Route path="audit" element={<TenantAuditTab />} />
              <Route path="settings" element={<TenantSettingsTab />} />
            </Route>
            <Route
              path="/admin/audit"
              element={
                <AdminRoute>
                  <AdminLayout>
                    <AuditLogPage />
                  </AdminLayout>
                </AdminRoute>
              }
            />
            <Route
              path="/admin/settings"
              element={
                <AdminRoute>
                  <AdminLayout>
                    <AdminSettings />
                  </AdminLayout>
                </AdminRoute>
              }
            />

            <Route path="*" element={<NotFound />} />
          </Routes>
          <MovementToast />
        </AuthProvider>
      </BrowserRouter>
    </TooltipProvider>
  </QueryClientProvider>
);

export default App;
