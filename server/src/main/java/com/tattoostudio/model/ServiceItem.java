package com.tattoostudio.model;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A service the shop offers, e.g. "Skin Fade". Every service takes one fixed
 * appointment slot (see app.booking.slot-length), so it has no duration of its own.
 * Named ServiceItem so it does not clash with Spring's @Service annotation.
 */
@Document("services")
public class ServiceItem {

    @Id
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    /** Card label shown on the MiniDisc, e.g. "MD 01". */
    private String label;
    /** Key of the frontend colour palette for this service, e.g. "blue" or "sunset". */
    private String palette;
    private int sortOrder;
    private boolean active = true;

    public ServiceItem() {
    }

    public ServiceItem(String name, String description, BigDecimal price, String label, String palette, int sortOrder) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.label = label;
        this.palette = palette;
        this.sortOrder = sortOrder;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getPalette() { return palette; }
    public void setPalette(String palette) { this.palette = palette; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
