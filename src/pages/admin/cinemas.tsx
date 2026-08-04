import React, { useEffect, useState } from 'react';
import { Button, Modal, Space, Table, Tooltip, message, Empty} from 'antd';
import { history } from 'umi';
import * as catalogApi from '@/api/catalog';
import * as adminApi from '@/api/admin';
import { cityIdToName } from '@/components/AmapLocationPicker/cityMap';
import { useAccess } from '@/access';
import { getCinemaIdFromAccessToken, useAuthStore } from '@/stores/auth';
import type { CinemaVO } from '@/types';

const AdminCinemasPage: React.FC = () => {
  const [data, setData] = useState<CinemaVO[]>([]);
  const { canAdmin } = useAccess();
  const user = useAuthStore((state) => state.user);
  const staffCinemaId = user?.role === 'staff' ? user.cinemaId || getCinemaIdFromAccessToken() : undefined;

  const load = () => void catalogApi.listCinemas({ sort: 'price', page: 1, size: 50 })
    .then((r) => setData(r.items))
    .catch(() => setData([]));

  useEffect(() => { load(); }, []);

  const remove = (cinema: CinemaVO) => Modal.confirm({
    title: `确认删除“${cinema.name}”吗？`,
    content: '删除后公开端将不再展示该影院，且无法恢复。若仍有员工绑定该影院，请先迁移员工。',
    okText: '确认删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await adminApi.deleteCinema(cinema.cinemaId);
        message.success('影院已删除');
        load();
      } catch {
        // 请求层已统一提示并执行对应错误策略，避免 Modal 回调形成未处理拒绝。
      }
    },
  });

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        {canAdmin && <Button type="primary" onClick={() => history.push('/admin/cinemas/new')}>
          + 新建影院
        </Button>}
      </Space>
      <Table
        rowKey="cinemaId"
        dataSource={data}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有数据" /> }}
        columns={[
          { title: '影院名称', dataIndex: 'name' },
          { title: '地址', dataIndex: 'address' },
          {
            title: '城市',
            render: (_, cinema) => cinema.cityName || cityIdToName(cinema.cityId),
          },
          {
            title: '操作',
            render: (_, r) => {
              const cannotManageCinema = user?.role === 'staff' && staffCinemaId !== r.cinemaId;
              const scopeHint = staffCinemaId ? '员工只能管理所属影院' : '未识别所属影院，暂不可执行影院管理操作';
              return (
              <Space>
                {cannotManageCinema ? (
                  <Tooltip title={scopeHint}>
                    <span><Button type="link" disabled>编辑</Button></span>
                  </Tooltip>
                ) : (
                  <Button type="link" onClick={() => history.push(`/admin/cinemas/${r.cinemaId}`)}>编辑</Button>
                )}
                {cannotManageCinema ? (
                  <Tooltip title={scopeHint}>
                    <span><Button type="link" disabled>影厅管理</Button></span>
                  </Tooltip>
                ) : (
                  <Button type="link" onClick={() => history.push(`/admin/cinemas/${r.cinemaId}/halls`)}>影厅管理</Button>
                )}
                {canAdmin ? (
                  <Button type="link" danger onClick={() => remove(r)}>删除</Button>
                ) : (
                  <Tooltip title="仅管理员可删除影院">
                    <span><Button type="link" danger disabled>删除</Button></span>
                  </Tooltip>
                )}
              </Space>
              );
            },
          },
        ]}
      />
    </div>
  );
};

export default AdminCinemasPage;
