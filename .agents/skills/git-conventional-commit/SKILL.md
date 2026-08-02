---
name: git-conventional-commit
description: 生成符合 Conventional Commits 规范的 Git 提交信息。每当用户要求提交代码、commit、git commit 时触发。自动分析暂存区的变更内容，生成结构化的提交信息（feat/fix/docs/style/refactor/test/chore），并在提交前展示给用户确认。也适用于用户提到"提交"、"推送"、"commit"、"写commit"等场景。
---

# Git Conventional Commit

基于 Conventional Commits 规范生成结构化的 Git 提交信息。分析 `git diff --staged` 的变更内容，自动判断提交类型并生成中文描述的提交信息。

## 提交类型

| 类型 | 说明 | 适用场景 |
|------|------|---------|
| `feat` | 新功能 | 新增页面、组件、功能模块 |
| `fix` | 修复 bug | 修复缺陷、异常、逻辑错误 |
| `docs` | 文档变更 | README、注释、接口文档 |
| `style` | 代码格式 | 空格、缩进、引号、格式调整（不影响功能） |
| `refactor` | 重构 | 代码结构调整、提取公共逻辑（不改变功能） |
| `test` | 添加测试 | 单元测试、集成测试 |
| `chore` | 构建/工具/配置 | 依赖更新、配置文件、构建脚本 |

## 提交信息格式

```
<type>(<scope>): <中文描述>

<详细说明（可选）>

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

- `type`: 必填，从上述 7 种中选择
- `scope`: 可选，模块或功能域（如 `layouts`、`pages`、`config`）
- 中文描述: 必填，简短说明做了什么
- 详细说明: 可选，记录做了哪些具体变更及原因
- 签名: 自动追加 Claude 签名

## 执行流程

### 步骤 1: 检查暂存区

```bash
git diff --staged --stat
git diff --staged --name-only
```

如果没有暂存的变更，询问用户是否有文件需要 add，并列出当前有变更但未暂存的文件：

```bash
git status --short
```

### 步骤 2: 分析变更内容

根据文件的路径和 diff 内容判断：

- `src/pages/` 下的变更 → 通常是 `feat`（新页面）或 `fix`（修复页面）
- `src/layouts/`、`src/components/` → `feat` 或 `refactor`
- `.umirc.ts`、`package.json`、`pnpm-workspace.yaml` → `chore`
- `*.less`、`*.css` → `style`
- `*.md` → `docs`
- `test/`、`__tests__/` → `test`

对于 diff 内容：
- 新增组件/页面/Hook → `feat`
- 修复错误/异常处理 → `fix`
- 添加校验/错误提示 → `fix`
- 删除未使用的代码/注释 → `chore` 或 `refactor`
- 代码组织调整 → `refactor`

### 步骤 3: 生成提交信息

根据变更分析，生成一条或多条提交信息候选，展示给用户确认。

**scope 参考：**
- 通用: `layouts`、`pages`、`components`、`utils`、`hooks`、`styles`
- 业务域: `员工`、`组织`、`考勤`、`薪资`、`审批`、`设置`
- 配置: `config`、`build`、`deps`

### 步骤 4: 用户确认后执行

用户确认或修改后，执行：

```bash
git commit -m "确认的提交信息"
```

**规则：**
- 不要用 `git commit -a`（避免提交未跟踪的文件）
- 如果还有未暂存的变更，提醒用户是否需要一并暂存
- commit 完成后，询问用户是否需要 `git push`
- 如果用户要求 push，执行 `git push`
- 推送前向用户确认当前分支名和目标分支

## 示例

**示例 1: 新增页面**
```
变更: src/pages/employee/list/index.tsx (新增)
提交: feat(员工): 新增员工列表页面
```

**示例 2: 修复 bug**
```
变更: src/layouts/index.tsx (修改侧栏折叠逻辑)
提交: fix(layouts): 修复侧栏菜单折叠状态异常
```

**示例 3: 配置变更**
```
变更: .umirc.ts (修改路由配置)、package.json (新增依赖)
提交: chore(config): 添加路由配置并安装 antd 图标库
```

**示例 4: 重构**
```
变更: src/utils/request.ts (提取公共请求逻辑)
提交: refactor(utils): 提取 axios 公共请求拦截器
```

**示例 5: 样式调整**
```
变更: src/layouts/index.less (修改侧栏样式)
提交: style(layouts): 调整侧栏菜单高亮光条颜色
```

## 注意事项

- 一次 commit 聚焦一个主题，避免大杂烩
- 如果本次变更涉及多个独立主题，建议分批提交，并向用户说明
- 描述用中文，简洁明了，不要照搬文件路径
- scope 用中文业务名更直观，用英文技术名更规范——根据项目惯例选择
