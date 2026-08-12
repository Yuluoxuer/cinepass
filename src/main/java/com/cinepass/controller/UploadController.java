package com.cinepass.controller;

import com.cinepass.aop.RateLimit;
import com.cinepass.common.Result;
import com.cinepass.security.Staff;
import com.cinepass.util.PosterImageSanitizer;
import com.cinepass.util.PosterImageSanitizer.SanitizedPoster;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 运营端文件上传。
 * <pre>
 * POST /api/v1/admin/upload/poster  — 上传海报图片，返回可访问 URL
 * </pre>
 * <p>校验：Content-Type 辅助 + 魔数 + ImageIO 解码重编码为 PNG（忽略客户端扩展名）。
 */
@RestController
@RequestMapping("/api/v1/admin/upload")
@Staff
public class UploadController {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private Path absoluteUploadDir;

    @PostConstruct
    void init() {
        absoluteUploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * 上传海报：消毒后落盘为 {@code /uploads/posters/{uuid}.png}。
     */
    @RateLimit(key = "upload", permits = 5, windowSeconds = 60)
    @PostMapping("/poster")
    public Result<String> uploadPoster(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail(400, "文件为空");
        }
        if (!PosterImageSanitizer.isAllowedDeclaredContentType(file.getContentType())) {
            return Result.fail(400, "仅支持 JPEG/PNG/GIF 格式");
        }

        final SanitizedPoster sanitized;
        try {
            sanitized = PosterImageSanitizer.sanitize(file.getBytes());
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (IOException e) {
            return Result.fail(500, "文件读取失败");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + sanitized.getExtension();
        try {
            Path dirPath = absoluteUploadDir.resolve("posters").normalize();
            if (!dirPath.startsWith(absoluteUploadDir)) {
                return Result.fail(500, "上传目录配置非法");
            }
            Files.createDirectories(dirPath);
            Path filePath = dirPath.resolve(filename).normalize();
            if (!filePath.startsWith(dirPath)) {
                return Result.fail(400, "非法文件名");
            }
            Files.write(filePath, sanitized.getBytes());

            return Result.success("/uploads/posters/" + filename);
        } catch (IOException e) {
            return Result.fail(500, "文件上传失败");
        }
    }
}
