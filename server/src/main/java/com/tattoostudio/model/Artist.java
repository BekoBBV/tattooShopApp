package com.tattoostudio.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An artist. Artists never have portrait photos: the site shows their name, their seal (the first
 * letter of the name), their style, a signature piece and their work.
 */
@Document("artists")
public class Artist {

    @Id
    private String id;
    /** First name as shown everywhere, e.g. "Kai". */
    private String name;
    /** The name in the page address, e.g. "kai" for /artists/kai. */
    @Indexed(unique = true, sparse = true)
    private String slug;
    /** One short line, e.g. "Bold traditional colour". */
    private String style;
    private String bio;
    /** Custom work per hour, e.g. 90 (EUR). */
    private BigDecimal hourlyRate;
    /** A full day on a large piece, e.g. 550 (EUR). */
    private BigDecimal dayRate;
    /** The piece on the artist's hanging scroll; null until one is uploaded. */
    private Image signaturePiece;
    /** Kept free after each client, before the next booking can start. */
    private int breakMinutes = 30;
    private int sortOrder;
    private boolean active = true;
    private List<WorkingHours> weeklyHours = new ArrayList<>();
    private List<DayException> exceptions = new ArrayList<>();

    public Artist() {
    }

    public Artist(String name, String slug, String style, String bio, BigDecimal hourlyRate, BigDecimal dayRate,
            int sortOrder) {
        this.name = name;
        this.slug = slug;
        this.style = style;
        this.bio = bio;
        this.hourlyRate = hourlyRate;
        this.dayRate = dayRate;
        this.sortOrder = sortOrder;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
    public BigDecimal getDayRate() { return dayRate; }
    public void setDayRate(BigDecimal dayRate) { this.dayRate = dayRate; }
    public Image getSignaturePiece() { return signaturePiece; }
    public void setSignaturePiece(Image signaturePiece) { this.signaturePiece = signaturePiece; }
    public int getBreakMinutes() { return breakMinutes; }
    public void setBreakMinutes(int breakMinutes) { this.breakMinutes = breakMinutes; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<WorkingHours> getWeeklyHours() { return weeklyHours; }
    public void setWeeklyHours(List<WorkingHours> weeklyHours) { this.weeklyHours = weeklyHours; }
    public List<DayException> getExceptions() { return exceptions; }
    public void setExceptions(List<DayException> exceptions) { this.exceptions = exceptions; }
}
