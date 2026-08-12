package com.cinepass.controller;

import com.cinepass.aop.RateLimit;
import com.cinepass.common.Result;
import com.cinepass.security.Public;
import com.cinepass.service.EsSearchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索联想与搜索入口（C 端公开接口）。
 * <pre>
 * GET /api/v1/search/suggestions  搜索框实时补全（ES Completion Suggester）
 * </pre>
 */
@Api(tags = "搜索")
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final EsSearchService esSearchService;

    public SearchController(EsSearchService esSearchService) {
        this.esSearchService = esSearchService;
    }

    /** 搜索联想：根据输入前缀返回候选词列表，静默失败时返回空数组 */
    @ApiOperation("搜索联想（ES Completion Suggester）")
    @RateLimit(key = "search", permits = 60, windowSeconds = 60)
    @GetMapping("/suggestions")
    @Public
    public Result<List<String>> suggest(
            @RequestParam String q,
            @RequestParam(defaultValue = "8") int size) {
        return Result.success(esSearchService.suggest(q, size));
    }
}
