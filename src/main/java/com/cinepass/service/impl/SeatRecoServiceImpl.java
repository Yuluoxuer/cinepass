package com.cinepass.service.impl;

import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.dto.RecommendSeatsDTO;
import com.cinepass.service.SeatInventoryService;
import com.cinepass.service.SeatRecoService;
import com.cinepass.vo.SeatPlanVO;
import com.cinepass.vo.SeatRecoResultVO;
import com.cinepass.vo.SeatVO;
import com.cinepass.vo.ShowSeatMapVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link SeatRecoService} 实现：简单连座打分，最多返回 3 个方案。
 */
@Service
public class SeatRecoServiceImpl implements SeatRecoService {

    private final SeatInventoryService seatInventoryService;

    public SeatRecoServiceImpl(SeatInventoryService seatInventoryService) {
        this.seatInventoryService = seatInventoryService;
    }

    @Override
    public SeatRecoResultVO recommend(RecommendSeatsDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getShowId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "showId 不能为空");
        }
        int count = dto.getCount() == null ? 1 : dto.getCount();
        if (count < 1 || count > 4) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "count 须为 1–4");
        }
        boolean together = dto.getTogether() == null || Boolean.TRUE.equals(dto.getTogether());
        String preferRow = StringUtils.hasText(dto.getPreferRow()) ? dto.getPreferRow().trim() : "middle";
        String preferSide = StringUtils.hasText(dto.getPreferSide()) ? dto.getPreferSide().trim() : "center";

        ShowSeatMapVO map = seatInventoryService.getShowSeatMap(dto.getShowId().trim());
        List<SeatVO> available = new ArrayList<SeatVO>();
        if (map.getSeats() != null) {
            for (SeatVO s : map.getSeats()) {
                if ("available".equals(s.getStatus()) && !"disabled".equals(s.getType())) {
                    available.add(s);
                }
            }
        }

        int rows = map.getRows() != null ? map.getRows() : 1;
        int cols = map.getCols() != null ? map.getCols() : 1;
        List<Candidate> candidates = new ArrayList<Candidate>();

        Map<Integer, List<SeatVO>> byRow = new HashMap<Integer, List<SeatVO>>();
        for (SeatVO s : available) {
            Integer rn = s.getRowNo();
            if (rn == null) {
                continue;
            }
            List<SeatVO> list = byRow.get(rn);
            if (list == null) {
                list = new ArrayList<SeatVO>();
                byRow.put(rn, list);
            }
            list.add(s);
        }
        for (List<SeatVO> rowSeats : byRow.values()) {
            Collections.sort(rowSeats, new Comparator<SeatVO>() {
                @Override
                public int compare(SeatVO a, SeatVO b) {
                    Integer ca = a.getColNo() == null ? 0 : a.getColNo();
                    Integer cb = b.getColNo() == null ? 0 : b.getColNo();
                    return ca.compareTo(cb);
                }
            });
        }

        if (together) {
            for (List<SeatVO> rowSeats : byRow.values()) {
                for (int i = 0; i <= rowSeats.size() - count; i++) {
                    List<SeatVO> block = new ArrayList<SeatVO>();
                    boolean contiguous = true;
                    for (int j = 0; j < count; j++) {
                        SeatVO cur = rowSeats.get(i + j);
                        if (j > 0) {
                            SeatVO prev = rowSeats.get(i + j - 1);
                            int prevCol = prev.getColNo() == null ? 0 : prev.getColNo();
                            int curCol = cur.getColNo() == null ? 0 : cur.getColNo();
                            if (curCol != prevCol + 1) {
                                contiguous = false;
                                break;
                            }
                        }
                        block.add(cur);
                    }
                    if (contiguous && block.size() == count) {
                        candidates.add(new Candidate(block,
                                score(block, rows, cols, preferRow, preferSide, true)));
                    }
                }
            }
        } else {
            for (List<SeatVO> rowSeats : byRow.values()) {
                if (rowSeats.size() >= count) {
                    List<SeatVO> block = new ArrayList<SeatVO>(rowSeats.subList(0, count));
                    candidates.add(new Candidate(block,
                            score(block, rows, cols, preferRow, preferSide, false)));
                }
            }
            if (candidates.isEmpty() && available.size() >= count) {
                List<SeatVO> sorted = new ArrayList<SeatVO>(available);
                Collections.sort(sorted, new Comparator<SeatVO>() {
                    @Override
                    public int compare(SeatVO a, SeatVO b) {
                        int ra = a.getRowNo() == null ? 0 : a.getRowNo();
                        int rb = b.getRowNo() == null ? 0 : b.getRowNo();
                        if (ra != rb) {
                            return Integer.compare(ra, rb);
                        }
                        int ca = a.getColNo() == null ? 0 : a.getColNo();
                        int cb = b.getColNo() == null ? 0 : b.getColNo();
                        return Integer.compare(ca, cb);
                    }
                });
                candidates.add(new Candidate(new ArrayList<SeatVO>(sorted.subList(0, count)),
                        score(sorted.subList(0, count), rows, cols, preferRow, preferSide, false)));
            }
        }

        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Double.compare(b.score, a.score);
            }
        });

        List<SeatPlanVO> plans = new ArrayList<SeatPlanVO>();
        int limit = Math.min(3, candidates.size());
        for (int i = 0; i < limit; i++) {
            Candidate c = candidates.get(i);
            List<String> ids = new ArrayList<String>();
            for (SeatVO s : c.seats) {
                ids.add(s.getSeatId());
            }
            plans.add(SeatPlanVO.builder()
                    .planId("sp_" + (i + 1))
                    .seatIds(ids)
                    .score(round1(c.score))
                    .explain(buildExplain(c.seats, preferRow, preferSide))
                    .seats(c.seats)
                    .build());
        }

        Map<String, Object> compromise = null;
        if (plans.isEmpty()) {
            compromise = new HashMap<String, Object>();
            compromise.put("suggestion", "本场无法满足 " + count + " 连座，建议减少票数或换场");
            compromise.put("altShowIds", Collections.emptyList());
        }

        return SeatRecoResultVO.builder()
                .showId(map.getShowId())
                .plans(plans)
                .compromise(compromise)
                .build();
    }

    private double score(List<SeatVO> seats, int rows, int cols,
                         String preferRow, String preferSide, boolean together) {
        double sumRow = 0;
        double sumCol = 0;
        for (SeatVO s : seats) {
            sumRow += s.getRowNo() == null ? 1 : s.getRowNo();
            sumCol += s.getColNo() == null ? 1 : s.getColNo();
        }
        double avgRow = sumRow / seats.size();
        double avgCol = sumCol / seats.size();
        double midCol = (cols + 1) / 2.0;

        double rowTarget = (rows + 1) / 2.0;
        if ("front".equals(preferRow)) {
            rowTarget = Math.max(1, rows / 3.0);
        } else if ("back".equals(preferRow)) {
            rowTarget = Math.max(1, rows * 2.0 / 3.0);
        }
        double colTarget = midCol;
        if ("edge".equals(preferSide) || "aisle".equals(preferSide)) {
            colTarget = 1;
        }

        double centerScore = 100.0 - Math.min(100.0, Math.abs(avgCol - midCol) * (100.0 / Math.max(1, cols)));
        double viewScore = 100.0 - Math.min(100.0, Math.abs(avgRow - rowTarget) * (100.0 / Math.max(1, rows)));
        double sideScore = 100.0 - Math.min(100.0, Math.abs(avgCol - colTarget) * (100.0 / Math.max(1, cols)));
        double togetherBonus = together ? 90.0 : 50.0;
        double edgePenalty = (avgCol <= 1.5 || avgCol >= cols - 0.5) ? 20.0 : 0.0;

        return 0.35 * centerScore + 0.30 * viewScore + 0.15 * sideScore
                + 0.15 * togetherBonus - 0.05 * edgePenalty;
    }

    private String buildExplain(List<SeatVO> seats, String preferRow, String preferSide) {
        boolean golden = false;
        for (SeatVO s : seats) {
            if ("golden".equals(s.getZone())) {
                golden = true;
                break;
            }
        }
        String zone = golden ? "黄金区" : "普通区";
        String rowHint = "middle".equals(preferRow) ? "居中" : preferRow;
        return zone + rowHint + "连座";
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static final class Candidate {
        private final List<SeatVO> seats;
        private final double score;

        private Candidate(List<SeatVO> seats, double score) {
            this.seats = seats;
            this.score = score;
        }
    }
}
