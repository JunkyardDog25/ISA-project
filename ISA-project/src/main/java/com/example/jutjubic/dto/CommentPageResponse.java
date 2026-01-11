package com.example.jutjubic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * DTO za paginiranu listu komentara sa dodatnim metapodacima.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CommentPageResponse {

    // ----- Content -----
    private List<CommentDto> content;

    // ----- Pagination Metadata -----
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;

    /**
     * Kreira CommentPageResponse od liste CommentDto i Page metapodataka.
     */
    public static CommentPageResponse from(List<CommentDto> comments, Page<?> page) {
        return new CommentPageResponse(
            comments,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast(),
            page.isEmpty()
        );
    }
}

