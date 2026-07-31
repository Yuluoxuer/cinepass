import type {
  CinemaVO,
  HallVO,
  MovieVO,
  SeatMapVO,
  SeatVO,
  ShowVO,
  UserVO,
} from '@/types';

const poster = (id: string, title: string) =>
  `https://picsum.photos/seed/${id}/400/600`;

export const MOCK_USERS: Array<UserVO & { password: string; phoneRaw?: string }> = [
  {
    userId: 'u1',
    nickname: '演示用户甲',
    phone: '138****0001',
    phoneRaw: '13800000001',
    role: 'user',
    avatarUrl: null,
    password: 'demo123456',
  },
  {
    userId: 'u_staff',
    nickname: '运营小王',
    phone: '139****0002',
    phoneRaw: '13900000002',
    role: 'staff',
    avatarUrl: null,
    password: 'demo123456',
  },
  {
    userId: 'u_admin',
    nickname: '系统管理员',
    phone: '137****0003',
    phoneRaw: '13700000003',
    role: 'admin',
    avatarUrl: null,
    password: 'demo123456',
  },
];

export const MOCK_MOVIES: MovieVO[] = [
  {
    movieId: 'm100',
    title: '流浪地球 3',
    posterUrl: poster('m100', '流浪地球3'),
    genres: ['科幻', '冒险'],
    rating: 9.1,
    durationMin: 173,
    releaseDate: '2026-02-01',
    status: 'hot_showing',
    description:
      '太阳危机再临，联合政府启动流浪地球最终计划。刘培强的故事仍在延续，人类文明再次面临抉择。',
    cast: '吴京 / 刘德华 / 李雪健',
    wantSeeCount: 12890,
  },
  {
    movieId: 'm101',
    title: '热辣滚烫',
    posterUrl: poster('m101', '热辣滚烫'),
    genres: ['喜剧', '运动'],
    rating: 8.6,
    durationMin: 129,
    releaseDate: '2026-01-12',
    status: 'hot_showing',
    description: '一个普通人的自我突破与热血成长故事。',
    cast: '贾玲 / 雷佳音',
    wantSeeCount: 9800,
  },
  {
    movieId: 'm102',
    title: '封神第二部',
    posterUrl: poster('m102', '封神2'),
    genres: ['奇幻', '动作'],
    rating: 8.2,
    durationMin: 148,
    releaseDate: '2026-01-29',
    status: 'hot_showing',
    description: '殷商战场再起烽烟，哪吒与杨戬踏上新征途。',
    cast: '于适 / 费翔 / 那尔那茜',
    wantSeeCount: 15200,
  },
  {
    movieId: 'm103',
    title: '喜剧之王新篇',
    posterUrl: poster('m103', '喜剧之王'),
    genres: ['喜剧'],
    rating: 8.0,
    durationMin: 112,
    releaseDate: '2026-03-01',
    status: 'hot_showing',
    description: '小人物的大梦想，在舞台上绽放光芒。',
    cast: '王宝强 / 黄渤',
    wantSeeCount: 6400,
  },
  {
    movieId: 'm201',
    title: '哪吒之魔童闹海',
    posterUrl: poster('m201', '哪吒3'),
    genres: ['动画', '奇幻'],
    rating: null,
    durationMin: 140,
    releaseDate: '2026-08-15',
    status: 'coming_soon',
    description: '东海再起波澜，哪吒与敖丙再度联手。',
    cast: '吕艳婷 / 囧森瑟夫',
    wantSeeCount: 22000,
  },
  {
    movieId: 'm202',
    title: '星际穿越：归途',
    posterUrl: poster('m202', '星际'),
    genres: ['科幻', '剧情'],
    rating: null,
    durationMin: 165,
    releaseDate: '2026-09-01',
    status: 'coming_soon',
    description: '跨越虫洞的归途，一次关于爱与时间的续章。',
    cast: '待公布',
    wantSeeCount: 8900,
  },
];

export const MOCK_CINEMAS: CinemaVO[] = [
  {
    cinemaId: 'c12',
    name: '万达影城（五角场店）',
    address: '杨浦区翔殷路 1088 号',
    cityId: 'city_sh',
    lat: 31.2989,
    lng: 121.514,
    distanceMeters: 1200,
    minPrice: 45,
  },
  {
    cinemaId: 'c13',
    name: '百丽宫影城（环贸店）',
    address: '徐汇区淮海中路 999 号',
    cityId: 'city_sh',
    lat: 31.2156,
    lng: 121.449,
    distanceMeters: 3500,
    minPrice: 55,
  },
  {
    cinemaId: 'c14',
    name: 'CGV 影城（大宁店）',
    address: '静安区共和新路 2008 号',
    cityId: 'city_sh',
    lat: 31.275,
    lng: 121.45,
    distanceMeters: 5200,
    minPrice: 42,
  },
];

function buildConvexSeats(seatMapId: string, rows: number, cols: number): SeatVO[] {
  const seats: SeatVO[] = [];
  let coupleSeq = 1;
  for (let r = 1; r <= rows; r++) {
    const startCol = r <= 2 ? 3 : r >= rows - 1 ? 2 : 1;
    const endCol = r <= 2 ? cols - 2 : r >= rows - 1 ? cols - 1 : cols;
    let colNo = 0;
    for (let c = startCol; c <= endCol; c++) {
      colNo += 1;
      const isCouple = r === 5 && (c === 4 || c === 5);
      const pairId = isCouple ? `cp_${coupleSeq}` : null;
      if (isCouple && c === 5) coupleSeq += 1;
      const zone: SeatVO['zone'] = r >= 3 && r <= 5 && c >= 3 && c <= cols - 2 ? 'golden' : 'normal';
      seats.push({
        seatId: `${seatMapId}:${r}:${c}`,
        seatName: `${r}排${colNo}座`,
        rowNo: r,
        colNo,
        graphRow: r,
        graphCol: c,
        type: isCouple ? 'couple' : 'normal',
        zone,
        defaultStatus: 'available',
        couplePairId: isCouple ? 'cp_1' : null,
      });
    }
  }
  return seats;
}

export const MOCK_SEAT_MAPS: SeatMapVO[] = [
  {
    seatMapId: 'sm1',
    rows: 8,
    cols: 12,
    screenLabel: '银幕',
    mutable: true,
    seats: buildConvexSeats('sm1', 8, 12),
  },
];

export const MOCK_HALLS: HallVO[] = [
  { hallId: 'h1', cinemaId: 'c12', name: '1号厅', seatMapId: 'sm1', showCount: 3 },
  { hallId: 'h2', cinemaId: 'c12', name: 'IMAX厅', seatMapId: 'sm1', showCount: 2 },
  { hallId: 'h3', cinemaId: 'c13', name: '1号厅', seatMapId: 'sm1', showCount: 2 },
  { hallId: 'h4', cinemaId: 'c14', name: '2号厅', seatMapId: 'sm1', showCount: 1 },
];

function todayPlus(days: number): string {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

function isoAt(date: string, hh: number, mm: number): string {
  return `${date}T${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}:00+08:00`;
}

function endIso(start: string, durationMin: number): string {
  const m = start.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})/);
  if (!m) return start;
  let total = Number(m[2]) * 60 + Number(m[3]) + durationMin;
  const day = new Date(`${m[1]}T00:00:00`);
  day.setDate(day.getDate() + Math.floor(total / (24 * 60)));
  total = ((total % (24 * 60)) + 24 * 60) % (24 * 60);
  const hh = String(Math.floor(total / 60)).padStart(2, '0');
  const mm = String(total % 60).padStart(2, '0');
  const date = day.toISOString().slice(0, 10);
  return `${date}T${hh}:${mm}:00+08:00`;
}

const hotMovies = MOCK_MOVIES.filter((m) => m.status === 'hot_showing');

export function buildMockShows(): ShowVO[] {
  const shows: ShowVO[] = [];
  let n = 900;
  for (let day = 0; day < 5; day++) {
    const date = todayPlus(day);
    for (const cinema of MOCK_CINEMAS) {
      const halls = MOCK_HALLS.filter((h) => h.cinemaId === cinema.cinemaId);
      for (const movie of hotMovies) {
        for (const hall of halls) {
          for (const slot of [
            [14, 10],
            [16, 30],
            [19, 0],
          ] as const) {
            const start = isoAt(date, slot[0], slot[1]);
            const price = hall.name.includes('IMAX') ? 88 : cinema.minPrice || 45;
            const remain = 40 + ((n * 7) % 60);
            const ratio = remain / 96;
            shows.push({
              showId: `s${n++}`,
              movieId: movie.movieId,
              cinemaId: cinema.cinemaId,
              hallId: hall.hallId,
              hallName: hall.name,
              startTime: start,
              endTime: endIso(start, movie.durationMin),
              price,
              seatRemain: remain,
              seatRemainLevel:
                ratio >= 0.4 ? 'ample' : ratio >= 0.15 ? 'tight' : 'almost_full',
              status: 'on_sale',
            });
          }
        }
      }
    }
  }
  return shows;
}
