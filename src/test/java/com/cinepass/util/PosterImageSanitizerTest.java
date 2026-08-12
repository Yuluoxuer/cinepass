package com.cinepass.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PosterImageSanitizer} 魔数 / 解码 / 重编码。
 */
class PosterImageSanitizerTest {

    @Test
    void detectFormat_pngJpegGif() throws Exception {
        assertEquals(PosterImageSanitizer.DetectedFormat.PNG, PosterImageSanitizer.detectFormat(pngBytes()));
        assertEquals(PosterImageSanitizer.DetectedFormat.JPEG, PosterImageSanitizer.detectFormat(jpegBytes()));
        assertEquals(PosterImageSanitizer.DetectedFormat.GIF, PosterImageSanitizer.detectFormat(gifBytes()));
    }

    @Test
    void detectFormat_rejectsHtmlAndWebpHeader() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        assertNull(PosterImageSanitizer.detectFormat(html));
        byte[] webp = new byte[16];
        webp[0] = 'R';
        webp[1] = 'I';
        webp[2] = 'F';
        webp[3] = 'F';
        webp[8] = 'W';
        webp[9] = 'E';
        webp[10] = 'B';
        webp[11] = 'P';
        assertNull(PosterImageSanitizer.detectFormat(webp));
    }

    @Test
    void sanitize_png_rewritesAsPng() throws Exception {
        PosterImageSanitizer.SanitizedPoster out = PosterImageSanitizer.sanitize(pngBytes());
        assertEquals(".png", out.getExtension());
        assertEquals("image/png", out.getContentType());
        assertEquals(PosterImageSanitizer.DetectedFormat.PNG, PosterImageSanitizer.detectFormat(out.getBytes()));
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(out.getBytes())));
    }

    @Test
    void sanitize_html_rejected() {
        byte[] html = "<!DOCTYPE html><html></html>".getBytes();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PosterImageSanitizer.sanitize(html));
        assertTrue(ex.getMessage().contains("无法识别") || ex.getMessage().contains("解码"));
    }

    @Test
    void sanitize_jpeg_rewritesAsPng() throws Exception {
        PosterImageSanitizer.SanitizedPoster out = PosterImageSanitizer.sanitize(jpegBytes());
        assertEquals(".png", out.getExtension());
    }

    @Test
    void isAllowedDeclaredContentType() {
        assertTrue(PosterImageSanitizer.isAllowedDeclaredContentType("image/jpeg"));
        assertTrue(PosterImageSanitizer.isAllowedDeclaredContentType("image/png; charset=binary"));
        assertTrue(!PosterImageSanitizer.isAllowedDeclaredContentType("image/webp"));
        assertTrue(!PosterImageSanitizer.isAllowedDeclaredContentType("text/html"));
    }

    private static byte[] pngBytes() throws Exception {
        return encode("png");
    }

    private static byte[] jpegBytes() throws Exception {
        return encode("jpg");
    }

    private static byte[] gifBytes() throws Exception {
        return encode("gif");
    }

    private static byte[] encode(String format) throws Exception {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 8, 8);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, format, baos);
        return baos.toByteArray();
    }
}
