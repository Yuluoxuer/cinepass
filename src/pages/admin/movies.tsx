import React, { useEffect, useState } from 'react';
import { Alert, Button, Input, Space, Table, Tag, Empty, Modal, message } from 'antd';
import { history } from 'umi';
import * as catalogApi from '@/api/catalog';
import { takeDownMovie, relistMovie } from '@/api/admin';
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

  const confirmDown = (r: MovieVO) => {
    Modal.confirm({
      title: `下架《${r.title}》？`,
      content: '下架前会校验该影片是否还有未来在售场次；下架后不再出现在热映/待映列表及推荐中。',
      okText: '下架',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await takeDownMovie(r.movieId);
          message.success('已下架');
          void load();
        } catch (error) {
          // 后端会返回「还有 N 场在售场次」等冲突提示
          message.error(error instanceof Error ? error.message : '下架失败，请稍后重试');
        }
      },
    });
  };

  const confirmRelist = (r: MovieVO) => {
    Modal.confirm({
      title: `上架《${r.title}》？`,
      content: '将根据上映日期自动设为「热映」或「待映」。',
      okText: '上架',
      onOk: async () => {
        try {
          const updated = await relistMovie(r.movieId);
          message.success(updated.status === 'hot_showing' ? '已上架为热映' : '已上架为待映');
          void load();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '上架失败，请稍后重试');
        }
      },
    });
  };

  const load = async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const res = await catalogApi.listMovies({ q: q || undefined, page: 1, size: 50 });
      setData(res.items);
    } catch (error) {
      setData([]);
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
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有数据" /> }}
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
              <>
                <Button type="link" onClick={() => history.push(`/admin/movies/${r.movieId}`)}>
                  编辑
                </Button>
                {r.status === 'off' ? (
                  <Button type="link" onClick={() => confirmRelist(r)}>
                    上架
                  </Button>
                ) : (
                  <Button type="link" danger onClick={() => confirmDown(r)}>
                    下架
                  </Button>
                )}
              </>
            ),
          },
        ]}
      />
    </div>
  );
};

export default AdminMoviesPage;
