import React, { useEffect, useState } from 'react';
import { Button, Form, Input, InputNumber, message, Card } from 'antd';
import { history, useParams } from 'umi';
import * as catalogApi from '@/api/catalog';
import * as adminApi from '@/api/admin';
import AmapLocationPicker from '@/components/AmapLocationPicker';
import type { SelectedLocation } from '@/components/AmapLocationPicker/interface';
import { cityNameToId, cityIdToName } from '@/components/AmapLocationPicker/cityMap';

const MAP_HEIGHT = 520;

const CinemaFormPage: React.FC = () => {
  const { cinemaId } = useParams<{ cinemaId: string }>();
  const isNew = !cinemaId || cinemaId === 'new';
  const [form] = Form.useForm();
  const [mapCenter, setMapCenter] = useState<{ lng?: number; lat?: number }>({});

  useEffect(() => {
    if (!isNew && cinemaId) {
      void catalogApi.getCinema(cinemaId).then((c) => {
        // 编辑回显：城市编码转中文名显示
        form.setFieldsValue({
          ...c,
          cityId: cityIdToName(c.cityId),
        });
        setMapCenter({ lng: c.lng, lat: c.lat });
      });
    }
  }, [cinemaId, isNew, form]);

  const onFinish = async (v: Record<string, unknown>) => {
    // 提交时：城市中文名转编码（如"上海市" → city_sh）
    const cityInput = String(v.cityId || '');
    const body = {
      ...v,
      cityId: cityNameToId(cityInput) || cityInput,
    };
    if (isNew) await adminApi.createCinema(body);
    else await adminApi.updateCinema(cinemaId!, body);
    message.success('已保存');
    history.push('/admin/cinemas');
  };

  const handleLocationSelect = (loc: SelectedLocation) => {
    // 城市字段显示中文名（如"上海市"）
    form.setFields([
      { name: 'name', value: loc.name },
      { name: 'address', value: loc.address },
      { name: 'lat', value: loc.lat },
      { name: 'lng', value: loc.lng },
      { name: 'cityId', value: loc.cityName ?? '' },
    ]);
  };

  return (
    <div>
      <h2 style={{ marginBottom: 16 }}>{isNew ? '新建影院' : '编辑影院'}</h2>

      <div style={{ display: 'flex', gap: 24, alignItems: 'flex-start' }}>
        {/* 左侧 — 表单 */}
        <Card
          title="影院信息"
          style={{ flex: '0 0 400px' }}
          styles={{ body: { paddingBottom: 12 } }}
        >
          <Form form={form} layout="vertical" onFinish={onFinish}>
            <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入影院名称' }]}>
              <Input placeholder="点击地图或搜索自动填充" />
            </Form.Item>

            <Form.Item name="address" label="地址" rules={[{ required: true, message: '请输入地址' }]}>
              <Input placeholder="点击地图或搜索自动填充" />
            </Form.Item>

            <Form.Item name="cityId" label="城市">
              <Input placeholder="点击地图自动填充（提交时转为城市编码）" />
            </Form.Item>

            <div style={{ display: 'flex', gap: 12 }}>
              <Form.Item name="lat" label="纬度" style={{ flex: 1 }}>
                <InputNumber style={{ width: '100%' }} placeholder="自动填充" />
              </Form.Item>
              <Form.Item name="lng" label="经度" style={{ flex: 1 }}>
                <InputNumber style={{ width: '100%' }} placeholder="自动填充" />
              </Form.Item>
            </div>

            <div style={{ marginTop: 8, display: 'flex', gap: 8 }}>
              <Button onClick={() => history.push('/admin/cinemas')}>取消</Button>
              <Button type="primary" htmlType="submit">保存</Button>
            </div>
          </Form>
        </Card>

        {/* 右侧 — 地图 */}
        <Card
          title="地图选址"
          style={{ flex: 1, minWidth: 0 }}
          styles={{ body: { padding: 0 } }}
        >
          <div style={{ height: MAP_HEIGHT }}>
            <AmapLocationPicker
              onSelect={handleLocationSelect}
              initialLng={mapCenter.lng}
              initialLat={mapCenter.lat}
            />
          </div>
          <div
            style={{
              padding: '8px 12px',
              fontSize: 12,
              color: '#888',
              borderTop: '1px solid #f0f0f0',
            }}
          >
            点击地图任意位置自动填充经纬度与地址，或使用搜索框查找影院。点击
            <span style={{ color: '#37b7a5', margin: '0 2px' }}>⊕</span>
            可定位到当前位置。
          </div>
        </Card>
      </div>
    </div>
  );
};

export default CinemaFormPage;
