import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  base: '/beam/',
  define: {
    'import.meta.env.VITE_API_URL': JSON.stringify(process.env.API_URL || 'http://localhost:5000/api'),
  },
})
