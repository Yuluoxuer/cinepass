package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解锁结果（幂等：已释放/已过期仍返回 released=true）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlockResultVO {

    /** 锁座凭证 ID */
    private String lockId;

    /** 是否已释放 */
    private Boolean released;
}
