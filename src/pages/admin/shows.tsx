import React, { useEffect, useMemo, useState } from 'react';
import { Button, DatePicker, Form, InputNumber, Modal, Select, Space, Table, message } from 'antd';
import dayjs from 'dayjs';
import * as catalogApi from '@/api/catalog';
import * as adminApi from '@/api/admin';
import type { CinemaVO, MovieVO, HallVO, ShowVO, ZonePrice } from '@/types';
import { zoneLabel } from '@/utils/zone';

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

  const startTimeValid = (value: dayjs.Dayjs) => value.isAfter(dayjs());
  const localScheduleConflict = (hallId: string, start: dayjs.Dayjs, end: dayjs.Dayjs, excludeShowId?: string) =>
    shows.some((show) => {
      if (show.showId === excludeShowId || show.hallId !== hallId || show.status === 'cancelled') return false;
      return start.isBefore(dayjs(show.endTime).add(20, 'minute')) && end.add(20, 'minute').isAfter(dayjs(show.startTime));
    });

  useEffect(() => {
    void catalogApi.listCinemas({ sort: 'price', page: 1, size: 50 }).then((r) => setCinemas(r.items)).catch(() => setCinemas([]));
    void catalogApi.listMovies({ page: 1, size: 50 }).then((r) => setMovies(r.items)).catch(() => setMovies([]));
  }, []);

  useEffect(() => {
    if (!cinemaId) return;
    void adminApi.listHalls({ cinemaId }).then((r) => setHalls(r.items)).catch(() => setHalls([]));
  }, [cinemaId]);

  const onFormHallChange = (hallId: string) => {
    const hall = formHalls.find((h) => h.hallId === hallId) || halls.find((h) => h.hallId === hallId);
    setFormZones([]);
    form.setFieldValue('zonePriceMap', undefined);
    if (!hall) return;
    // 当前真实接口未提供按影厅读取座位图分区的能力，不能凭空构造分区价格。
    message.warning('当前无法读取该影厅的座位图分区，暂不能创建排片。请等待座位图分区读取接口接入。');
  };

  const query = async () => {
    if (!cinemaId || !movieId || !date) {
      message.warning('请先选齐影院、影片、日期');
      return;
    }
    try {
      const res = await adminApi.adminListShows({ cinemaId, movieId, date });
      setShows(res.items);
    } catch {
      setShows([]);
    }
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
    let zones = show.zonePrices?.map((z) => z.zone) || [];
    if (!zones.length) zones = ['A'];
    setEditZones(zones);
    const priceMap: Record<string, number> = {};
    for (const z of zones) {
      const hit = show.zonePrices?.find((p) => p.zone === z);
      priceMap[z] = hit?.price ?? show.price;
    }
    editForm.setFieldsValue({ zonePriceMap: priceMap, startTime: dayjs(show.startTime) });
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
            title: '状态',
            dataIndex: 'status',
            render: (status: ShowVO['status']) =>
              status === 'off_sale' ? '已停售' : status === 'cancelled' ? '已取消' : '售票中',
          },
          {
            title: '操作',
            render: (_, r) => (
              <Space>
                <Button type="link" disabled={r.status !== 'on_sale'} onClick={() => void openEdit(r)}>
                  编辑区价
                </Button>
                <Button
                  type="link"
                  disabled={r.status !== 'on_sale'}
                  onClick={() => {
                    Modal.confirm({
                      title: '确认停售？',
                      content: '停售后将关闭该场次购票，并取消所有未支付订单；已出票订单不受影响。',
                      onOk: async () => {
                        try {
                          await adminApi.closeShowSale(r.showId);
                          message.success('该场次已停售');
                          await query();
                        } catch {
                          // 请求层已处理。
                        }
                      },
                    });
                  }}
                >
                  停售
                </Button>
                <Button
                  type="link"
                  danger
                  disabled={r.status === 'cancelled'}
                  onClick={async () => {
                    try {
                      const impact = await adminApi.getShowImpact(r.showId);
                      Modal.confirm({
                        title: '确认取消场次？',
                        content: `将取消 ${impact.pendingPayCount} 笔待支付订单，并使 ${impact.issuedCount} 张未核销票券失效；已核销 ${impact.usedCount} 笔仅保留记录。`,
                        okButtonProps: { danger: true },
                        onOk: async () => {
                          try {
                            await adminApi.cancelShow(r.showId);
                            message.success('场次已取消，关联订单已按规则处理');
                            await query();
                          } catch {
                            // 请求层已处理。
                          }
                        },
                      });
                    } catch {
                      // 请求层已处理。
                    }
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
              message.error('无法确认影厅座位图分区，不能创建包含未知分区价格的排片');
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
            const movie = movies.find((m) => m.movieId === v.movieId);
            const startMoment = dayjs(v.startTime);
            const endMoment = startMoment.add(movie?.durationMin || 120, 'minute');
            if (!startTimeValid(startMoment)) {
              message.error('开场时间必须晚于当前时间');
              return;
            }
            if (localScheduleConflict(v.hallId, startMoment, endMoment)) {
              message.error('与当前列表中的同影厅场次冲突，前后需预留 20 分钟缓冲');
              return;
            }
            const start = startMoment.format('YYYY-MM-DDTHH:mm:ss+08:00');
            const end = endMoment.format('YYYY-MM-DDTHH:mm:ss+08:00');
            try {
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
            } catch {
              // 请求层已处理。
            }
          }}
        >
          <Form.Item name="cinemaId" label="影院" rules={[{ required: true }]}>
            <Select
              options={cinemas.map((c) => ({ value: c.cinemaId, label: c.name }))}
              onChange={(v) => {
                form.setFieldValue('hallId', undefined);
                setFormZones([]);
                void adminApi.listHalls({ cinemaId: v }).then((r) => setFormHalls(r.items)).catch(() => setFormHalls([]));
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
              onChange={onFormHallChange}
            />
          </Form.Item>
          <Form.Item name="startTime" label="开场时间" rules={[{ required: true }]}>
            <DatePicker
              showTime
              disabledDate={(d) => !!d && d.endOf('day').isBefore(dayjs().startOf('day'))}
              style={{ width: '100%' }}
            />
          </Form.Item>
          {formZones.length === 0 ? (
            <p style={{ color: '#999' }}>真实接口暂未提供座位图分区读取，选择影厅后无法生成区价；为避免创建未知分区排片，暂不允许提交。</p>
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
            const startMoment = dayjs(v.startTime);
            const movie = movies.find((m) => m.movieId === editShow.movieId);
            const endMoment = startMoment.add(movie?.durationMin || 120, 'minute');
            if (!startTimeValid(startMoment)) {
              message.error('开场时间必须晚于当前时间');
              return;
            }
            if (localScheduleConflict(editShow.hallId, startMoment, endMoment, editShow.showId)) {
              message.error('与当前列表中的同影厅场次冲突，前后需预留 20 分钟缓冲');
              return;
            }
            try {
              await adminApi.updateShow(editShow.showId, {
                zonePrices,
                startTime: startMoment.format('YYYY-MM-DDTHH:mm:ss+08:00'),
                endTime: endMoment.format('YYYY-MM-DDTHH:mm:ss+08:00'),
              });
              message.success('区价已更新');
              setEditShow(null);
              void query();
            } catch {
              // 请求层已处理。
            }
          }}
        >
          <Form.Item name="startTime" label="开场时间" rules={[{ required: true }]}>
            <DatePicker
              showTime
              disabledDate={(d) => !!d && d.endOf('day').isBefore(dayjs().startOf('day'))}
              style={{ width: '100%' }}
            />
          </Form.Item>
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
