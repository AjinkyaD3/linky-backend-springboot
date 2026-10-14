package com.ajinkya.linky.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stores avatar images.
 *
 * Nothing the client says about the file is trusted: not its name, not its extension and not its declared
 * content type. The real type is identified from the file's leading bytes ("magic numbers") and must be PNG,
 * JPEG, GIF or WebP; the stored name and extension are derived from that detected type. This rules out
 * uploading HTML, SVG (which can carry script) or an executable renamed to ".png".
 */
@Service
public class FileStorageService {

    public static final long MAX_BYTES = 2L * 1024 * 1024;

    private final Path uploadDir = Paths.get("uploads", "avatars");

    public FileStorageService() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize upload directory", e);
        }
    }

    public String storeAvatar(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Failed to store empty file.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Image is too large (maximum 2 MB).");
        }

        try {
            byte[] bytes = file.getBytes();
            String extension = detectImageExtension(bytes);
            if (extension == null) {
                throw new IllegalArgumentException("Unsupported file. Upload a PNG, JPEG, GIF or WebP image.");
            }

            String newFilename = "user_" + userId + "_" + UUID.randomUUID() + "." + extension;
            Path destination = uploadDir.resolve(newFilename).normalize().toAbsolutePath();
            if (!destination.getParent().equals(uploadDir.toAbsolutePath())) {
                throw new SecurityException("Cannot store file outside current directory.");
            }

            Files.copy(new java.io.ByteArrayInputStream(bytes), destination, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/avatars/" + newFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file.", e);
        }
    }

    /** Returns "png", "jpg", "gif" or "webp" from the file's leading bytes, or null if it is none of them. */
    static String detectImageExtension(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
                && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return "gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
