import { create } from 'zustand';
import { wgs84ToGcj02, resolveCityId } from '@/components/AmapLocationPicker/cityMap';
import { AMAP_WEB_KEY } from '@/constants';

const STORAGE_KEY = 'miaoyu_location';
const CITY_KEY = 'miaoyu_city_id';
/** 坐标有效期：15 分钟 */
const LOCATION_TTL_MS = 15 * 60 * 1000;

type Permission = 'idle' | 'granted' | 'denied' | 'unavailable';

interface StoredLocation {
  lat: number;
  lng: number;
  cityId?: string;
  cityName?: string;
  updatedAt: number;
}

interface LocationState {
  lat: number | null;
  lng: number | null;
  cityId: string | null;
  cityName: string | null;
  permission: Permission;
  locating: boolean;
  lastUpdated: number | null;
  requestLocation: () => Promise<{ lat: number; lng: number }>;
  /** 返回有效期内坐标；未定位/过期返回 null（不自动重定位，避免静默权限请求） */
  ensureLocation: () => { lat: number; lng: number } | null;
  clearLocation: () => void;
}

function readStored(): StoredLocation | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredLocation;
    if (typeof parsed.lat !== 'number' || typeof parsed.lng !== 'number') return null;
    return parsed;
  } catch {
    return null;
  }
}

function writeStored(loc: StoredLocation) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(loc));
  } catch {
    /* ignore */
  }
}

/** 逆地理编码：定位成功后补齐城市名/城市 ID，失败不阻塞定位 */
async function reverseGeocode(lng: number, lat: number): Promise<{ cityId?: string; cityName?: string }> {
  try {
    const params = `key=${AMAP_WEB_KEY}&location=${lng},${lat}&output=JSON&radius=1000&extensions=all`;
    const urls = [
      `https://restapi.amap.com/v3/geocode/regeo?${params}`,
      `/amap-api/v3/geocode/regeo?${params}`,
    ];
    for (const url of urls) {
      const res = await fetch(url);
      const data = await res.json();
      if (data?.status === '1' && data.regeocode?.addressComponent) {
        const ac = data.regeocode.addressComponent;
        const pick = (v: unknown): string => {
          if (Array.isArray(v)) return v[0] ? String(v[0]) : '';
          return v ? String(v) : '';
        };
        const cityName = pick(ac.city) || pick(ac.province) || pick(ac.district) || '';
        return { cityId: resolveCityId(ac), cityName };
      }
    }
  } catch {
    /* 网络失败不阻塞定位 */
  }
  return {};
}

function browserGeolocation(timeout = 10_000): Promise<{ lng: number; lat: number }> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('浏览器不支持定位'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lng: pos.coords.longitude, lat: pos.coords.latitude }),
      reject,
      { enableHighAccuracy: false, timeout, maximumAge: 60_000 },
    );
  });
}

/** 两点间球面距离（km，Haversine），用于判断浏览器定位与 IP 定位是否一致 */
function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371;
  const rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(lat2 - lat1);
  const dLng = rad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

/** 是否在中国境内（与 cityMap.isOutOfChina 边界一致） */
function isInChina(lng: number, lat: number): boolean {
  return lng >= 72.004 && lng <= 137.8347 && lat >= 0.8293 && lat <= 55.8271;
}

/**
 * 高德 IP 定位（REST /v3/ip）：基于真实出口 IP 判定位置，无需浏览器授权。
 * 用于纠正浏览器旧缓存定位（如梯子残留的新加坡坐标）。
 * 返回 GCJ-02 城市中心坐标；失败返回 null。
 */
async function amapIpLocation(): Promise<{ lng: number; lat: number } | null> {
  const params = `key=${AMAP_WEB_KEY}`;
  const urls = [
    `https://restapi.amap.com/v3/ip?${params}`,
    `/amap-api/v3/ip?${params}`,
  ];
  for (const url of urls) {
    try {
      const res = await fetch(url);
      const data = await res.json();
      if (data?.status !== '1') continue;
      // location 字段（个别返回）："lng,lat"
      if (typeof data.location === 'string' && data.location.includes(',')) {
        const [lng, lat] = data.location.split(',').map(Number);
        if (Number.isFinite(lng) && Number.isFinite(lat)) return { lng, lat };
      }
      // 标准返回：rectangle 城市包围盒，取中心
      if (typeof data.rectangle === 'string' && data.rectangle.includes(';')) {
        const [a, b] = data.rectangle.split(';');
        const [lng1, lat1] = a.split(',').map(Number);
        const [lng2, lat2] = b.split(',').map(Number);
        if ([lng1, lat1, lng2, lat2].every(Number.isFinite)) {
          return { lng: (lng1 + lng2) / 2, lat: (lat1 + lat2) / 2 };
        }
      }
    } catch {
      /* 尝试下一个 URL */
    }
  }
  return null;
}

function initFromStorage() {
  const stored = readStored();
  if (!stored) {
    return { lat: null, lng: null, cityId: null, cityName: null, permission: 'idle' as Permission, lastUpdated: null };
  }
  return {
    lat: stored.lat,
    lng: stored.lng,
    cityId: stored.cityId || null,
    cityName: stored.cityName || null,
    permission: 'granted' as Permission,
    lastUpdated: stored.updatedAt,
  };
}

export const useLocationStore = create<LocationState>((set, get) => ({
  ...initFromStorage(),
  locating: false,

  requestLocation: async () => {
    if (get().locating) {
      // 已有定位请求进行中，复用当前坐标或等待
      if (get().lat != null && get().lng != null) return { lat: get().lat!, lng: get().lng! };
    }
    set({ locating: true });
    const store = (loc: { lng: number; lat: number }) => {
      const [gcjLng, gcjLat] = wgs84ToGcj02(loc.lng, loc.lat);
      const now = Date.now();
      set({ lat: gcjLat, lng: gcjLng, permission: 'granted', lastUpdated: now, locating: false });
      writeStored({ lat: gcjLat, lng: gcjLng, updatedAt: now });
      return { lat: gcjLat, lng: gcjLng };
    };

    try {
      // 1) 先走高德 IP 定位：无需授权，基于真实出口 IP，纠正梯子残留缓存
      const ipLoc = await amapIpLocation();
      console.log('[定位调试] IP定位结果:', ipLoc);

      // 2) 再试浏览器精确定位（可能返回旧缓存/境外坐标）
      let browserLoc: { lng: number; lat: number } | null = null;
      try {
        browserLoc = await browserGeolocation();
        console.log('[定位调试] 浏览器定位结果:', browserLoc);
      } catch (err) {
        console.log('[定位调试] 浏览器定位失败:', err);
        /* 浏览器定位失败则用 IP 定位兜底 */
      }

      // 3) 交叉校验：浏览器结果境外、或与 IP 城市相距过远 → 判定不可信，丢弃
      if (browserLoc && ipLoc) {
        const dist = haversineKm(browserLoc.lat, browserLoc.lng, ipLoc.lat, ipLoc.lng);
        console.log('[定位调试] 浏览器与IP定位距离:', dist.toFixed(2), 'km');
        console.log('[定位调试] 浏览器定位是否在中国:', isInChina(browserLoc.lng, browserLoc.lat));
        if (!isInChina(browserLoc.lng, browserLoc.lat) || dist > 800) {
          console.log('[定位调试] 浏览器定位被丢弃（境外或距离过远）');
          browserLoc = null; // 丢弃不可信结果
        }
      }

      const final = browserLoc ?? ipLoc;
      console.log('[定位调试] 最终使用的定位:', final, '来源:', browserLoc ? '浏览器' : 'IP');
      if (!final) {
        // 浏览器与 IP 定位都失败
        set({ permission: 'unavailable', locating: false });
        throw new Error('定位失败，请检查网络');
      }
      const result = store(final);
      // 异步补齐城市信息；不阻塞返回
      void reverseGeocode(result.lng, result.lat).then((city) => {
        console.log('[定位调试] 逆地理编码结果:', city);
        if (!city.cityId && !city.cityName) return;
        set((s) => ({
          cityId: city.cityId ?? s.cityId,
          cityName: city.cityName ?? s.cityName,
        }));
        const cur = get();
        writeStored({
          lat: cur.lat!,
          lng: cur.lng!,
          cityId: cur.cityId || undefined,
          cityName: cur.cityName || undefined,
          updatedAt: cur.lastUpdated!,
        });
        if (city.cityId) {
          try {
            localStorage.setItem(CITY_KEY, city.cityId);
          } catch {
            /* ignore */
          }
        }
      });
      return result;
    } catch (error) {
      const msg = error instanceof Error ? error.message : '';
      let permission: Permission = 'denied';
      if (typeof error === 'object' && error !== null && 'code' in error) {
        const code = (error as GeolocationPositionError).code;
        if (code === 1) permission = 'denied'; // PERMISSION_DENIED
        else if (code === 2) permission = 'denied'; // POSITION_UNAVAILABLE
        else permission = 'idle'; // TIMEOUT(3) 可重试
      } else if (msg.includes('不支持')) {
        permission = 'unavailable';
      }
      set({ permission, locating: false });
      throw error;
    }
  },

  ensureLocation: () => {
    const { lat, lng, lastUpdated } = get();
    if (lat == null || lng == null) return null;
    if (lastUpdated != null && Date.now() - lastUpdated > LOCATION_TTL_MS) return null;
    return { lat, lng };
  },

  clearLocation: () => {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
    set({ lat: null, lng: null, cityId: null, cityName: null, permission: 'idle', lastUpdated: null });
  },
}));
