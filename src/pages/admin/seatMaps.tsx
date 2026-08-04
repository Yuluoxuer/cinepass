import React, { useEffect, useMemo, useState } from 'react';
import { Button, Modal, Select, Space, Table, Tag, Tooltip, message, Empty } from 'antd';
import { history } from 'umi';
import * as adminApi from '@/api/admin';
import * as catalogApi from '@/api/catalog';
import { getCinemaIdFromAccessToken, useAuthStore } from '@/stores/auth';
import type { CinemaVO, SeatMapVO } from '@/types';

const SeatMapsPage: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const staffCinemaId = user?.role === 'staff' ? user.cinemaId || getCinemaIdFromAccessToken() : undefined;
  const [cinemas, setCinemas] = useState<CinemaVO[]>([]);
  const [cinemaId, setCinemaId] = useState(staffCinemaId || '');
  const [data, setData] = useState<SeatMapVO[]>([]);

  const cinemaNameById = useMemo(() => {
    const map: Record<string, string> = {};
    cinemas.forEach((c) => {
      map[c.cinemaId] = c.name;
    });
    return map;
  }, [cinemas]);

  const load = () => {
    if (!cinemaId) {
      setData([]);
      return;
    }
    void adminApi
      .listSeatMaps({ cinemaId, page: 1, size: 50 })
      .then((r) => setData(r.items))
      .catch(() => setData([]));
  };

  useEffect(() => {
    void catalogApi
      .listCinemas({ sort: 'price', page: 1, size: 50 })
      .then((r) => {
        const items = staffCinemaId
          ? r.items.filter((c) => c.cinemaId === staffCinemaId)
          : r.items;
        setCinemas(items);
        if (staffCinemaId) setCinemaId(staffCinemaId);
        else if (!cinemaId && items[0]) setCinemaId(items[0].cinemaId);
      })
      .catch(() => setCinemas([]));
  }, [staffCinemaId]);

  useEffect(() => {
    load();
  }, [cinemaId]);

  const seatMapDisplayName = (seatMap: SeatMapVO) => {
    const label = (seatMap.screenLabel || '').trim();
    if (label && label !== '银幕') return label;
    return `${label || '银幕'}（${seatMap.rows}×${seatMap.cols}）`;
  };

  const requestDelete = (seatMap: SeatMapVO) => {
    Modal.confirm({
      title: `确认删除座位图「${seatMapDisplayName(seatMap)}」？`,
      content: '删除后不可恢复。若仍被影厅或场次引用，服务端会拒绝删除。',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await adminApi.deleteSeatMap(seatMap.seatMapId);
          message.success('已删除');
          load();
        } catch {
          // 请求层已处理。
        }
      },
    });
  };

  return (
    <div>
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          placeholder="所属影院"
          style={{ width: 240 }}
          value={cinemaId || undefined}
          disabled={!!staffCinemaId}
          options={cinemas.map((c) => ({ value: c.cinemaId, label: c.name }))}
          onChange={setCinemaId}
        />
        <Button
          type="primary"
          disabled={!cinemaId}
          onClick={() => history.push(`/admin/seat-maps/new?cinemaId=${encodeURIComponent(cinemaId)}`)}
        >
          + 新建座位图
        </Button>
      </Space>
      <Table
        rowKey="seatMapId"
        dataSource={data}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有数据" /> }}
        columns={[
          {
            title: '座位图',
            render: (_, r) => (
              <Tooltip title={`ID：${r.seatMapId}`}>
                <span>{seatMapDisplayName(r)}</span>
              </Tooltip>
            ),
          },
          {
            title: '影院',
            dataIndex: 'cinemaId',
            render: (id: string | undefined) =>
              (id && cinemaNameById[id]) || id || '—',
          },
          { title: '行列', render: (_, r) => `${r.rows} × ${r.cols}` },
          {
            title: '座位数',
            dataIndex: 'seatCount',
            render: (v: number | undefined, r) => v ?? r.seats?.length ?? 0,
          },
          {
            title: '可编辑',
            dataIndex: 'mutable',
            render: (v: boolean | undefined) => (
              <Tag color={v === false ? 'default' : 'green'}>{v === false ? '否' : '是'}</Tag>
            ),
          },
          {
            title: '操作',
            render: (_, r) => (
              <Space>
                <Button type="link" onClick={() => history.push(`/admin/seat-maps/${r.seatMapId}`)}>
                  {r.mutable === false ? '查看' : '编辑'}
                </Button>
                <Button type="link" danger disabled={r.mutable === false} onClick={() => requestDelete(r)}>
                  删除
                </Button>
              </Space>
            ),
          },
        ]}
      />
    </div>
  );
};

export default SeatMapsPage;
