# Notepad
<!-- Auto-managed by OMC. Manual edits preserved in MANUAL section. -->

## Priority Context
<!-- ALWAYS loaded. Keep under 500 chars. Critical discoveries only. -->
## Summary for orchestrator
- Changed: `src/app.tsx` — removed `message.error` from installApiRejectionGuard; guard now only preventDefault + rAF dismissDevOverlay (clearRuntimeErrors(true)). Early IIFE kept.
- Unchanged: `src/api/client.ts` already correct — request() wraps requestOnce, UNAUTHORIZED/401 → pendingAuthRedirect forever-pending Promise; requestOnce catch rethrows ApiError.
- tsc: Shell tool broken in this session (no exit status / spawn aborted); could not run `pnpm exec tsc --noEmit`. Change is deletion-only; ReadLints clean on the two files. Parent should re-run tsc.

## Working Memory
<!-- Session notes. Auto-pruned after 7 days. -->
### 2026-08-03 08:11
ApiError fix: app.tsx guard no longer toasts; client.ts already had pendingAuthRedirect + ApiError rethrow. Shell/tsc unavailable in session.


## MANUAL
<!-- User content. Never auto-pruned. -->

