import React, { useEffect, useState } from 'react';
import { Alert, Button, Input, Space, Table, Tag } from 'antd';
import { history } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { MovieVO } from '@/types';

const movieStatusMeta: Record<MovieVO['status'], { label: string; color: string }> = {
  hot_showing: { label: '热映', color: 'red' },
  coming_soon: { label: '待映', color: 'blue' },
  off: { label: '已下架', color: 'default' },
};

const AdminMoviesPage: React.FC = () => {
  const [data, setData] = useState<MovieVO[]>([]);
  const [q, setQ] = useState('');
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const res = await catalogApi.listMovies({ q: q || undefined, page: 1, size: 50 });
      setData(res.items);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : '影片列表加载失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => history.push('/admin/movies/new')}>
          + 新建影片
        </Button>
        <Input.Search placeholder="搜索片名" value={q} onChange={(e) => setQ(e.target.value)} onSearch={() => load()} />
      </Space>
      {loadError && (
        <Alert
          type="error"
          showIcon
          message="影片列表加载失败"
          description={loadError}
          action={<Button size="small" onClick={() => void load()}>重试</Button>}
          style={{ marginBottom: 16 }}
        />
      )}
      <Table
        rowKey="movieId"
        loading={loading}
        dataSource={data}
        columns={[
          {
            title: '海报',
            dataIndex: 'posterUrl',
            render: (u: string) => <img src={u} alt="" style={{ width: 40, height: 60, objectFit: 'cover' }} />,
          },
          { title: '片名', dataIndex: 'title' },
          { title: '类型', dataIndex: 'genres', render: (g: string[]) => g.join('/') },
          {
            title: '状态',
            dataIndex: 'status',
            render: (status: MovieVO['status']) => {
              const meta = movieStatusMeta[status] || { label: '未知状态', color: 'default' };
              return <Tag color={meta.color}>{meta.label}</Tag>;
            },
          },
          { title: '上映日', dataIndex: 'releaseDate' },
          {
            title: '操作',
            render: (_, r) => (
              <Button type="link" onClick={() => history.push(`/admin/movies/${r.movieId}`)}>
                编辑
              </Button>
            ),
          },
        ]}
      />
    </div>
  );
};

export default AdminMoviesPage;
