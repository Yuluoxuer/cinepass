import { defineConfig } from 'umi';
import os from 'os';

/**
 * 探测本机最可能被手机访问到的局域网 IPv4：
 * 排除虚拟网卡（VMware/VirtualBox/Hyper-V 的 MAC）、排除网关类地址（如 192.168.x.1），
 * 优先私网地址。可用环境变量 LAN_IP 强制指定。
 */
function detectLanIpv4(): string {
  const forced = process.env.LAN_IP;
  if (forced) return forced;
  const virtualMac = /^(00:50:56|00:0c:29|08:00:27|00:15:5d|00:05:69|00:1c:42)/i;
  const isGatewayLike = (ip: string) => {
    const last = ip.split('.').pop();
    return last === '1' && /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(ip);
  };
  const isPrivate = (ip: string) =>
    /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(ip);
  const all: Array<{ ip: string; mac: string }> = [];
  const ifaces = os.networkInterfaces();
  for (const name of Object.keys(ifaces)) {
    for (const info of ifaces[name] || []) {
      if (info.family === 'IPv4' && !info.internal) {
        all.push({ ip: info.address, mac: info.mac });
      }
    }
  }
  if (!all.length) return 'localhost';
  const score = (c: { ip: string; mac: string }) =>
    (c.mac && virtualMac.test(c.mac) ? 0 : 4) + (isGatewayLike(c.ip) ? 0 : 2) + (isPrivate(c.ip) ? 1 : 0);
  all.sort((a, b) => score(b) - score(a));
  return all[0].ip;
}

/** 仅开发环境注入局域网 IP；生产构建 host 不会是 localhost，无需写入包体 */
const LAN_IP = process.env.NODE_ENV === 'production' ? '' : detectLanIpv4();

export default defineConfig({
  plugins: ['@umijs/plugins/dist/antd', '@umijs/plugins/dist/request'],
  // 注意：umi 的 define 会自动 JSON.stringify 值，这里直接传纯字符串，
  // 不能再手动 JSON.stringify，否则会双重转义成带引号的字符串。
  define: { __LAN_IP__: LAN_IP },
  routes: [
    { path: '/error', component: '@/pages/error', layout: false },
    {
      path: '/',
      component: '@/layouts/ClientLayout',
      routes: [
        { path: '/', component: '@/pages/home/index' },
        { path: '/movies', component: '@/pages/movies/index' },
        { path: '/search', component: '@/pages/search/index' },
        { path: '/movies/:movieId', component: '@/pages/movies/detail' },
        { path: '/cinemas', component: '@/pages/cinemas/index' },
        { path: '/booking/cinemas', component: '@/pages/booking/cinemas' },
        { path: '/booking/shows', component: '@/pages/booking/shows' },
        { path: '/booking/seats', component: '@/pages/booking/seats' },
        { path: '/booking/confirm', component: '@/pages/booking/confirm' },
        { path: '/booking/pay', component: '@/pages/booking/pay' },
        { path: '/booking/ticket', component: '@/pages/booking/ticket' },
        { path: '/me', component: '@/pages/me/index' },
        { path: '/me/orders', component: '@/pages/me/orders' },
        { path: '/me/orders/:orderId', component: '@/pages/me/orderDetail' },
        { path: '/me/want-see', component: '@/pages/me/wantSee' },
        { path: '/agent', component: '@/pages/agent/index' },
      ],
    },
    { path: '/m/pay/:orderId', component: '@/pages/mobile/pay', layout: false },
    { path: '/m/redeem/:orderId', component: '@/pages/mobile/redeem', layout: false },
    { path: '/admin/login', component: '@/pages/admin/login', layout: false },
    {
      path: '/admin',
      component: '@/layouts/AdminLayout',
      routes: [
        { path: '/admin', component: '@/pages/admin/dashboard' },
        { path: '/admin/movies', component: '@/pages/admin/movies' },
        { path: '/admin/movies/new', component: '@/pages/admin/movieForm' },
        { path: '/admin/movies/:movieId', component: '@/pages/admin/movieForm' },
        { path: '/admin/cinemas', component: '@/pages/admin/cinemas' },
        { path: '/admin/cinemas/new', component: '@/pages/admin/cinemaForm' },
        { path: '/admin/cinemas/:cinemaId', component: '@/pages/admin/cinemaForm' },
        { path: '/admin/cinemas/:cinemaId/halls', component: '@/pages/admin/halls' },
        { path: '/admin/seat-maps', component: '@/pages/admin/seatMaps' },
        { path: '/admin/seat-maps/new', component: '@/pages/admin/seatMapEditor' },
        { path: '/admin/seat-maps/:seatMapId', component: '@/pages/admin/seatMapEditor' },
        { path: '/admin/shows', component: '@/pages/admin/shows' },
        { path: '/admin/orders', component: '@/pages/admin/orders' },
        { path: '/admin/tickets/verify', component: '@/pages/admin/ticketVerify' },
        { path: '/admin/users', component: '@/pages/admin/users' },
      ],
    },
  ],
  npmClient: 'pnpm',
  proxy: {
    // Agent 服务独立端口 8001，需在通用 /api 转发之前命中（umi 按最长前缀匹配）
    '/api/v1/agent': {
      target: 'http://localhost:8001',
      changeOrigin: true,
    },
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    '/uploads': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    '/amap-api': {
      target: 'https://restapi.amap.com',
      changeOrigin: true,
      pathRewrite: { '^/amap-api': '' },
    },
  },
  request: {},
  antd: {
    theme: {
      token: {
        colorPrimary: '#37b7a5',
        colorError: '#d9423a',
        colorWarning: '#f2b84b',
        borderRadius: 4,
        fontFamily:
          "'MiSans', 'HarmonyOS Sans SC', 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif",
        colorBgLayout: '#f6f1e8',
        colorBgContainer: '#fffdf9',
      },
    },
  },
  esbuildMinifyIIFE: true,
});
