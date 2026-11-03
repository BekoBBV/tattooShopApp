package com.tattoostudio.dto;

import java.math.BigDecimal;

import com.tattoostudio.model.ServiceItem;

/** A service as the admin sees it, including hidden ones. */
public record AdminServiceDto(String id, String name, String description, BigDecimal price, String label,
        String palette, int sortOrder, boolean active) {

    public static AdminServiceDto from(ServiceItem s) {
        return new AdminServiceDto(s.getId(), s.getName(), s.getDescription(), s.getPrice(), s.getLabel(),
                s.getPalette(), s.getSortOrder(), s.isActive());
    }
}
