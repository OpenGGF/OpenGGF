package com.openggf.game.sonic3k;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.game.sonic3k.scroll.SwScrlFbz;
import com.openggf.level.scroll.M68KMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class TestFbzScrollHandler {
    @Test
    void indoorScatterUsesAllRomSpeedGroupsAndExactVerticalRatio() {
        Fixture f = fixture(0);
        int[] scroll = new int[224];

        f.handler.update(scroll, 0x1000, 0x0200, 0, 0);

        assertEquals(0x00F8, f.handler.getVscrollFactorBG() & 0xFFFF);
        assertEquals(-0x0100, M68KMath.unpackBG(scroll[0]));
        assertEquals(-0x0700, M68KMath.unpackBG(scroll[8]));
        assertEquals(-0x1000, M68KMath.unpackFG(scroll[0]));
    }

    @Test
    void outdoorDeformUsesBobAndReadThenIncrementE00Drift() {
        Fixture f = fixture(0);
        f.events.setBackgroundOutdoor(true);
        f.events.setOutdoorBobOffset(5);
        int[] first = new int[224];
        int[] later = new int[224];

        f.handler.update(first, 0x1000, 0x0200, 0, 0);
        for (int frame = 1; frame < 256; frame++) {
            f.handler.update(new int[224], 0x1000, 0x0200, frame, 0);
        }
        f.handler.update(later, 0x1000, 0x0200, 256, 0);

        assertEquals(0x001B, f.handler.getVscrollFactorBG() & 0xFFFF);
        assertEquals(-0x0480, M68KMath.unpackBG(first[0]));
        assertEquals(-0x04F0, M68KMath.unpackBG(later[0]));
    }

    @Test
    void oneRomAccumulatorFreezesIndoorsAndPreservesPhaseAcrossOutdoorAndBossModes() {
        Fixture f = fixture(1);
        int[] scroll = new int[224];

        f.events.setBackgroundOutdoor(true);
        f.handler.update(scroll, 0x1000, 0x0200, 10, 1);
        assertEquals(0x00000E00, f.state.hScrollAccumulator());

        f.events.setBackgroundOutdoor(false);
        f.handler.update(scroll, 0x1000, 0x0200, 11, 1);
        assertEquals(0x00000E00, f.state.hScrollAccumulator(), "indoor deform must freeze HScroll+$1FC");

        for (int ordinaryStage : new int[]{4, 8, 0x0C}) {
            f.events.setBossBackgroundState(ordinaryStage, 0, 0);
            f.handler.update(scroll, 0x2800, 0x0400, ordinaryStage + 8, 1);
            assertEquals(0x00000E00, f.state.hScrollAccumulator(),
                    "ordinary stage $" + Integer.toHexString(ordinaryStage)
                            + " must not run FBZ2_CloudDeform");
        }

        f.events.setBossBackgroundState(0x10, 0, 0);
        f.handler.update(scroll, 0x2800, 0x0400, 24, 1);
        assertEquals(0x00008E00, f.state.hScrollAccumulator(), "boss mode resumes the same accumulator with +$8000");

        f.events.setBossBackgroundState(0, 0, 0);
        f.events.setBackgroundOutdoor(true);
        f.handler.update(scroll, 0x1000, 0x0200, 25, 1);
        assertEquals(0x00009C00, f.state.hScrollAccumulator(), "outdoor mode resumes the boss-advanced phase with +$E00");
    }

    @Test
    void providerReloadCreatesFreshFbzAccumulatorOwner() throws Exception {
        var providerA = new com.openggf.game.sonic3k.scroll.Sonic3kScrollHandlerProvider();
        var providerB = new com.openggf.game.sonic3k.scroll.Sonic3kScrollHandlerProvider();
        var rom = new com.openggf.data.Rom();
        providerA.load(rom);
        providerB.load(rom);
        assertNotSame(providerA.getHandler(4), providerB.getHandler(4));
    }

    private static Fixture fixture(int act) {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(act);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(act, PlayerCharacter.SONIC_AND_TAILS, events);
        SwScrlFbz handler = new SwScrlFbz(() -> state);
        handler.init(act, 0, 0);
        return new Fixture(events, state, handler);
    }

    private record Fixture(Sonic3kFBZEvents events, FbzZoneRuntimeState state, SwScrlFbz handler) {}
}
