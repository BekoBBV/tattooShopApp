package com.tattoostudio.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Image;

/**
 * Checks uploaded pictures and hands them to the image store. The type is read from the file's
 * first bytes, not from its name or the browser's word for it, so only real JPG, PNG, HEIC or WebP
 * pictures are kept.
 */
@Component
public class ImageUploads {

    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");

    private final ImageStore store;
    private final ImageSettings settings;

    public ImageUploads(ImageStore store, ImageSettings settings) {
        this.store = store;
        this.settings = settings;
    }

    public int maxPerUpload() {
        return settings.maxPerUpload();
    }

    /**
     * Checks every file first, then stores them all. If one fails to store, the ones already
     * stored are deleted again, so nothing is left half-done.
     */
    public List<Image> storeAll(List<MultipartFile> files, String folder) {
        List<MultipartFile> given = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (given.size() > settings.maxPerUpload()) {
            throw invalid(HttpStatus.BAD_REQUEST, "You can add up to " + settings.maxPerUpload() + " photos");
        }
        List<Checked> checked = given.stream().map(this::check).toList();
        List<Image> stored = new ArrayList<>();
        try {
            for (Checked c : checked) {
                stored.add(store.store(c.bytes(), folder, c.extension()));
            }
        } catch (RuntimeException e) {
            deleteAll(stored);
            throw e;
        }
        return stored;
    }

    /** One required picture (flash art, a portfolio photo, a signature piece). */
    public Image storeOne(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw invalid(HttpStatus.BAD_REQUEST, "Please choose a picture");
        }
        Checked c = check(file);
        return store.store(c.bytes(), folder, c.extension());
    }

    public void delete(Image image) {
        if (image != null) {
            store.delete(image);
        }
    }

    public void deleteAll(Collection<Image> images) {
        if (images != null) {
            images.forEach(this::delete);
        }
    }

    private record Checked(byte[] bytes, String extension) {
    }

    private Checked check(MultipartFile file) {
        if (file.getSize() > settings.maxSize().toBytes()) {
            throw invalid(HttpStatus.PAYLOAD_TOO_LARGE, describe(file) + " is larger than "
                    + settings.maxSize().toMegabytes() + " MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String extension = extensionOf(bytes);
        if (extension == null) {
            throw invalid(HttpStatus.BAD_REQUEST, describe(file) + " isn't a JPG, PNG or HEIC photo");
        }
        return new Checked(bytes, extension);
    }

    /** The picture type from its first bytes, or null if it isn't one we accept. */
    static String extensionOf(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8),
                new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A })) {
            return "png";
        }
        if (b.length >= 12 && ascii(b, 4, 4).equals("ftyp") && HEIC_BRANDS.contains(ascii(b, 8, 4))) {
            return "heic";
        }
        if (b.length >= 12 && ascii(b, 0, 4).equals("RIFF") && ascii(b, 8, 4).equals("WEBP")) {
            return "webp";
        }
        return null;
    }

    private static String ascii(byte[] b, int from, int length) {
        return new String(b, from, length, StandardCharsets.US_ASCII);
    }

    private static String describe(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "The file" : "\"" + name + "\"";
    }

    private static ApiException invalid(HttpStatus status, String message) {
        return new ApiException(status, ErrorCode.INVALID_FILE, message);
    }
}
