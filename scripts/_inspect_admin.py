from pathlib import Path
t = Path("src/api/admin.ts").read_text()
i = t.find("getDashboardStats")
print(repr(t[i:i+180]))
print("---imports---")
print(repr(t[:120]))
