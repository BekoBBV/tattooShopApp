package com.tattoostudio.storage;

import com.tattoostudio.model.Image;

/**
 * Where uploaded pictures are kept: Cloudinary when {@code app.images.cloudinary-url} is set,
 * otherwise a folder on this computer (fine for development; a hosted server's disk is wiped on
 * every deploy). Pictures are served from addresses with a long random part, so nobody can guess
 * the address of a reference photo.
 */
public interface ImageStore {

    /**
     * @param folder    e.g. "flash", "works", "references", "messages"
     * @param extension the file type's usual extension, e.g. "jpg"
     */
    Image store(byte[] bytes, String folder, String extension);

    /** Deletes the picture; failures are only logged (a leftover picture harms nobody). */
    void delete(Image image);
}
