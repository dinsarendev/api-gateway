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
          target: env.API_URL || 'http://localhost:26080',
          changeOrigin: true,
          rewrite: path => '/api/management' + path,
        },
      },
    },
    build: {
      outDir:      './dist',
      emptyOutDir: true,
    },
  };
});