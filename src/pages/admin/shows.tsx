import React, { useEffect, useMemo, useState } from 'react';
import { Button, DatePicker, Empty, Form, InputNumber, Modal, Segmented, Select, Space, Table, message } from 'antd';
import dayjs from 'dayjs';
import * as catalogApi from '@/api/catalog';
import * as adminApi from '@/api/admin';
import type { CinemaVO, MovieVO, HallVO, ShowVO, ZonePrice } from '@/types';
import { distinctZones, zoneLabel } from '@/utils/zone';
import { getCinemaIdFromAccessToken, useAuthStore } from '@/stores/auth';

/** 场次筛选：全部 / 未开始 / 已开始 / 已取消 */
type ShowFilter = 'all' | 'upcoming' | 'started' | 'cancelled';

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
  const user = useAuthStore((s) => s.user);
  const staffCinemaId = user?.role === 'staff' ? user.cinemaId || getCinemaIdFromAccessToken() : undefined;

  const [cinemas, setCinemas] = useState<CinemaVO[]>([]);
  const [movies, setMovies] = useState<MovieVO[]>([]);
  const [halls, setHalls] = useState<HallVO[]>([]);
  const [cinemaId, setCinemaId] = useState<string | undefined>(staffCinemaId);
  const [movieId, setMovieId] = useState<string | undefined>();
  const [date, setDate] = useState<string | undefined>();
  const [showFilter, setShowFilter] = useState<ShowFilter>('all');
  const [shows, setShows] = useState<ShowVO[]>([]);
  const [querying, setQuerying] = useState(false);
  const [queried, setQueried] = useState(false);
  const [open, setOpen] = useState(false);
  const [editShow, setEditShow] = useState<ShowVO | null>(null);
  const [form] = Form.useForm();
  const [editForm] = Form.useForm();
  const [formHalls, setFormHalls] = useState<HallVO[]>([]);
  const [formZones, setFormZones] = useState<string[]>([]);
  const [editZones, setEditZones] = useState<string[]>([]);

  // 批量创建
  const [batchOpen, setBatchOpen] = useState(false);
  const [batchForm] = Form.useForm();
  const [batchHalls, setBatchHalls] = useState<HallVO[]>([]);
  const [batchZones, setBatchZones] = useState<string[]>([]);
  const [batchMovieDuration, setBatchMovieDuration] = useState<number>(120);

  const movieTitleById = useMemo(() => {
    const map = new Map<string, string>();
    movies.forEach((m) => map.set(m.movieId, m.title));
    return map;
  }, [movies]);

  const displayedShows = useMemo(() => {
    const now = dayjs();
    if (showFilter === 'all') return shows;
    if (showFilter === 'cancelled') {
      return shows.filter((s) => s.status === 'cancelled');
    }
    if (showFilter === 'upcoming') {
      return shows.filter((s) => s.status !== 'cancelled' && dayjs(s.startTime).isAfter(now));
    }
    // 已开始：开场时间已到且未取消
    return shows.filter((s) => s.status !== 'cancelled' && !dayjs(s.startTime).isAfter(now));
  }, [shows, showFilter]);

  const filterCounts = useMemo(() => {
    const now = dayjs();
    let upcoming = 0;
    let started = 0;
    let cancelled = 0;
    for (const s of shows) {
      if (s.status === 'cancelled') {
        cancelled += 1;
        continue;
      }
      if (dayjs(s.startTime).isAfter(now)) upcoming += 1;
      else started += 1;
    }
    return {
      all: shows.length,
      upcoming,
      started,
      cancelled,
    };
  }, [shows]);

  const startTimeValid = (value: dayjs.Dayjs) => value.isAfter(dayjs());
  const localScheduleConflict = (hallId: string, start: dayjs.Dayjs, end: dayjs.Dayjs, excludeShowId?: string) =>
    shows.some((show) => {
      if (show.showId === excludeShowId || show.hallId !== hallId || show.status === 'cancelled') return false;
      return start.isBefore(dayjs(show.endTime).add(20, 'minute')) && end.add(20, 'minute').isAfter(dayjs(show.startTime));
    });

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
    void catalogApi
      .listMovies({ page: 1, size: 50 })
      .then((r) => setMovies(r.items))
      .catch(() => setMovies([]));
  }, [staffCinemaId]);

  useEffect(() => {
    if (!cinemaId) {
      setHalls([]);
      return;
    }
    void adminApi.listHalls({ cinemaId }).then((r) => setHalls(r.items)).catch(() => setHalls([]));
  }, [cinemaId]);

  const query = async (override?: { cinemaId?: string; movieId?: string | null; date?: string | null }) => {
    const nextCinemaId = override?.cinemaId ?? cinemaId;
    const nextMovieId = override && 'movieId' in override ? override.movieId || undefined : movieId;
    const nextDate = override && 'date' in override ? override.date || undefined : date;
    if (!nextCinemaId) {
      message.warning('请先选择影院');
      return;
    }
    setQuerying(true);
    try {
      const res = await adminApi.adminListShows({
        cinemaId: nextCinemaId,
        movieId: nextMovieId,
        date: nextDate,
      });
      setShows(res.items || []);
    } catch {
      setShows([]);
    } finally {
      setQueried(true);
      setQuerying(false);
    }
  };

  // 选定影院后自动查询（影片/日期可空 = 该院全部排片）
  useEffect(() => {
    if (!cinemaId) {
      setShows([]);
      setQueried(false);
      return;
    }
    void query({ cinemaId, movieId, date });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cinemaId, movieId, date]);

  const loadHallZones = async (hallId: string, hallList: HallVO[]) => {
    const hall = hallList.find((h) => h.hallId === hallId);
    setFormZones([]);
    form.setFieldValue('zonePriceMap', undefined);
    if (!hall?.seatMapId) {
      message.warning('该影厅未绑定座位图，无法创建排片');
      return;
    }
    try {
      const map = await adminApi.getSeatMapTemplate(hall.seatMapId);
      const zones = distinctZones(map.seats || []);
      if (!zones.length) {
        message.warning('座位图没有分区信息，无法创建排片');
        return;
      }
      setFormZones(zones);
    } catch {
      // 请求层已处理。
    }
  };

  const onFormHallChange = (hallId: string) => {
    void loadHallZones(hallId, formHalls.length ? formHalls : halls);
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
    let zones = show.zonePrices?.map((z) => z.zone).filter(Boolean) || [];
    // 以座位图实际分区为准（含自定义区名）；已存分区价作价格回填
    try {
      let hall = halls.find((h) => h.hallId === show.hallId);
      if (!hall?.seatMapId) {
        const hallPage = await adminApi.listHalls({ cinemaId: show.cinemaId, page: 1, size: 100 });
        hall = (hallPage.items || []).find((h) => h.hallId === show.hallId);
      }
      if (hall?.seatMapId) {
        const map = await adminApi.getSeatMapTemplate(hall.seatMapId);
        const fromMap = distinctZones(map.seats || []);
        if (fromMap.length) {
          zones = fromMap;
        }
      }
    } catch {
      // 请求层已处理；退回已有 zonePrices / 兜底 A
    }
    if (!zones.length) zones = ['A'];
    setEditZones(zones);
    const priceMap: Record<string, number> = {};
    const saved = new Map((show.zonePrices || []).map((z) => [z.zone, z.price]));
    for (const z of zones) {
      priceMap[z] = saved.has(z) ? Number(saved.get(z)) : show.price;
    }
    editForm.setFieldsValue({ zonePriceMap: priceMap, startTime: dayjs(show.startTime) });
  };

  /** 批量创建：打开预填表单 */
  const openBatchCreate = async () => {
    if (!cinemaId) { message.warning('请先选择影院'); return; }
    batchForm.resetFields();
    setBatchZones([]);
    setBatchMovieDuration(120);
    // 预填影院
    batchForm.setFieldsValue({ cinemaId });
    // 加载该影院所有影厅
    try {
      const list = await adminApi.listHalls({ cinemaId, page: 1, size: 200 });
      setBatchHalls(list.items || []);
    } catch { setBatchHalls([]); }
    setBatchOpen(true);
  };

  /** 批量创建：选择影厅后加载分区 */
  const loadBatchZones = async (hallId: string) => {
    const hall = batchHalls.find((h) => h.hallId === hallId);
    if (!hall?.seatMapId) { setBatchZones([]); return; }
    try {
      const seatMap = await adminApi.getSeatMapTemplate(hall.seatMapId);
      const zones = distinctZones(seatMap?.seats || []);
      setBatchZones(zones);
      if (!zones.length) zones.push('A');
      const init: Record<string, number> = {};
      zones.forEach((z) => { init[z] = 0; });
      batchForm.setFieldsValue({ batchZonePrices: init });
    } catch { setBatchZones([]); }
  };

  /** 批量创建：选择影片后更新时长 */
  const onBatchMovieChange = (movieId: string) => {
    const movie = movies.find((m) => m.movieId === movieId);
    if (movie?.durationMin) {
      setBatchMovieDuration(movie.durationMin);
      batchForm.setFieldsValue({ intervalMin: movie.durationMin + 30 });
    }
  };

  const emptyText = !cinemaId
    ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请先选择影院" />
    : querying
      ? '查询中…'
      : queried
        ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有数据" />
        : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请点击查询" />;

  return (
    <div>
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          placeholder="影院"
          style={{ width: 200 }}
          options={cinemas.map((c) => ({ value: c.cinemaId, label: c.name }))}
          value={cinemaId}
          disabled={!!staffCinemaId}
          onChange={(v) => setCinemaId(v)}
        />
        <Select
          placeholder="全部影片"
          style={{ width: 200 }}
          allowClear
          options={movies.map((m) => ({ value: m.movieId, label: m.title }))}
          value={movieId}
          onChange={(v) => setMovieId(v)}
        />
        <DatePicker
          placeholder="全部日期"
          allowClear
          value={date ? dayjs(date) : null}
          onChange={(d) => setDate(d ? d.format('YYYY-MM-DD') : undefined)}
        />
        <Button type="primary" loading={querying} onClick={() => void query()}>
          查询
        </Button>
        <Button onClick={openCreate}>+ 新建场次</Button>
        <Button onClick={openBatchCreate}>📋 批量创建</Button>
      </Space>
      <div style={{ marginBottom: 16 }}>
        <span style={{ marginRight: 12, color: 'rgba(0,0,0,0.65)' }}>场次状态</span>
        <Segmented
          value={showFilter}
          onChange={(v) => setShowFilter(v as ShowFilter)}
          options={[
            { value: 'all', label: `全部 (${filterCounts.all})` },
            { value: 'upcoming', label: `未开始 (${filterCounts.upcoming})` },
            { value: 'started', label: `已开始 (${filterCounts.started})` },
            { value: 'cancelled', label: `已取消 (${filterCounts.cancelled})` },
          ]}
        />
      </div>
      <Table
        rowKey="showId"
        loading={querying}
        dataSource={displayedShows}
        locale={{ emptyText }}
        columns={[
          {
            title: '开场',
            dataIndex: 'startTime',
            render: (t: string) => t.replace('T', ' ').slice(0, 16),
          },
          {
            title: '影片',
            dataIndex: 'movieId',
            render: (id: string) => movieTitleById.get(id) || id,
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
                  disabled={r.status !== 'off_sale' && r.status !== 'cancelled'}
                  onClick={() => {
                    Modal.confirm({
                      title: '确认恢复售票？',
                      content: '恢复后将重新开放该场次购票。',
                      onOk: async () => {
                        try {
                          await adminApi.resumeShowSale(r.showId);
                          message.success('该场次已恢复售票');
                          await query();
                        } catch {
                          // 请求层已处理。
                        }
                      },
                    });
                  }}
                >
                  恢复售票
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
              await query({ cinemaId: v.cinemaId, movieId, date });
            } catch {
              // 请求层已处理。
            }
          }}
        >
          <Form.Item name="cinemaId" label="影院" rules={[{ required: true }]}>
            <Select
              options={cinemas.map((c) => ({ value: c.cinemaId, label: c.name }))}
              disabled={!!staffCinemaId}
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
          <Form.Item
            name="startTime"
            label="开场时间"
            rules={[{ required: true, message: '请选择开场时间' }]}
          >
            <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
          </Form.Item>
          {zonePriceFields}
        </Form>
      </Modal>

      <Modal
        title="编辑区价 / 开场时间"
        open={!!editShow}
        onCancel={() => setEditShow(null)}
        onOk={() => editForm.submit()}
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
                startTime: startMoment.format('YYYY-MM-DDTHH:mm:ss+08:00'),
                endTime: endMoment.format('YYYY-MM-DDTHH:mm:ss+08:00'),
                zonePrices,
              });
              message.success('已更新');
              setEditShow(null);
              await query();
            } catch {
              // 请求层已处理。
            }
          }}
        >
          <Form.Item name="startTime" label="开场时间" rules={[{ required: true }]}>
            <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
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

      {/* 批量创建场次 */}
      <Modal
        title="📋 批量创建场次"
        open={batchOpen}
        onCancel={() => setBatchOpen(false)}
        onOk={() => batchForm.submit()}
        width={600}
        destroyOnClose
        okText="批量创建"
      >
        <Form
          form={batchForm}
          layout="vertical"
          onFinish={async (v) => {
            const zonePriceMap = (v.batchZonePrices || {}) as Record<string, number>;
            const zonePrices: ZonePrice[] = batchZones.map((z) => ({
              zone: z,
              price: Number(zonePriceMap[z] || 0),
            }));
            if (!batchZones.length) {
              message.error('所选影厅没有座位分区，无法批量创建');
              return;
            }
            const hasPrice = zonePrices.some((z) => z.price > 0);
            if (!hasPrice) {
              message.error('请至少填写一个分区的票价');
              return;
            }
            try {
              const res = await adminApi.createBatchShows({
                movieId: v.movieId,
                cinemaId: v.cinemaId,
                hallId: v.hallId,
                dateStart: v.dateRange[0].format('YYYY-MM-DD'),
                dateEnd: v.dateRange[1].format('YYYY-MM-DD'),
                timeStart: v.timeRange[0].format('HH:mm'),
                timeEnd: v.timeRange[1].format('HH:mm'),
                intervalMin: v.intervalMin,
                zonePrices,
              });
              message.success(`成功创建 ${res.length} 个场次`);
              setBatchOpen(false);
              await query();
            } catch {
              // 请求层已处理
            }
          }}
        >
          <Form.Item name="cinemaId" label="影院" rules={[{ required: true }]}>
            <Select
              disabled={!!staffCinemaId}
              options={cinemas.map((c) => ({ value: c.cinemaId, label: c.name }))}
            />
          </Form.Item>
          <Form.Item name="movieId" label="影片" rules={[{ required: true }]}>
            <Select
              showSearch
              filterOption={(input, option) =>
                (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
              }
              options={movies.map((m) => ({ value: m.movieId, label: `${m.title}（${m.durationMin || '?'}分钟）` }))}
              onChange={(v) => onBatchMovieChange(v)}
            />
          </Form.Item>
          <Form.Item name="hallId" label="影厅" rules={[{ required: true }]}>
            <Select
              options={batchHalls.map((h) => ({ value: h.hallId, label: h.name }))}
              onChange={(v) => void loadBatchZones(v)}
            />
          </Form.Item>
          <Form.Item
            name="dateRange"
            label="日期范围"
            rules={[{ required: true, message: '请选择起止日期' }]}
            extra={`影片时长 ${batchMovieDuration} 分钟，间隔不得小于该值`}
          >
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="timeRange"
            label="每日时段"
            rules={[{ required: true, message: '请选择每日起止时间' }]}
          >
            <DatePicker.RangePicker picker="time" format="HH:mm" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="intervalMin"
            label="场次间隔（分钟）"
            rules={[
              { required: true, message: '请输入间隔分钟数' },
              {
                validator: (_, v) =>
                  v >= batchMovieDuration
                    ? Promise.resolve()
                    : Promise.reject(`间隔不得小于影片时长 ${batchMovieDuration} 分钟`),
              },
            ]}
            extra="两场之间（上一场开场→下一场开场）的间隔"
          >
            <InputNumber min={batchMovieDuration} style={{ width: '100%' }} addonAfter="分钟" />
          </Form.Item>
          {batchZones.length > 0 && (
            <>
              <div style={{ marginBottom: 8, fontWeight: 600 }}>分区票价</div>
              {batchZones.map((z) => (
                <Form.Item
                  key={z}
                  name={['batchZonePrices', z]}
                  label={`${zoneLabel(z)} 价格`}
                  rules={[{ required: true, message: `请填写${zoneLabel(z)}价格` }]}
                >
                  <InputNumber min={0.01} precision={2} style={{ width: '100%' }} addonBefore="¥" />
                </Form.Item>
              ))}
            </>
          )}
        </Form>
      </Modal>
    </div>
  );
};

export default AdminShowsPage;
