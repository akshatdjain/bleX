import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AppLayout } from "@/components/AppLayout";
import { MovementToast } from "@/components/MovementToast";
import Index from "./pages/Index";
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
      <BrowserRouter>
        <AppLayout>
          <Routes>
            <Route path="/" element={<Index />} />
            <Route path="/zones/:zoneId" element={<ZoneDetail />} />
            <Route path="/logs" element={<Logs />} />
            <Route path="/assets" element={<Assets />} />
            <Route path="/assets/:assetId" element={<AssetDetail />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </AppLayout>
        <MovementToast />
      </BrowserRouter>
    </TooltipProvider>
  </QueryClientProvider>
);

export default App;
