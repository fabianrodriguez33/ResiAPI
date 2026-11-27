import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// La web se sirve en /app/ desde el mismo Spring que la API: así no hace falta CORS.
export default defineConfig({
  base: '/app/',
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': 'http://localhost:8080' } },
})
