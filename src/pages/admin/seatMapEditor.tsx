import React, { useEffect, useMemo, useState } from 'react';
import { Button, Input, InputNumber, Space, Tag, message } from 'antd';
import { history, useParams } from 'umi';
import * as adminApi from '@/api/admin';
import type { SeatVO, SeatType, SeatZone } from '@/types';
import { DEFAULT_ZONE_PRESETS, distinctZones, zoneColor, zoneLabel } from '@/utils/zone';
import styles from './seatMapEditor.less';

type Cell =
  | { kind: 'empty' }
  | {
      kind: 'seat';
      type: SeatType;
      zone: SeatZone;
      defaultStatus: 'available' | 'unavailable';
      couplePairId: string | null;
      rowNo?: number;
      colNo?: number;
      seatName?: string;
      seatId?: string;
    };

const SeatMapEditorPage: React.FC = () => {
  const { seatMapId } = useParams<{ seatMapId: string }>();
  const isNew = !seatMapId || seatMapId === 'new';
  const [rows, setRows] = useState(8);
  const [cols, setCols] = useState(12);
  const [screenLabel, setScreenLabel] = useState('银幕');
  const [id, setId] = useState(seatMapId || '');
  const [grid, setGrid] = useState<Cell[][]>([]);
  const [tool, setTool] = useState<'add' | 'delete' | 'zone' | 'type'>('add');
  const [zone, setZone] = useState<SeatZone>('A');
  const [customZone, setCustomZone] = useState('');
  const [seatType, setSeatType] = useState<SeatType>('normal');
  const [zonePresets, setZonePresets] = useState<string[]>([...DEFAULT_ZONE_PRESETS]);

  const emptyGrid = (r: number, c: number): Cell[][] =>
    Array.from({ length: r }, () => Array.from({ length: c }, () => ({ kind: 'empty' as const })));

  useEffect(() => {
    if (isNew) {
      setGrid(emptyGrid(rows, cols));
      return;
    }
    void adminApi.getSeatMapTemplate(seatMapId!).then((map) => {
      setRows(map.rows);
      setCols(map.cols);
      setScreenLabel(map.screenLabel);
      setId(map.seatMapId);
      const g = emptyGrid(map.rows, map.cols);
      for (const s of map.seats) {
        g[s.graphRow - 1][s.graphCol - 1] = {
          kind: 'seat',
          type: s.type,
          zone: s.zone,
          defaultStatus: s.defaultStatus || 'available',
          couplePairId: s.couplePairId,
          rowNo: s.rowNo,
          colNo: s.colNo,
          seatName: s.seatName,
          seatId: s.seatId,
        };
      }
      setGrid(g);
      const found = distinctZones(map.seats);
      setZonePresets(Array.from(new Set([...DEFAULT_ZONE_PRESETS, ...found])));
      if (found[0]) setZone(found[0]);
    });
  }, [seatMapId, isNew]);

  const seatCount = useMemo(
    () => grid.flat().filter((c) => c.kind === 'seat').length,
    [grid],
  );

  const usedZones = useMemo(
    () =>
      distinctZones(
        grid.flat().filter((c): c is Extract<Cell, { kind: 'seat' }> => c.kind === 'seat'),
      ),
    [grid],
  );

  const generate = () => {
    setGrid(emptyGrid(rows, cols));
  };

  const addCustomZone = () => {
    const code = customZone.trim();
    if (!code) {
      message.warning('请输入分区 code');
      return;
    }
    if (code.length > 16) {
      message.warning('分区 code 最长 16 字符');
      return;
    }
    if (!zonePresets.includes(code)) {
      setZonePresets((prev) => [...prev, code]);
    }
    setZone(code);
    setCustomZone('');
    message.success(`已选用 ${zoneLabel(code)}`);
  };

  const paint = (ri: number, ci: number) => {
    setGrid((prev) => {
      const next = prev.map((row) => row.slice());
      const cell = next[ri][ci];
      if (tool === 'add') {
        next[ri][ci] = {
          kind: 'seat',
          type: seatType,
          zone,
          defaultStatus: 'available',
          couplePairId: seatType === 'couple' ? `cp_${ri}_${ci}` : null,
        };
      } else if (tool === 'delete') {
        next[ri][ci] = { kind: 'empty' };
      } else if (tool === 'zone' && cell.kind === 'seat') {
        next[ri][ci] = { ...cell, zone };
      } else if (tool === 'type' && cell.kind === 'seat') {
        next[ri][ci] = {
          ...cell,
          type: seatType,
          couplePairId: seatType === 'couple' ? cell.couplePairId || `cp_${ri}_${ci}` : null,
        };
      }
      return next;
    });
  };

  const toSeats = (): SeatVO[] => {
    const seats: SeatVO[] = [];
    const mapId = id || `sm_${Date.now().toString(36)}`;
    for (let r = 0; r < grid.length; r++) {
      let colNo = 0;
      for (let c = 0; c < grid[r].length; c++) {
        const cell = grid[r][c];
        if (cell.kind !== 'seat') continue;
        colNo += 1;
        const rowNo = r + 1;
        seats.push({
          seatId: cell.seatId || `${mapId}:${rowNo}:${c + 1}`,
          seatName: cell.seatName || `${rowNo}排${colNo}座`,
          rowNo,
          colNo,
          graphRow: r + 1,
          graphCol: c + 1,
          type: cell.type,
          zone: cell.zone,
          defaultStatus: cell.defaultStatus,
          couplePairId: cell.couplePairId,
        });
      }
    }
    return seats;
  };

  const save = async () => {
    const seats = toSeats();
    if (seats.length === 0) {
      message.warning('请至少添加一个座位');
      return;
    }
    const body = {
      seatMapId: id || undefined,
      rows,
      cols,
      screenLabel,
      seats,
      zones: distinctZones(seats),
      mutable: true,
    };
    if (isNew || !id) {
      const created = await adminApi.createSeatMap(body);
      message.success('已创建');
      history.replace(`/admin/seat-maps/${created.seatMapId}`);
    } else {
      await adminApi.updateSeatMap(id, body);
      message.success('已保存');
    }
  };

  return (
    <div>
      <Space wrap style={{ marginBottom: 16 }}>
        <span>行</span>
        <InputNumber min={1} max={30} value={rows} onChange={(v) => setRows(v || 1)} />
        <span>列</span>
        <InputNumber min={1} max={40} value={cols} onChange={(v) => setCols(v || 1)} />
        <Button onClick={generate}>生成画布</Button>
        <Input
          style={{ width: 120 }}
          value={screenLabel}
          onChange={(e) => setScreenLabel(e.target.value)}
          placeholder="银幕"
        />
        <Input
          style={{ width: 160 }}
          value={id}
          onChange={(e) => setId(e.target.value)}
          placeholder="座位图 ID"
          disabled={!isNew && !!seatMapId}
        />
        <Button type="primary" onClick={save}>
          保存
        </Button>
      </Space>
      <div className={styles.layout}>
        <div className={styles.tools}>
          <h4>工具</h4>
          {(['add', 'delete', 'zone', 'type'] as const).map((t) => (
            <Button
              key={t}
              block
              type={tool === t ? 'primary' : 'default'}
              style={{ marginBottom: 8 }}
              onClick={() => setTool(t)}
            >
              {t === 'add' ? '添加座位' : t === 'delete' ? '删除' : t === 'zone' ? '刷区' : '刷类型'}
            </Button>
          ))}
          <h4>分区（自定义 code）</h4>
          <p className={styles.hint}>当前画笔：{zoneLabel(zone)}</p>
          <div className={styles.zoneList}>
            {zonePresets.map((z) => (
              <Button
                key={z}
                block
                type={zone === z ? 'primary' : 'default'}
                style={{
                  marginBottom: 6,
                  borderColor: zoneColor(z),
                  background: zone === z ? undefined : zoneColor(z),
                  color: zone === z ? undefined : '#333',
                }}
                onClick={() => setZone(z)}
              >
                {zoneLabel(z)}
              </Button>
            ))}
          </div>
          <Space.Compact style={{ width: '100%', marginTop: 8 }}>
            <Input
              placeholder="自定义区 code"
              value={customZone}
              maxLength={16}
              onChange={(e) => setCustomZone(e.target.value)}
              onPressEnter={addCustomZone}
            />
            <Button onClick={addCustomZone}>添加</Button>
          </Space.Compact>
          <h4 style={{ marginTop: 16 }}>类型</h4>
          <Button block type={seatType === 'normal' ? 'primary' : 'default'} onClick={() => setSeatType('normal')}>
            普通座
          </Button>
          <Button
            block
            type={seatType === 'couple' ? 'primary' : 'default'}
            style={{ marginTop: 8 }}
            onClick={() => setSeatType('couple')}
          >
            情侣座
          </Button>
          <p style={{ marginTop: 16, color: '#666' }}>座位数：{seatCount}</p>
          <div className={styles.usedZones}>
            <span style={{ color: '#666', fontSize: 12 }}>本图已用区：</span>
            {usedZones.length === 0 ? (
              <span style={{ color: '#999', fontSize: 12 }}>无</span>
            ) : (
              usedZones.map((z) => (
                <Tag key={z} color={zoneColor(z)} style={{ marginTop: 4 }}>
                  {zoneLabel(z)}
                </Tag>
              ))
            )}
          </div>
        </div>
        <div className={styles.canvas}>
          <div className={styles.screen}>{screenLabel}</div>
          <div
            className={styles.grid}
            style={{
              gridTemplateColumns: `repeat(${cols}, 28px)`,
            }}
          >
            {grid.map((row, ri) =>
              row.map((cell, ci) => (
                <button
                  key={`${ri}-${ci}`}
                  type="button"
                  title={cell.kind === 'seat' ? zoneLabel(cell.zone) : '空'}
                  className={`${styles.cell} ${cell.kind === 'empty' ? styles.empty : styles.seat}`}
                  style={
                    cell.kind === 'seat'
                      ? {
                          background: zoneColor(cell.zone),
                          borderStyle: 'solid',
                          opacity: cell.type === 'couple' ? 0.95 : 1,
                        }
                      : undefined
                  }
                  onClick={() => paint(ri, ci)}
                >
                  {cell.kind === 'seat' && cell.type === 'couple' ? '♥' : cell.kind === 'seat' ? cell.zone : ''}
                </button>
              )),
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default SeatMapEditorPage;
