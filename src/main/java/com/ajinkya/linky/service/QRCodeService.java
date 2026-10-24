package com.ajinkya.linky.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class QRCodeService {

    public static final int MIN_SIZE = 100;
    public static final int MAX_SIZE = 1000;
    public static final int MAX_MARGIN = 10;
    private static final Pattern HEX = Pattern.compile("^#?[0-9a-fA-F]{6}$");

    /** Appearance options for a QR code. Colours are 6-digit hex, with or without a leading '#'. */
    public record Style(int size, String foreground, String background, int margin) {
        public static Style defaults() {
            return new Style(250, "000000", "ffffff", 4);
        }
    }

    /** Validates and normalises style input; throws IllegalArgumentException (mapped to HTTP 400). */
    public Style style(Integer size, String foreground, String background, Integer margin) {
        Style d = Style.defaults();
        int s = size == null ? d.size() : size;
        int m = margin == null ? d.margin() : margin;
        if (s < MIN_SIZE || s > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between " + MIN_SIZE + " and " + MAX_SIZE);
        }
        if (m < 0 || m > MAX_MARGIN) {
            throw new IllegalArgumentException("margin must be between 0 and " + MAX_MARGIN);
        }
        String fg = hex(foreground == null || foreground.isBlank() ? d.foreground() : foreground, "foreground");
        String bg = hex(background == null || background.isBlank() ? d.background() : background, "background");
        if (fg.equals(bg)) {
            throw new IllegalArgumentException("foreground and background colours must differ or the code is unreadable");
        }
        return new Style(s, fg, bg, m);
    }

    private static String hex(String value, String field) {
        if (!HEX.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a 6-digit hex colour like 1a2b3c");
        }
        return value.startsWith("#") ? value.substring(1).toLowerCase() : value.toLowerCase();
    }

    public byte[] generatePng(String text, Style style) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, style.size(), style.size(), hints(style));
            MatrixToImageConfig config = new MatrixToImageConfig(argb(style.foreground()), argb(style.background()));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(MatrixToImageWriter.toBufferedImage(matrix, config), "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Could not generate QR code", e);
        }
    }

    public String generateSvg(String text, Style style) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints(style));
            int w = matrix.getWidth();
            int h = matrix.getHeight();

            StringBuilder svg = new StringBuilder();
            svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(style.size())
                    .append("\" height=\"").append(style.size())
                    .append("\" viewBox=\"0 0 ").append(w).append(' ').append(h)
                    .append("\" shape-rendering=\"crispEdges\">\n");
            svg.append("<rect width=\"").append(w).append("\" height=\"").append(h)
                    .append("\" fill=\"#").append(style.background()).append("\"/>\n");
            svg.append("<path d=\"");
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (matrix.get(x, y)) {
                        svg.append('M').append(x).append(',').append(y).append("h1v1h-1z ");
                    }
                }
            }
            svg.append("\" fill=\"#").append(style.foreground()).append("\"/>\n</svg>");
            return svg.toString();
        } catch (WriterException e) {
            throw new RuntimeException("Could not generate QR code SVG", e);
        }
    }

    private static Map<EncodeHintType, Object> hints(Style style) {
        // High error correction keeps the code scannable even with low-contrast custom colours.
        return Map.of(EncodeHintType.MARGIN, style.margin(),
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q);
    }

    private static int argb(String hex) {
        return 0xFF000000 | Integer.parseInt(hex, 16);
    }
}
