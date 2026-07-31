import React, { useEffect } from 'react';
import { Button, Form, Input, InputNumber, message } from 'antd';
import { history, useParams } from 'umi';
import * as catalogApi from '@/api/catalog';
import * as adminApi from '@/api/admin';

const CinemaFormPage: React.FC = () => {
  const { cinemaId } = useParams<{ cinemaId: string }>();
  const isNew = !cinemaId || cinemaId === 'new';
  const [form] = Form.useForm();

  useEffect(() => {
    if (!isNew && cinemaId) {
      void catalogApi.getCinema(cinemaId).then((c) => form.setFieldsValue(c));
    }
  }, [cinemaId, isNew]);

  const onFinish = async (v: Record<string, unknown>) => {
    if (isNew) await adminApi.createCinema(v);
    else await adminApi.updateCinema(cinemaId!, v);
    message.success('已保存');
    history.push('/admin/cinemas');
  };

  return (
    <div>
      <h2>{isNew ? '新建影院' : '编辑影院'}</h2>
      <Form form={form} layout="vertical" style={{ maxWidth: 520, marginTop: 16 }} onFinish={onFinish}>
        <Form.Item name="name" label="名称" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item name="address" label="地址" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item name="cityId" label="城市" initialValue="city_sh">
          <Input />
        </Form.Item>
        <Form.Item name="lat" label="纬度">
          <InputNumber style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="lng" label="经度">
          <InputNumber style={{ width: '100%' }} />
        </Form.Item>
        <Button onClick={() => history.push('/admin/cinemas')} style={{ marginRight: 8 }}>
          取消
        </Button>
        <Button type="primary" htmlType="submit">
          保存
        </Button>
      </Form>
    </div>
  );
};

export default CinemaFormPage;
