package com.demo.bwcommon.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Unified page result payload.
 *
 * @param <T> data item type
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    /**
     * Current page number.
     */
    private long pageNum;

    /**
     * Current page size.
     */
    private long pageSize;

    /**
     * Total record count.
     */
    private long total;

    /**
     * Total page count.
     */
    private long totalPages;

    /**
     * Current page records.
     */
    private List<T> list;

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(pageNum, pageSize, 0, 0, Collections.emptyList());
    }
}
