/**
 * 城市名称 → city_xx 编码映射
 * 覆盖中国主要城市（约 60+）
 */
const CITY_NAME_TO_ID: Record<string, string> = {
  // 直辖市
  '上海市': 'city_sh', '上海': 'city_sh',
  '北京市': 'city_bj', '北京': 'city_bj',
  '重庆市': 'city_cq', '重庆': 'city_cq',
  '天津市': 'city_tj', '天津': 'city_tj',
  // 省会 & 主要城市
  '广州市': 'city_gz', '广州': 'city_gz',
  '深圳市': 'city_sz', '深圳': 'city_sz',
  '杭州市': 'city_hz', '杭州': 'city_hz',
  '成都市': 'city_cd', '成都': 'city_cd',
  '武汉市': 'city_wh', '武汉': 'city_wh',
  '南京市': 'city_nj', '南京': 'city_nj',
  '苏州市': 'city_suzhou', '苏州': 'city_suzhou',
  '西安市': 'city_xa', '西安': 'city_xa',
  '长沙市': 'city_cs', '长沙': 'city_cs',
  '厦门市': 'city_xm', '厦门': 'city_xm',
  '福州市': 'city_fz', '福州': 'city_fz',
  '合肥市': 'city_hf', '合肥': 'city_hf',
  '济南市': 'city_jn', '济南': 'city_jn',
  '青岛市': 'city_qd', '青岛': 'city_qd',
  '大连市': 'city_dl', '大连': 'city_dl',
  '沈阳市': 'city_sy', '沈阳': 'city_sy',
  '哈尔滨市': 'city_hrb', '哈尔滨': 'city_hrb',
  '长春市': 'city_cc', '长春': 'city_cc',
  '郑州市': 'city_zz', '郑州': 'city_zz',
  '石家庄市': 'city_sjz', '石家庄': 'city_sjz',
  '太原市': 'city_ty', '太原': 'city_ty',
  '南昌市': 'city_nc', '南昌': 'city_nc',
  '昆明市': 'city_km', '昆明': 'city_km',
  '贵阳市': 'city_gy', '贵阳': 'city_gy',
  '南宁市': 'city_nn', '南宁': 'city_nn',
  '海口市': 'city_hk_hainan', '海口': 'city_hk_hainan',
  '兰州市': 'city_lz', '兰州': 'city_lz',
  '银川市': 'city_yc', '银川': 'city_yc',
  '西宁市': 'city_xining', '西宁': 'city_xining',
  '乌鲁木齐市': 'city_wlmq', '乌鲁木齐': 'city_wlmq',
  '呼和浩特市': 'city_hhht', '呼和浩特': 'city_hhht',
  '拉萨市': 'city_ls', '拉萨': 'city_ls',
  '无锡市': 'city_wx', '无锡': 'city_wx',
  '常州市': 'city_changzhou', '常州': 'city_changzhou',
  '宁波市': 'city_nb', '宁波': 'city_nb',
  '温州市': 'city_wz', '温州': 'city_wz',
  '东莞市': 'city_dg', '东莞': 'city_dg',
  '佛山市': 'city_fs', '佛山': 'city_fs',
  '珠海市': 'city_zhuhai', '珠海': 'city_zhuhai',
  '惠州市': 'city_huizhou', '惠州': 'city_huizhou',
  '中山市': 'city_zs', '中山': 'city_zs',
  '绍兴市': 'city_shaoxing', '绍兴': 'city_shaoxing',
  '南通市': 'city_nt', '南通': 'city_nt',
  '徐州市': 'city_xz', '徐州': 'city_xz',
  '洛阳市': 'city_ly', '洛阳': 'city_ly',
  '桂林市': 'city_gl', '桂林': 'city_gl',
  '三亚市': 'city_sanya', '三亚': 'city_sanya',
};

/**
 * 从高德逆地理编码的 addressComponent 中解析 cityId
 * 处理直辖市 city 字段为空的特殊情况
 */
export function resolveCityId(comp: {
  city?: string | any[];
  province?: string | any[];
  district?: string | any[];
}): string | undefined {
  if (!comp) return undefined;

  // 高德 REST API 对直辖市返回 city: []（空数组），需同时处理 string 和 array
  const cityStr = extractCityString(comp.city);

  // 先尝试 city 字段（普通城市）
  if (cityStr) {
    const code = matchCity(cityStr);
    if (code) return code;
  }

  // 直辖市 city 为空，用 province（如"上海市"）
  const provStr = extractCityString(comp.province);
  if (provStr) {
    const code = matchCity(provStr);
    if (code) return code;
  }

  // 最后尝试 district
  const distStr = extractCityString(comp.district);
  if (distStr) {
    const code = matchCity(distStr);
    if (code) return code;
  }

  return undefined;
}

/** 处理高德返回的 city/province/district：可能是 string 或 string[] */
function extractCityString(val?: string | any[]): string {
  if (!val) return '';
  if (typeof val === 'string') return val;
  if (Array.isArray(val) && val.length > 0 && typeof val[0] === 'string') return val[0];
  return '';
}

/** 在映射表中查找城市编码 */
function matchCity(name: string): string | undefined {
  if (!name) return undefined;
  // 直接匹配
  if (CITY_NAME_TO_ID[name]) return CITY_NAME_TO_ID[name];
  // 去"市"匹配
  const trimmed = name.replace(/市$/, '');
  if (CITY_NAME_TO_ID[trimmed]) return CITY_NAME_TO_ID[trimmed];
  // 去"省"/"自治区"/"特别行政区"后尝试
  const stripped = name.replace(/(省|自治区|特别行政区)$/, '');
  if (stripped !== name && CITY_NAME_TO_ID[stripped]) return CITY_NAME_TO_ID[stripped];
  return undefined;
}

/** 编码 → 城市中文名（取带"市"的名称，如 city_sh → 上海市） */
export const CITY_ID_TO_NAME: Record<string, string> = (() => {
  const m: Record<string, string> = {};
  for (const [name, code] of Object.entries(CITY_NAME_TO_ID)) {
    if (name.endsWith('市') && !m[code]) m[code] = name;
  }
  return m;
})();

/** 城市中文名 → 编码（如"上海市" → city_sh） */
export function cityNameToId(name: string): string | undefined {
  if (!name) return undefined;
  if (CITY_NAME_TO_ID[name]) return CITY_NAME_TO_ID[name];
  const trimmed = name.replace(/市$/, '');
  if (CITY_NAME_TO_ID[trimmed]) return CITY_NAME_TO_ID[trimmed];
  return undefined;
}

/** 城市编码 → 中文名（如 city_sh → 上海市），未知编码原样返回 */
export function cityIdToName(code: string | undefined): string {
  if (!code) return '';
  return CITY_ID_TO_NAME[code] || code;
}

// ---- WGS-84 → GCJ-02 坐标转换 ----

const PI = Math.PI;
const A = 6378245.0; // 长半轴
const EE = 0.00669342162296594323; // 扁率

function isOutOfChina(lng: number, lat: number): boolean {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
}

function transformLat(x: number, y: number): number {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0;
  ret += ((20.0 * Math.sin(y * PI) + 40.0 * Math.sin((y / 3.0) * PI)) * 2.0) / 3.0;
  ret += ((160.0 * Math.sin((y / 12.0) * PI) + 320.0 * Math.sin((y * PI) / 30.0)) * 2.0) / 3.0;
  return ret;
}

function transformLng(x: number, y: number): number {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0;
  ret += ((20.0 * Math.sin(x * PI) + 40.0 * Math.sin((x / 3.0) * PI)) * 2.0) / 3.0;
  ret += ((150.0 * Math.sin((x / 12.0) * PI) + 300.0 * Math.sin((x / 30.0) * PI)) * 2.0) / 3.0;
  return ret;
}

/**
 * WGS-84 坐标 → GCJ-02 坐标
 * 浏览器原生定位返回的是 WGS-84，在中国境内需要转换为 GCJ-02 才能在高德地图上正确显示
 * @returns [lng, lat] GCJ-02 坐标
 */
export function wgs84ToGcj02(lng: number, lat: number): [number, number] {
  if (isOutOfChina(lng, lat)) return [lng, lat];

  let dlat = transformLat(lng - 105.0, lat - 35.0);
  let dlng = transformLng(lng - 105.0, lat - 35.0);

  const radlat = (lat / 180.0) * PI;
  let magic = Math.sin(radlat);
  magic = 1 - EE * magic * magic;
  const sqrtmagic = Math.sqrt(magic);

  dlat = (dlat * 180.0) / (((A * (1 - EE)) / (magic * sqrtmagic)) * PI);
  dlng = (dlng * 180.0) / ((A / sqrtmagic) * Math.cos(radlat) * PI);

  return [lng + dlng, lat + dlat];
}
