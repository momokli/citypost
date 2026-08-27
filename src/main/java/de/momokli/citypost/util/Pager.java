package de.momokli.citypost.util;

/**
 * Einfache Paginierungs-Mathematik (Seitenanzahl, Seiten-Clamping).
 */
public final class Pager {

    private Pager() {
    }

    public static int pageCount(int itemCount, int pageSize) {
        if (itemCount <= 0 || pageSize <= 0) {
            return 1;
        }
        return (itemCount + pageSize - 1) / pageSize;
    }

    public static int clampPage(int page, int itemCount, int pageSize) {
        int pages = pageCount(itemCount, pageSize);
        if (page < 0) {
            return 0;
        }
        if (page >= pages) {
            return pages - 1;
        }
        return page;
    }
}
