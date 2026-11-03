package com.tattoostudio.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.dto.AvailabilityDto;
import com.tattoostudio.dto.AvailabilityDto.Day;
import com.tattoostudio.dto.AvailabilityDto.Slot;
import com.tattoostudio.model.Artist;
import com.tattoostudio.service.AvailabilityService;
import com.tattoostudio.service.CatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/availability")
@Tag(name = "Booking", description = "Free times, booking, lookup and cancellation (public)")
public class AvailabilityController {

    private final AvailabilityService availability;
    private final CatalogService catalog;
    private final BookingRules rules;

    public AvailabilityController(AvailabilityService availability, CatalogService catalog, BookingRules rules) {
        this.availability = availability;
        this.catalog = catalog;
        this.rules = rules;
    }

    @GetMapping
    @Operation(summary = "Free start times for a service over several days",
            description = "Leave artistId empty for \"any artist\". Times are local shop time (Europe/Sofia).")
    public AvailabilityDto get(
            @RequestParam String serviceId,
            @Parameter(description = "Artist id, or empty for any artist") @RequestParam(required = false) String artistId,
            @Parameter(description = "First date, e.g. 2026-10-20 (default: today)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Number of days (1–31)") @RequestParam(defaultValue = "7") @Min(1) @Max(31) int days) {
        catalog.activeService(serviceId); // 404 for unknown services; every artist offers every service
        Artist artist = artistId == null || artistId.isBlank() ? null : catalog.activeArtist(artistId);
        List<Artist> everyone = catalog.activeArtists();
        LocalDate first = from == null ? availability.today() : from;

        List<Day> result = new ArrayList<>(days);
        availability.freeStartsForDays(artist, everyone, first, days).forEach((date, free) -> {
            List<Slot> slots = free.entrySet().stream()
                    .map(e -> new Slot(e.getKey().atZone(rules.timezone()).toLocalTime(), e.getKey(), e.getValue()))
                    .toList();
            result.add(new Day(date, slots));
        });
        return new AvailabilityDto(serviceId, artist == null ? null : artist.getId(), result);
    }
}
