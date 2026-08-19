#!/usr/bin/env python3
"""Dump conflict hunks with surrounding context for review."""
from pathlib import Path
import sys

files = sys.argv[1:] or [
  "src/components/LoginModal/index.tsx",
  "src/mock/router.ts",
  "src/pages/admin/cinemaForm.tsx",
  "src/pages/admin/cinemas.tsx",
  "src/pages/admin/dashboard.tsx",
  "src/pages/admin/halls.tsx",
  "src/pages/admin/movieForm.tsx",
  "src/pages/admin/movies.tsx",
  "src/pages/admin/orders.tsx",
  "src/pages/admin/seatMapEditor.tsx",
  "src/pages/admin/seatMaps.tsx",
  "src/pages/admin/shows.tsx",
  "src/pages/admin/ticketVerify.tsx",
  "src/pages/admin/users.tsx",
  "src/pages/booking/cinemas.tsx",
  "src/pages/booking/shows.tsx",
  "src/pages/cinemas/index.tsx",
]

out = Path("scripts/_conflict_dump.txt")
parts = []
for f in files:
  text = Path(f).read_text()
  parts.append("=" * 80)
  parts.append(f)
  parts.append("=" * 80)
  lines = text.splitlines()
  i = 0
  hunk = 0
  while i < len(lines):
    if lines[i].startswith("<<<<<<<"):
      hunk += 1
      start = max(0, i - 3)
      j = i
      while j < len(lines) and not lines[j].startswith(">>>>>>>"):
        j += 1
      end = min(len(lines), j + 4)
      parts.append(f"--- hunk {hunk} lines {i+1}-{j+1} ---")
      parts.extend(f"{n+1}|{lines[n]}" for n in range(start, end))
      parts.append("")
      i = j + 1
    else:
      i += 1
out.write_text("\n".join(parts))
print(f"wrote {out} ({out.stat().st_size} bytes)")
