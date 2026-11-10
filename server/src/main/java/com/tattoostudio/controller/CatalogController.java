package com.tattoostudio.controller;

import java.time.DayOfWeek;
import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tattoostudio.dto.ArtistDto;
import com.tattoostudio.dto.FlashDto;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.WorkDto;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.FlashDesign;
import com.tattoostudio.service.CatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** The public studio: artists, their work and the flash sheet. */
@RestController
@RequestMapping("/api")
@Tag(name = "Studio", description = "Artists, portfolios and flash designs (public)")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/artists")
    @Operation(summary = "The artists, in studio order, with their ratings")
    public List<ArtistDto> listArtists() {
        return catalog.artistDtos(catalog.activeArtists());
    }

    @GetMapping("/artists/search")
    @Operation(summary = "Search, sort and page the artists (signed in)",
            description = "Search by name, style or bio; sort by order or name; day = works that weekday.")
    @SecurityRequirement(name = "bearer")
    public PageDto<ArtistDto> searchArtists(@Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) DayOfWeek day) {
        return catalog.searchArtists(list.text(), true, day, list).map(catalog::artistDtos);
    }

    @GetMapping("/artists/{slugOrId}")
    @Operation(summary = "One artist by page name (e.g. kai) or id")
    public ArtistDto getArtist(@PathVariable String slugOrId) {
        return catalog.artistDto(catalog.activeArtistBySlugOrId(slugOrId));
    }

    @GetMapping("/artists/{slugOrId}/works")
    @Operation(summary = "An artist's portfolio, newest first")
    public List<WorkDto> works(@PathVariable String slugOrId) {
        Artist artist = catalog.activeArtistBySlugOrId(slugOrId);
        return catalog.works(artist.getId());
    }

    @GetMapping("/flash")
    @Operation(summary = "The flash sheet: designs that can be booked now",
            description = "Every design is tattooed once: booked designs come off the sheet. Filter by artist and size "
                    + "(SMALL up to 8 cm on the longer side, MEDIUM up to 12 cm, LARGE more).")
    public List<FlashDto> sheet(@Parameter(description = "Artist id") @RequestParam(required = false) String artistId,
            @RequestParam(required = false) FlashDesign.Size size) {
        return catalog.sheet(artistId, size);
    }

    @GetMapping("/flash/{id}")
    @Operation(summary = "One flash design", description = "bookable is false once it is booked or off the sheet.")
    public FlashDto flash(@PathVariable String id) {
        return catalog.flashDto(id);
    }
}
