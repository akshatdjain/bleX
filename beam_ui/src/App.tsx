import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Route, Routes, Navigate } from "react-router-dom";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AppLayout } from "@/components/AppLayout";
import { ProtectedRoute } from "@/components/ProtectedRoute";
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

const queryClient = new QueryClient();

const App = () => (
  <QueryClientProvider client={queryClient}>
    <TooltipProvider>
      <Toaster />
      <Sonner />
      <BrowserRouter basename="/blex">
        <Routes>
          {/* Public routes — no AppLayout */}
          <Route path="/" element={<Index />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />

          {/* Protected routes — wrapped in AppLayout */}
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

          {/* Redirect old "/" dashboard behaviour for direct nav */}
          <Route path="*" element={<NotFound />} />
        </Routes>
        <MovementToast />
      </BrowserRouter>
    </TooltipProvider>
  </QueryClientProvider>
);

export default App;
