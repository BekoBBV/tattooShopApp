package com.tattoostudio.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.model.Message;
import com.tattoostudio.model.Review;
import com.tattoostudio.util.Paging;

/** Searching reviews (public site and admin) and contact messages (admin). */
@Service
public class InboxService {

    static final Map<String, String> REVIEW_SORTS = Map.of("date", "createdAt", "rating", "rating", "name", "name");
    static final Map<String, String> MESSAGE_SORTS = Map.of("date", "createdAt", "name", "name");

    private final MongoTemplate mongo;

    public InboxService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * @param approved  true: shown on the site, false: waiting for approval, null: both
     * @param minRating only reviews with at least this many stars (null: any)
     */
    public PageDto<Review> searchReviews(String text, Boolean approved, Integer minRating, ListQuery list) {
        List<Criteria> filters = Paging.filters();
        if (approved != null) {
            filters.add(Criteria.where("approved").is(approved));
        }
        if (text != null) {
            filters.add(Paging.contains(text, "name", "text"));
        }
        if (minRating != null) {
            filters.add(Criteria.where("rating").gte(minRating));
        }
        return Paging.find(mongo, Review.class, filters, list, REVIEW_SORTS, "date,desc", page -> page);
    }

    /** @param read true: read, false: unread, null: both */
    public PageDto<Message> searchMessages(String text, Boolean read, ListQuery list) {
        List<Criteria> filters = Paging.filters();
        if (read != null) {
            filters.add(Criteria.where("read").is(read));
        }
        if (text != null) {
            filters.add(Paging.contains(text, "name", "contact", "text"));
        }
        return Paging.find(mongo, Message.class, filters, list, MESSAGE_SORTS, "date,desc", page -> page);
    }
}
