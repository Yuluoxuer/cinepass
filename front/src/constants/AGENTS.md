<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# constants

## Purpose
Placeholder directory for application-wide constant values (enums, static maps, magic strings). Currently empty — add constants here as the project grows to avoid scattering magic values across page and service files.

## For AI Agents

### Working In This Directory
- Name files by domain, e.g. `status.ts`, `pagination.ts`, `routes.ts`
- Export as named constants (`export const PAGE_SIZE = 20`); avoid default exports
- Do not import from `@/stores` or `@/api` here — constants must be side-effect-free

<!-- MANUAL: -->
