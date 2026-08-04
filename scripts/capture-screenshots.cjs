/**
 * Capture frontend screenshots for docs/02 系分.
 * Usage: node scripts/capture-screenshots.cjs
 */
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const BASE = process.env.FRONT_URL || 'http://localhost:8000';
const OUT = path.resolve(__dirname, '../../docs/screenshots/frontend');

async function shot(page, name) {
  const file = path.join(OUT, `${name}.png`);
  await page.waitForTimeout(600);
  await page.screenshot({ path: file, fullPage: false });
  console.log('saved', file);
}

async function enableMock(page) {
  await page.goto(`${BASE}/`);
  await page.evaluate(() => {
    localStorage.setItem('miaoyu_use_mock', '1');
  });
}

async function mockLogin(page, account) {
  // Use mock API via page.evaluate fetch to get token, then seed auth store keys
  await page.goto(`${BASE}/`);
  await page.evaluate(async (acc) => {
    localStorage.setItem('miaoyu_use_mock', '1');
    // Call through window — mock is client-side only, so simulate login payload from seed
    const users = {
      演示用户甲: {
        userId: 'u1',
        nickname: '演示用户甲',
        phone: '138****0001',
        role: 'user',
        cinemaId: null,
        avatarUrl: null,
      },
      运营小王: {
        userId: 'u_staff',
        nickname: '运营小王',
        phone: '139****0002',
        role: 'staff',
        cinemaId: 'c12',
        avatarUrl: null,
      },
      系统管理员: {
        userId: 'u_admin',
        nickname: '系统管理员',
        phone: '137****0003',
        role: 'admin',
        cinemaId: null,
        avatarUrl: null,
      },
    };
    const user = users[acc];
    if (!user) throw new Error('unknown account ' + acc);
    const token = 'tok_shot_' + user.userId;
    localStorage.setItem('miaoyu_access_token', token);
    localStorage.setItem('miaoyu_token_expire_at', String(Date.now() + 3600_000));
    localStorage.setItem('miaoyu_user', JSON.stringify(user));
  }, account);
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await context.newPage();

  await enableMock(page);
  await page.reload();
  await page.waitForLoadState('networkidle').catch(() => {});
  await shot(page, '01-home');

  await page.goto(`${BASE}/movies`);
  await page.waitForLoadState('networkidle').catch(() => {});
  await shot(page, '02-movies');

  await page.goto(`${BASE}/movies/m100`);
  await page.waitForLoadState('networkidle').catch(() => {});
  await shot(page, '03-movie-detail');

  await page.goto(`${BASE}/cinemas`);
  await page.waitForLoadState('networkidle').catch(() => {});
  await shot(page, '04-cinemas');

  await page.goto(`${BASE}/booking/cinemas?movieId=m100`);
  await page.waitForLoadState('networkidle').catch(() => {});
  await shot(page, '05-booking-cinemas');

  const date = new Date().toISOString().slice(0, 10);
  await page.goto(`${BASE}/booking/shows?movieId=m100&cinemaId=c12&date=${date}`);
  await page.waitForLoadState('networkidle').catch(() => {});
  await shot(page, '06-booking-shows');

  await page.goto(`${BASE}/booking/seats?showId=s900`);
  await page.waitForLoadState('networkidle').catch(() => {});
  await shot(page, '07-booking-seats');

  await page.goto(`${BASE}/agent`);
  await page.waitForLoadState('networkidle').catch(() => {});
  await shot(page, '08-agent');

  await mockLogin(page, '演示用户甲');
  await page.goto(`${BASE}/me`);
  await page.waitForLoadState('networkidle').catch(() => {});
  await shot(page, '09-me');

  // staff admin — real login so mock token map is populated
  await page.goto(`${BASE}/admin/login`);
  await page.evaluate(() => {
    localStorage.clear();
    localStorage.setItem('miaoyu_use_mock', '1');
  });
  await page.reload();
  await page.waitForSelector('button[type="submit"]', { timeout: 15000 });
  await page.locator('input').nth(0).fill('运营小王');
  await page.locator('input[type="password"]').fill('demo123456');
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/\/admin(?!\/login)/, { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(1000);
  await shot(page, '10-admin-dashboard-staff');

  await page.goto(`${BASE}/admin/cinemas`);
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(500);
  await shot(page, '11-admin-cinemas-staff');

  await page.goto(`${BASE}/admin/shows`);
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(800);
  await shot(page, '12-admin-shows-staff');

  await page.goto(`${BASE}/admin/seat-maps`);
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(500);
  await shot(page, '13-admin-seat-maps');

  await page.goto(`${BASE}/admin/movies`);
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(500);
  await shot(page, '14-admin-movies');

  // admin
  await page.goto(`${BASE}/admin/login`);
  await page.evaluate(() => {
    localStorage.clear();
    localStorage.setItem('miaoyu_use_mock', '1');
  });
  await page.reload();
  await page.waitForSelector('button[type="submit"]', { timeout: 15000 });
  await page.locator('input').nth(0).fill('系统管理员');
  await page.locator('input[type="password"]').fill('demo123456');
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/\/admin(?!\/login)/, { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(1000);

  await page.goto(`${BASE}/admin/cinemas`);
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(500);
  await shot(page, '15-admin-cinemas-admin');

  await page.goto(`${BASE}/admin/users`);
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(800);
  await shot(page, '16-admin-users');

  await page.goto(`${BASE}/admin/login`);
  await page.waitForTimeout(400);
  await shot(page, '18-admin-login');

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${BASE}/m/pay/o_demo?t=demo`);
  await page.waitForTimeout(800);
  await shot(page, '17-mobile-pay');

  await browser.close();
  console.log('done');
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
