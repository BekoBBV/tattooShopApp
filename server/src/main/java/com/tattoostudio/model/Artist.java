package com.tattoostudio.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** An artist. Every artist offers every service. */
@Document("artists")
public class Artist {

    @Id
    private String id;
    /** Full name, e.g. "Jax Monroe". */
    private String name;
    /** Social handle, e.g. "@jaxcuts". */
    private String handle;
    private String specialty;
    private String bio;
    private String photoUrl;
    /** Accent colour of the spray-can avatar: red, orange, yellow, green, cyan or purple. */
    private String accent;
    private int sortOrder;
    private boolean active = true;
    private List<WorkingHours> weeklyHours = new ArrayList<>();
    private List<DayException> exceptions = new ArrayList<>();

    public Artist() {
    }

    public Artist(String name, String handle, String specialty, String bio, String accent, int sortOrder) {
        this.name = name;
        this.handle = handle;
        this.specialty = specialty;
        this.bio = bio;
        this.accent = accent;
        this.sortOrder = sortOrder;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHandle() { return handle; }
    public void setHandle(String handle) { this.handle = handle; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getAccent() { return accent; }
    public void setAccent(String accent) { this.accent = accent; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public List<WorkingHours> getWeeklyHours() { return weeklyHours; }
    public void setWeeklyHours(List<WorkingHours> weeklyHours) { this.weeklyHours = weeklyHours; }

    public List<DayException> getExceptions() { return exceptions; }
    public void setExceptions(List<DayException> exceptions) { this.exceptions = exceptions; }
}
