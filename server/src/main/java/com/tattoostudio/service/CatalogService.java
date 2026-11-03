package com.tattoostudio.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.ServiceItem;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.ServiceItemRepository;
import com.tattoostudio.util.Paging;

/** Looks up what customers may book: active services and active artists. */
@Service
public class CatalogService {

    private final ServiceItemRepository services;
    private final ArtistRepository artists;
    private final MongoTemplate mongo;

    public CatalogService(ServiceItemRepository services, ArtistRepository artists, MongoTemplate mongo) {
        this.services = services;
        this.artists = artists;
        this.mongo = mongo;
    }

    static final Map<String, String> SERVICE_SORTS = Map.of("order", "sortOrder", "name", "name", "price", "price");
    static final Map<String, String> ARTIST_SORTS = Map.of("order", "sortOrder", "name", "name");

    /**
     * Services matching the search text (name, description, label) and price range; {@code active}
     * true for the booking page's services, false for hidden ones, null for both (admin).
     */
    public PageDto<ServiceItem> searchServices(String text, Boolean active, BigDecimal minPrice, BigDecimal maxPrice,
            ListQuery list) {
        List<Criteria> filters = Paging.filters();
        if (active != null) {
            filters.add(Criteria.where("active").is(active));
        }
        if (text != null) {
            filters.add(Paging.contains(text, "name", "description", "label"));
        }
        if (minPrice != null) {
            filters.add(Criteria.where("price").gte(minPrice));
        }
        if (maxPrice != null) {
            filters.add(Criteria.where("price").lte(maxPrice));
        }
        return Paging.find(mongo, ServiceItem.class, filters, list, SERVICE_SORTS, "order,asc", page -> page);
    }

    /**
     * Artists matching the search text (name, handle, specialty, bio) who work on {@code day};
     * {@code active} true for the crew on the site, false for deactivated ones, null for both (admin).
     */
    public PageDto<Artist> searchArtists(String text, Boolean active, DayOfWeek day, ListQuery list) {
        List<Criteria> filters = Paging.filters();
        if (active != null) {
            filters.add(Criteria.where("active").is(active));
        }
        if (text != null) {
            filters.add(Paging.contains(text, "name", "handle", "specialty", "bio"));
        }
        if (day != null) {
            filters.add(Criteria.where("weeklyHours.day").is(day));
        }
        return Paging.find(mongo, Artist.class, filters, list, ARTIST_SORTS, "order,asc", page -> page);
    }

    public List<ServiceItem> activeServices() {
        return services.findByActiveTrueOrderBySortOrderAsc();
    }

    public List<Artist> activeArtists() {
        return artists.findByActiveTrueOrderBySortOrderAsc();
    }

    /** @throws ApiException 404 if the service does not exist or is hidden */
    public ServiceItem activeService(String id) {
        return services.findById(id)
                .filter(ServiceItem::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SERVICE_NOT_FOUND, "Service not found"));
    }

    /** @throws ApiException 404 if the artist does not exist or is inactive */
    public Artist activeArtist(String id) {
        return artists.findById(id)
                .filter(Artist::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ARTIST_NOT_FOUND, "Artist not found"));
    }
}
