package com.tattoostudio.storage;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tattoostudio.model.Image;

/**
 * Uploads pictures to Cloudinary through its upload API (signed requests, no SDK needed).
 * The stored addresses ask Cloudinary for the best format per browser (f_auto), so HEIC photos
 * from iPhones show everywhere.
 */
public class CloudinaryImageStore implements ImageStore {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStore.class);

    private final String cloud;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;
    private final RestClient rest;
    private final Clock clock;

    /** @param cloudinaryUrl {@code cloudinary://KEY:SECRET@CLOUD} */
    public CloudinaryImageStore(String cloudinaryUrl, String folder, RestClient.Builder rest, Clock clock) {
        URI uri = URI.create(cloudinaryUrl.trim());
        String[] credentials = uri.getUserInfo() == null ? new String[0] : uri.getUserInfo().split(":", 2);
        if (!"cloudinary".equals(uri.getScheme()) || credentials.length != 2 || uri.getHost() == null) {
            throw new IllegalArgumentException("app.images.cloudinary-url must look like cloudinary://KEY:SECRET@CLOUD");
        }
        this.apiKey = credentials[0];
        this.apiSecret = credentials[1];
        this.cloud = uri.getHost();
        this.folder = folder;
        this.rest = rest.baseUrl("https://api.cloudinary.com/v1_1/" + cloud + "/image").build();
        this.clock = clock;
    }

    @Override
    public Image store(byte[] bytes, String subfolder, String extension) {
        String target = folder + "/" + subfolder;
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return UUID.randomUUID() + "." + extension;
            }
        });
        form.add("api_key", apiKey);
        form.add("timestamp", timestamp);
        form.add("folder", target);
        form.add("signature", sign("folder=" + target + "&timestamp=" + timestamp));
        Map<?, ?> result = rest.post().uri("/upload").contentType(MediaType.MULTIPART_FORM_DATA).body(form)
                .retrieve().body(Map.class);
        Object publicId = result == null ? null : result.get("public_id");
        if (publicId == null) {
            throw new IllegalStateException("Cloudinary did not return the picture's id");
        }
        String base = "https://res.cloudinary.com/" + cloud + "/image/upload/";
        return new Image(publicId.toString(), base + "c_limit,w_2000,f_auto,q_auto/" + publicId,
                base + "c_limit,w_600,f_auto,q_auto/" + publicId);
    }

    @Override
    public void delete(Image image) {
        if (image == null || image.id() == null) {
            return;
        }
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("public_id", image.id());
        form.add("api_key", apiKey);
        form.add("timestamp", timestamp);
        form.add("signature", sign("public_id=" + image.id() + "&timestamp=" + timestamp));
        try {
            rest.post().uri("/destroy").contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Could not delete picture {} from Cloudinary: {}", image.id(), e.getMessage());
        }
    }

    /** Cloudinary's signature: SHA-1 of the sorted parameters followed by the API secret. */
    private String sign(String sortedParams) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(sha1.digest((sortedParams + apiSecret).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
