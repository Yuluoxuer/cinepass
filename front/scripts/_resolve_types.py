from pathlib import Path
p = Path("src/types/index.ts")
t = p.read_text()
old = """export interface LoginResult {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  nickname: string;
  phone: string | null;
  role: UserRole;
  cinemaId?: string | null;
}"""
new = """export interface LoginResult {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  nickname: string;
  phone: string | null;
  role: UserRole;
  /** staff 所属影院；user/admin 为 null */
  cinemaId?: string | null;
}"""
if old not in t:
    raise SystemExit("LoginResult block not found")
p.write_text(t.replace(old, new, 1))
print("types ok")
