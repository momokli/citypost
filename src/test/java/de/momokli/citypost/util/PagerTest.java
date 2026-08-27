package de.momokli.citypost.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagerTest {

    @Test
    void pageCount() {
        assertEquals(1, Pager.pageCount(0, 27));
        assertEquals(1, Pager.pageCount(27, 27));
        assertEquals(2, Pager.pageCount(28, 27));
        assertEquals(4, Pager.pageCount(100, 27));
    }

    @Test
    void clampPage() {
        assertEquals(0, Pager.clampPage(-5, 10, 27));
        assertEquals(0, Pager.clampPage(0, 10, 27));
        assertEquals(0, Pager.clampPage(5, 10, 27)); // nur 1 Seite
        assertEquals(1, Pager.clampPage(1, 54, 27));
        assertEquals(1, Pager.clampPage(99, 54, 27)); // auf letzte Seite geklemmt
    }
}
