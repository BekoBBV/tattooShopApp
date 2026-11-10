package com.tattoostudio.service;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tattoostudio.dto.AdminFlashDto;
import com.tattoostudio.dto.FlashRequest;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.FlashDesign;
import com.tattoostudio.model.Image;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.FlashRepository;
import com.tattoostudio.security.StaffUser;
import com.tattoostudio.storage.ImageUploads;

/**
 * Artists manage their own flash designs; the admin manages everyone's. Bookings keep their own copy
 * of a design's name, size and price, so editing or deleting a design never changes a booking.
 */
@Service
public class FlashService {

    private static final Logger log = LoggerFactory.getLogger(FlashService.class);

    private final FlashRepository flash;
    private final ArtistRepository artists;
    private final CatalogService catalog;
    private final ImageUploads images;
    private final MongoTemplate mongo;
    private final Clock clock;

    public FlashService(FlashRepository flash, ArtistRepository artists, CatalogService catalog, ImageUploads images,
            MongoTemplate mongo, Clock clock) {
        this.flash = flash;
        this.artists = artists;
        this.catalog = catalog;
        this.images = images;
        this.mongo = mongo;
        this.clock = clock;
    }

    /** Adds a design for {@code artistId}, with its drawing if one is given. */
    public AdminFlashDto create(String artistId, FlashRequest request, MultipartFile image) {
        if (artistId == null || artists.findById(artistId).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.ARTIST_NOT_FOUND, "Please choose the artist");
        }
        FlashDesign design = new FlashDesign();
        design.setArtistId(artistId);
        design.setSortOrder(flash.findByArtistIdOrderBySortOrderAsc(artistId).stream()
                .mapToInt(FlashDesign::getSortOrder).max().orElse(0) + 1);
        design.setCreatedAt(clock.instant());
        apply(design, request);
        if (image != null && !image.isEmpty()) {
            design.setImage(images.storeOne(image, "flash"));
        }
        FlashDesign saved = flash.save(design);
        log.info("Added flash design {} for artist {}", saved.getName(), artistId);
        return dto(saved);
    }

    public AdminFlashDto update(String id, FlashRequest request, StaffUser user) {
        FlashDesign design = owned(id, user);
        apply(design, request);
        return dto(flash.save(design));
    }

    /** The "Available" switch; only that field is written. */
    public AdminFlashDto setAvailable(String id, boolean available, StaffUser user) {
        owned(id, user);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("available", available), FlashDesign.class);
        return dto(owned(id, user));
    }

    /** Replaces the drawing (the old one is deleted). */
    public AdminFlashDto replaceImage(String id, MultipartFile file, StaffUser user) {
        FlashDesign design = owned(id, user);
        Image old = design.getImage();
        Image stored = images.storeOne(file, "flash");
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("image", stored), FlashDesign.class);
        images.delete(old);
        return dto(owned(id, user));
    }

    /** Deletes a design for good. Refused while someone holds or has booked it. */
    public void delete(String id, StaffUser user) {
        FlashDesign design = owned(id, user);
        Booking claim = catalog.claims(List.of(id)).get(id);
        if (claim != null && claim.getStatus() != BookingStatus.DONE) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, design.getName() + " is booked ("
                    + claim.getCode() + "). Cancel that booking first, or switch the design off.");
        }
        flash.delete(design);
        images.delete(design.getImage());
        log.info("{} deleted flash design {}", user.username(), design.getName());
    }

    /** The design, if it belongs to the signed-in artist (the admin may change any). */
    private FlashDesign owned(String id, StaffUser user) {
        FlashDesign design = catalog.flash(id);
        if (!user.isAdmin() && !design.getArtistId().equals(user.artistId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.FLASH_NOT_FOUND, "This flash design doesn't exist (any more)");
        }
        return design;
    }

    private static void apply(FlashDesign design, FlashRequest r) {
        if (r.durationMinutes() % 30 != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "The time must be a multiple of 30 minutes, e.g. 1.5 hours");
        }
        design.setName(r.name().trim());
        design.setWidthCm(r.widthCm());
        design.setHeightCm(r.heightCm());
        design.setDurationMinutes(r.durationMinutes());
        design.setPrice(r.price());
        if (r.available() != null) {
            design.setAvailable(r.available());
        }
    }

    private AdminFlashDto dto(FlashDesign design) {
        return catalog.adminDtos(List.of(design)).get(0);
    }
}
