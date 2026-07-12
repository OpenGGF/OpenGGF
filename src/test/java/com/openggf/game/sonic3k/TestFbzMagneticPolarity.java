package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.game.PlayerCharacter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TestFbzMagneticPolarity {
    @Test void anPalFbzTogglesOnlyAtThe256FrameLowByteEdge() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(0);

        FbzZoneRuntimeState state = state(events);
        Sonic3kPaletteCycler.dispatchFbzMagneticPhase(state, 1);
        assertEquals(Sonic3kFBZEvents.MagneticPolarity.NEUTRAL, events.getMagneticPolarity());
        assertEquals(1, events.getMagneticTimerPhase());

        Sonic3kPaletteCycler.dispatchFbzMagneticPhase(state, 0x100);
        assertEquals(Sonic3kFBZEvents.MagneticPolarity.ATTRACT, events.getMagneticPolarity());
        assertEquals(0, events.getMagneticTimerPhase());

        Sonic3kPaletteCycler.dispatchFbzMagneticPhase(state, 0x200);
        assertEquals(Sonic3kFBZEvents.MagneticPolarity.REPEL, events.getMagneticPolarity());
        assertEquals(0, events.getMagneticTimerPhase());
    }

    @Test void repeatedDispatchAtOneLowByteEdgeIsIdempotent() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(0);

        FbzZoneRuntimeState state = state(events);
        Sonic3kPaletteCycler.dispatchFbzMagneticPhase(state, 0x100);
        Sonic3kPaletteCycler.dispatchFbzMagneticPhase(state, 0x100);

        assertEquals(Sonic3kFBZEvents.MagneticPolarity.ATTRACT, events.getMagneticPolarity());
        assertEquals(0, events.getMagneticTimerPhase());
    }

    @Test void distinctLowByteEdgesRemainDistinctAcrossFrameCounterWrap() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(0);

        FbzZoneRuntimeState state = state(events);
        Sonic3kPaletteCycler.dispatchFbzMagneticPhase(state, 0xFFFF_FF00);
        Sonic3kPaletteCycler.dispatchFbzMagneticPhase(state, 0xFFFF_FF00);
        assertEquals(Sonic3kFBZEvents.MagneticPolarity.ATTRACT, events.getMagneticPolarity());

        Sonic3kPaletteCycler.dispatchFbzMagneticPhase(state, 0x0000_0000);
        Sonic3kPaletteCycler.dispatchFbzMagneticPhase(state, 0x0000_0000);
        assertEquals(Sonic3kFBZEvents.MagneticPolarity.REPEL, events.getMagneticPolarity());
    }

    @Test void eventPaletteFoundationOwnsLineFourColorsTwoThroughNineAndBossColorOne() {
        assertEquals(3, FbzPaletteFoundation.PALETTE_LINE_INDEX);
        assertEquals(2, FbzPaletteFoundation.BACKGROUND_FIRST_COLOR);
        assertEquals(8, FbzPaletteFoundation.BACKGROUND_COLOR_COUNT);
        assertEquals(1, FbzPaletteFoundation.BOSS_COLOR_INDEX);
        assertEquals(S3kPaletteOwners.FBZ_EVENT_PALETTE, FbzPaletteFoundation.OWNER);
        assertNotEquals(S3kPaletteOwners.ZONE_EVENT_PALETTE_LOAD, FbzPaletteFoundation.OWNER);
    }

    private static FbzZoneRuntimeState state(Sonic3kFBZEvents events) {
        return new FbzZoneRuntimeState(0, PlayerCharacter.SONIC_AND_TAILS, events);
    }
}
