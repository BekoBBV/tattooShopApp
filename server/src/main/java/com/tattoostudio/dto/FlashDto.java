package com.tattoostudio.dto;

import java.math.BigDecimal;

import com.tattoostudio.model.FlashDesign;
import com.tattoostudio.model.Image;

/**
 * A flash design as customers see it. {@code bookable} is false once someone booked it (every
 * design is tattooed once) or the artist took it off the sheet.
 */
public record FlashDto(String id, String artistId, String artistName, String name, int widthCm, int heightCm,
        FlashDesign.Size size, int durationMinutes, BigDecimal price, BigDecimal deposit, Image image, boolean bookable) {
}
