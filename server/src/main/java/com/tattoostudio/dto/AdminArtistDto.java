package com.tattoostudio.dto;

import java.util.List;

import com.tattoostudio.model.Artist;
import com.tattoostudio.model.DayException;
import com.tattoostudio.model.WorkingHours;

/**
 * An artist as the admin sees them: profile, hours, exceptions, status, login name (null if none) and
 * {@code conflicts}: upcoming bookings outside their hours.
 */
public record AdminArtistDto(String id, String name, String handle, String specialty, String bio, String photoUrl,
        String accent, List<WorkingHours> weeklyHours, List<DayException> exceptions, int sortOrder, boolean active,
        String username, List<StaffBookingDto> conflicts) {

    public static AdminArtistDto from(Artist b, List<DayException> exceptions, String username,
            List<StaffBookingDto> conflicts) {
        return new AdminArtistDto(b.getId(), b.getName(), b.getHandle(), b.getSpecialty(), b.getBio(), b.getPhotoUrl(),
                b.getAccent(), b.getWeeklyHours(), exceptions, b.getSortOrder(), b.isActive(), username, conflicts);
    }
}
