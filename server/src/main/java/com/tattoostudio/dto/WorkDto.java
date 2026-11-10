package com.tattoostudio.dto;

import java.time.Instant;

import com.tattoostudio.model.Image;
import com.tattoostudio.model.Work;

/** A portfolio photo. */
public record WorkDto(String id, String artistId, Image image, String caption, Instant createdAt) {

    public static WorkDto from(Work w) {
        return new WorkDto(w.getId(), w.getArtistId(), w.getImage(), w.getCaption(), w.getCreatedAt());
    }
}
