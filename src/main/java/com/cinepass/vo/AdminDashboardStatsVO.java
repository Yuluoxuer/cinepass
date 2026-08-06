package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 运营 Dashboard 统计数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardStatsVO {
    private String date;
    private long totalOrderCount;
    private long pendingPayOrderCount;
    private long issuedOrderCount;
    private long onSaleShowCount;
}
