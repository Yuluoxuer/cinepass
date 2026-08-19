# 搜索接口文档

Base URL: `/api/v1`

---

## 1. 搜索影片

```
GET /movies
```

### 入参 (Query Params)

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `q` | string | 否 | - | 搜索关键词，走 ES 全文检索，匹配字段见下方 ES 索引说明 |
| `status` | string | 否 | - | 影片状态筛选。可选值：`hot_showing`（热映）、`coming_soon`（即将上映）、`off`（下架）。不传则返回全部状态。ES 内作为 filter 条件，不参与相关性算分 |
| `page` | int | 否 | 1 | 页码，从 1 开始。ES 内转为 `from`/`size` |
| `size` | int | 否 | 10 | 每页条数 |

### ES 索引 — movie

```
索引名: movie
```

| 字段 | 类型 | 分词器 | 说明 |
|---|---|---|---|
| `title` | text | ik_smart | 片名，搜索主字段，权重最高 |
| `description` | text | ik_smart | 简介，权重低于 title |
| `cast` | text | ik_smart | 演职人员，支持按演员/导演搜 |
| `director` | text | ik_smart | 导演 |
| `genres` | keyword | - | 类型标签，精确匹配 |
| `status` | keyword | - | 状态，filter 过滤 |
| `rating` | float | - | 评分 |
| `durationMin` | integer | - | 时长 |
| `releaseDate` | date | - | 上映日期 |
| `wantSeeCount` | integer | - | 想看人数 |

### ES 查询逻辑

```json
// q 不为空时
{
  "query": {
    "bool": {
      "must": [{
        "multi_match": {
          "query": "{{q}}",
          "fields": ["title^4", "cast^2", "director^2", "description"]
        }
      }],
      "filter": [
        // status 不为空时: { "term": { "status": "{{status}}" } }
      ]
    }
  },
  "from": "(page - 1) * size",
  "size": "size"
}

// q 为空时：match_all + filter(status)，按 releaseDate 降序 + rating 降序
```

### 出参

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "items": [
      {
        "movieId": "string",
        "title": "string",
        "posterUrl": "string",
        "genres": ["动作", "科幻"],
        "rating": 8.5,
        "durationMin": 120,
        "releaseDate": "2026-01-15",
        "status": "hot_showing",
        "description": "影片简介...",
        "cast": "主演A, 主演B",
        "director": "导演姓名",
        "castMembers": [
          {
            "name": "主演A",
            "role": "角色名",
            "avatarUrl": null
          }
        ],
        "wantSeeCount": 1234,
        "nextShowDate": "2026-08-06"
      }
    ],
    "page": 1,
    "size": 10,
    "total": 56
  }
}
```

### data 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `items` | MovieVO[] | 影片列表 |
| `page` | int | 当前页码 |
| `size` | int | 每页条数 |
| `total` | int | 总条数 |

### MovieVO 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `movieId` | string | 影片 ID |
| `title` | string | 片名 |
| `posterUrl` | string | 海报图 URL |
| `genres` | string[] | 类型标签（如 动作、科幻） |
| `rating` | number \| null | 豆瓣式评分 (0-10)，null 表示暂无 |
| `durationMin` | int | 时长（分钟） |
| `releaseDate` | string | 上映日期 `yyyy-MM-dd` |
| `status` | string | `hot_showing`（热映）/ `coming_soon`（即将上映）/ `off`（下架） |
| `description` | string | 简介 |
| `cast` | string | 演职人员文本（逗号分隔） |
| `director` | string | 导演 |
| `castMembers` | CastMemberVO[] | 结构化演职人员（可选） |
| `wantSeeCount` | int | 想看人数 |
| `nextShowDate` | string \| null | 最近一场排片日期，null 表示暂无排片 |

### 调用示例

```bash
# 搜索影片
curl "http://localhost:8080/api/v1/movies?q=星际&status=hot_showing&page=1&size=10"

# 不传 q 则返回全部影片（可按 status 筛选）
curl "http://localhost:8080/api/v1/movies?status=coming_soon&page=1&size=10"
```

---

## 2. 搜索影院

```
GET /cinemas
```

### 入参 (Query Params)

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `q` | string | 否 | - | 搜索关键词，走 ES 全文检索，匹配字段见下方 ES 索引说明 |
| `movieId` | string | 否 | - | 按影片 ID 筛选（查询某影片在哪些影院有排片）。ES 内作为 filter 条件 |
| `lat` | double | 否 | - | 用户纬度（GCJ-02 坐标系），配合 `sort=distance` 使用 |
| `lng` | double | 否 | - | 用户经度（GCJ-02 坐标系），配合 `sort=distance` 使用 |
| `sort` | string | 否 | - | 排序方式。可选值：`distance`（按距离升序，需配合 lat/lng 做脚本排序）、`price`（按最低票价升序）。不传则按 ES 相关性评分排序 |
| `page` | int | 否 | 1 | 页码，从 1 开始。ES 内转为 `from`/`size` |
| `size` | int | 否 | 10 | 每页条数 |

> **注意：**
> - `sort=distance` 时必须同时传入 `lat` 和 `lng`，后端使用 ES `script_sort` 基于 `geo_point` 计算距离并排序
> - `lat`/`lng` 使用 GCJ-02 坐标系（高德/国测局标准），前端已做 WGS84 → GCJ02 转换
> - `q` 和 `movieId` 可同时传入，取交集（bool query 的 must + filter）

### ES 索引 — cinema

```
索引名: cinema
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | text (ik_smart) | 影院名称，搜索主字段，权重最高 |
| `address` | text (ik_smart) | 地址，搜索字段，权重低于 name |
| `cityId` | keyword | 城市 ID，filter 过滤 |
| `location` | geo_point | 影院经纬度（GCJ-02），用于距离排序 |
| `minPrice` | float | 最低票价，用于价格排序 |
| `features` | keyword[] | 特色标签 |
| `tags` | keyword[] | 服务标签 |

### ES 查询逻辑

```json
// q 不为空时 — 全文搜索
{
  "query": {
    "bool": {
      "must": [{
        "multi_match": {
          "query": "{{q}}",
          "fields": ["name^4", "address"]
        }
      }],
      "filter": [
        // movieId 不为空时: { "term": { "movieIds": "{{movieId}}" } }
      ]
    }
  },
  "sort": [
    // sort=distance: { "_script": { "type": "number", "script": { ... 用 geo_point 算距离 }, "order": "asc" } }
    // sort=price:    { "minPrice": { "order": "asc" } }
    // 默认:          { "_score": { "order": "desc" } }
  ],
  "from": "(page - 1) * size",
  "size": "size"
}

// q 为空时 — match_all + filter(movieId) + sort
```

### 出参

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "items": [
      {
        "cinemaId": "string",
        "name": "万达影城（五角场店）",
        "address": "上海市杨浦区xxx路xx号",
        "cityId": "city_sh",
        "cityName": "上海",
        "lat": 31.2304,
        "lng": 121.4737,
        "distanceMeters": 1250,
        "minPrice": 39.9,
        "features": ["IMAX", "杜比全景声"],
        "trafficNote": "地铁10号线五角场站",
        "tags": ["退改签", "小吃"]
      }
    ],
    "page": 1,
    "size": 10,
    "total": 23
  }
}
```

### CinemaVO 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `cinemaId` | string | 影院 ID |
| `name` | string | 影院名称 |
| `address` | string | 详细地址 |
| `cityId` | string | 城市 ID（如 `city_sh`） |
| `cityName` | string | 城市名称 |
| `lat` | double | 纬度（GCJ-02） |
| `lng` | double | 经度（GCJ-02） |
| `distanceMeters` | int \| null | 距离用户位置多少米。仅当传了 `lat`+`lng` 且 `sort=distance` 时有值 |
| `minPrice` | double \| null | 该影院当前最低票价 |
| `features` | string[] | 特色厅标签（如 IMAX、杜比全景声、4DX） |
| `trafficNote` | string | 交通提示 |
| `tags` | string[] | 服务标签（如 退改签、小吃、停车） |

### 调用示例

```bash
# 搜索影院（关键词）
curl "http://localhost:8080/api/v1/cinemas?q=万达&page=1&size=10"

# 按距离排序（需传入用户坐标 GCJ-02）
curl "http://localhost:8080/api/v1/cinemas?q=万达&lat=31.2304&lng=121.4737&sort=distance&page=1&size=10"

# 按价格排序
curl "http://localhost:8080/api/v1/cinemas?q=万达&sort=price&page=1&size=10"

# 查某影片在哪些影院有排片
curl "http://localhost:8080/api/v1/cinemas?movieId=m001&sort=price&page=1&size=20"

# 按影片 + 距离组合查询
curl "http://localhost:8080/api/v1/cinemas?movieId=m001&lat=31.2304&lng=121.4737&sort=distance"
```

---

## 3. 通用说明

### 统一响应信封

所有接口返回的顶层结构一致：

```json
{
  "code": 200,
  "message": "ok",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | 业务状态码，200 表示成功 |
| `message` | string | 状态描述 |
| `data` | T | 实际数据，类型因接口而异 |

### 分页结构

所有列表接口返回的 `data` 中包含分页信息：

```json
{
  "items": [...],
  "page": 1,
  "size": 10,
  "total": 56
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `items` | T[] | 当前页数据列表 |
| `page` | int | 当前页码 |
| `size` | int | 每页条数 |
| `total` | int | 总记录数 |

### 前端搜索流程

```
用户输入关键词 → 回车
  ↓
跳转 /search?q=xxx，默认显示「影片」Tab
  ↓
影片 Tab: GET /movies?q=xxx&page=1&size=10
  → 后端 ES multi_match(title^4, cast^2, director^2, description)
  → 返回 PageResult<MovieVO>
  ↓（用户点击「影院」Tab）
影院 Tab: GET /cinemas?q=xxx&page=1&size=10
  → 后端 ES multi_match(name^4, address)
  → 返回 PageResult<CinemaVO>
  ↓（用户点击「距离优先」）
获取浏览器定位 → WGS84→GCJ02 转换
  ↓
影院 Tab: GET /cinemas?q=xxx&lat=31.2&lng=121.5&sort=distance&page=1&size=10
  → 后端 ES bool(must: multi_match + filter) + script_sort(geo_distance)
  → 返回 PageResult<CinemaVO>（含 distanceMeters）
  ↓（用户点击「价格优先」）
影院 Tab: GET /cinemas?q=xxx&sort=price&page=1&size=10
  → 后端 ES sort(minPrice asc)
  → 返回 PageResult<CinemaVO>
```

### 后端 ES 索引总览

| 索引 | 搜索字段 | 权重 | 可筛选字段 | 可排序 |
|---|---|---|---|---|
| `movie` | title, cast, director, description | title^4, cast^2, director^2, desc^1 | status | - |
| `cinema` | name, address | name^4, address^1 | movieId | score / distance / minPrice |
