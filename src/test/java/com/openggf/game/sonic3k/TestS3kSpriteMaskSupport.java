package com.openggf.game.sonic3k;

import com.openggf.graphics.SpriteSatEntry;
import com.openggf.graphics.SpriteSatMaskPostProcessor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS3kSpriteMaskSupport {
    @Test
    void frame4PreservesTheNativeSatControlPairExactly() {
        // Map_SpriteMask frame 4 size byte $03: one tile wide, four tiles (32 lines) tall.
        List<S3kSpriteMaskSupport.ControlEntry> entries =
                S3kSpriteMaskSupport.frame4Entries(0x2B40, 0x5F0);

        assertEquals(List.of(
                new S3kSpriteMaskSupport.ControlEntry(0x2B48, 0x5E0, 1, 4, 0x7C0),
                new S3kSpriteMaskSupport.ControlEntry(0x2B40, 0x5E0, 1, 4, 0)), entries);
    }

    @Test
    void frame4MasksAllThirtyTwoLinesBelowItsOrigin() {
        // soz_completerun row 56840: the end-boss mask pair sits at y 192 and the
        // boss's lower pieces end on line 203, which native hides.
        List<SpriteSatEntry> sat = new ArrayList<>();
        sat.add(SpriteSatEntry.of(32, 200, 4, 2, 0x7D8, 1, false, false, true, false));
        for (S3kSpriteMaskSupport.ControlEntry entry : S3kSpriteMaskSupport.frame4Entries(0, 208)) {
            sat.add(SpriteSatEntry.of(entry.x(), entry.y(), entry.widthTiles(), entry.heightTiles(),
                    0, entry.rawTileWordLow11(), 0, false, false, false, false));
        }
        SpriteSatEntry boss = SpriteSatEntry.of(212, 172, 4, 4, 0x3DC, 3, false, false, true, false);
        sat.add(boss);

        List<SpriteSatEntry> processed = SpriteSatMaskPostProcessor.process(sat, true);

        SpriteSatEntry clipped = processed.get(processed.size() - 1);
        assertEquals(172, clipped.visibleTopY());
        assertEquals(192, clipped.visibleBottomY());
    }
}
