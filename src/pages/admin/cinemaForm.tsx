import React, { useEffect, useState } from 'react';
import { Button, Card, Form, Input, InputNumber, message } from 'antd';
import { history, useParams } from 'umi';
import * as catalogApi from '@/api/catalog';
import * as adminApi from '@/api/admin';
import AmapLocationPicker from '@/components/AmapLocationPicker';
import type { SelectedLocation } from '@/components/AmapLocationPicker/interface';
import { cityIdToName, cityNameToId } from '@/components/AmapLocationPicker/cityMap';

const CinemaFormPage: React.FC = () => {
  const { cinemaId } = useParams<{ cinemaId: string }>();
  const isNew = !cinemaId || cinemaId === 'new';
  const [form] = Form.useForm();
  const cityName = Form.useWatch('cityName', form);
  const [mapCenter, setMapCenter] = useState<{ lng?: number; lat?: number }>({});

  useEffect(() => {
    if (!isNew && cinemaId) void catalogApi.getCinema(cinemaId)
      .then((cinema) => {
        form.setFieldsValue({ ...cinema, cityName: cinema.cityName || cityIdToName(cinema.cityId), tagsText: cinema.tags?.join(', ') });
        setMapCenter({ lng: cinema.lng, lat: cinema.lat });
      })
      .catch(() => {});
  }, [cinemaId, form, isNew]);

  const onFinish = async (values: Record<string, unknown>) => {
    const lat = values.lat as number | undefined;
    const lng = values.lng as number | undefined;
    const tags = String(values.tagsText || '').split(/[,，]/).map((item) => item.trim()).filter(Boolean);
    const cityName = String(values.cityName || '').trim();
    const common = {
      cityId: cityNameToId(cityName) || undefined,
      cityName,
      name: String(values.name).trim(), address: String(values.address).trim(),
    };
    const trafficNote = String(values.trafficNote || '').trim();
    try {
      if (isNew) {
        if (lat == null || lng == null) { message.error('新建影院必须选择经纬度'); return; }
        await adminApi.createCinema({ ...common, lat, lng, trafficNote: trafficNote || undefined, tags: tags.length ? tags : undefined });
      } else {
        const coordinatePatch = lat != null && lng != null ? { lat, lng } : {};
        // 编辑时空值也是显式变更，确保可以清空既有交通说明和特色标签。
        await adminApi.updateCinema(cinemaId!, { ...common, ...coordinatePatch, trafficNote, tags });
      }
      message.success('已保存'); history.push('/admin/cinemas');
    } catch {
      // 请求层已处理。
    }
  };

  const locationSelected = (location: SelectedLocation) => form.setFieldsValue({
    name: location.name,
    address: location.address,
    lat: location.lat,
    lng: location.lng,
    ...(location.cityName ? { cityName: location.cityName } : {}),
  });
  const coordinateRule = (name: string, min: number, max: number) => [{ required: isNew, message: `请输入${name}` }, { type: 'number' as const, min, max, message: `${name}范围应为 ${min} 至 ${max}` }];

  return <div>
    <h2 style={{ marginBottom: 16 }}>{isNew ? '新建影院' : '编辑影院'}</h2>
    <div style={{ display: 'flex', gap: 24, alignItems: 'flex-start' }}>
      <Card title="影院信息" style={{ flex: '0 0 400px' }} styles={{ body: { paddingBottom: 12 } }}>
        <Form form={form} layout="vertical" onFinish={onFinish}>
          <Form.Item name="name" label="名称" rules={[{ required: true, whitespace: true, message: '请输入影院名称' }]}><Input /></Form.Item>
          <Form.Item name="address" label="地址" rules={[{ required: true, whitespace: true, message: '请输入地址' }]}><Input /></Form.Item>
          <Form.Item name="cityName" label="城市" rules={[{ required: true, whitespace: true, message: '请输入城市名称' }]}><Input placeholder="例如：上海市" /></Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="lat" label="纬度" style={{ flex: 1 }} rules={coordinateRule('纬度', -90, 90)}><InputNumber style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="lng" label="经度" style={{ flex: 1 }} rules={coordinateRule('经度', -180, 180)}><InputNumber style={{ width: '100%' }} /></Form.Item>
          </div>
          <Form.Item name="trafficNote" label="交通说明"><Input placeholder="例如：地铁 1 号线直达" /></Form.Item>
          <Form.Item name="tagsText" label="特色标签"><Input placeholder="IMAX, 杜比" /></Form.Item>
          <Button onClick={() => history.push('/admin/cinemas')}>取消</Button><Button type="primary" htmlType="submit" style={{ marginLeft: 8 }}>保存</Button>
        </Form>
      </Card>
      <Card title="地图选址" style={{ flex: 1, minWidth: 0 }} styles={{ body: { padding: 0 } }}><div style={{ height: 520 }}><AmapLocationPicker onSelect={locationSelected} cityName={cityName} initialLng={mapCenter.lng} initialLat={mapCenter.lat} /></div></Card>
    </div>
  </div>;
};

export default CinemaFormPage;
