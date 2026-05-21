import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";
import path from "path";

// Mirrors the Caddy rewrite rules from DGX production exactly.
// /api/*     → asset_api /dashboard/* (dashboard read endpoints, JWT cookie auth)
// /asset/*   → asset_api /* (device/Pi/Android endpoints — auth.ts uses /asset/api/auth/*)
// /beam/*    → served by Vite itself (SPA)
export default defineConfig({
  server: {
    host: "::",
    port: 8080,
    hmr: { overlay: false },
    proxy: {
      // /asset/api/* → asset_api directly (auth, web-nonce etc. used by auth.ts)
      "/asset": {
        target: "http://localhost:5000",
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/asset/, ""),
      },
      // /api/history/* → /dashboard/assets/history
      "/api/history": {
        target: "http://localhost:5000",
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api\/history/, "/dashboard/assets/history"),
      },
      // /api/* → /dashboard/* (zones, scanners, assets, health, notifications)
      "/api": {
        target: "http://localhost:5000",
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, "/dashboard"),
      },
    },
  },
  base: "/beam/",
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
