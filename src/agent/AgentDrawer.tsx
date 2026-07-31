import React, { useEffect, useRef } from 'react';
import { history } from 'umi';
import { useAgentStore } from '@/stores/agent';
import CardRenderer from './CardRenderer';
import styles from './AgentDrawer.less';

const CHIPS = ['周末看喜剧', '两张票', '黄金区', '带对象看IMAX'];

const AgentDrawer: React.FC = () => {
  const open = useAgentStore((s) => s.open);
  const messages = useAgentStore((s) => s.messages);
  const progress = useAgentStore((s) => s.progress);
  const sending = useAgentStore((s) => s.sending);
  const closeDrawer = useAgentStore((s) => s.closeDrawer);
  const sendMessage = useAgentStore((s) => s.sendMessage);
  const clickCardAction = useAgentStore((s) => s.clickCardAction);
  const inputRef = useRef<HTMLInputElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const [text, setText] = React.useState('');

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, open]);

  if (!open) return null;

  const onSend = () => {
    const v = text.trim();
    if (!v || sending) return;
    setText('');
    void sendMessage(v);
  };

  const stepIndex = progress?.currentIndex ?? 0;

  return (
    <div className={styles.mask}>
      <aside className={styles.drawer}>
        <header className={styles.head}>
          <strong>妙语助手</strong>
          <div className={styles.progress}>
            {(progress?.steps || ['选片', '影院', '场次', '选座', '支付']).map((s, i) => (
              <span key={s} className={i <= stepIndex ? styles.dotOn : styles.dot} title={s} />
            ))}
          </div>
          <button type="button" className={styles.manualBtn} onClick={() => { closeDrawer(); history.push('/booking/cinemas'); }}>
            转手动
          </button>
          <button type="button" className={styles.close} onClick={closeDrawer}>
            ×
          </button>
        </header>
        <div className={styles.timeline}>
          {messages.map((m) => (
            <div key={m.id} className={`${styles.bubble} ${styles[m.role]}`}>
              <div className={m.loading ? styles.loading : undefined}>{m.text}</div>
              {m.cards?.map((c) => (
                <CardRenderer
                  key={c.cardId}
                  card={c}
                  onAction={(actionId, itemId, draftPatch) => {
                    if (actionId === 'go_pay' && itemId) {
                      closeDrawer();
                      history.push(`/booking/pay?orderId=${itemId}`);
                      return;
                    }
                    if (actionId === 'manual') {
                      closeDrawer();
                      history.push('/booking/seats');
                      return;
                    }
                    if (actionId === 'fill_slot') {
                      void sendMessage(itemId || c.actions.find((a) => a.actionId === actionId)?.label || '');
                      return;
                    }
                    if (actionId === 'view_order' && itemId) {
                      closeDrawer();
                      history.push(`/me/orders/${itemId}`);
                      return;
                    }
                    void clickCardAction({
                      cardId: c.cardId,
                      actionId,
                      itemId,
                      draftPatch,
                    });
                  }}
                />
              ))}
            </div>
          ))}
          <div ref={bottomRef} />
        </div>
        <div className={styles.chips}>
          {CHIPS.map((c) => (
            <button key={c} type="button" onClick={() => sendMessage(c)} disabled={sending}>
              {c}
            </button>
          ))}
        </div>
        <div className={styles.inputBar}>
          <input
            ref={inputRef}
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="输入消息…"
            onKeyDown={(e) => e.key === 'Enter' && onSend()}
          />
          <button type="button" className="miaoyu-btn-primary" onClick={onSend} disabled={sending}>
            发送
          </button>
        </div>
      </aside>
    </div>
  );
};

export default AgentDrawer;
