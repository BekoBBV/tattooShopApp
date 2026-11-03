package com.tattoostudio.dto;

import java.util.List;
import java.util.function.Function;

/** One page of a list: the items plus what the pager needs (pages count from 0). */
public record PageDto<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public <R> PageDto<R> map(Function<List<T>, List<R>> mapper) {
        return new PageDto<>(mapper.apply(items), page, size, totalItems, totalPages);
    }
}
