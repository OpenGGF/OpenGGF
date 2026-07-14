package com.openggf.game.sonic3k;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestFbzActTransitionPlcSelection {
    @Test
    void fbzReloadLoadsPrimary1cOnceAndSuppressesSecondary1d() {
        assertEquals(List.of(0x1C), Sonic3k.selectLevelPlcs(0x1C, 0x1D, true));
        assertEquals(List.of(0x1C, 0x1D), Sonic3k.selectLevelPlcs(0x1C, 0x1D, false));
        assertEquals(List.of(0x1C), Sonic3k.selectLevelPlcs(0x1C, 0x1C, false));
    }
}
