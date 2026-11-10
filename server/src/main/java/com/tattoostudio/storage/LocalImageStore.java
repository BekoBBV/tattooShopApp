package com.tattoostudio.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tattoostudio.model.Image;

/** Keeps pictures in a folder on this computer; they are served at /uploads/... (see StorageConfig). */
public class LocalImageStore implements ImageStore {

    private static final Logger log = LoggerFactory.getLogger(LocalImageStore.class);

    private final Path root;

    public LocalImageStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    @Override
    public Image store(byte[] bytes, String folder, String extension) {
        String name = folder + "/" + UUID.randomUUID() + "." + extension;
        Path file = root.resolve(name);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the picture", e);
        }
        String url = "/uploads/" + name;
        return new Image(name, url, url);
    }

    @Override
    public void delete(Image image) {
        if (image == null || image.id() == null) {
            return;
        }
        Path file = root.resolve(image.id()).normalize();
        if (!file.startsWith(root)) {
            return; // never outside the uploads folder
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete picture {}: {}", image.id(), e.getMessage());
        }
    }
}
