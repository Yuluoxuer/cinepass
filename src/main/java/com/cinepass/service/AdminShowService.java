package com.cinepass.service;

import com.cinepass.dto.ShowBatchCreateDTO;
import com.cinepass.dto.ShowCreateDTO;
import com.cinepass.dto.ShowUpdateDTO;
import com.cinepass.vo.ShowVO;

import java.util.List;

/**
 * 运营端排片写操作（创建 / 改期改价 / 取消 / 停售 / 恢复）。
 */
public interface AdminShowService {

    /** 创建场次；同厅时间重叠（含清场缓冲）则 409 */
    ShowVO create(ShowCreateDTO dto);

    /** 批量创建场次；返回创建成功的场次列表 */
    List<ShowVO> batchCreate(ShowBatchCreateDTO dto);

    /** 改时间或分区价；有在途锁座/订单时禁止改时 */
    ShowVO update(String showId, ShowUpdateDTO dto);

    /** 取消场次 */
    ShowVO cancel(String showId);

    /** 停售 */
    ShowVO closeSale(String showId);

    /** 恢复开售 */
    ShowVO resumeSale(String showId);
}
