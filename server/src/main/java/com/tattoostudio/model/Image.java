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

    /** How the id of a picture that ships with the website starts. */
    public static final String BUNDLED = "bundled:";

    /**
     * A picture that ships with the website itself (client/public/images/...), such as the sample
     * flash drawings. It is served from the site, so it survives a host wiping its disk, and it is
     * never deleted from the image store: replacing or deleting the design only lets go of it.
     */
    public static Image bundled(String path) {
        String url = "/images/" + path;
        return new Image(BUNDLED + path, url, url);
    }

    /**
     * A picture that ships with the website, with a smaller copy for lists and thumbnails (e.g. the
     * artists' scroll pictures: the full one on the artist's page, the small one everywhere else).
     */
    public static Image bundled(String path, String thumbPath) {
        return new Image(BUNDLED + path, "/images/" + path, "/images/" + thumbPath);
    }

    public boolean isBundled() {
        return id != null && id.startsWith(BUNDLED);
    }
}
