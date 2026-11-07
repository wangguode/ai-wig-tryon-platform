import path from "path";
import { fileURLToPath } from "url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { viteSingleFile } from "vite-plugin-singlefile";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), viteSingleFile()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
  server: {
    proxy: {
      // 开发期将 /api、/files、/admin 代理到后端服务，避免跨域
      // /admin 是运营管理后台（资产分层架构）的接口前缀，未代理时 vite 会
      // 把它当成 SPA 路由 fallback 返回 index.html，前端 fetch 解析 HTML 报
      // "Unexpected token '<'" — 这是登录页的常见症状。
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/files": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/admin": {
        target: "http://localhost:8080",
        changeOrigin: true,
        // 浏览器直接打开 /admin 时进 SPA 登录页；/admin/wig-assets 等 API 仍走后端
        bypass(req) {
          const path = (req.url ?? "").split("?")[0];
          if (path === "/admin" || path === "/admin/") return "/index.html";
          // 误粘贴中文后缀等脏路径（如 /admin（登录）也回 SPA
          if (path.startsWith("/admin") && !/^\/admin\/[\w-]+/.test(path)) {
            return "/index.html";
          }
        },
      },
    },
  },
});
