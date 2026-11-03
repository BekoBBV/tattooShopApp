package com.tattoostudio.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;

import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;

/**
 * Runs a list query in MongoDB: filters, then sort, then one page (skip + limit), plus the total
 * count for the pager. Every list in the API goes through here, so they all page and sort the
 * same way.
 */
public final class Paging {

    private Paging() {
    }

    /**
     * @param filters    the list's search conditions (all must match); empty means everything
     * @param sortFields allowed sort names (as the client sends them) mapped to document fields
     * @param defaultSort used when the client sends no sort, e.g. "date,desc"
     * @param map        turns one page of documents into DTOs (one call per page, so it can batch lookups)
     */
    public static <T, R> PageDto<R> find(MongoTemplate mongo, Class<T> type, List<Criteria> filters, ListQuery list,
            Map<String, String> sortFields, String defaultSort, Function<List<T>, List<R>> map) {
        Criteria criteria = filters.isEmpty() ? new Criteria() : new Criteria().andOperator(filters);
        Query query = Query.query(criteria);
        long total = mongo.count(query, type);
        int size = list.sizeOrDefault();
        int page = list.pageOrFirst();
        query.with(sort(list.sort() == null ? defaultSort : list.sort(), sortFields))
                .skip((long) page * size)
                .limit(size);
        List<R> items = map.apply(mongo.find(query, type));
        int totalPages = (int) ((total + size - 1) / size);
        return new PageDto<>(items, page, size, total, totalPages);
    }

    /** "price,desc" → price descending; a second key (the id) keeps pages stable when values tie. */
    static Sort sort(String value, Map<String, String> sortFields) {
        String[] parts = value.split(",");
        String field = sortFields.get(parts[0]);
        if (field == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "sort must be one of " + String.join(", ", sortFields.keySet()));
        }
        Sort.Direction direction = parts.length > 1 && parts[1].equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, field).and(Sort.by(direction, "_id"));
    }

    /** Matches when any of the fields contains the text (not case-sensitive, taken literally). */
    public static Criteria contains(String text, String... fields) {
        Pattern pattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
        return new Criteria().orOperator(Arrays.stream(fields).map(f -> Criteria.where(f).regex(pattern)).toList());
    }

    /** A growable list of conditions to pass to {@link #find}. */
    public static List<Criteria> filters() {
        return new ArrayList<>();
    }
}
