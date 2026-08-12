import React, { useEffect, useState } from 'react';
import { Button, DatePicker, Form, Input, InputNumber, Select, Upload, message } from 'antd';
import type { UploadProps } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import zhCN from 'antd/locale/zh_CN';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { history, useParams } from 'umi';
import * as catalogApi from '@/api/catalog';
import * as adminApi from '@/api/admin';

const MAX_TITLE_LENGTH = 100;
const MAX_POSTER_URL_LENGTH = 2048;
const MAX_GENRE_COUNT = 5;
const MAX_GENRE_LENGTH = 20;
const MAX_DURATION_MIN = 600;
const MAX_CAST_LENGTH = 200;
const MIN_DESCRIPTION_LENGTH = 10;
const MAX_DESCRIPTION_LENGTH = 2000;

dayjs.locale('zh-cn');

function normalizeGenres(value: unknown): string[] {
  return String(value || '')
    .split(/[,，、/]/)
    .map((genre) => genre.trim())
    .filter(Boolean);
}

const MovieFormPage: React.FC = () => {
  const { movieId } = useParams<{ movieId: string }>();
  const isNew = !movieId || movieId === 'new';
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const posterUrl = Form.useWatch('posterUrl', form);

  const beforeUpload: UploadProps['beforeUpload'] = async (file) => {
    setUploading(true);
    try {
      const url = await adminApi.uploadPoster(file);
      form.setFieldValue('posterUrl', url);
      message.success('海报上传成功');
    } catch (err) {
      message.error(err instanceof Error ? err.message : '上传失败');
    } finally {
      setUploading(false);
    }
    return false;
  };

  const uploadProps: UploadProps = {
    beforeUpload,
    accept: 'image/jpeg,image/png,image/gif',
    maxCount: 1,
    showUploadList: false,
    disabled: uploading,
  };

  useEffect(() => {
    if (!isNew && movieId) {
      void catalogApi.getMovie(movieId)
        .then((m) => {
          form.setFieldsValue({ ...m, genres: m.genres.join(','), releaseDate: dayjs(m.releaseDate) });
        })
        .catch(() => {});
    }
  }, [movieId, isNew]);

  const onFinish = async (v: Record<string, unknown>) => {
    setLoading(true);
    try {
      const body = {
        ...v,
        title: String(v.title || '').trim(),
        posterUrl: String(v.posterUrl || '').trim(),
        genres: normalizeGenres(v.genres),
        releaseDate: dayjs(v.releaseDate as string | Date).format('YYYY-MM-DD'),
        cast: String(v.cast || '').trim(),
        description: String(v.description || '').trim(),
      };
      try {
        if (isNew) await adminApi.createMovie(body);
        else await adminApi.updateMovie(movieId!, body);
        message.success('已保存');
        history.push('/admin/movies');
      } catch {
        // 请求层已处理。
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <Form form={form} layout="vertical" style={{ maxWidth: 640 }} onFinish={onFinish}>
        <Form.Item
          name="title"
          label="片名"
          rules={[
            { required: true, whitespace: true, message: '请输入片名' },
            { max: MAX_TITLE_LENGTH, message: `片名不能超过 ${MAX_TITLE_LENGTH} 个字符` },
          ]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          name="posterUrl"
          label="海报 URL"
          rules={[
            { required: true, whitespace: true, message: '请输入海报 URL' },
            { max: MAX_POSTER_URL_LENGTH, message: '海报 URL 过长' },
            {
              validator: async (_, value) => {
                if (!value) return;
                const v = String(value).trim();
                // 允许相对路径（如 /uploads/posters/xxx.jpg）
                if (v.startsWith('/')) return;
                try {
                  const url = new URL(v);
                  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
                    throw new Error('协议不支持');
                  }
                } catch {
                  throw new Error('请输入有效的 http(s) 海报 URL 或上传图片');
                }
              },
            },
          ]}
        >
          <Input />
        </Form.Item>
        {posterUrl ? (
          <div style={{ marginBottom: 16, textAlign: 'center' }}>
            <img
              src={String(posterUrl).startsWith('/') ? String(posterUrl) : String(posterUrl)}
              alt="海报预览"
              style={{ maxWidth: 200, maxHeight: 300, borderRadius: 8, border: '1px solid #e8e8e8' }}
            />
          </div>
        ) : null}
        <Form.Item label="上传新海报">
          <Upload.Dragger {...uploadProps}>
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">{uploading ? '上传中…' : '点击或拖拽图片到此处上传'}</p>
            <p className="ant-upload-hint">支持 JPEG / PNG / GIF，单文件不超过 5MB（服务端将重编码为 PNG）</p>
          </Upload.Dragger>
        </Form.Item>
        <Form.Item
          name="genres"
          label="类型（使用逗号、顿号或斜杠分隔）"
          rules={[
            {
              validator: async (_, value) => {
                const genres = normalizeGenres(value);
                if (genres.length === 0) throw new Error('请至少填写一个影片类型');
                if (genres.length > MAX_GENRE_COUNT) {
                  throw new Error(`影片类型不能超过 ${MAX_GENRE_COUNT} 个`);
                }
                if (genres.some((genre) => genre.length > MAX_GENRE_LENGTH)) {
                  throw new Error(`单个类型不能超过 ${MAX_GENRE_LENGTH} 个字符`);
                }
              },
            },
          ]}
        >
          <Input placeholder="科幻,冒险" />
        </Form.Item>
        <Form.Item
          name="durationMin"
          label="时长（分钟）"
          rules={[
            { required: true, message: '请输入影片时长' },
            {
              validator: async (_, value) => {
                if (!Number.isInteger(value) || value < 1 || value > MAX_DURATION_MIN) {
                  throw new Error(`时长应为 1 至 ${MAX_DURATION_MIN} 分钟的整数`);
                }
              },
            },
          ]}
        >
          <InputNumber min={1} max={MAX_DURATION_MIN} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="releaseDate"
          label="上映日"
          rules={[
            { required: true, message: '请选择上映日' },
          ]}
        >
          <DatePicker
            locale={zhCN.DatePicker}
            format="YYYY-MM-DD"
            style={{ width: '100%' }}
          />
        </Form.Item>
        <Form.Item name="status" label="状态" initialValue="coming_soon" rules={[{ required: true, message: '请选择影片状态' }]}>
          <Select
            options={[
              { value: 'hot_showing', label: '热映' },
              { value: 'coming_soon', label: '待映' },
              { value: 'off', label: '下架' },
            ]}
          />
        </Form.Item>
        <Form.Item
          name="rating"
          label="评分"
          rules={[
            {
              validator: async (_, value) => {
                if (value == null || value === '') return;
                const rating = Number(value);
                if (!Number.isFinite(rating) || rating < 0 || rating > 10 || Math.round(rating * 10) !== rating * 10) {
                  throw new Error('评分须为 0 至 10 的数字，最多保留一位小数');
                }
              },
            },
          ]}
        >
          <InputNumber min={0} max={10} precision={1} step={0.1} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="cast" label="主演" rules={[{ max: MAX_CAST_LENGTH, message: `主演不能超过 ${MAX_CAST_LENGTH} 个字符` }]}>
          <Input />
        </Form.Item>
        <Form.Item
          name="description"
          label="简介"
          rules={[
            { required: true, whitespace: true, message: '请输入影片简介' },
            { min: MIN_DESCRIPTION_LENGTH, message: `简介至少 ${MIN_DESCRIPTION_LENGTH} 个字符` },
            { max: MAX_DESCRIPTION_LENGTH, message: `简介不能超过 ${MAX_DESCRIPTION_LENGTH} 个字符` },
          ]}
        >
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
