package com.tattoostudio.controller;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tattoostudio.dto.ActiveRequest;
import com.tattoostudio.dto.AdminArtistDto;
import com.tattoostudio.dto.AdminServiceDto;
import com.tattoostudio.dto.ArtistLoginRequest;
import com.tattoostudio.dto.ArtistOptionDto;
import com.tattoostudio.dto.ArtistRequest;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.ServiceRequest;
import com.tattoostudio.service.AdminCatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

/** Admin: services, artists and artist logins. Hide / deactivate with PATCH active: false; DELETE removes for good. */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin catalog", description = "Services, artists and artist logins (admin only)")
@SecurityRequirement(name = "bearer")
public class AdminCatalogController {

    private final AdminCatalogService catalog;

    public AdminCatalogController(AdminCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/services")
    @Operation(summary = "All services, including hidden ones",
            description = "active=true / false (all when left out), price range, search by name, description or label; "
                    + "sort by order, name or price.")
    public PageDto<AdminServiceDto> services(@Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @PositiveOrZero BigDecimal minPrice,
            @RequestParam(required = false) @PositiveOrZero BigDecimal maxPrice) {
        return catalog.searchServices(list.text(), active, minPrice, maxPrice, list);
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a service")
    public AdminServiceDto createService(@Valid @RequestBody ServiceRequest request) {
        return catalog.createService(request);
    }

    @PutMapping("/services/{id}")
    @Operation(summary = "Edit a service", description = "Send active: true to show a hidden service again.")
    public AdminServiceDto updateService(@PathVariable String id, @Valid @RequestBody ServiceRequest request) {
        return catalog.updateService(id, request);
    }

    @PatchMapping("/services/{id}")
    @Operation(summary = "Show or hide a service", description = "Changes only whether it is on the booking page.")
    public AdminServiceDto setServiceActive(@PathVariable String id, @Valid @RequestBody ActiveRequest request) {
        return catalog.setServiceActive(id, request.active());
    }

    @DeleteMapping("/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a service", description = "Refused (409) while it has upcoming bookings; "
            + "to take it off the booking page only, send PATCH with active: false.")
    public void deleteService(@PathVariable String id) {
        catalog.deleteService(id);
    }

    @GetMapping("/artists")
    @Operation(summary = "All artists, including inactive ones, with exceptions and login name",
            description = "active=true / false (all when left out), working day, search by name, handle, specialty or bio; "
                    + "sort by order or name.")
    public PageDto<AdminArtistDto> artists(@Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) DayOfWeek day) {
        return catalog.searchArtists(list.text(), active, day, list);
    }

    @GetMapping("/artists/options")
    @Operation(summary = "Every artist's id, name, active state and login name",
            description = "A light list for pickers (no hours or bookings), sorted by name.")
    public List<ArtistOptionDto> artistOptions() {
        return catalog.artistOptions();
    }

    @PostMapping("/artists")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an artist")
    public AdminArtistDto createArtist(@Valid @RequestBody ArtistRequest request) {
        return catalog.createArtist(request);
    }

    @PutMapping("/artists/{id}")
    @Operation(summary = "Edit an artist", description = "Send active: true to reactivate.")
    public AdminArtistDto updateArtist(@PathVariable String id, @Valid @RequestBody ArtistRequest request) {
        return catalog.updateArtist(id, request);
    }

    @PatchMapping("/artists/{id}")
    @Operation(summary = "Activate or deactivate an artist", description = "Changes only whether they are on the booking "
            + "page (and can sign in); hours stay as they are. Deactivating is refused (409) while they have upcoming bookings.")
    public AdminArtistDto setArtistActive(@PathVariable String id, @Valid @RequestBody ActiveRequest request) {
        return catalog.setArtistActive(id, request.active());
    }

    @DeleteMapping("/artists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an artist and their login", description = "Only for artists without any bookings (409 "
            + "otherwise); to stop new bookings, send PATCH with active: false (refused while they have upcoming bookings).")
    public void deleteArtist(@PathVariable String id) {
        catalog.deleteArtist(id);
    }

    @PostMapping("/artists/{id}/login")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create or reset an artist's login")
    public Map<String, String> setLogin(@PathVariable String id, @Valid @RequestBody ArtistLoginRequest request) {
        return Map.of("username", catalog.setArtistLogin(id, request));
    }
}
