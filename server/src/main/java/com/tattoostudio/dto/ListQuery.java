package com.tattoostudio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Paging, sorting and free-text search for every list, from the query string:
 * {@code ?page=0&size=10&sort=price,desc&q=fade}. Each list adds its own filters next to these.
 */
public record ListQuery(
        @Min(0) @Schema(description = "Page number, starting at 0", example = "0") Integer page,
        @Min(1) @Max(500) @Schema(description = "Items per page (1-500)", example = "10") Integer size,
        @Pattern(regexp = "[A-Za-z]{1,30}(,(asc|desc))?", message = "use field or field,asc / field,desc")
        @Schema(description = "Sort field and direction, e.g. price,desc", example = "name,asc") String sort,
        @Size(max = 100) @Schema(description = "Search text") String q) {

    public static final int DEFAULT_SIZE = 10;

    public int pageOrFirst() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }

    /** The search text, or null when empty. */
    public String text() {
        return q == null || q.isBlank() ? null : q.trim();
    }
}
