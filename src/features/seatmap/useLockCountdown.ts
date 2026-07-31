import { useEffect, useState } from 'react';

export function useLockCountdown(expireAt?: string | null) {
  const [remainMs, setRemainMs] = useState(0);

  useEffect(() => {
    if (!expireAt) {
      setRemainMs(0);
      return;
    }
    const tick = () => {
      setRemainMs(Math.max(0, new Date(expireAt).getTime() - Date.now()));
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [expireAt]);

  const totalSec = Math.floor(remainMs / 1000);
  const mm = String(Math.floor(totalSec / 60)).padStart(2, '0');
  const ss = String(totalSec % 60).padStart(2, '0');
  const warning = totalSec > 0 && totalSec < 180;
  const expired = !!expireAt && remainMs <= 0;

  return { remainMs, text: `${mm}:${ss}`, warning, expired };
}
