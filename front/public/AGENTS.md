<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# public

## Purpose
Static assets served directly at the web root. Files placed here are accessible via root-relative URLs without being processed by Umi's bundler. Suitable for images, favicons, and other binary assets.

## Key Files

| File | Description |
|------|-------------|
| `banner.png` | Banner image asset used in the application UI |

## For AI Agents

### Working In This Directory
- Files here are served as-is — do not place TypeScript or source files here
- Reference assets via root-relative paths, e.g. `/banner.png`
- Avoid placing large binaries here; prefer CDN links for heavy media

<!-- MANUAL: -->
