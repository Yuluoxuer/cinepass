from pathlib import Path
admin = Path("src/api/admin.ts")
text = admin.read_text()
text = text.replace(
    "import { get, post, put, del } from ./client;",
    "import { get, post, put, del, type RequestOptions } from ./client;",
    1,
)
old = "export function getDashboardStats(date: string) {\n  return get<AdminDashboardStatsVO>(/admin/dashboard/stats, { date });\n}"
new = "export function getDashboardStats(date: string, opts?: RequestOptions) {\n  return get<AdminDashboardStatsVO>(/admin/dashboard/stats, { date }, opts);\n}"
if old not in text:
    raise SystemExit("not found: " + repr(old))
admin.write_text(text.replace(old, new, 1))
print("admin patched")
