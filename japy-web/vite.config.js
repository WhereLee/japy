import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';
export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url))
        }
    },
    server: {
        port: 3005,
        proxy: {
            '/api': {
                target: 'http://localhost:8085',
                changeOrigin: true,
                rewrite: function (p) { return p.replace(/^\/api/, ''); }
            }
        }
    }
});
