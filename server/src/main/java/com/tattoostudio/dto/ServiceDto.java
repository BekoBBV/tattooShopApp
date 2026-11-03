package com.tattoostudio.dto;

import java.math.BigDecimal;

import com.tattoostudio.model.ServiceItem;

/** A service as shown to customers. */
public record ServiceDto(String id, String name, String description, BigDecimal price, String label, String palette) {

    public static ServiceDto from(ServiceItem s) {
        return new ServiceDto(s.getId(), s.getName(), s.getDescription(), s.getPrice(), s.getLabel(), s.getPalette());
    }
}
