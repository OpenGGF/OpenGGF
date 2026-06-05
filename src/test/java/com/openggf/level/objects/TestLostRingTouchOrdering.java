package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GLCommand;
import com.openggf.level.rings.LostRingObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stage-2.1 ordering invariant for the type-keyed every-frame lost-ring touch branch.
 * <p>
 * Models ROM {@code Touch_ChkValue} ring branch (docs/s2disasm/s2.asm:85196-85219):
 * a spilled ring ({@link LostRingObjectInstance}) in slot order collects when the toucher
 * is not invulnerable ({@code invulnerable_time < 90}) and, either way, breaks the touch
 * loop — so a lower-slot ring suppresses a later hazard exactly as ROM does. The branch is
 * keyed on the {@code LostRingObjectInstance} type marker, NOT the {@code 0x47} byte shape,
 * so other SPECIAL objects sharing the byte shape keep their own listener path.
 */
public class TestLostRingTouchOrdering {

    private ObjectManager objectManager;
    private TouchResponseTable table;
    private AbstractPlayableSprite player;

    @BeforeEach
    public void setUp() {
        table = Mockito.mock(TouchResponseTable.class);
        // Ring size index $07 → (8, 8); generic SPECIAL/HURT mocks use the same.
        when(table.getWidthRadius(anyInt())).thenReturn(8);
        when(table.getHeightRadius(anyInt())).thenReturn(8);

        DebugOverlayManager debugOverlay = mock(DebugOverlayManager.class);
        when(debugOverlay.isEnabled(any())).thenReturn(false);
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0);
        when(camera.getY()).thenReturn((short) 0);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        when(camera.isVerticalWrapEnabled()).thenReturn(false);
        ObjectServices services = new TestObjectServices()
                .withDebugOverlay(debugOverlay)
                .withCamera(camera);
        objectManager = new ObjectManager(List.of(), new ObjectRegistry() {
            @Override
            public ObjectInstance create(ObjectSpawn spawn) {
                return null;
            }

            @Override
            public void reportCoverage(List<ObjectSpawn> spawns) {
            }

            @Override
            public String getPrimaryName(int objectId) {
                return "Test";
            }
        }, 0, null, table, null, camera, services);
        objectManager.resetTouchResponses();
        // Keep the static camera bounds at the default 320x224 viewport so on-screen
        // gating does not drop a ring/hazard placed at the player's centre.
        AbstractObjectInstance.resetCameraBoundsForTests();

        player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) 160);
        when(player.getCentreY()).thenReturn((short) 112);
        when(player.getYRadius()).thenReturn((short) 20);
        when(player.getCrouching()).thenReturn(false);
        when(player.getRolling()).thenReturn(false);
        when(player.getSpindash()).thenReturn(false);
        when(player.getInvincibleFrames()).thenReturn(0);
        when(player.getInvulnerable()).thenReturn(false);
        when(player.getDead()).thenReturn(false);
        when(player.getRingCount()).thenReturn(0);
        when(player.getInvulnerableFrames()).thenReturn(0);
        when(player.isCpuControlled()).thenReturn(false);
    }

    @AfterEach
    public void tearDown() {
        SessionManager.clear();
    }

    /** Build an overlapping spilled ring at the player's centre on the given slot. */
    private LostRingObjectInstance ringAtSlot(int slot) {
        LostRingObjectInstance ring = LostRingObjectInstance.forTest(160, 112, 0, 0, 0, 0xFF);
        ring.setSlotIndex(slot);
        objectManager.addDynamicObject(ring);
        ring.snapshotPreUpdatePosition();
        return ring;
    }

    /** Build an overlapping HURT hazard at the player's centre on the given slot. */
    private MockHurtObject hurtAtSlot(int slot) {
        MockHurtObject hazard = new MockHurtObject(160, 112);
        hazard.setSlotIndex(slot);
        objectManager.addDynamicObject(hazard);
        hazard.snapshotPreUpdatePosition();
        return hazard;
    }

    @Test
    public void lowerSlotRingSuppressesLaterHazard() {
        LostRingObjectInstance ring = ringAtSlot(20);
        hurtAtSlot(21);

        objectManager.runTouchResponsesForPlayer(player, 1);

        assertTrue(ring.isCollected(), "Lower-slot ring should be collected");
        verify(player, never()).applyHurtOrDeath(anyInt(), any(), anyBoolean());
    }

    @Test
    public void higherSlotRingDoesNotSuppressEarlierHazard() {
        LostRingObjectInstance ring = ringAtSlot(21);
        hurtAtSlot(20);

        objectManager.runTouchResponsesForPlayer(player, 1);

        verify(player).applyHurtOrDeath(anyInt(), any(), anyBoolean());
        assertFalse(ring.isCollected(), "Hazard fires first; ring at a higher slot is not reached");
    }

    @Test
    public void overlapDuringInvulnerabilityBreaksWithoutCollecting() {
        when(player.getInvulnerableFrames()).thenReturn(120); // >= 90
        LostRingObjectInstance ring = ringAtSlot(20);
        hurtAtSlot(21);

        objectManager.runTouchResponsesForPlayer(player, 1);

        assertFalse(ring.isCollected(), "Invulnerable toucher must not collect");
        verify(player, never()).applyHurtOrDeath(anyInt(), any(), anyBoolean());
    }

    @Test
    public void collectsOnceInvulnerabilityDropsWhileStillOverlapping() {
        when(player.getInvulnerableFrames()).thenReturn(91);
        LostRingObjectInstance ring = ringAtSlot(20);

        objectManager.runTouchResponsesForPlayer(player, 1); // frame 1: still invuln (>=90)
        assertFalse(ring.isCollected());

        when(player.getInvulnerableFrames()).thenReturn(89); // dropped below 90, still overlapping
        objectManager.runTouchResponsesForPlayer(player, 2); // frame 2: must collect (every-frame eval)
        assertTrue(ring.isCollected(),
                "Branch is every-frame, not edge-triggered: must collect once invuln drops");
    }

    @Test
    public void placedRingObjectNotHandledByLostRingBranch() {
        // A SPECIAL object that shares the $47 byte shape but is NOT a LostRingObjectInstance
        // must go through its own listener path, not the lost-ring branch.
        MockSpecialRingShapedObject placed = new MockSpecialRingShapedObject(160, 112);
        placed.setSlotIndex(20);
        objectManager.addDynamicObject(placed);
        placed.snapshotPreUpdatePosition();

        objectManager.runTouchResponsesForPlayer(player, 1);

        assertTrue(placed.touchedViaListenerPath,
                "A non-lost-ring $47 object must be handled by its own listener path");
    }

    // ==================== Helper objects (slot-controllable AbstractObjectInstance) ====

    /** HURT hazard ($80 category) that overlaps the player. */
    private static final class MockHurtObject extends AbstractObjectInstance
            implements TouchResponseProvider {
        private final int x;
        private final int y;

        private MockHurtObject(int x, int y) {
            super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "MockHurtObject");
            this.x = x;
            this.y = y;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public int getCollisionFlags() {
            return 0x87; // HURT category + size index $07
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public boolean requiresRenderFlagForTouch() {
            return false;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }

    /** SPECIAL object with the $47 byte shape but NOT a LostRingObjectInstance. */
    private static final class MockSpecialRingShapedObject extends AbstractObjectInstance
            implements TouchResponseProvider, TouchResponseListener {
        private final int x;
        private final int y;
        boolean touchedViaListenerPath = false;

        private MockSpecialRingShapedObject(int x, int y) {
            super(new ObjectSpawn(x, y, 0x25, 0, 0, false, 0), "MockSpecialRingShapedObject");
            this.x = x;
            this.y = y;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public int getCollisionFlags() {
            return 0x47; // same byte shape as a lost ring
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public boolean requiresRenderFlagForTouch() {
            return false;
        }

        @Override
        public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
            touchedViaListenerPath = true;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }
}
