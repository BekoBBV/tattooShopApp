package com.tattoostudio.dto;

import java.time.Instant;

import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Image;
import com.tattoostudio.model.Work;

/** A portfolio photo in the studio's newest work, with the artist it is by (for the link to their page). */
public record RecentWorkDto(String id, Image image, String caption, Instant createdAt, String artistId,
        String artistName, String artistSlug) {

    public static RecentWorkDto from(Work w, Artist a) {
        return new RecentWorkDto(w.getId(), w.getImage(), w.getCaption(), w.getCreatedAt(), a.getId(), a.getName(),
                a.getSlug());
    }
}
