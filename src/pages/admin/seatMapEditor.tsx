import React, { useEffect, useMemo, useState } from 'react';
import { Button, Input, InputNumber, Space, message } from 'antd';
import { history, useParams } from 'umi';
import * as adminApi from '@/api/admin';
import type { SeatVO, SeatType, SeatZone } from '@/types';
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
  const [zone, setZone] = useState<SeatZone>('normal');
  const [seatType, setSeatType] = useState<SeatType>('normal');

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
    });
  }, [seatMapId, isNew]);

  const seatCount = useMemo(
    () => grid.flat().filter((c) => c.kind === 'seat').length,
    [grid],
  );

  const generate = () => {
    setGrid(emptyGrid(rows, cols));
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
    const body = {
      seatMapId: id || undefined,
      rows,
      cols,
      screenLabel,
      seats,
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
          <h4>分区</h4>
          <Button block type={zone === 'normal' ? 'primary' : 'default'} onClick={() => setZone('normal')}>
            普通
          </Button>
          <Button block type={zone === 'golden' ? 'primary' : 'default'} style={{ marginTop: 8 }} onClick={() => setZone('golden')}>
            黄金
          </Button>
          <h4 style={{ marginTop: 16 }}>类型</h4>
          <Button block type={seatType === 'normal' ? 'primary' : 'default'} onClick={() => setSeatType('normal')}>
            普通座
          </Button>
          <Button block type={seatType === 'couple' ? 'primary' : 'default'} style={{ marginTop: 8 }} onClick={() => setSeatType('couple')}>
            情侣座
          </Button>
          <p style={{ marginTop: 16, color: '#666' }}>座位数：{seatCount}</p>
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
                  className={`${styles.cell} ${
                    cell.kind === 'empty'
                      ? styles.empty
                      : cell.zone === 'golden'
                        ? styles.golden
                        : cell.type === 'couple'
                          ? styles.couple
                          : styles.seat
                  }`}
                  onClick={() => paint(ri, ci)}
                >
                  {cell.kind === 'seat' && cell.type === 'couple' ? '♥' : ''}
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
