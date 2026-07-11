package com.openggf.mods;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStockProgressionAnchors {
    @Test
    void exposesOnlySonic2ResultsDrivenActBoundariesInPhaseTwo() {
        assertTrue(StockProgressionAnchors.contains("s2", "cpz2"));
        assertTrue(StockProgressionAnchors.contains("s2", "mtz3"));
        assertFalse(StockProgressionAnchors.contains("s2", "mtz2"));
        assertFalse(StockProgressionAnchors.contains("s2", "scz1"));
        assertFalse(StockProgressionAnchors.contains("s1", "ghz3"));
        assertFalse(StockProgressionAnchors.contains("s3k", "aiz2"));
        assertThrows(UnsupportedOperationException.class,
                () -> StockProgressionAnchors.anchorsFor("s2").add("scz1"));
        assertThrows(IllegalArgumentException.class,
                () -> StockProgressionAnchors.anchorsFor("unknown"));
    }
}
