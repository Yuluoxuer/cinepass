package com.cinepass.controller;

import com.cinepass.common.Result;
import com.cinepass.security.Staff;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 运营端文件上传。
 * <pre>
 * POST /api/v1/admin/upload/poster  — 上传海报图片，返回可访问 URL
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/admin/upload")
@Staff
public class UploadController {

    private static final Set<String> ALLOWED_CONTENT_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    ));

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @PostMapping("/poster")
    public Result<String> uploadPoster(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return Result.fail(400, "文件为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return Result.fail(400, "仅支持 JPEG/PNG/WebP/GIF 格式");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;

        Path dirPath = Paths.get(uploadDir, "posters");
        Files.createDirectories(dirPath);
        Path filePath = dirPath.resolve(filename);
        file.transferTo(filePath.toFile());

        String url = "/uploads/posters/" + filename;
        return Result.success(url);
    }
}
