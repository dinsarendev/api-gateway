import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  return {
    plugins: [react()],
    base: '/',
    server: {
      port: 3000,
      proxy: {
        '/admin': {
          target: env.API_URL || 'http://localhost:25010',
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir:      './dist',
      emptyOutDir: true,
    },
  };
});