package com.tattoostudio.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("reviews")
public class Review {

    @Id
    private String id;
    private String name;
    private int rating;
    private String text;
    /** New reviews stay hidden until the admin approves them. */
    private boolean approved;
    private Instant createdAt;

    public Review() {
    }

    public Review(String name, int rating, String text, boolean approved, Instant createdAt) {
        this.name = name;
        this.rating = rating;
        this.text = text;
        this.approved = approved;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
