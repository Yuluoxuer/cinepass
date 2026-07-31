import React, { useEffect, useMemo, useState } from 'react';
import { Button, DatePicker, Form, InputNumber, Modal, Select, Space, Table, message } from 'antd';
import dayjs from 'dayjs';
import * as catalogApi from '@/api/catalog';
import * as adminApi from '@/api/admin';
import type { CinemaVO, MovieVO, HallVO, ShowVO, ZonePrice, SeatMapVO } from '@/types';
import { distinctZones, zoneLabel } from '@/utils/zone';

function formatZonePrices(zonePrices?: ZonePrice[], fallback?: number) {
  if (zonePrices && zonePrices.length > 0) {
    const min = Math.min(...zonePrices.map((z) => z.price));
    const detail = zonePrices.map((z) => `${zoneLabel(z.zone)}¥${z.price}`).join(' / ');
    return `¥${min}起（${detail}）`;
  }
  if (fallback != null) return `¥${fallback}起`;
  return '—';
}

const AdminShowsPage: React.FC = () => {
  const [cinemas, setCinemas] = useState<CinemaVO[]>([]);
  const [movies, setMovies] = useState<MovieVO[]>([]);
  const [halls, setHalls] = useState<HallVO[]>([]);
  const [cinemaId, setCinemaId] = useState<string>();
  const [movieId, setMovieId] = useState<string>();
  const [date, setDate] = useState(dayjs().format('YYYY-MM-DD'));
  const [shows, setShows] = useState<ShowVO[]>([]);
  const [open, setOpen] = useState(false);
  const [editShow, setEditShow] = useState<ShowVO | null>(null);
  const [form] = Form.useForm();
  const [editForm] = Form.useForm();
  const [formHalls, setFormHalls] = useState<HallVO[]>([]);
  const [formZones, setFormZones] = useState<string[]>([]);
  const [editZones, setEditZones] = useState<string[]>([]);
  const [seatMapCache, setSeatMapCache] = useState<Record<string, SeatMapVO>>({});

  useEffect(() => {
    void catalogApi.listCinemas({ page: 1, size: 50 }).then((r) => setCinemas(r.items));
    void catalogApi.listMovies({ page: 1, size: 50 }).then((r) => setMovies(r.items));
  }, []);

  useEffect(() => {
    if (!cinemaId) return;
    void adminApi.listHalls(cinemaId).then((r) => setHalls(r.items));
  }, [cinemaId]);

  const loadSeatMapZones = async (seatMapId: string): Promise<string[]> => {
    if (seatMapCache[seatMapId]) {
      const cached = seatMapCache[seatMapId];
      return cached.zones?.length ? cached.zones : distinctZones(cached.seats);
    }
    const map = await adminApi.getSeatMapTemplate(seatMapId);
    setSeatMapCache((prev) => ({ ...prev, [seatMapId]: map }));
    return map.zones?.length ? map.zones : distinctZones(map.seats);
  };

  const onFormHallChange = async (hallId: string) => {
    const hall = formHalls.find((h) => h.hallId === hallId) || halls.find((h) => h.hallId === hallId);
    if (!hall) {
      setFormZones([]);
      return;
    }
    const zones = await loadSeatMapZones(hall.seatMapId);
    setFormZones(zones);
    const prices: Record<string, number> = {};
    zones.forEach((z, i) => {
      prices[z] = 45 + i * 10;
    });
    form.setFieldsValue({ zonePriceMap: prices });
  };

  const query = async () => {
    if (!cinemaId || !movieId || !date) {
      message.warning('请先选齐影院、影片、日期');
      return;
    }
    const res = await catalogApi.listShows({ cinemaId, movieId, date });
    setShows(res.items);
  };

  const zonePriceFields = useMemo(
    () =>
      formZones.map((z) => (
        <Form.Item
          key={z}
          name={['zonePriceMap', z]}
          label={`${zoneLabel(z)} 价格`}
          rules={[{ required: true, message: `请填写${zoneLabel(z)}价格` }]}
        >
          <InputNumber min={0.01} precision={2} style={{ width: '100%' }} addonBefore="¥" />
        </Form.Item>
      )),
    [formZones],
  );

  const openCreate = () => {
    form.resetFields();
    setFormZones([]);
    setFormHalls(halls);
    if (cinemaId) form.setFieldValue('cinemaId', cinemaId);
    if (movieId) form.setFieldValue('movieId', movieId);
    setOpen(true);
  };

  const openEdit = async (show: ShowVO) => {
    setEditShow(show);
    const hall = halls.find((h) => h.hallId === show.hallId);
    let zones = show.zonePrices?.map((z) => z.zone) || [];
    if (hall) {
      zones = await loadSeatMapZones(hall.seatMapId);
    }
    setEditZones(zones);
    const priceMap: Record<string, number> = {};
    for (const z of zones) {
      const hit = show.zonePrices?.find((p) => p.zone === z);
      priceMap[z] = hit?.price ?? show.price;
    }
    editForm.setFieldsValue({ zonePriceMap: priceMap });
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
        <Button onClick={openCreate}>+ 新建场次</Button>
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
          {
            title: '票价',
            render: (_, r) => formatZonePrices(r.zonePrices, r.price),
          },
          { title: '余座', dataIndex: 'seatRemain' },
          {
            title: '操作',
            render: (_, r) => (
              <Space>
                <Button type="link" onClick={() => void openEdit(r)}>
                  编辑区价
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

      <Modal
        title="新建场次"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        width={560}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={async (v) => {
            if (formZones.length === 0) {
              message.error('请选择影厅以加载分区');
              return;
            }
            const zonePriceMap = (v.zonePriceMap || {}) as Record<string, number>;
            const zonePrices: ZonePrice[] = formZones.map((z) => ({
              zone: z,
              price: Number(zonePriceMap[z]),
            }));
            if (zonePrices.some((p) => !(p.price > 0))) {
              message.error('请为每个分区填写有效价格');
              return;
            }
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
              zonePrices,
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
                form.setFieldValue('hallId', undefined);
                setFormZones([]);
                void adminApi.listHalls(v).then((r) => setFormHalls(r.items));
              }}
            />
          </Form.Item>
          <Form.Item name="movieId" label="影片" rules={[{ required: true }]}>
            <Select options={movies.map((m) => ({ value: m.movieId, label: m.title }))} />
          </Form.Item>
          <Form.Item name="hallId" label="影厅" rules={[{ required: true }]}>
            <Select
              options={(formHalls.length ? formHalls : halls).map((h) => ({
                value: h.hallId,
                label: h.name,
              }))}
              onChange={(v) => void onFormHallChange(v)}
            />
          </Form.Item>
          <Form.Item name="startTime" label="开场时间" rules={[{ required: true }]}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          {formZones.length === 0 ? (
            <p style={{ color: '#999' }}>选择影厅后，将按该座位图分区填写各区价格</p>
          ) : (
            <>
              <p style={{ color: '#666', marginBottom: 8 }}>
                本厅座位图分区：{formZones.map((z) => zoneLabel(z)).join('、')}
              </p>
              {zonePriceFields}
            </>
          )}
        </Form>
      </Modal>

      <Modal
        title={editShow ? `编辑区价 · ${editShow.hallName}` : '编辑区价'}
        open={!!editShow}
        onCancel={() => setEditShow(null)}
        onOk={() => editForm.submit()}
        width={480}
        destroyOnClose
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={async (v) => {
            if (!editShow) return;
            const zonePriceMap = (v.zonePriceMap || {}) as Record<string, number>;
            const zonePrices: ZonePrice[] = editZones.map((z) => ({
              zone: z,
              price: Number(zonePriceMap[z]),
            }));
            if (zonePrices.some((p) => !(p.price > 0))) {
              message.error('请为每个分区填写有效价格');
              return;
            }
            await adminApi.updateShow(editShow.showId, { zonePrices });
            message.success('区价已更新');
            setEditShow(null);
            void query();
          }}
        >
          {editZones.map((z) => (
            <Form.Item
              key={z}
              name={['zonePriceMap', z]}
              label={`${zoneLabel(z)} 价格`}
              rules={[{ required: true, message: `请填写${zoneLabel(z)}价格` }]}
            >
              <InputNumber min={0.01} precision={2} style={{ width: '100%' }} addonBefore="¥" />
            </Form.Item>
          ))}
        </Form>
      </Modal>
    </div>
  );
};

export default AdminShowsPage;
