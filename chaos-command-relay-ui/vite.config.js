import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  base: '/chaos/',
  plugins: [vue(), tailwindcss()],
  server: {
    port: 5174,
    proxy: {
      '/api/relay': {
        target: 'http://localhost:18090',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/relay/, ''),
      },
    },
  },
  test: {
    environment: 'node',
  },
});
