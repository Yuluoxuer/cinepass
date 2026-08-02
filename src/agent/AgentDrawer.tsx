import React, { useEffect, useRef } from 'react';
import { history } from 'umi';
import { useAgentStore } from '@/stores/agent';
import { useBookingStore } from '@/stores/booking';
import { completedStepFlags, progressFromDraft } from '@/utils/bookingProgress';
import CardRenderer from './CardRenderer';
import styles from './AgentDrawer.less';

const CHIPS = ['明天下午', '两张', '坐中间', '周末看喜剧'];

const AgentDrawer: React.FC = () => {
  const open = useAgentStore((s) => s.open);
  const messages = useAgentStore((s) => s.messages);
  const progress = useAgentStore((s) => s.progress);
  const sending = useAgentStore((s) => s.sending);
  const closeDrawer = useAgentStore((s) => s.closeDrawer);
  const sendMessage = useAgentStore((s) => s.sendMessage);
  const clickCardAction = useAgentStore((s) => s.clickCardAction);
  const draft = useBookingStore((s) => s.draft);
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

  const derived = progressFromDraft(draft);
  const steps = progress?.steps?.length ? progress.steps : derived.steps;
  const stepIndex = progress?.currentIndex ?? derived.currentIndex;
  const doneFlags = completedStepFlags(
    draft || {
      movieId: undefined,
      cinemaId: undefined,
      showId: undefined,
      lockId: undefined,
      orderId: undefined,
      state: 'Idle',
    },
  );

  const draftTitle = draft?.filmTitle
    ? draft.cinemaId
      ? `《${draft.filmTitle}》· 草稿已同步`
      : `已选《${draft.filmTitle}》· 下一步选影院`
    : steps[stepIndex]
      ? `当前步骤 · ${steps[stepIndex]}`
      : '正在读取购票草稿';
  const draftMeta = draft?.movieId
    ? '已完备步骤不会因打开助手而回退'
    : '偏好会与手动页面实时同步';

  return (
    <>
      <div className={styles.backdrop} onClick={closeDrawer} aria-hidden />
      <aside className={`${styles.drawer} ${styles.open}`} aria-label="妙语助手">
        <header className={styles.head}>
          <div>
            <span className={styles.eyebrow}>✦ MIU AGENT</span>
            <h2>妙语助手</h2>
          </div>
          <div className={styles.headActions}>
            <button
              type="button"
              className={styles.manualBtn}
              onClick={() => {
                closeDrawer();
                if (draft?.movieId && draft?.cinemaId) {
                  history.push(
                    `/booking/shows?movieId=${draft.movieId}&cinemaId=${draft.cinemaId}`,
                  );
                } else if (draft?.movieId) {
                  history.push(`/booking/cinemas?movieId=${draft.movieId}`);
                } else {
                  history.push('/booking/cinemas');
                }
              }}
            >
              转手动
            </button>
            <button type="button" className={styles.close} onClick={closeDrawer} aria-label="关闭">
              ×
            </button>
          </div>
        </header>

        <div className={styles.agentProgress} aria-label="购票进度">
          {steps.map((s, i) => {
            const fieldDone = doneFlags[i] || i < stepIndex;
            const current = i === stepIndex && stepIndex < 5;
            const done = fieldDone && !current;
            return (
              <div
                key={s}
                className={`${styles.progressStep} ${done ? styles.done : ''} ${current ? styles.current : ''}`}
              >
                <i>{String(i + 1).padStart(2, '0')}</i>
                <span>{s}</span>
              </div>
            );
          })}
        </div>

        <div className={styles.draftGlance}>
          <span className={styles.syncDot} />
          <div>
            <b>{draftTitle}</b>
            <small>{draftMeta}</small>
          </div>
        </div>

        <div className={styles.chat}>
          {messages.map((m) => (
            <div key={m.id} className={`${styles.bubble} ${styles[m.role]}`}>
              <div className={m.loading ? styles.loading : undefined}>{m.text}</div>
              {m.cards?.map((c) => (
                <CardRenderer
                  key={c.cardId}
                  card={c}
                  onAction={(actionId, itemId, draftPatch) => {
                    if (actionId === 'go_pay' && itemId) {
                      void (async () => {
                        const result = await clickCardAction({ cardId: c.cardId, actionId, itemId, draftPatch });
                        if (!result) return;
                        closeDrawer();
                        history.push(`/booking/pay?orderId=${itemId}`);
                      })();
                      return;
                    }
                    if (actionId === 'manual') {
                      void (async () => {
                        const result = await clickCardAction({ cardId: c.cardId, actionId, itemId, draftPatch });
                        if (!result) return;
                        const latest = result?.draft || useBookingStore.getState().draft;
                        closeDrawer();
                        if (latest?.showId) {
                          history.push(`/booking/seats?showId=${latest.showId}`);
                        } else if (latest?.movieId && latest.cinemaId) {
                          history.push(`/booking/shows?movieId=${latest.movieId}&cinemaId=${latest.cinemaId}`);
                        } else if (latest?.movieId) {
                          history.push(`/booking/cinemas?movieId=${latest.movieId}`);
                        } else {
                          history.push('/');
                        }
                      })();
                      return;
                    }
                    if (actionId === 'view_order' && itemId) {
                      void (async () => {
                        const result = await clickCardAction({ cardId: c.cardId, actionId, itemId, draftPatch });
                        if (!result) return;
                        closeDrawer();
                        history.push(`/me/orders/${itemId}`);
                      })();
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

        <footer className={styles.compose}>
          <div className={styles.quickPrompts}>
            {CHIPS.map((c) => (
              <button key={c} type="button" onClick={() => sendMessage(c)} disabled={sending}>
                {c}
              </button>
            ))}
          </div>
          <form
            className={styles.form}
            onSubmit={(e) => {
              e.preventDefault();
              onSend();
            }}
          >
            <input
              ref={inputRef}
              value={text}
              onChange={(e) => setText(e.target.value)}
              aria-label="给妙语助手发消息"
              placeholder="说说你想看什么…"
              autoComplete="off"
              disabled={sending}
            />
            <button type="submit" aria-label="发送" disabled={sending}>
              ↗
            </button>
          </form>
          <small>Agent 可以选片、选影院与选座；支付必须由你完成</small>
        </footer>
      </aside>
    </>
  );
};

export default AgentDrawer;
