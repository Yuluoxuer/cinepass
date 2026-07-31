import React, { useEffect, useState } from 'react';
import { Button, Form, Input, InputNumber, Select, message } from 'antd';
import { history, useParams } from 'umi';
import * as catalogApi from '@/api/catalog';
import * as adminApi from '@/api/admin';

const MovieFormPage: React.FC = () => {
  const { movieId } = useParams<{ movieId: string }>();
  const isNew = !movieId || movieId === 'new';
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!isNew && movieId) {
      void catalogApi.getMovie(movieId).then((m) => {
        form.setFieldsValue({ ...m, genres: m.genres.join(',') });
      });
    }
  }, [movieId, isNew]);

  const onFinish = async (v: Record<string, unknown>) => {
    setLoading(true);
    try {
      const body = {
        ...v,
        genres: String(v.genres || '')
          .split(/[,，]/)
          .map((s) => s.trim())
          .filter(Boolean),
      };
      if (isNew) await adminApi.createMovie(body);
      else await adminApi.updateMovie(movieId!, body);
      message.success('已保存');
      history.push('/admin/movies');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <h2>{isNew ? '新建影片' : '编辑影片'}</h2>
      <Form form={form} layout="vertical" style={{ maxWidth: 640, marginTop: 16 }} onFinish={onFinish}>
        <Form.Item name="title" label="片名" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item name="posterUrl" label="海报 URL" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item name="genres" label="类型（逗号分隔）" rules={[{ required: true }]}>
          <Input placeholder="科幻,冒险" />
        </Form.Item>
        <Form.Item name="durationMin" label="时长（分钟）" rules={[{ required: true }]}>
          <InputNumber min={1} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="releaseDate" label="上映日" rules={[{ required: true }]}>
          <Input placeholder="YYYY-MM-DD" />
        </Form.Item>
        <Form.Item name="status" label="状态" initialValue="coming_soon">
          <Select
            options={[
              { value: 'hot_showing', label: '热映' },
              { value: 'coming_soon', label: '待映' },
              { value: 'off', label: '下架' },
            ]}
          />
        </Form.Item>
        <Form.Item name="rating" label="评分">
          <InputNumber min={0} max={10} step={0.1} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="cast" label="主演">
          <Input />
        </Form.Item>
        <Form.Item name="description" label="简介" rules={[{ required: true }]}>
          <Input.TextArea rows={4} />
        </Form.Item>
        <Button onClick={() => history.push('/admin/movies')} style={{ marginRight: 8 }}>
          取消
        </Button>
        <Button type="primary" htmlType="submit" loading={loading}>
          保存
        </Button>
      </Form>
    </div>
  );
};

export default MovieFormPage;
