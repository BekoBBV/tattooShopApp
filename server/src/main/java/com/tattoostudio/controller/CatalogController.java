package com.tattoostudio.controller;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tattoostudio.dto.ArtistDto;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.ServiceDto;
import com.tattoostudio.service.AvailabilityService;
import com.tattoostudio.service.CatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

/** Public, read-only info about what the shop offers and who works there. */
@RestController
@RequestMapping("/api")
@Tag(name = "Catalog", description = "Services and artists (public)")
public class CatalogController {

    private final CatalogService catalog;
    private final AvailabilityService availability;

    public CatalogController(CatalogService catalog, AvailabilityService availability) {
        this.catalog = catalog;
        this.availability = availability;
    }

    @GetMapping("/services")
    @Operation(summary = "List active services")
    public List<ServiceDto> listServices() {
        return catalog.activeServices().stream().map(ServiceDto::from).toList();
    }

    @GetMapping("/services/search")
    @Operation(summary = "Search services (signed in)",
            description = "Paging, sorting (order, name, price) and search by text and price range. Signed-in users only; "
                    + "guests see the plain list above.")
    @SecurityRequirement(name = "bearer")
    public PageDto<ServiceDto> searchServices(@Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) @PositiveOrZero BigDecimal minPrice,
            @RequestParam(required = false) @PositiveOrZero BigDecimal maxPrice) {
        return catalog.searchServices(list.text(), true, minPrice, maxPrice, list)
                .map(page -> page.stream().map(ServiceDto::from).toList());
    }

    @GetMapping("/services/{id}")
    @Operation(summary = "Get one active service")
    public ServiceDto getService(@PathVariable String id) {
        return ServiceDto.from(catalog.activeService(id));
    }

    @GetMapping("/artists")
    @Operation(summary = "List active artists with their weekly hours and upcoming days off")
    public List<ArtistDto> listArtists() {
        return catalog.activeArtists().stream().map(b -> ArtistDto.from(b, availability.today())).toList();
    }

    @GetMapping("/artists/search")
    @Operation(summary = "Search artists (signed in)",
            description = "Paging, sorting (order, name) and search by text and working day (e.g. day=SATURDAY).")
    @SecurityRequirement(name = "bearer")
    public PageDto<ArtistDto> searchArtists(@Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) DayOfWeek day) {
        LocalDate today = availability.today();
        return catalog.searchArtists(list.text(), true, day, list)
                .map(page -> page.stream().map(b -> ArtistDto.from(b, today)).toList());
    }

    @GetMapping("/artists/{id}")
    @Operation(summary = "Get one active artist")
    public ArtistDto getArtist(@PathVariable String id) {
        return ArtistDto.from(catalog.activeArtist(id), availability.today());
    }
}
