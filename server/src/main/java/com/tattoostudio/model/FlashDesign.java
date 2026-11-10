package com.tattoostudio.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A ready-made design at a fixed size, time and price. Every design is one of a kind: it is
 * tattooed once, so while a booking holds it (see the index on Booking) it is off the sheet.
 */
@Document("flash")
public class FlashDesign {

    /** Small, medium or large, by the longer side: up to 8 cm, up to 12 cm, more. */
    public enum Size { SMALL, MEDIUM, LARGE }

    @Id
    private String id;
    @Indexed
    private String artistId;
    private String name;
    private int widthCm;
    private int heightCm;
    /** How long tattooing it takes, a multiple of 30 minutes. */
    private int durationMinutes;
    private BigDecimal price;
    /** Null until the artist uploads the drawing. */
    private Image image;
    /** The artist's "Available" switch: false keeps it off the sheet. */
    private boolean available = true;
    private int sortOrder;
    private Instant createdAt;

    public Size size() {
        int longer = Math.max(widthCm, heightCm);
        return longer <= 8 ? Size.SMALL : longer <= 12 ? Size.MEDIUM : Size.LARGE;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getArtistId() { return artistId; }
    public void setArtistId(String artistId) { this.artistId = artistId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getWidthCm() { return widthCm; }
    public void setWidthCm(int widthCm) { this.widthCm = widthCm; }
    public int getHeightCm() { return heightCm; }
    public void setHeightCm(int heightCm) { this.heightCm = heightCm; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Image getImage() { return image; }
    public void setImage(Image image) { this.image = image; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
