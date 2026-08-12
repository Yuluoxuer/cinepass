import React, { useState } from 'react';
import { Button, Input, Result, Descriptions } from 'antd';
import * as adminApi from '@/api/admin';
import type { TicketVerifyVO } from '@/types';
import { formatDateTime } from '@/utils/format';

const TicketVerifyPage: React.FC = () => {
  const [payload, setPayload] = useState('');
  const [result, setResult] = useState<TicketVerifyVO | null>(null);

  const verify = async () => {
    try {
      const res = await adminApi.verifyTicket(payload.trim());
      setResult(res);
    } catch {
      setResult(null);
    }
  };

  return (
    <div>
      <h2>取票核验</h2>
      <Input.TextArea
        rows={3}
        value={payload}
        onChange={(e) => setPayload(e.target.value)}
        placeholder="粘贴 qrPayload"
        style={{ maxWidth: 520, marginTop: 12 }}
      />
      <div style={{ marginTop: 12 }}>
        <Button type="primary" onClick={verify}>
          核验
        </Button>
      </div>
      {result ? (
        result.valid ? (
          <Descriptions title="核验通过" bordered style={{ marginTop: 24 }} column={1}>
            <Descriptions.Item label="订单">{result.orderId}</Descriptions.Item>
            <Descriptions.Item label="取票码">{result.ticketCode}</Descriptions.Item>
            <Descriptions.Item label="影片">{result.movieTitle}</Descriptions.Item>
            <Descriptions.Item label="影院">{result.cinemaName}</Descriptions.Item>
            <Descriptions.Item label="影厅">{result.hallName}</Descriptions.Item>
            <Descriptions.Item label="场次">{formatDateTime(result.startTime)}</Descriptions.Item>
            <Descriptions.Item label="座位">{result.seatIds?.join('、')}</Descriptions.Item>
          </Descriptions>
        ) : (
          <Result status="error" title="核验失败" subTitle={result.reason || 'TICKET_INVALID'} />
        )
      ) : null}
    </div>
  );
};

export default TicketVerifyPage;
