package com.openggf.game.sonic3k;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS3kSpriteMaskSupport {
    @Test
    void frame4PreservesTheNativeSatControlPairExactly() {
        List<S3kSpriteMaskSupport.ControlEntry> entries =
                S3kSpriteMaskSupport.frame4Entries(0x2B40, 0x5F0);

        assertEquals(List.of(
                new S3kSpriteMaskSupport.ControlEntry(0x2B48, 0x5E0, 4, 1, 0x7C0),
                new S3kSpriteMaskSupport.ControlEntry(0x2B40, 0x5E0, 4, 1, 0)), entries);
    }
}
