package com.tattoostudio.controller;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.dto.AvailabilityDto;
import com.tattoostudio.dto.AvailabilityDto.Day;
import com.tattoostudio.dto.AvailabilityDto.Slot;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.FlashDesign;
import com.tattoostudio.service.AvailabilityService;
import com.tattoostudio.service.CatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/availability")
@Tag(name = "Booking", description = "Free times, booking, deposits, lookup, cancellation and reviews (public)")
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
    @Operation(summary = "Free start times for a flash design or a consultation over several days",
            description = "Give flashId (its artist and time) or artistId (a 30-minute consultation). "
                    + "Times are local studio time (Europe/Sofia).")
    public AvailabilityDto get(
            @Parameter(description = "Flash design id") @RequestParam(required = false) String flashId,
            @Parameter(description = "Artist id, for a consultation") @RequestParam(required = false) String artistId,
            @Parameter(description = "First date, e.g. 2026-11-20 (default: today)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Number of days (1–31)") @RequestParam(defaultValue = "14") @Min(1) @Max(31) int days) {
        Artist artist;
        Duration length;
        if (flashId != null && !flashId.isBlank()) {
            FlashDesign flash = catalog.flash(flashId);
            artist = catalog.activeArtist(flash.getArtistId());
            length = Duration.ofMinutes(flash.getDurationMinutes());
        } else if (artistId != null && !artistId.isBlank()) {
            artist = catalog.activeArtist(artistId);
            length = rules.consultationLength();
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Give a flashId or an artistId");
        }
        LocalDate first = from == null ? availability.today() : from;
        List<Day> result = new ArrayList<>(days);
        availability.freeStartsForDays(artist, length, first, days).forEach((date, free) -> result.add(new Day(date,
                !availability.worksOn(artist, date),
                free.stream().map(t -> new Slot(t.atZone(rules.timezone()).toLocalTime(), t)).toList())));
        return new AvailabilityDto(artist.getId(), flashId == null || flashId.isBlank() ? null : flashId,
                (int) length.toMinutes(), result);
    }
}
