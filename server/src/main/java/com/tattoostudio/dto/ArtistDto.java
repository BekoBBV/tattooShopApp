package com.tattoostudio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.tattoostudio.model.Artist;
import com.tattoostudio.model.DayException;
import com.tattoostudio.model.Image;
import com.tattoostudio.model.WorkingHours;

/**
 * An artist as shown to customers: profile, rates, signature piece, regular weekly hours (Monday
 * first), upcoming days off or special hours, and their rating from approved reviews
 * ({@code averageRating} is null without reviews).
 */
public record ArtistDto(String id, String name, String slug, String style, String bio, BigDecimal hourlyRate,
        BigDecimal dayRate, Image signaturePiece, List<WorkingHours> weeklyHours, List<DayException> exceptions,
        Double averageRating, int reviewCount) {

    public static ArtistDto from(Artist a, LocalDate today, Double averageRating, int reviewCount) {
        List<WorkingHours> hours = a.getWeeklyHours().stream()
                .sorted(Comparator.comparing(WorkingHours::day))
                .toList();
        List<DayException> upcoming = a.getExceptions().stream()
                .filter(e -> !e.date().isBefore(today))
                .sorted(Comparator.comparing(DayException::date))
                .toList();
        return new ArtistDto(a.getId(), a.getName(), a.getSlug(), a.getStyle(), a.getBio(), a.getHourlyRate(),
                a.getDayRate(), a.getSignaturePiece(), hours, upcoming, averageRating, reviewCount);
    }
}
