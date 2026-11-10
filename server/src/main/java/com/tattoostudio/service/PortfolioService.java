package com.tattoostudio.service;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tattoostudio.dto.WorkDto;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Image;
import com.tattoostudio.model.Work;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.WorkRepository;
import com.tattoostudio.security.StaffUser;
import com.tattoostudio.storage.ImageUploads;

/** An artist's portfolio photos and the signature piece on their hanging scroll. */
@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    static final int MAX_CAPTION = 60;
    static final int MAX_WORKS = 60;

    private final WorkRepository works;
    private final ArtistRepository artists;
    private final ImageUploads images;
    private final MongoTemplate mongo;
    private final Clock clock;

    public PortfolioService(WorkRepository works, ArtistRepository artists, ImageUploads images, MongoTemplate mongo,
            Clock clock) {
        this.works = works;
        this.artists = artists;
        this.images = images;
        this.mongo = mongo;
        this.clock = clock;
    }

    public WorkDto add(String artistId, MultipartFile file, String caption) {
        String text = caption == null ? "" : caption.trim();
        if (text.length() > MAX_CAPTION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "The caption can be at most " + MAX_CAPTION + " characters");
        }
        if (works.countByArtistId(artistId) >= MAX_WORKS) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                    "The portfolio holds up to " + MAX_WORKS + " photos. Remove one first.");
        }
        Work work = new Work();
        work.setArtistId(artistId);
        work.setCaption(text);
        work.setCreatedAt(clock.instant());
        work.setImage(images.storeOne(file, "works"));
        return WorkDto.from(works.save(work));
    }

    public void delete(String workId, StaffUser user) {
        Work work = works.findById(workId)
                .filter(w -> user.isAdmin() || w.getArtistId().equals(user.artistId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Photo not found"));
        works.delete(work);
        images.delete(work.getImage());
        log.info("{} removed a portfolio photo of artist {}", user.username(), work.getArtistId());
    }

    /** Sets (or with {@code file} null, removes) the signature piece; the old picture is deleted. */
    public Image setSignature(String artistId, MultipartFile file) {
        Artist artist = artists.findById(artistId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ARTIST_NOT_FOUND, "Artist not found"));
        Image stored = file == null ? null : images.storeOne(file, "signatures");
        Update update = stored == null ? new Update().unset("signaturePiece") : new Update().set("signaturePiece", stored);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(artistId)), update, Artist.class);
        images.delete(artist.getSignaturePiece());
        return stored;
    }
}
