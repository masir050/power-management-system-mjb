import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    // 3000 被占用时直接报错退出，而不是悄悄换到 3001——
    // 端口被占通常意味着上一次的 vite 没关干净，按报错里的提示清理后再启动。
    strictPort: true,
    // Vite 的 SPA 回退由 appType: 'spa'（默认值）负责，不需要 webpack-dev-server 的 historyApiFallback
    appType: 'spa',
    proxy: {
      // 所有 /api 请求统一走网关（8080）：JWT 校验、Sentinel 限流、按前缀 lb 转发都在网关完成。
      // 直连 8082(power-device) 会让登录 404，且绕过鉴权/限流。
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // WebSocket 告警推送：网关按 lb://power-alert 转发 /ws/alert，需单独开 ws 代理
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true
      }
    }
  }
})
