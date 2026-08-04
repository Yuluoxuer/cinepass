import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Input, InputNumber, Select, Space, Tag, message } from 'antd';
import { history, useLocation, useParams } from 'umi';
import * as adminApi from '@/api/admin';
import * as catalogApi from '@/api/catalog';
import { getCinemaIdFromAccessToken, useAuthStore } from '@/stores/auth';
import type { CinemaVO, SeatType, SeatVO, SeatZone } from '@/types';
import { DEFAULT_ZONE_PRESETS, distinctZones, zoneColor, zoneLabel } from '@/utils/zone';
import styles from './seatMapEditor.less';

type Cell = { kind: 'empty' } | { kind: 'seat'; type: SeatType; zone: SeatZone; defaultStatus: 'available' | 'unavailable'; couplePairId: string | null };

const SeatMapEditorPage: React.FC = () => {
  const location = useLocation();
  const { seatMapId } = useParams<{ seatMapId: string }>();
  const isNew = !seatMapId || seatMapId === 'new';
  const queryCinemaId = new URLSearchParams(location.search).get('cinemaId') || '';
  const user = useAuthStore((s) => s.user);
  const staffCinemaId = user?.role === 'staff' ? user.cinemaId || getCinemaIdFromAccessToken() : undefined;
  const [cinemaId, setCinemaId] = useState(queryCinemaId || staffCinemaId || '');
  const [cinemas, setCinemas] = useState<CinemaVO[]>([]);
  const [rows, setRows] = useState(8);
  const [cols, setCols] = useState(12);
  const [screenLabel, setScreenLabel] = useState('银幕');
  const [id, setId] = useState('');
  const [mutable, setMutable] = useState(true);
  const [loading, setLoading] = useState(!isNew);
  const [tool, setTool] = useState<'add' | 'delete' | 'zone' | 'type'>('add');
  const [zone, setZone] = useState<SeatZone>('A');
  const [customZone, setCustomZone] = useState('');
  const [seatType, setSeatType] = useState<SeatType>('normal');
  const [zonePresets, setZonePresets] = useState<string[]>([...DEFAULT_ZONE_PRESETS]);
  const emptyGrid = (r: number, c: number): Cell[][] => Array.from({ length: r }, () => Array.from({ length: c }, () => ({ kind: 'empty' as const })));
  const [grid, setGrid] = useState<Cell[][]>(() => emptyGrid(8, 12));
  const readOnly = !isNew && mutable === false;

  const resizeGrid = (nextRows: number, nextCols: number) => setGrid((previous) => Array.from(
    { length: nextRows },
    (_, row) => Array.from({ length: nextCols }, (_, col) => previous[row]?.[col] || { kind: 'empty' as const }),
  ));

  useEffect(() => {
    const preferred = queryCinemaId || staffCinemaId || '';
    setCinemaId(preferred);
    void catalogApi.listCinemas({ sort: 'price', page: 1, size: 50 })
      .then((result) => {
        const items = staffCinemaId
          ? result.items.filter((cinema) => cinema.cinemaId === staffCinemaId)
          : result.items;
        setCinemas(items);
        if (staffCinemaId) setCinemaId(staffCinemaId);
      })
      .catch(() => setCinemas([]));
  }, [queryCinemaId, staffCinemaId]);

  useEffect(() => {
    if (isNew) {
      setLoading(false);
      return;
    }
    setLoading(true);
    void adminApi.getSeatMapTemplate(seatMapId!)
      .then((map) => {
        setRows(map.rows);
        setCols(map.cols);
        setScreenLabel(map.screenLabel || '银幕');
        setId(map.seatMapId);
        setCinemaId(map.cinemaId || '');
        setMutable(map.mutable !== false);
        const next = emptyGrid(map.rows, map.cols);
        for (const seat of map.seats || []) {
          const r = seat.graphRow - 1;
          const c = seat.graphCol - 1;
          if (r < 0 || c < 0 || r >= map.rows || c >= map.cols) continue;
          next[r][c] = {
            kind: 'seat',
            type: seat.type,
            zone: seat.zone,
            defaultStatus: seat.defaultStatus || 'available',
            couplePairId: seat.couplePairId,
          };
        }
        setGrid(next);
        const found = distinctZones(map.seats || []);
        setZonePresets(Array.from(new Set([...DEFAULT_ZONE_PRESETS, ...found])));
        if (found[0]) setZone(found[0]);
      })
      .catch(() => {
        message.error('座位图加载失败');
        history.replace('/admin/seat-maps');
      })
      .finally(() => setLoading(false));
  }, [isNew, seatMapId]);

  const generate = () => setGrid(emptyGrid(rows, cols));
  const seatCount = useMemo(() => grid.flat().filter((cell) => cell.kind === 'seat').length, [grid]);
  const usedZones = useMemo(() => distinctZones(grid.flat().filter((cell): cell is Extract<Cell, { kind: 'seat' }> => cell.kind === 'seat')), [grid]);

  const addCustomZone = () => {
    const code = customZone.trim();
    if (!code || code.length > 16) { message.warning('请输入不超过 16 个字符的分区 code'); return; }
    if (!zonePresets.includes(code)) setZonePresets((current) => [...current, code]);
    setZone(code); setCustomZone('');
  };

  const pairCoordinates = (cells: Cell[][], couplePairId: string | null) => {
    if (!couplePairId) return [] as Array<[number, number]>;
    const coordinates: Array<[number, number]> = [];
    cells.forEach((line, row) => line.forEach((candidate, col) => {
      if (candidate.kind === 'seat' && candidate.couplePairId === couplePairId) coordinates.push([row, col]);
    }));
    return coordinates;
  };

  const paint = (row: number, col: number) => {
    if (readOnly) return;
    setGrid((previous) => {
    const next = previous.map((cells) => cells.slice());
    const cell = next[row][col];
    if (tool === 'delete') {
      if (cell.kind === 'seat' && cell.type === 'couple') {
        pairCoordinates(next, cell.couplePairId).forEach(([pairRow, pairCol]) => { next[pairRow][pairCol] = { kind: 'empty' }; });
      } else {
        next[row][col] = { kind: 'empty' };
      }
      return next;
    }
    if (tool === 'zone' && cell.kind === 'seat') { next[row][col] = { ...cell, zone }; return next; }
    if (tool === 'type' && cell.kind === 'seat') {
      if (seatType === 'couple') {
        message.warning('情侣座必须通过“添加座位”一次添加一对，不能对刷子类型直接设置');
        return previous;
      }
      if (cell.type === 'couple') {
        pairCoordinates(next, cell.couplePairId).forEach(([pairRow, pairCol]) => {
          next[pairRow][pairCol] = { ...next[pairRow][pairCol], type: 'normal', couplePairId: null } as Extract<Cell, { kind: 'seat' }>;
        });
      } else {
        next[row][col] = { ...cell, type: 'normal', couplePairId: null };
      }
      return next;
    }
    if (tool !== 'add') return next;
    if (seatType === 'couple') {
      if (col + 1 >= cols || next[row][col + 1].kind !== 'empty') { message.warning('情侣座需要右侧相邻空位'); return previous; }
      const couplePairId = `cp_${row}_${col}_${Date.now().toString(36)}`;
      const pair = { kind: 'seat' as const, type: 'couple' as const, zone, defaultStatus: 'available' as const, couplePairId };
      next[row][col] = pair; next[row][col + 1] = { ...pair };
      return next;
    }
    next[row][col] = { kind: 'seat', type: seatType, zone, defaultStatus: 'available', couplePairId: null };
    return next;
  });
  };

  const toSeats = (): SeatVO[] => {
    const mapId = id.trim() || `sm_${Date.now().toString(36)}`;
    return grid.flatMap((line, rowIndex) => line.flatMap((cell, colIndex) => cell.kind === 'seat' ? [{
      seatId: `${mapId}:${rowIndex + 1}:${colIndex + 1}`, seatName: `${rowIndex + 1}排${colIndex + 1}座`, rowNo: rowIndex + 1, colNo: colIndex + 1,
      graphRow: rowIndex + 1, graphCol: colIndex + 1, type: cell.type, zone: cell.zone, defaultStatus: cell.defaultStatus, couplePairId: cell.couplePairId,
    }] : []));
  };

  const save = async () => {
    if (readOnly) { message.warning('该座位图已被场次引用，不可修改'); return; }
    const normalizedId = id.trim();
    if (normalizedId.length > 32) { message.warning('座位图 ID 不能超过 32 个字符'); return; }
    const seats = toSeats();
    if (!cinemaId) { message.warning('请选择所属影院'); return; }
    if (!seats.length) { message.warning('请至少添加一个座位'); return; }
    const pairCounts = new Map<string, number>();
    seats.filter((seat) => seat.type === 'couple').forEach((seat) => {
      if (seat.couplePairId) pairCounts.set(seat.couplePairId, (pairCounts.get(seat.couplePairId) || 0) + 1);
    });
    if (seats.some((seat) => seat.type === 'couple' && !seat.couplePairId) || [...pairCounts.values()].some((count) => count !== 2)) {
      message.error('情侣座必须以完整的一对保存，请检查座位图后重试');
      return;
    }
    try {
      if (isNew) {
        const created = await adminApi.createSeatMap({ seatMapId: normalizedId || undefined, cinemaId, rows, cols, screenLabel, seats });
        message.success('已创建座位图');
        history.replace('/admin/seat-maps');
      } else {
        await adminApi.updateSeatMap(seatMapId!, { rows, cols, screenLabel, seats });
        message.success('已保存');
        history.replace('/admin/seat-maps');
      }
    } catch {
      // 请求层已处理。
    }
  };

  if (loading) {
    return <div style={{ padding: 48, color: '#666' }}>加载座位图…</div>;
  }

  return <div>
    {readOnly ? (
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="该座位图已被场次引用，仅可查看"
        action={<Button size="small" onClick={() => history.push('/admin/seat-maps')}>返回列表</Button>}
      />
    ) : null}
    <Space wrap style={{ marginBottom: 16 }}>
      <Select placeholder="所属影院" style={{ width: 220 }} value={cinemaId || undefined} disabled={!isNew || !!queryCinemaId || !!staffCinemaId} options={cinemas.map((cinema) => ({ value: cinema.cinemaId, label: cinema.name }))} onChange={setCinemaId} />
      <span>行</span><InputNumber min={1} max={30} value={rows} disabled={readOnly} onChange={(value) => { const nextRows = value || 1; setRows(nextRows); resizeGrid(nextRows, cols); }} />
      <span>列</span><InputNumber min={1} max={40} value={cols} disabled={readOnly} onChange={(value) => { const nextCols = value || 1; setCols(nextCols); resizeGrid(rows, nextCols); }} />
      <Button onClick={generate} disabled={readOnly}>生成画布</Button>
      <Input style={{ width: 120 }} value={screenLabel} disabled={readOnly} onChange={(event) => setScreenLabel(event.target.value)} placeholder="银幕" />
      <Input style={{ width: 160 }} value={id} maxLength={32} disabled={!isNew} onChange={(event) => setId(event.target.value)} placeholder="座位图 ID（可选）" />
      {!readOnly ? <Button type="primary" onClick={save}>{isNew ? '创建座位图' : '保存修改'}</Button> : null}
      <Button onClick={() => history.push('/admin/seat-maps')}>返回列表</Button>
    </Space>
    <div className={styles.layout}><div className={styles.tools}>
      <h4>工具</h4>{(['add', 'delete', 'zone', 'type'] as const).map((item) => <Button key={item} block type={tool === item ? 'primary' : 'default'} style={{ marginBottom: 8 }} onClick={() => setTool(item)}>{({ add: '添加座位', delete: '删除', zone: '刷分区', type: '刷类型' })[item]}</Button>)}
      <h4>分区</h4><p className={styles.hint}>当前画笔：{zoneLabel(zone)}</p>{zonePresets.map((item) => <Button key={item} block type={zone === item ? 'primary' : 'default'} style={{ marginBottom: 6, borderColor: zoneColor(item), background: zone === item ? undefined : zoneColor(item), color: zone === item ? undefined : '#333' }} onClick={() => setZone(item)}>{zoneLabel(item)}</Button>)}
      <Space.Compact style={{ width: '100%', marginTop: 8 }}><Input placeholder="自定义分区 code" value={customZone} maxLength={16} onChange={(event) => setCustomZone(event.target.value)} onPressEnter={addCustomZone} /><Button onClick={addCustomZone}>添加</Button></Space.Compact>
      <h4 style={{ marginTop: 16 }}>类型</h4><Button block type={seatType === 'normal' ? 'primary' : 'default'} onClick={() => setSeatType('normal')}>普通座</Button><Button block type={seatType === 'couple' ? 'primary' : 'default'} style={{ marginTop: 8 }} onClick={() => setSeatType('couple')}>情侣座（一次添加一对）</Button>
      <p style={{ marginTop: 16, color: '#666' }}>座位数：{seatCount}</p>{usedZones.map((item) => <Tag key={item} color={zoneColor(item)}>{zoneLabel(item)}</Tag>)}
    </div><div className={styles.canvas}><div className={styles.screen}>{screenLabel}</div><div className={styles.grid} style={{ gridTemplateColumns: `repeat(${cols}, 28px)` }}>{grid.map((line, row) => line.map((cell, col) => <button key={`${row}-${col}`} type="button" title={cell.kind === 'seat' ? zoneLabel(cell.zone) : '空'} className={`${styles.cell} ${cell.kind === 'empty' ? styles.empty : styles.seat}`} style={cell.kind === 'seat' ? { background: zoneColor(cell.zone), borderStyle: 'solid' } : undefined} onClick={() => paint(row, col)}>{cell.kind === 'seat' && cell.type === 'couple' ? '♥' : cell.kind === 'seat' ? cell.zone : ''}</button>))}</div></div></div>
  </div>;
};

export default SeatMapEditorPage;
