import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { nodePolyfills } from 'vite-plugin-node-polyfills'
import path from 'path'
import { fileURLToPath } from 'url'
import { undiciShimPlugin } from './vite-plugin-undici-shim.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  plugins: [
    react(),
    nodePolyfills({
      globals: {
        Buffer: true,
        global: true,
        process: true,
      },
      // Exclude undici to prevent Request import issues
      exclude: ['undici'],
    }),
    undiciShimPlugin(),
  ],
  define: {
    global: 'globalThis',
  },
  resolve: {
    alias: {
      buffer: 'buffer',
      // Alias undici and all its subpaths to our shim
      'undici': path.resolve(__dirname, 'src/polyfills/undici-shim.js'),
      'undici/fetch': path.resolve(__dirname, 'src/polyfills/undici-shim.js'),
    },
    dedupe: ['@stomp/stompjs', 'sockjs-client'],
  },
  optimizeDeps: {
    include: ['@stomp/stompjs', 'sockjs-client'],
    exclude: ['undici'],
    esbuildOptions: {
      define: {
        global: 'globalThis',
      },
    },
  },
  build: {
    commonjsOptions: {
      transformMixedEsModules: true,
    },
    rollupOptions: {
      // Don't mark undici as external - we want to replace it with our shim
      // The alias will handle replacing undici imports with our shim
    },
  },
  server: {
    port: 3000,
    host: true,
    strictPort: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        timeout: 60000,
        ws: true, // Enable WebSocket support for SockJS fallback/polling
        configure: (proxy, _options) => {
          proxy.on('error', (err, _req, _res) => {
            // Only log non-ECONNRESET errors to reduce noise
            if (err.code !== 'ECONNRESET' && err.code !== 'ECONNREFUSED') {
              console.log('Proxy error:', err);
            }
          });
          proxy.on('proxyReq', (proxyReq, req, _res) => {
            console.log('Proxying request:', req.method, req.url);
          });
          proxy.on('proxyReqWs', (proxyReq, req, socket) => {
            console.log('Proxying WebSocket request:', req.url);
          });
        }
      },
      '/ws': {
        target: 'http://localhost:8080', // Route through API Gateway
        ws: true,
        changeOrigin: true,
        secure: false,
        configure: (proxy, _options) => {
          proxy.on('error', (err, _req, _res) => {
            // Only log non-ECONNRESET errors to reduce noise
            if (err.code !== 'ECONNRESET' && err.code !== 'ECONNREFUSED') {
              console.log('WebSocket proxy error:', err);
            }
          });
          proxy.on('proxyReqWs', (proxyReq, req, socket) => {
            console.log('Proxying WebSocket upgrade:', req.url);
          });
        }
      },
      '/internal': {
        target: 'http://localhost:8080', // Route through API Gateway
        changeOrigin: true,
        secure: false,
        timeout: 60000,
        configure: (proxy, _options) => {
          proxy.on('error', (err, _req, _res) => {
            // Only log non-ECONNRESET errors to reduce noise
            if (err.code !== 'ECONNRESET' && err.code !== 'ECONNREFUSED') {
              console.log('Internal API proxy error:', err);
            }
          });
          proxy.on('proxyReq', (proxyReq, req, _res) => {
            console.log('Proxying internal request:', req.method, req.url);
          });
        }
      }
    }
  }
})

