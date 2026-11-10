package com.tattoostudio.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A photo of finished work in an artist's portfolio. */
@Document("works")
public class Work {

    @Id
    private String id;
    @Indexed
    private String artistId;
    private Image image;
    /** Short title, e.g. "Warrior sleeve" (may be empty). */
    private String caption;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getArtistId() { return artistId; }
    public void setArtistId(String artistId) { this.artistId = artistId; }
    public Image getImage() { return image; }
    public void setImage(Image image) { this.image = image; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
