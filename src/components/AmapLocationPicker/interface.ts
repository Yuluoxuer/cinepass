/** 地图选址结果 */
export interface SelectedLocation {
  /** POI 名称或推荐名称 */
  name: string;
  /** 格式化完整地址 */
  address: string;
  /** 纬度 */
  lat: number;
  /** 经度 */
  lng: number;
  /** 城市中文名（如"上海市"），用于表单显示 */
  cityName?: string;
  /** 城市编码（city_xx），用于提交后端 */
  cityId?: string;
}

/** AmapLocationPicker 组件 Props */
export interface AmapLocationPickerProps {
  /** 选址完成回调，返回解析后的位置信息 */
  onSelect: (location: SelectedLocation) => void;
  /** 当前表单填写的城市名称，优先用于限定地点搜索范围 */
  cityName?: string;
  /** 初始地图中心经度 */
  initialLng?: number;
  /** 初始地图中心纬度 */
  initialLat?: number;
}
