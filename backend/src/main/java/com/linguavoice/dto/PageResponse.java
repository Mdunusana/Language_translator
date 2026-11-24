package com.linguavoice.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** A page of results. {@code page} is zero-based. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
