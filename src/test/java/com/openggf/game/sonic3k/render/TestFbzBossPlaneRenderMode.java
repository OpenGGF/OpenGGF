package com.openggf.game.sonic3k.render;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.render.AdvancedRenderFrameState;
import com.openggf.game.render.AdvancedRenderModeController;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFbzBossPlaneRenderMode {
    @Test
    void reversedModeSwapsPlaneSourcesAndProvidesIndependentVscrollWords() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_AND_TAILS, events);
        FbzBossPlaneRenderMode mode = new FbzBossPlaneRenderMode(() -> state, () -> 0x135, () -> 0x246);
        AdvancedRenderModeController controller = new AdvancedRenderModeController();
        controller.register(mode);

        events.setPlaneAssignmentMode(Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED);
        AdvancedRenderFrameState.Builder builder = AdvancedRenderFrameState.builder();
        mode.contribute(null, builder);
        AdvancedRenderFrameState frame = builder.build();

        assertTrue(frame.reversePlaneAssignment());
        assertTrue(frame.hasForegroundVScrollOverride());
        assertTrue(frame.hasBackgroundVScrollOverride());
        assertEquals((short) 0x135, frame.foregroundVScrollOverride());
        assertEquals((short) 0x246, frame.backgroundVScrollOverride());

        events.setPlaneAssignmentMode(Sonic3kFBZEvents.PlaneAssignmentMode.NORMAL);
        builder = AdvancedRenderFrameState.builder();
        mode.contribute(null, builder);
        frame = builder.build();
        assertFalse(frame.reversePlaneAssignment());
        assertFalse(frame.hasForegroundVScrollOverride());
        assertFalse(frame.hasBackgroundVScrollOverride());
    }
}
