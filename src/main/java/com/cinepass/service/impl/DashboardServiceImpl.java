package com.cinepass.service.impl;

import com.cinepass.mapper.OrderTicketMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.security.Roles;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.DashboardService;
import com.cinepass.vo.AdminDashboardStatsVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final OrderTicketMapper orderTicketMapper;
    private final ShowMapper showMapper;

    public DashboardServiceImpl(OrderTicketMapper orderTicketMapper, ShowMapper showMapper) {
        this.orderTicketMapper = orderTicketMapper;
        this.showMapper = showMapper;
    }

    @Override
    public AdminDashboardStatsVO getStats(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // staff 仅统计本影院；admin 全量
        String cinemaId = null;
        if (!SecurityContext.isAdmin()) {
            cinemaId = SecurityContext.getCurrentCinemaId();
            if (!StringUtils.hasText(cinemaId)) {
                cinemaId = null;
            }
        }

        long total = orderTicketMapper.countAdmin(null, null, null, null, cinemaId);
        long pending = orderTicketMapper.countAdmin(null, "pending_pay", null, null, cinemaId);
        long issued = orderTicketMapper.countAdmin(null, "issued", null, null, cinemaId);
        long onSaleShows = showMapper.countOnSaleByDate(dateStr, cinemaId);

        return AdminDashboardStatsVO.builder()
                .date(dateStr)
                .totalOrderCount(total)
                .pendingPayOrderCount(pending)
                .issuedOrderCount(issued)
                .onSaleShowCount(onSaleShows)
                .build();
    }
}
