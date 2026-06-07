package com.milestoneflow.shared.api;

/**
 * Pagination metadata for list API responses.
 *
 * <p>Encapsulates validation and calculation rules for pagination state.
 * Used by {@link ApiResponse} factory methods to construct paginated envelopes.
 *
 * <p>Page numbering is zero-based ({@code page = 0} is the first page).
 *
 * @param page          current page number (zero-based, >= 0)
 * @param size          page size (> 0)
 * @param totalElements total number of elements across all pages (>= 0)
 * @param totalPages    total number of pages (>= 0)
 * @param hasNext       whether a subsequent page exists
 */
public record PageMeta(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    /**
     * Compact constructor with validation.
     */
    public PageMeta {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0, got: " + page);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0, got: " + size);
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be >= 0, got: " + totalElements);
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("totalPages must be >= 0, got: " + totalPages);
        }
    }

    /**
     * Create a {@code PageMeta} from raw pagination data, computing
     * {@code totalPages} and {@code hasNext} automatically.
     *
     * @param page          current page (zero-based)
     * @param size          page size
     * @param totalElements total element count
     * @return validated PageMeta with computed fields
     */
    public static PageMeta of(int page, int size, long totalElements) {
        int totalPages = (size > 0) ? (int) Math.ceilDiv(totalElements, size) : 0;
        boolean hasNext = page + 1 < totalPages;
        return new PageMeta(page, size, totalElements, totalPages, hasNext);
    }
}
