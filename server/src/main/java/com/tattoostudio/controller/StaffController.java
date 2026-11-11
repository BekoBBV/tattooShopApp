package com.tattoostudio.controller;

import java.time.LocalDate;
import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tattoostudio.dto.ActiveRequest;
import com.tattoostudio.dto.AdminFlashDto;
import com.tattoostudio.dto.CustomerOptionDto;
import com.tattoostudio.dto.FlashRequest;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.ReserveSessionRequest;
import com.tattoostudio.dto.ScheduleDto;
import com.tattoostudio.dto.ScheduleResponse;
import com.tattoostudio.dto.StaffBookingDto;
import com.tattoostudio.dto.StatusUpdateRequest;
import com.tattoostudio.dto.WorkDto;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.BookingType;
import com.tattoostudio.model.Image;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.security.StaffUser;
import com.tattoostudio.service.CatalogService;
import com.tattoostudio.service.FlashService;
import com.tattoostudio.service.PortfolioService;
import com.tattoostudio.service.ScheduleService;
import com.tattoostudio.service.StaffBookingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** The artist area. Everything here is scoped to the signed-in artist. */
@RestController
@RequestMapping("/api/staff")
@Tag(name = "Artist", description = "The signed-in artist's calendar, sessions, hours, flash designs and portfolio")
@SecurityRequirement(name = "bearer")
public class StaffController {

    private final StaffBookingService staffBookings;
    private final ScheduleService schedules;
    private final ArtistRepository artists;
    private final CatalogService catalog;
    private final FlashService flash;
    private final PortfolioService portfolio;

    public StaffController(StaffBookingService staffBookings, ScheduleService schedules, ArtistRepository artists,
            CatalogService catalog, FlashService flash, PortfolioService portfolio) {
        this.staffBookings = staffBookings;
        this.schedules = schedules;
        this.artists = artists;
        this.catalog = catalog;
        this.flash = flash;
        this.portfolio = portfolio;
    }

    // ---- Calendar ----

    @GetMapping("/bookings")
    @Operation(summary = "My bookings (the calendar)", description = "Dates are inclusive. Default: the last 7 days and the "
            + "next 30. Search by customer, phone, email, code, title, idea or notes; sort by date, title, type, customer, "
            + "price or status.")
    public PageDto<StaffBookingDto> bookings(@AuthenticationPrincipal Jwt jwt, @Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) BookingType type) {
        return staffBookings.list(new StaffBookingService.Filter(artistId(jwt), from, to, status, type, null, null), list);
    }

    @PatchMapping("/bookings/{id}")
    @Operation(summary = "Mark one of my bookings Done, No-show or Cancelled",
            description = "Cancelling refunds a paid deposit (the studio cancelled).")
    public StaffBookingDto updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @Valid @RequestBody StatusUpdateRequest request) {
        artistId(jwt); // the admin has no own bookings and uses /api/admin/bookings
        return staffBookings.updateStatus(id, request.status(), StaffUser.from(jwt));
    }

    @GetMapping("/customers")
    @Operation(summary = "Customers I can reserve a session for",
            description = "People from my consultations and sessions, latest booking each; search by name, phone or email.")
    public List<CustomerOptionDto> customers(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Size(max = 100) String q) {
        return staffBookings.customers(artistId(jwt), q == null || q.isBlank() ? null : q.trim());
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reserve time for a custom piece",
            description = "The time is removed from online booking and the customer sees the session in their bookings "
                    + "(and gets an email to pay the deposit, if there is one).")
    public StaffBookingDto reserveSession(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReserveSessionRequest request) {
        return staffBookings.reserveSession(artistId(jwt), request);
    }

    // ---- Hours ----

    @GetMapping("/schedule")
    @Operation(summary = "My weekly hours, upcoming days off and break after each client",
            description = "conflicts lists upcoming customer bookings outside these hours.")
    public ScheduleResponse schedule(@AuthenticationPrincipal Jwt jwt) {
        return schedules.get(artistId(jwt));
    }

    @PutMapping("/schedule")
    @Operation(summary = "Replace my weekly hours, days off and break")
    public ScheduleResponse updateSchedule(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ScheduleDto schedule) {
        return schedules.update(artistId(jwt), schedule);
    }

    // ---- Flash designs ----

    @GetMapping("/flash")
    @Operation(summary = "My flash designs", description = "With their state: on the sheet, hidden, held, booked or tattooed.")
    public PageDto<AdminFlashDto> myFlash(@AuthenticationPrincipal Jwt jwt, @Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) Boolean available) {
        return catalog.searchFlash(artistId(jwt), available, list.text(), list);
    }

    @PostMapping(value = "/flash", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a flash design", description = "A multipart form: the part \"flash\" is the JSON request, "
            + "\"image\" the drawing (optional, JPG, PNG, HEIC or WebP, 10 MB).")
    public AdminFlashDto addFlash(@AuthenticationPrincipal Jwt jwt, @Valid @RequestPart("flash") FlashRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return flash.create(artistId(jwt), request, image);
    }

    @PutMapping("/flash/{id}")
    @Operation(summary = "Edit one of my flash designs", description = "Bookings keep the name, size and price they were booked with.")
    public AdminFlashDto editFlash(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @Valid @RequestBody FlashRequest request) {
        artistId(jwt);
        return flash.update(id, request, StaffUser.from(jwt));
    }

    @PatchMapping("/flash/{id}")
    @Operation(summary = "Switch one of my flash designs on or off the sheet", description = "{ active } is the Available switch.")
    public AdminFlashDto setFlashAvailable(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @Valid @RequestBody ActiveRequest request) {
        artistId(jwt);
        return flash.setAvailable(id, request.active(), StaffUser.from(jwt));
    }

    @PutMapping(value = "/flash/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Replace the drawing of one of my flash designs")
    public AdminFlashDto replaceFlashImage(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestPart("image") MultipartFile image) {
        artistId(jwt);
        return flash.replaceImage(id, image, StaffUser.from(jwt));
    }

    @DeleteMapping("/flash/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one of my flash designs", description = "Refused while it is held or booked.")
    public void deleteFlash(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        artistId(jwt);
        flash.delete(id, StaffUser.from(jwt));
    }

    // ---- Portfolio ----

    @GetMapping("/works")
    @Operation(summary = "My portfolio, newest first")
    public List<WorkDto> works(@AuthenticationPrincipal Jwt jwt) {
        return catalog.works(artistId(jwt));
    }

    @PostMapping(value = "/works", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a photo to my portfolio", description = "Parts: \"image\" and an optional \"caption\" (max 60).")
    public WorkDto addWork(@AuthenticationPrincipal Jwt jwt, @RequestPart("image") MultipartFile image,
            @RequestPart(value = "caption", required = false) String caption) {
        return portfolio.add(artistId(jwt), image, caption);
    }

    @DeleteMapping("/works/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a photo from my portfolio")
    public void deleteWork(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        artistId(jwt);
        portfolio.delete(id, StaffUser.from(jwt));
    }

    @PutMapping(value = "/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Set the signature piece on my hanging scroll")
    public Image setSignature(@AuthenticationPrincipal Jwt jwt, @RequestPart("image") MultipartFile image) {
        return portfolio.setSignature(artistId(jwt), image);
    }

    @DeleteMapping("/signature")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Take the signature piece off my hanging scroll")
    public void removeSignature(@AuthenticationPrincipal Jwt jwt) {
        portfolio.setSignature(artistId(jwt), null);
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
