import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Both the dev server (npm run dev) and the production preview (npm run preview, used by CI)
// forward every /api call to the Spring Boot backend on 8081, so the browser talks to one origin
// and no CORS setup is needed.
// /robots.txt and /sitemap.xml come from the backend too (ADR-0019), as nginx serves them in production.
const backend = 'http://localhost:8081';
const proxy = {
  '/api': backend,
  '/robots.txt': { target: backend, rewrite: () => '/api/seo/robots.txt' },
  '/sitemap.xml': { target: backend, rewrite: () => '/api/seo/sitemap.xml' },
};

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy },
  preview: { port: 5173, strictPort: true, proxy },
});
