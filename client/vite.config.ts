import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Forward API calls to Spring Boot during development; xfwd passes the visitor's
    // real IP (X-Forwarded-For) so the server's rate limits can tell visitors apart.
    // /uploads: pictures the server keeps in its own folder when Cloudinary isn't set up.
    proxy: {
      '/api': { target: 'http://localhost:8080', xfwd: true },
      '/uploads': { target: 'http://localhost:8080' },
    },
  },
})
