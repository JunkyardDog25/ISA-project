package com.example.jutjubic.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic paginated response wrapper with metadata.
 * @param <T> The type of content in the page
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    // ----- Content -----
    private List<T> content;

    // ----- Pagination Metadata -----
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;

    /**
     * Create a PageResponse from Spring's Page object.
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast(),
            page.isEmpty()
        );
    }

    /**
     * Create an empty PageResponse (useful for error cases where you still want a consistent payload shape).
     */
    public static <T> PageResponse<T> empty() {
        return new PageResponse<>(
                List.of(),
                0,
                0,
                0L,
                0,
                true,
                true,
                true
        );
    }
}
