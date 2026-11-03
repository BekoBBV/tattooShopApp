package com.tattoostudio.dto;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.tattoostudio.model.Artist;
import com.tattoostudio.model.DayException;
import com.tattoostudio.model.WorkingHours;

/**
 * An artist as shown to customers: profile, regular weekly hours (Monday first) and upcoming
 * date exceptions, so the booking page can say "Day off" or show special hours.
 */
public record ArtistDto(String id, String name, String handle, String specialty, String bio, String photoUrl,
        String accent, List<WorkingHours> weeklyHours, List<DayException> exceptions) {

    public static ArtistDto from(Artist b, LocalDate today) {
        List<WorkingHours> hours = b.getWeeklyHours().stream()
                .sorted(Comparator.comparing(WorkingHours::day))
                .toList();
        List<DayException> upcoming = b.getExceptions().stream()
                .filter(e -> !e.date().isBefore(today))
                .sorted(Comparator.comparing(DayException::date))
                .toList();
        return new ArtistDto(b.getId(), b.getName(), b.getHandle(), b.getSpecialty(), b.getBio(), b.getPhotoUrl(),
                b.getAccent(), hours, upcoming);
    }
}
