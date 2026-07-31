import React, { useEffect, useState } from 'react';
import { Button, DatePicker, Form, InputNumber, Modal, Select, Space, Table, message } from 'antd';
import dayjs from 'dayjs';
import * as catalogApi from '@/api/catalog';
import * as adminApi from '@/api/admin';
import type { CinemaVO, MovieVO, HallVO, ShowVO } from '@/types';

const AdminShowsPage: React.FC = () => {
  const [cinemas, setCinemas] = useState<CinemaVO[]>([]);
  const [movies, setMovies] = useState<MovieVO[]>([]);
  const [halls, setHalls] = useState<HallVO[]>([]);
  const [cinemaId, setCinemaId] = useState<string>();
  const [movieId, setMovieId] = useState<string>();
  const [date, setDate] = useState(dayjs().format('YYYY-MM-DD'));
  const [shows, setShows] = useState<ShowVO[]>([]);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    void catalogApi.listCinemas({ page: 1, size: 50 }).then((r) => setCinemas(r.items));
    void catalogApi.listMovies({ page: 1, size: 50 }).then((r) => setMovies(r.items));
  }, []);

  useEffect(() => {
    if (!cinemaId) return;
    void adminApi.listHalls(cinemaId).then((r) => setHalls(r.items));
  }, [cinemaId]);

  const query = async () => {
    if (!cinemaId || !movieId || !date) {
      message.warning('请先选齐影院、影片、日期');
      return;
    }
    const res = await catalogApi.listShows({ cinemaId, movieId, date });
    setShows(res.items);
  };

  return (
    <div>
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          placeholder="影院"
          style={{ width: 200 }}
          options={cinemas.map((c) => ({ value: c.cinemaId, label: c.name }))}
          value={cinemaId}
          onChange={setCinemaId}
        />
        <Select
          placeholder="影片"
          style={{ width: 200 }}
          options={movies.map((m) => ({ value: m.movieId, label: m.title }))}
          value={movieId}
          onChange={setMovieId}
        />
        <DatePicker
          value={dayjs(date)}
          onChange={(d) => setDate(d ? d.format('YYYY-MM-DD') : date)}
        />
        <Button type="primary" onClick={query}>
          查询
        </Button>
        <Button onClick={() => setOpen(true)}>+ 新建场次</Button>
      </Space>
      <Table
        rowKey="showId"
        dataSource={shows}
        columns={[
          {
            title: '开场',
            dataIndex: 'startTime',
            render: (t: string) => t.replace('T', ' ').slice(0, 16),
          },
          { title: '影厅', dataIndex: 'hallName' },
          { title: '票价', dataIndex: 'price' },
          { title: '余座', dataIndex: 'seatRemain' },
          {
            title: '操作',
            render: (_, r) => (
              <Space>
                <Button
                  type="link"
                  onClick={() => {
                    Modal.confirm({
                      title: '更新票价',
                      content: (
                        <InputNumber id="show-price" defaultValue={r.price} style={{ width: '100%' }} />
                      ),
                      onOk: async () => {
                        const el = document.getElementById('show-price') as HTMLInputElement;
                        await adminApi.updateShow(r.showId, { price: Number(el?.value) || r.price });
                        message.success('已更新');
                        void query();
                      },
                    });
                  }}
                >
                  编辑
                </Button>
                <Button
                  type="link"
                  danger
                  onClick={async () => {
                    await adminApi.cancelShow(r.showId);
                    message.success('已取消');
                    void query();
                  }}
                >
                  取消
                </Button>
              </Space>
            ),
          },
        ]}
      />
      <Modal title="新建场次" open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()} width={520}>
        <Form
          form={form}
          layout="vertical"
          onFinish={async (v) => {
            const start = dayjs(v.startTime).format('YYYY-MM-DDTHH:mm:ss+08:00');
            const movie = movies.find((m) => m.movieId === v.movieId);
            const end = dayjs(v.startTime)
              .add(movie?.durationMin || 120, 'minute')
              .format('YYYY-MM-DDTHH:mm:ss+08:00');
            await adminApi.createShow({
              movieId: v.movieId,
              cinemaId: v.cinemaId,
              hallId: v.hallId,
              startTime: start,
              endTime: end,
              price: v.price,
            });
            message.success('已创建（将锁定座位图）');
            setOpen(false);
            setCinemaId(v.cinemaId);
            setMovieId(v.movieId);
            setDate(dayjs(v.startTime).format('YYYY-MM-DD'));
            setTimeout(() => void query(), 100);
          }}
        >
          <Form.Item name="cinemaId" label="影院" rules={[{ required: true }]}>
            <Select
              options={cinemas.map((c) => ({ value: c.cinemaId, label: c.name }))}
              onChange={(v) => {
                form.setFieldValue('cinemaId', v);
                void adminApi.listHalls(v).then((r) => setHalls(r.items));
              }}
            />
          </Form.Item>
          <Form.Item name="movieId" label="影片" rules={[{ required: true }]}>
            <Select options={movies.map((m) => ({ value: m.movieId, label: m.title }))} />
          </Form.Item>
          <Form.Item name="hallId" label="影厅" rules={[{ required: true }]}>
            <Select options={halls.map((h) => ({ value: h.hallId, label: h.name }))} />
          </Form.Item>
          <Form.Item name="startTime" label="开场时间" rules={[{ required: true }]}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="price" label="票价" rules={[{ required: true }]} initialValue={45}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AdminShowsPage;
