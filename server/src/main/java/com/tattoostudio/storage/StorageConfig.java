package com.tattoostudio.storage;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Picks the image store and serves locally kept pictures at /uploads/. */
@Configuration
@EnableConfigurationProperties(ImageSettings.class)
public class StorageConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    private final ImageSettings settings;

    public StorageConfig(ImageSettings settings) {
        this.settings = settings;
    }

    @Bean
    ImageStore imageStore(Clock clock) {
        if (settings.cloudinaryUrl() != null && !settings.cloudinaryUrl().isBlank()) {
            log.info("Pictures are stored on Cloudinary");
            return new CloudinaryImageStore(settings.cloudinaryUrl(), settings.folder(), RestClient.builder(), clock);
        }
        LocalImageStore local = new LocalImageStore(settings.localDir());
        log.info("Pictures are stored in {} (set app.images.cloudinary-url to use Cloudinary)", local.root());
        return local;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = settings.localDir().toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
