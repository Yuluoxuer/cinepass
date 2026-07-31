import { defineConfig } from 'umi';

export default defineConfig({
  plugins: ['@umijs/plugins/dist/antd', '@umijs/plugins/dist/request'],
  routes: [
    {
      path: '/',
      component: '@/layouts/ClientLayout',
      routes: [
        { path: '/', component: '@/pages/home/index' },
        { path: '/movies', component: '@/pages/movies/index' },
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
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
  request: {},
  antd: {
    theme: {
      token: {
        colorPrimary: '#0f8f84',
        borderRadius: 3,
        fontFamily: "'Manrope', 'Noto Sans SC', 'PingFang SC', sans-serif",
      },
    },
  },
  esbuildMinifyIIFE: true,
});
