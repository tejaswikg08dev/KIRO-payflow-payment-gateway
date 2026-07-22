package com.payflow.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Standard paginated response for list endpoints.
 * 
 * Example:
 * GET /v1/payments?page=1&per_page=20
 * 
 * Response:
 * {
 *   "success": true,
 *   "data": [ {...}, {...} ],
 *   "pagination": { "total": 150, "page": 1, "per_page": 20, "total_pages": 8 },
 *   "timestamp": "2026-07-19T14:30:00Z"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedResponse<T> {

    private boolean success;
    private List<T> data;
    private PaginationInfo pagination;
    private Instant timestamp;

    public static <T> PagedResponse<T> of(List<T> data, long total, int page, int perPage) {
        int totalPages = (int) Math.ceil((double) total / perPage);
        return PagedResponse.<T>builder()
                .success(true)
                .data(data)
                .pagination(new PaginationInfo(total, page, perPage, totalPages))
                .timestamp(Instant.now())
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationInfo {
        private long total;
        private int page;
        private int perPage;
        private int totalPages;
    }
}
