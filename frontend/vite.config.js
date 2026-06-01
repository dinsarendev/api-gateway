import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/',
  server: {
    port: 3000,
    proxy: {
      '/admin': { target: 'http://localhost:25010', changeOrigin: true },
    },
  },
  build: {
    outDir:     '../src/main/resources/static',
    emptyOutDir: true,
  },
});