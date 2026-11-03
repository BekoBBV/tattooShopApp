package com.tattoostudio.controller;

import java.time.LocalDate;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.ScheduleDto;
import com.tattoostudio.dto.ScheduleResponse;
import com.tattoostudio.dto.StaffBookingDto;
import com.tattoostudio.dto.StatusUpdateRequest;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.security.StaffUser;
import com.tattoostudio.service.ScheduleService;
import com.tattoostudio.service.StaffBookingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** An artist's own bookings and hours. Everything here is scoped to the signed-in artist. */
@RestController
@RequestMapping("/api/staff")
@Tag(name = "Artist", description = "The signed-in artist's own bookings and working hours")
@SecurityRequirement(name = "bearer")
public class StaffController {

    private final StaffBookingService staffBookings;
    private final ScheduleService schedules;
    private final ArtistRepository artists;

    public StaffController(StaffBookingService staffBookings, ScheduleService schedules, ArtistRepository artists) {
        this.staffBookings = staffBookings;
        this.schedules = schedules;
        this.artists = artists;
    }

    @GetMapping("/bookings")
    @Operation(summary = "My bookings", description = "Dates are inclusive. Default: the last 7 days and the next 30. "
            + "Search by customer, phone, email, code, service or notes; sort by date, service, customer, price or status.")
    public PageDto<StaffBookingDto> bookings(@AuthenticationPrincipal Jwt jwt, @Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) BookingStatus status) {
        return staffBookings.list(artistId(jwt), from, to, status, list);
    }

    @PatchMapping("/bookings/{id}")
    @Operation(summary = "Mark one of my bookings Done, No-show or Cancelled")
    public StaffBookingDto updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @Valid @RequestBody StatusUpdateRequest request) {
        artistId(jwt); // the admin has no own bookings and uses /api/admin/bookings
        return staffBookings.updateStatus(id, request.status(), StaffUser.from(jwt));
    }

    @GetMapping("/schedule")
    @Operation(summary = "My weekly hours and upcoming date exceptions", description = "conflicts lists upcoming bookings outside these hours.")
    public ScheduleResponse schedule(@AuthenticationPrincipal Jwt jwt) {
        return schedules.get(artistId(jwt));
    }

    @PutMapping("/schedule")
    @Operation(summary = "Replace my weekly hours and date exceptions")
    public ScheduleResponse updateSchedule(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ScheduleDto schedule) {
        return schedules.update(artistId(jwt), schedule);
    }

    /** The signed-in, active artist; the admin login is not an artist and has no own bookings or hours. */
    private String artistId(Jwt jwt) {
        String artistId = StaffUser.from(jwt).artistId();
        if (artistId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                    "This login is not linked to an artist. Use the admin pages instead.");
        }
        if (!artists.findById(artistId).map(Artist::isActive).orElse(false)) {
            // Deactivated after this token was issued
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "This artist account is no longer active.");
        }
        return artistId;
    }
}
