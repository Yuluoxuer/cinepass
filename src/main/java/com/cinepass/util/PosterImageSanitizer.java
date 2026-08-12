package com.cinepass.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * 海报图片消毒：魔数识别 → ImageIO 解码 → 重编码为 PNG，剥离元数据与伪装载荷。
 */
public final class PosterImageSanitizer {

    /** 单边最大边长，防超大图 */
    public static final int MAX_SIDE = 4096;

    /** 最大像素数，防解压炸弹 */
    public static final long MAX_PIXELS = 16L * 1024 * 1024;

    private PosterImageSanitizer() {
    }

    /**
     * 消毒结果：落盘字节与固定扩展名（始终 .png）。
     */
    public static final class SanitizedPoster {
        private final byte[] bytes;
        private final String extension;
        private final String contentType;

        public SanitizedPoster(byte[] bytes, String extension, String contentType) {
            this.bytes = bytes;
            this.extension = extension;
            this.contentType = contentType;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public String getExtension() {
            return extension;
        }

        public String getContentType() {
            return contentType;
        }
    }

    /** 探测到的输入格式（仅用于校验，输出统一 PNG） */
    public enum DetectedFormat {
        JPEG,
        PNG,
        GIF
    }

    /**
     * 校验并重编码；失败抛出 {@link IllegalArgumentException}（消息可直接返回前端）。
     */
    public static SanitizedPoster sanitize(byte[] raw) throws IOException {
        if (raw == null || raw.length == 0) {
            throw new IllegalArgumentException("文件为空");
        }
        DetectedFormat format = detectFormat(raw);
        if (format == null) {
            throw new IllegalArgumentException("无法识别为合法 JPEG/PNG/GIF 图片");
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(raw));
        if (image == null) {
            throw new IllegalArgumentException("图片无法解码，可能已损坏或格式伪装");
        }
        int w = image.getWidth();
        int h = image.getHeight();
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("图片尺寸无效");
        }
        if (w > MAX_SIDE || h > MAX_SIDE) {
            throw new IllegalArgumentException("图片边长不得超过 " + MAX_SIDE + " 像素");
        }
        if ((long) w * (long) h > MAX_PIXELS) {
            throw new IllegalArgumentException("图片像素过多");
        }

        // 统一 TYPE_INT_RGB 再写 PNG，去掉动画帧/奇异色彩空间与元数据
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics g = rgb.getGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(rgb, "png", out)) {
            throw new IOException("PNG 编码失败");
        }
        return new SanitizedPoster(out.toByteArray(), ".png", "image/png");
    }

    /**
     * 按文件头识别格式；WebP 等不在支持列表内返回 null。
     */
    public static DetectedFormat detectFormat(byte[] raw) {
        if (raw == null || raw.length < 12) {
            return null;
        }
        // JPEG
        if ((raw[0] & 0xFF) == 0xFF && (raw[1] & 0xFF) == 0xD8 && (raw[2] & 0xFF) == 0xFF) {
            return DetectedFormat.JPEG;
        }
        // PNG
        if ((raw[0] & 0xFF) == 0x89 && raw[1] == 0x50 && raw[2] == 0x4E && raw[3] == 0x47
                && raw[4] == 0x0D && raw[5] == 0x0A && raw[6] == 0x1A && raw[7] == 0x0A) {
            return DetectedFormat.PNG;
        }
        // GIF87a / GIF89a
        if (raw[0] == 'G' && raw[1] == 'I' && raw[2] == 'F' && raw[3] == '8'
                && (raw[4] == '7' || raw[4] == '9') && raw[5] == 'a') {
            return DetectedFormat.GIF;
        }
        return null;
    }

    /** 客户端声明的 Content-Type 是否允许（辅助校验，不能单独信任） */
    public static boolean isAllowedDeclaredContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase(Locale.ROOT).trim();
        int semi = ct.indexOf(';');
        if (semi >= 0) {
            ct = ct.substring(0, semi).trim();
        }
        return "image/jpeg".equals(ct)
                || "image/jpg".equals(ct)
                || "image/png".equals(ct)
                || "image/gif".equals(ct);
    }
}
