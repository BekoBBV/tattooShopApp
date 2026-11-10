package com.tattoostudio.model;

/**
 * An uploaded picture (flash art, portfolio work, a reference photo, a message attachment).
 *
 * @param id       where the image store keeps it (the Cloudinary public id or the local file name);
 *                 needed to delete it again
 * @param url      the picture at up to 2000 px, for viewing
 * @param thumbUrl the picture at up to 600 px wide, for lists and thumbnails
 */
public record Image(String id, String url, String thumbUrl) {
}
