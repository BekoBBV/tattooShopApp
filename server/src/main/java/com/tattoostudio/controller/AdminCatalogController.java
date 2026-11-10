package com.tattoostudio.controller;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

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
import com.tattoostudio.dto.AdminArtistDto;
import com.tattoostudio.dto.AdminFlashDto;
import com.tattoostudio.dto.ArtistLoginRequest;
import com.tattoostudio.dto.ArtistOptionDto;
import com.tattoostudio.dto.ArtistRequest;
import com.tattoostudio.dto.FlashRequest;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.model.Image;
import com.tattoostudio.security.StaffUser;
import com.tattoostudio.service.AdminCatalogService;
import com.tattoostudio.service.CatalogService;
import com.tattoostudio.service.FlashService;
import com.tattoostudio.service.PortfolioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Admin: the artists (profiles, hours, logins) and every artist's flash designs. */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin: studio", description = "Artists and the flash catalogue (admin only)")
@SecurityRequirement(name = "bearer")
public class AdminCatalogController {

    private final AdminCatalogService admin;
    private final CatalogService catalog;
    private final FlashService flash;
    private final PortfolioService portfolio;

    public AdminCatalogController(AdminCatalogService admin, CatalogService catalog, FlashService flash,
            PortfolioService portfolio) {
        this.admin = admin;
        this.catalog = catalog;
        this.flash = flash;
        this.portfolio = portfolio;
    }

    // ---- Flash catalogue ----

    @GetMapping("/flash")
    @Operation(summary = "Every artist's flash designs", description = "Filter by artistId and available (the switch), "
            + "search by name; sort by order, name, price, time or created. state: on the sheet, hidden, held, booked or tattooed.")
    public PageDto<AdminFlashDto> flash(@Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) String artistId,
            @RequestParam(required = false) Boolean available) {
        return catalog.searchFlash(artistId == null || artistId.isBlank() ? null : artistId, available, list.text(), list);
    }

    @PostMapping(value = "/flash", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a flash design for an artist", description = "Parts: \"flash\" (JSON with artistId) and an "
            + "optional \"image\".")
    public AdminFlashDto addFlash(@Valid @RequestPart("flash") FlashRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return flash.create(request.artistId(), request, image);
    }

    @PutMapping("/flash/{id}")
    @Operation(summary = "Edit a flash design")
    public AdminFlashDto editFlash(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @Valid @RequestBody FlashRequest request) {
        return flash.update(id, request, StaffUser.from(jwt));
    }

    @PatchMapping("/flash/{id}")
    @Operation(summary = "Switch a flash design on or off the sheet", description = "{ active } is the Available switch.")
    public AdminFlashDto setFlashAvailable(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @Valid @RequestBody ActiveRequest request) {
        return flash.setAvailable(id, request.active(), StaffUser.from(jwt));
    }

    @PutMapping(value = "/flash/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Replace a flash design's drawing")
    public AdminFlashDto replaceFlashImage(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestPart("image") MultipartFile image) {
        return flash.replaceImage(id, image, StaffUser.from(jwt));
    }

    @DeleteMapping("/flash/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a flash design", description = "Refused while it is held or booked.")
    public void deleteFlash(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        flash.delete(id, StaffUser.from(jwt));
    }

    // ---- Artists ----

    @GetMapping("/artists")
    @Operation(summary = "All artists, including inactive ones, with exceptions and login name",
            description = "active=true / false (all when left out), working day, search by name, style or bio; "
                    + "sort by order or name.")
    public PageDto<AdminArtistDto> artists(@Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) DayOfWeek day) {
        return admin.searchArtists(list.text(), active, day, list);
    }

    @GetMapping("/artists/options")
    @Operation(summary = "Every artist's id, name, active state and login name",
            description = "A light list for pickers (no hours or bookings), sorted by name.")
    public List<ArtistOptionDto> artistOptions() {
        return admin.artistOptions();
    }

    @PostMapping("/artists")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an artist")
    public AdminArtistDto createArtist(@Valid @RequestBody ArtistRequest request) {
        return admin.createArtist(request);
    }

    @PutMapping("/artists/{id}")
    @Operation(summary = "Edit an artist", description = "Send active: true to reactivate.")
    public AdminArtistDto updateArtist(@PathVariable String id, @Valid @RequestBody ArtistRequest request) {
        return admin.updateArtist(id, request);
    }

    @PatchMapping("/artists/{id}")
    @Operation(summary = "Activate or deactivate an artist", description = "Changes only whether they are on the site "
            + "(and can sign in); hours stay as they are. Deactivating is refused (409) while they have upcoming bookings.")
    public AdminArtistDto setArtistActive(@PathVariable String id, @Valid @RequestBody ActiveRequest request) {
        return admin.setArtistActive(id, request.active());
    }

    @PutMapping(value = "/artists/{id}/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Set an artist's signature piece (the picture on their hanging scroll)")
    public Image setSignature(@PathVariable String id, @RequestPart("image") MultipartFile image) {
        return portfolio.setSignature(id, image);
    }

    @DeleteMapping("/artists/{id}/signature")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an artist's signature piece")
    public void removeSignature(@PathVariable String id) {
        portfolio.setSignature(id, null);
    }

    @DeleteMapping("/artists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an artist with their login, flash designs and portfolio", description = "Only for artists "
            + "without any bookings (409 otherwise); to stop new bookings, send PATCH with active: false.")
    public void deleteArtist(@PathVariable String id) {
        admin.deleteArtist(id);
    }

    @PostMapping("/artists/{id}/login")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create or reset an artist's login")
    public Map<String, String> setLogin(@PathVariable String id, @Valid @RequestBody ArtistLoginRequest request) {
        return Map.of("username", admin.setArtistLogin(id, request));
    }
}
