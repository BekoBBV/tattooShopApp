package com.tattoostudio.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tattoostudio.model.FlashDesign;
import com.tattoostudio.model.Image;

/**
 * A flash design as its artist and the admin see it.
 *
 * @param available   the artist's "Available" switch
 * @param state       where it stands: on the sheet, hidden (switched off), held while a deposit is paid,
 *                    booked, or tattooed
 * @param bookingCode the booking that holds it, if any
 */
public record AdminFlashDto(String id, String artistId, String artistName, String name, int widthCm, int heightCm,
        FlashDesign.Size size, int durationMinutes, BigDecimal price, Image image, boolean available, State state,
        String bookingCode, int sortOrder, Instant createdAt) {

    public enum State { ON_SHEET, HIDDEN, HELD, BOOKED, TATTOOED }
}
