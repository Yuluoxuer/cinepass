/**
 * 局域网 IPv4 自动探测。
 *
 * 二维码里的地址（/m/pay、/m/redeem 等）必须能被顾客手机访问到，
 * 手机与电脑在同一局域网，所以 host 必须是本机局域网 IP，
 * 不能是 localhost / 127.0.0.1（手机会把 localhost 解析成手机自己，从而连不上）。
 *
 * 探测优先级：
 * 1. 构建/启动时由 .umirc.ts 通过 define 注入的 __LAN_IP__（用 os.networkInterfaces() 探测，最可靠）
 * 2. 运行时 WebRTC 探测（新版 Chrome 会返回 mDNS 而探测不到真实 IP，仅作兜底）
 *
 * __LAN_IP__ 的全局类型声明见 src/typings.d.ts。
 */

let cachedIp: string | null = null;
let webrtcStarted = false;

/** host 是否为本地回环（localhost / 127.x / 0.0.0.0 / [::1]） */
function isLoopback(host: string): boolean {
  return (
    host.startsWith('localhost') ||
    host.startsWith('127.') ||
    host.startsWith('0.0.0.0') ||
    host.startsWith('[::1]') ||
    host === '::1'
  );
}

/** 是否为可被局域网手机访问的私网 IPv4（10.x / 172.16-31.x / 192.168.x） */
function isPrivateIpv4(ip: string): boolean {
  return /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(ip);
}

/** 同步读取局域网 IP：优先构建注入，其次 WebRTC 已探测到的结果 */
export function getLanIp(): string | null {
  if (cachedIp) return cachedIp;
  if (typeof __LAN_IP__ === 'string') {
    // 兼容 define 双重转义的历史值（首尾可能带一对字面量引号）
    const ip = __LAN_IP__.replace(/^"+|"+$/g, '');
    if (isPrivateIpv4(ip)) cachedIp = ip;
  }
  return cachedIp;
}

/** 模块加载时后台用 WebRTC 探测真实内网 IP（探测不到时静默回退） */
function startWebRtcDetect(): void {
  if (webrtcStarted || typeof RTCPeerConnection === 'undefined') return;
  webrtcStarted = true;
  try {
    const pc = new RTCPeerConnection({ iceServers: [] });
    pc.createDataChannel('');
    const settle = (ip: string | null): void => {
      if (ip && isPrivateIpv4(ip) && !cachedIp) cachedIp = ip;
      try {
        pc.close();
      } catch {
        /* noop */
      }
    };
    const timer = window.setTimeout(() => settle(null), 2000);
    pc.onicecandidate = (e) => {
      if (!e.candidate) {
        window.clearTimeout(timer);
        settle(null);
        return;
      }
      const m = /([0-9]{1,3}(\.[0-9]{1,3}){3})/.exec(e.candidate.candidate);
      if (m && isPrivateIpv4(m[1])) {
        window.clearTimeout(timer);
        settle(m[1]);
      }
    };
    pc.createOffer()
      .then((o) => pc.setLocalDescription(o))
      .catch(() => {
        window.clearTimeout(timer);
        settle(null);
      });
  } catch {
    /* WebRTC 不可用 */
  }
}

startWebRtcDetect();

/**
 * 把当前页面 host 中的本地回环地址替换为本机局域网 IP（保留端口）。
 * host 本身已是局域网 IP / 域名时原样返回。
 */
export function reachableHost(host: string): string {
  if (!isLoopback(host)) return host;
  const ip = getLanIp();
  if (!ip) return host;
  const port = window.location.port ? `:${window.location.port}` : '';
  return `${ip}${port}`;
}
