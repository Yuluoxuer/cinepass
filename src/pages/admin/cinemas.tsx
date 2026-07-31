import React, { useEffect, useState } from 'react';
import { Button, Space, Table } from 'antd';
import { history } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { CinemaVO } from '@/types';

const AdminCinemasPage: React.FC = () => {
  const [data, setData] = useState<CinemaVO[]>([]);

  useEffect(() => {
    void catalogApi.listCinemas({ page: 1, size: 50 }).then((r) => setData(r.items));
  }, []);

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => history.push('/admin/cinemas/new')}>
          + 新建影院
        </Button>
      </Space>
      <Table
        rowKey="cinemaId"
        dataSource={data}
        columns={[
          { title: '影院名称', dataIndex: 'name' },
          { title: '地址', dataIndex: 'address' },
          { title: '城市', dataIndex: 'cityId' },
          {
            title: '操作',
            render: (_, r) => (
              <Space>
                <Button type="link" onClick={() => history.push(`/admin/cinemas/${r.cinemaId}`)}>
                  编辑
                </Button>
                <Button type="link" onClick={() => history.push(`/admin/cinemas/${r.cinemaId}/halls`)}>
                  影厅管理
                </Button>
              </Space>
            ),
          },
        ]}
      />
    </div>
  );
};

export default AdminCinemasPage;
