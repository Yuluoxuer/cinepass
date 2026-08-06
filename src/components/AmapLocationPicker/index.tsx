import React, { useEffect, useRef, useState, useCallback } from 'react';
import { Spin, Button, Input, message } from 'antd';
import { AimOutlined } from '@ant-design/icons';
import AMapLoader from '@amap/amap-jsapi-loader';
import { AMAP_API_KEY, AMAP_WEB_KEY, AMAP_API_VERSION, DEFAULT_MAP_CENTER } from '@/constants';
import { resolveCityId, wgs84ToGcj02 } from './cityMap';
import type { AmapLocationPickerProps } from './interface';
import styles from './index.less';

interface TipItem {
  id: string;
  name: string;
  district: string;
  address: string;
  /** 经纬度字符串 "lng,lat"（REST API 返回格式） */
  location: string;
}

const AmapLocationPicker: React.FC<AmapLocationPickerProps> = ({
  onSelect,
  cityName,
  initialLng,
  initialLat,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<any>(null);
  const markerRef = useRef<any>(null);
  const amapNSRef = useRef<any>(null);
  const mountedRef = useRef(false);
  const searchTimerRef = useRef<number | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const reverseAbortControllerRef = useRef<AbortController | null>(null);
  const searchRequestIdRef = useRef(0);
  const reverseRequestIdRef = useRef(0);
  const onSelectRef = useRef(onSelect);
  const isInteractingWithDropdown = useRef(false);

  const [sdkLoading, setSdkLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [mapReady, setMapReady] = useState(false);
  const [geocoding, setGeocoding] = useState(false);
  const [locating, setLocating] = useState(false);
  const [searchValue, setSearchValue] = useState('');
  const [searchTips, setSearchTips] = useState<TipItem[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [tipIndex, setTipIndex] = useState(0);
  const [selectingPoi, setSelectingPoi] = useState(false);
  const [resolvedCityName, setResolvedCityName] = useState('');
  const searchCityRef = useRef('');

  // 搜索仅采用表单城市（用户显式填写）；自动定位得到的 resolvedCityName 不限制搜索范围，
  // 避免用户物理位置（如湘潭）干扰对其他城市（如上海）的搜索。
  // 地图点击/POI 选中后逆地理会通过 onSelect 回填表单 cityName，后续搜索自然限定到该城市。
  searchCityRef.current = cityName?.trim() || '';

  useEffect(() => {
    onSelectRef.current = onSelect;
  }, [onSelect]);

  useEffect(() => {
    // 城市变更后使旧搜索失效，避免其结果跨城市写回下拉框。
    searchRequestIdRef.current += 1;
    if (searchTimerRef.current) {
      window.clearTimeout(searchTimerRef.current);
      searchTimerRef.current = null;
    }
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    setSearchTips([]);
    setShowDropdown(false);
    setTipIndex(0);
  }, [cityName, resolvedCityName]);

  // ---- 生命周期 ----
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      if (searchTimerRef.current) {
        window.clearTimeout(searchTimerRef.current);
        searchTimerRef.current = null;
      }
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
      reverseAbortControllerRef.current?.abort();
      reverseAbortControllerRef.current = null;
      if (mapInstanceRef.current) {
        mapInstanceRef.current.destroy();
        mapInstanceRef.current = null;
      }
    };
  }, []);

  // ---- 初始化 SDK + 地图 ----
  useEffect(() => {
    setSdkLoading(true);
    setLoadError(null);

    AMapLoader.load({
      key: AMAP_API_KEY,
      version: AMAP_API_VERSION,
      plugins: ['AMap.Geocoder', 'AMap.Geolocation'],
    })
      .then((AMap: any) => {
        if (!mountedRef.current || !containerRef.current) return;

        amapNSRef.current = AMap;

        const center: [number, number] = [
          initialLng ?? DEFAULT_MAP_CENTER.lng,
          initialLat ?? DEFAULT_MAP_CENTER.lat,
        ];

        const map = new AMap.Map(containerRef.current, {
          zoom: 13,
          center,
          resizeEnable: true,
        });

        map.on('click', (e: any) => {
          handleMapClick(e.lnglat.getLng(), e.lnglat.getLat());
        });

        mapInstanceRef.current = map;
        setMapReady(true);
        setSdkLoading(false);
      })
      .catch((err: Error) => {
        console.error('高德地图加载失败:', err);
        if (!mountedRef.current) return;
        setLoadError('地图加载失败，请检查网络或 API Key 配置');
        setSdkLoading(false);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ---- 更新地图中心（initialLng/Lat 变化时） ----
  useEffect(() => {
    if (mapInstanceRef.current && (initialLng || initialLat)) {
      mapInstanceRef.current.setCenter([
        initialLng ?? DEFAULT_MAP_CENTER.lng,
        initialLat ?? DEFAULT_MAP_CENTER.lat,
      ]);
    }
  }, [initialLng, initialLat]);

  // ---- 在地图上放置/更新标记 ----
  const placeMarker = useCallback((lng: number, lat: number) => {
    const AMap = amapNSRef.current;
    const map = mapInstanceRef.current;
    if (!AMap || !map) return;

    if (markerRef.current) {
      markerRef.current.setMap(null);
      markerRef.current = null;
    }
    markerRef.current = new AMap.Marker({ position: [lng, lat], map });
  }, []);

  // ---- 逆地理编码并填充（点击地图 & 搜索选中都走这里） ----
  const reverseGeocodeAndFill = useCallback(
    (lng: number, lat: number) => {
      reverseAbortControllerRef.current?.abort();
      const requestId = reverseRequestIdRef.current + 1;
      reverseRequestIdRef.current = requestId;
      const controller = new AbortController();
      reverseAbortControllerRef.current = controller;
      setGeocoding(true);

      const params =
        `key=${AMAP_WEB_KEY}&location=${lng},${lat}&output=JSON&radius=1000&extensions=all`;
      const urls = [
        `https://restapi.amap.com/v3/geocode/regeo?${params}`,
        `/amap-api/v3/geocode/regeo?${params}`,
      ];

      const fallbackCoords = () => {
        if (!mountedRef.current || reverseRequestIdRef.current !== requestId) return;
        setGeocoding(false);
        onSelectRef.current({
          name: `坐标点 (${lng.toFixed(6)}, ${lat.toFixed(6)})`,
          address: `${lat.toFixed(6)}, ${lng.toFixed(6)}`,
          lat,
          lng,
        });
      };

      const attempt = (idx: number) => {
        if (!mountedRef.current || controller.signal.aborted || reverseRequestIdRef.current !== requestId) return;
        if (idx >= urls.length) {
          message.warning('地址解析失败，请检查 Web 服务 Key 是否有效');
          fallbackCoords();
          return;
        }

        fetch(urls[idx], { signal: controller.signal })
          .then((res) => res.json())
          .then((data: any) => {
            if (!mountedRef.current || controller.signal.aborted || reverseRequestIdRef.current !== requestId) return;

            if (data.status === '1' && data.regeocode) {
              setGeocoding(false);
              const regeo = data.regeocode;
              const ac = regeo.addressComponent || {};
              const cityName = extractCityName(ac);
              setResolvedCityName(cityName);
              onSelectRef.current({
                name: extractNameFromRegeo(regeo),
                address: extractAddress(regeo),
                lat,
                lng,
                cityName,
                cityId: resolveCityId(ac),
              });
              message.success(
                cityName ? `已自动填充位置信息（${cityName}）` : '已自动填充位置信息',
              );
            } else {
              attempt(idx + 1);
            }
          })
          .catch((error) => {
            if (error?.name === 'AbortError' || controller.signal.aborted) return;
            attempt(idx + 1);
          });
      };

      attempt(0);
    },
    [],
  );

  // ---- 点击地图选址 ----
  const handleMapClick = useCallback(
    (lng: number, lat: number) => {
      placeMarker(lng, lat);
      reverseGeocodeAndFill(lng, lat);
    },
    [placeMarker, reverseGeocodeAndFill],
  );

  // ---- 定位到我 ----
  const handleLocateMe = useCallback(() => {
    const AMap = amapNSRef.current;
    if (!AMap) return;

    setLocating(true);

    AMap.plugin('AMap.Geolocation', () => {
      if (!mountedRef.current) {
        setLocating(false);
        return;
      }

      try {
        const geolocation = new AMap.Geolocation({
          enableHighAccuracy: true,
          timeout: 10000,
          noGeoLocation: 3,
        });

        geolocation.getCurrentPosition((status: string, result: any) => {
          setLocating(false);
          if (!mountedRef.current) return;

          if (status === 'complete' && result.position) {
            const lng = result.position.lng;
            const lat = result.position.lat;
            const accuracy = result.accuracy || 0;

            if (mapInstanceRef.current) {
              mapInstanceRef.current.setZoomAndCenter(16, [lng, lat]);
            }

            handleMapClick(lng, lat);
            if (accuracy > 0) {
              message.info(`定位精度约 ${Math.round(accuracy)} 米`);
            }
          } else {
            message.warning('高德定位失败，尝试浏览器定位...');
            fallbackBrowserLocate();
          }
        });
      } catch {
        setLocating(false);
        message.warning('高德定位服务不可用，尝试浏览器定位...');
        fallbackBrowserLocate();
      }
    });
  }, [handleMapClick]);

  /** 浏览器原生定位（回退方案） */
  const fallbackBrowserLocate = useCallback(() => {
    if (!navigator.geolocation) {
      message.error('您的浏览器不支持定位功能');
      return;
    }

    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocating(false);
        if (!mountedRef.current) return;

        const [lng, lat] = wgs84ToGcj02(pos.coords.longitude, pos.coords.latitude);
        const accuracy = pos.coords.accuracy;

        if (mapInstanceRef.current) {
          mapInstanceRef.current.setZoomAndCenter(16, [lng, lat]);
        }

        handleMapClick(lng, lat);
        message.info(`定位精度约 ${Math.round(accuracy)} 米`);
      },
      (err) => {
        setLocating(false);
        if (!mountedRef.current) return;
        if (err.code === err.PERMISSION_DENIED) {
          message.warning('定位权限被拒绝，请在浏览器设置中允许获取位置');
        } else {
          message.warning('定位失败，请手动点击地图选址');
        }
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
    );
  }, [handleMapClick]);

  // ---- 地图就绪后自动定位到当前位置（无初始坐标时） ----
  useEffect(() => {
    if (!mapReady || initialLng || initialLat) return;
    handleLocateMe();
  }, [mapReady, initialLng, initialLat, handleLocateMe]);

  // ---- 搜索（REST API inputtips，走 Web 服务 Key，与逆地理编码同一套鉴权） ----
  const handleSearch = useCallback((value: string) => {
    setSearchValue(value);
    const requestId = searchRequestIdRef.current + 1;
    searchRequestIdRef.current = requestId;
    if (!value.trim()) {
      setSearchTips([]);
      setShowDropdown(false);
      setTipIndex(0);
      return;
    }

    if (searchTimerRef.current) {
      window.clearTimeout(searchTimerRef.current);
    }
    // 取消上一次未完成的请求
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    searchTimerRef.current = window.setTimeout(() => {
      if (!mountedRef.current) return;

      const kw = value.trim();
      const searchCity = searchCityRef.current;
      // 无城市时全国搜索，不传 city/citylimit 参数
      const cityParam = searchCity
        ? `&city=${encodeURIComponent(searchCity)}&citylimit=true`
        : '';
      const params = `key=${AMAP_WEB_KEY}&keywords=${encodeURIComponent(kw)}&datatype=all${cityParam}`;

      // 同时试直连和代理
      const urls = [
        `https://restapi.amap.com/v3/assistant/inputtips?${params}`,
        `/amap-api/v3/assistant/inputtips?${params}`,
      ];

      const ac = new AbortController();
      abortControllerRef.current = ac;

      const attempt = (idx: number) => {
        if (!mountedRef.current || ac.signal.aborted || searchRequestIdRef.current !== requestId || searchCityRef.current !== searchCity) return;
        if (idx >= urls.length) {
          console.warn('[search] 所有请求路径均失败');
          return;
        }

        fetch(urls[idx], { signal: ac.signal })
          .then((res) => res.json())
          .then((data: any) => {
            if (!mountedRef.current || ac.signal.aborted || searchRequestIdRef.current !== requestId || searchCityRef.current !== searchCity) return;
            console.log('[search] REST inputtips status=', data.status, 'tips=', data.tips?.length);

            if (data.status === '1' && data.tips?.length > 0) {
              const tips: TipItem[] = (data.tips as any[])
                .filter((t: any) => t.id && t.name)
                .map((t: any) => ({
                  id: t.id,
                  name: t.name,
                  district: t.district || '',
                  address: t.address || '',
                  location: t.location || '',
                }));
              const withCoord = tips.filter((t) => t.location);
              if (withCoord.length === 0 && tips.length > 0) {
                message.info('搜索结果缺少精确坐标，请尝试更具体的关键词或点击地图选址');
              }
              setSearchTips(tips);
              setShowDropdown(tips.length > 0);
              setTipIndex(0);
            } else {
              setSearchTips([]);
              setShowDropdown(false);
            }
          })
          .catch((err) => {
            if (err?.name === 'AbortError') return; // 被取消，忽略
            attempt(idx + 1);
          });
      };

      attempt(0);
    }, 300);
  }, []);

  // ---- 选中 POI ----
  const handleSelectTip = useCallback(
    (tip: TipItem) => {
      isInteractingWithDropdown.current = false;
      setShowDropdown(false);
      setSearchValue(tip.name);

      // REST API 返回的 location 是 "lng,lat" 字符串，部分提示项无坐标
      if (typeof tip.location !== 'string' || !tip.location.includes(',')) {
        message.warning('该地点缺少坐标信息，请选择其他结果或点击地图选址');
        return;
      }
      const [lngStr, latStr] = tip.location.split(',');
      const lng = parseFloat(lngStr);
      const lat = parseFloat(latStr);

      if (isNaN(lng) || isNaN(lat)) {
        message.warning('该地点坐标解析失败，请直接点击地图选址');
        return;
      }

      if (mapInstanceRef.current) {
        mapInstanceRef.current.setCenter([lng, lat]);
      }
      placeMarker(lng, lat);
      reverseGeocodeAndFill(lng, lat);
    },
    [placeMarker, reverseGeocodeAndFill],
  );

  // ---- 键盘 ----
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (!showDropdown || searchTips.length === 0) return;
      if (e.key === 'ArrowDown') { e.preventDefault(); setTipIndex((p) => (p + 1) % searchTips.length); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); setTipIndex((p) => (p - 1 + searchTips.length) % searchTips.length); }
      else if (e.key === 'Enter') { e.preventDefault(); if (searchTips[tipIndex]) handleSelectTip(searchTips[tipIndex]); }
      else if (e.key === 'Escape') setShowDropdown(false);
    },
    [showDropdown, searchTips, tipIndex, handleSelectTip],
  );

  // ---- 重试 ----
  const handleRetry = useCallback(() => {
    if (mapInstanceRef.current) { mapInstanceRef.current.destroy(); mapInstanceRef.current = null; }
    amapNSRef.current = null;
    markerRef.current = null;
    setMapReady(false);
    setLoadError(null);
    setSdkLoading(true);

    AMapLoader.load({
      key: AMAP_API_KEY, version: AMAP_API_VERSION,
      plugins: ['AMap.Geocoder', 'AMap.Geolocation'],
    })
      .then((AMap: any) => {
        if (!mountedRef.current || !containerRef.current) return;
        amapNSRef.current = AMap;
        const map = new AMap.Map(containerRef.current, {
          zoom: 13,
          center: [initialLng ?? DEFAULT_MAP_CENTER.lng, initialLat ?? DEFAULT_MAP_CENTER.lat],
          resizeEnable: true,
        });
        map.on('click', (e: any) => handleMapClick(e.lnglat.getLng(), e.lnglat.getLat()));
        mapInstanceRef.current = map;
        setMapReady(true);
        setSdkLoading(false);
      })
      .catch((err: Error) => {
        console.error('重试失败:', err);
        if (!mountedRef.current) return;
        setLoadError('地图加载失败，请检查网络或 API Key 配置');
        setSdkLoading(false);
      });
  }, [initialLng, initialLat, handleMapClick]);

  return (
    <div className={styles.mapContainer} ref={containerRef}>
      {sdkLoading && (
        <div className={styles.loadingOverlay}>
          <Spin tip="正在加载地图..." />
        </div>
      )}

      {loadError && (
        <div className={styles.errorOverlay}>
          <p>{loadError}</p>
          <Button onClick={handleRetry} type="primary" size="small">重试</Button>
        </div>
      )}

      {mapReady && !loadError && (
        <div className={styles.searchBox}>
          <Input
            id="amap-search-input"
            placeholder="搜索地点，如 万达影城"
            value={searchValue}
            onChange={(e) => handleSearch(e.target.value)}
            onFocus={() => { if (searchTips.length > 0) setShowDropdown(true); }}
            onBlur={() => {
              setTimeout(() => {
                if (mountedRef.current && !isInteractingWithDropdown.current) {
                  setShowDropdown(false);
                }
              }, 200);
            }}
            onKeyDown={handleKeyDown}
            allowClear
            style={{ borderRadius: 6 }}
          />
          {showDropdown && searchTips.length > 0 && (
            <div
              className={styles.poiDropdown}
              onMouseEnter={() => { isInteractingWithDropdown.current = true; }}
              onMouseLeave={() => { isInteractingWithDropdown.current = false; }}
            >
              {searchTips.map((tip, idx) => (
                <div
                  key={tip.id}
                  className={styles.poiItem}
                  style={{ background: idx === tipIndex ? '#e6f7f3' : undefined }}
                  onMouseDown={(e) => { e.preventDefault(); handleSelectTip(tip); }}
                  onMouseEnter={() => setTipIndex(idx)}
                >
                  <div className={styles.poiName}>{tip.name}</div>
                  <div className={styles.poiAddress}>{tip.district}{tip.address}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {mapReady && !loadError && (
        <div className={styles.btnGroup}>
          <Button
            icon={locating ? undefined : <AimOutlined />}
            onClick={handleLocateMe}
            loading={locating}
            size="small"
          >
            定位
          </Button>
        </div>
      )}

      {geocoding && <div className={styles.geocodingHint}>正在解析地址...</div>}
      {locating && <div className={styles.locateHint}>正在获取位置...</div>}
    </div>
  );
};

function extractAddress(regeo: any): string {
  return regeo.formatted_address || regeo.formattedAddress || '';
}

function extractCityName(ac: any): string {
  if (!ac) return '';
  const pick = (v: unknown): string => {
    if (Array.isArray(v)) return v[0] ? String(v[0]) : '';
    return v ? String(v) : '';
  };
  return pick(ac.city) || pick(ac.province) || pick(ac.district) || '';
}

function extractNameFromRegeo(regeo: any): string {
  if (regeo.pois?.length > 0 && regeo.pois[0].name) return regeo.pois[0].name;
  if (regeo.addressComponent?.building) return regeo.addressComponent.building;
  const comp = regeo.addressComponent;
  if (comp) {
    const streetNo =
      typeof comp.streetNumber === 'string' ? comp.streetNumber : comp.streetNumber?.street;
    const parts = [streetNo, comp.street].filter(Boolean);
    if (parts.length > 0) return parts.join('');
  }
  return extractAddress(regeo);
}

export default AmapLocationPicker;
