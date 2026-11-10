package com.tattoostudio.dto;

import java.math.BigDecimal;
import java.util.List;

import com.tattoostudio.model.Artist;
import com.tattoostudio.model.DayException;
import com.tattoostudio.model.Image;
import com.tattoostudio.model.WorkingHours;

/**
 * An artist as the admin sees them: profile, rates, hours, exceptions, break, status, login name (null
 * if none) and {@code conflicts}: upcoming customer bookings outside their hours.
 */
public record AdminArtistDto(String id, String name, String slug, String style, String bio, BigDecimal hourlyRate,
        BigDecimal dayRate, Image signaturePiece, List<WorkingHours> weeklyHours, List<DayException> exceptions,
        int breakMinutes, int sortOrder, boolean active, String username, List<StaffBookingDto> conflicts) {

    public static AdminArtistDto from(Artist a, List<DayException> exceptions, String username,
            List<StaffBookingDto> conflicts) {
        return new AdminArtistDto(a.getId(), a.getName(), a.getSlug(), a.getStyle(), a.getBio(), a.getHourlyRate(),
                a.getDayRate(), a.getSignaturePiece(), a.getWeeklyHours(), exceptions, a.getBreakMinutes(),
                a.getSortOrder(), a.isActive(), username, conflicts);
    }
}
