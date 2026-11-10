package com.tattoostudio.storage;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Upload rules and storage, configurable under {@code app.images.*}.
 *
 * @param cloudinaryUrl Cloudinary's API environment variable, {@code cloudinary://KEY:SECRET@CLOUD}
 *                      (empty: pictures are kept in {@code localDir})
 * @param folder        Cloudinary folder all pictures go into
 * @param localDir      folder for pictures without Cloudinary, served at /uploads/
 * @param maxSize       largest picture accepted
 * @param maxPerUpload  most pictures in one booking or message
 */
@ConfigurationProperties("app.images")
public record ImageSettings(
        String cloudinaryUrl,
        @DefaultValue("blade-and-brush") String folder,
        @DefaultValue("uploads") Path localDir,
        @DefaultValue("10MB") DataSize maxSize,
        @DefaultValue("5") int maxPerUpload) {
}
