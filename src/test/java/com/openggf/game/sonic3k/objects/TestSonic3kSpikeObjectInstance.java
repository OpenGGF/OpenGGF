package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic3kSpikeObjectInstance {

    @Test
    void spikesUseSolidObjectFullInclusiveRightEdge() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x1FF8, 0x0564, Sonic3kObjectIds.SPIKES, 0x00, 0, false, 0));

        assertTrue(spikes.usesInclusiveRightEdge(),
                "Obj_Spikes calls SolidObjectFull; SolidObject_cont rejects relX > width*2, not relX == width*2");
    }

    @Test
    void spikesUseSolidObjectFullAirborneStaleStandingBitReturn() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x01D0, 0x05F0, Sonic3kObjectIds.SPIKES, 0x00, 0, false, 0));

        assertTrue(spikes.airborneStaleStandingBitReturnsNoContact(null),
                "Obj_Spikes calls SolidObjectFull; an airborne player with this object's standing bit set "
                        + "must clear support and return before SolidObject_cont creates a fresh contact");
    }

    @Test
    void movingSpikesKeepSolidLatchOnLiveObjectSlot() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x0C06, 0x06D4, Sonic3kObjectIds.SPIKES, 0x01, 0, false, 0));

        assertTrue(spikes.usesInstanceSolidStateLatchKey(),
                "Obj_Spikes stores standing/pushing bits in status(a0), so retracting spikes must keep "
                        + "solid latch state on the live object slot while updateDynamicSpawn changes position");
    }

    @Test
    void spikesInitFrameDoesNotMoveOrRunSolidBodyUntilNextExecution() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x2000, 0x0145, Sonic3kObjectIds.SPIKES, 0x01, 0x02, false, 0));

        spikes.update(11818, null);

        assertEquals(0x0145, spikes.getY(),
                "Obj_Spikes init returns before sub_242B6 can apply vertical movement");
        assertFalse(spikes.isSolidFor(null),
                "Obj_Spikes init returns before loc_2413E can call SolidObjectFull");

        spikes.update(11819, null);

        assertEquals(0x014D, spikes.getY(),
                "First main routine execution applies sub_242B6 vertical movement");
        assertTrue(spikes.isSolidFor(null),
                "SolidObjectFull is available once the main routine body is reached");
    }

    @Test
    void movingSpikesUseSavedOriginForOffscreenLifecycle() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x1280, 0x09D0, Sonic3kObjectIds.SPIKES, 0x01, 0, false, 0));

        spikes.update(0, null);
        spikes.update(1, null);

        assertEquals(0x09D8, spikes.getY(),
                "First main routine execution should move the vertical spike from its placement Y.");
        assertEquals(0x1280, spikes.getOutOfRangeReferenceX(),
                "S3K Obj_Spikes uses saved $30(a0), not live position, for Sprite_OnScreen_Test2 "
                        + "(docs/skdisasm/sonic3k.asm:49038-49039,49071-49072,49102-49103).");
    }

    @Test
    void spikesSolidGateUsesRenderSpritesExclusiveBottomEdge() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x1170, 0x08B0, Sonic3kObjectIds.SPIKES, 0x30, 0, false, 0));

        AbstractObjectInstance.updateCameraBounds(0x10CE, 0x07C1, 0x10CE + 320, 0x07C1 + 224, 0);
        assertTrue(spikes.isWithinSolidContactBounds(),
                "One pixel inside Render_Sprites' bottom edge, spikes keep render_flags bit 7 set.");

        AbstractObjectInstance.updateCameraBounds(0x10CE, 0x07C0, 0x10CE + 320, 0x07C0 + 224, 0);
        assertFalse(spikes.isWithinSolidContactBounds(),
                "CNZ f21147: Render_Sprites rejects y_pos + height_pixels == camera_y + 224 + 2*height "
                        + "with bhs, so SolidObjectFull must see render_flags bit 7 clear next frame "
                        + "(sonic3k.asm:36358-36365, 41016-41018, 49011-49039).");
    }
}
