package com.tattoostudio.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tattoostudio.model.Image;

/** Pictures that ship with the website are never deleted from the image store. */
class ImageUploadsTest {

    @Test
    void onlyStoredPicturesAreDeleted() {
        List<Image> deleted = new ArrayList<>();
        ImageStore store = new ImageStore() {
            @Override
            public Image store(byte[] bytes, String folder, String extension) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(Image image) {
                deleted.add(image);
            }
        };
        ImageUploads uploads = new ImageUploads(store, null);
        Image uploaded = new Image("flash/own.png", "/uploads/flash/own.png", "/uploads/flash/own.png");
        Image bundled = Image.bundled("flash/peony.jpg");

        uploads.deleteAll(List.of(uploaded, bundled));

        assertThat(deleted).containsExactly(uploaded);
        assertThat(bundled.isBundled()).isTrue();
        assertThat(bundled.url()).isEqualTo("/images/flash/peony.jpg");
        assertThat(uploaded.isBundled()).isFalse();
    }
}
