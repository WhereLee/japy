package com.japy.common;

/**
 * 分页参数规整：防止 size 超限导致全表扫描（经典性能坑）。
 */
public final class PageParams {

    public static final int MAX_SIZE = 100;

    private PageParams() {}

    public static int page(int page) {
        return Math.max(page, 1);
    }

    public static int size(int size) {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }
}
