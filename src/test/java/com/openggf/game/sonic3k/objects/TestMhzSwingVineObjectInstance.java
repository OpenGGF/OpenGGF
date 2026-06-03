package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PostPlayerUpdateHook;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestMhzSwingVineObjectInstance {
    private static final int MHZ_SWING_VINE = 0x10;

    @Test
    void registryRoutesSklSlot10ToMhzSwingVine() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);

        ObjectInstance vine = registry.create(new ObjectSpawn(
                0x2600, 0x0660, MHZ_SWING_VINE, 0, 0, false, 0));

        assertEquals("MHZSwingVine", vine.getName(),
                "SKL slot $10 is Obj_MHZSwingVine and must not use the S3KL LBZ tube elevator");
    }

    @Test
    void playerBelowHandleIsCapturedAtRomHangPoint() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance vine = registry.create(new ObjectSpawn(
                0x2600, 0x0660, MHZ_SWING_VINE, 0, 0, false, 0));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x2600, (short) 0x0680);
        player.setYSpeed((short) 0x0300);

        assertEquals("MHZSwingVine", vine.getName(),
                "SKL slot $10 must construct the MHZ swing vine before capture can be validated");
        vine.update(0, player);

        assertTrue(player.isObjectControlled(),
                "Obj_MHZSwingVine writes object_control=3 on grab");
        assertTrue(player.isObjectControlAllowsCpu(),
                "object_control=3 is a bits 0-6 control state, not the ROM bit-7 full-control state");
        assertTrue(player.isObjectControlSuppressesMovement(),
                "object_control=3 suppresses normal player movement while hanging");
        assertEquals(0x2600, player.getCentreX(),
                "Grab snaps x_pos to the handle's x_pos");
        assertEquals(0x0684, player.getCentreY(),
                "Initial handle y_pos is parent y_pos+$10; grab snaps player y_pos to handle+$14");
        assertEquals((short) 0, player.getXSpeed());
        assertEquals((short) 0, player.getYSpeed());
        assertEquals((short) 0, player.getGSpeed());
        assertEquals(0x91, player.getMappingFrame(),
                "Stationary handle mode uses byte_22A4C's center hanging frame");
    }

    @Test
    void slowGrabJumpReleaseUsesStationaryHandleVelocity() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance vine = registry.create(new ObjectSpawn(
                0x2600, 0x0660, MHZ_SWING_VINE, 0, 0, false, 0));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x2600, (short) 0x0680);

        assertEquals("MHZSwingVine", vine.getName(),
                "SKL slot $10 must construct the MHZ swing vine before release can be validated");
        vine.update(0, player);
        player.setJumpInputPressed(true);
        vine.update(1, player);
        ((PostPlayerUpdateHook) vine).updatePostPlayer(1, player);

        assertFalse(player.isObjectControlled(),
                "Jump release clears object_control after applying the launch");
        assertTrue(player.getAir(), "Jump release sets Status_InAir");
        assertTrue(player.isJumping(), "Jump release sets the jumping byte");
        assertTrue(player.getRolling(), "Jump release sets Status_Roll");
        assertEquals((short) 0, player.getXSpeed(),
                "Slow grab byte 1 does not trigger loc_22690, so mode-0 release uses unchanged handle x delta");
        assertEquals((short) -0x0380, player.getYSpeed(),
                "Slow grab byte 1 keeps $30(a0)=0; loc_2291A applies the stationary handle -$380 y_vel boost");
        assertEquals(7, player.getXRadius());
        assertEquals(14, player.getYRadius());
    }

    @Test
    void jumpReleaseDoesNotDependOnPostPlayerHook() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance vine = registry.create(new ObjectSpawn(
                0x2600, 0x0660, MHZ_SWING_VINE, 0, 0, false, 0));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x2600, (short) 0x0680);

        vine.update(0, player);
        player.setJumpInputPressed(true);
        vine.update(1, player);

        assertFalse(player.isObjectControlled(),
                "loc_2291A clears object_control immediately when A/B/C is pressed, before the post-player hook");
        assertTrue(player.getAir(), "Jump release sets Status_InAir on the same object update");
        assertEquals((short) -0x0380, player.getYSpeed(),
                "The same-frame release must apply the stationary handle launch velocity");
    }

    @Test
    void fastGrabJumpReleaseUsesSwingVelocity() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance vine = registry.create(new ObjectSpawn(
                0x2600, 0x0660, MHZ_SWING_VINE, 0, 0, false, 0));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x2600, (short) 0x0680);
        player.setXSpeed((short) 0x0400);

        vine.update(0, player);
        player.setJumpInputPressed(true);
        vine.update(1, player);
        ((PostPlayerUpdateHook) vine).updatePostPlayer(1, player);

        assertEquals((short) 0x0800, player.getXSpeed(),
                "Fast grab byte $81 triggers loc_22690; loc_2215C writes x_vel from cos(angle)<<3");
        assertEquals((short) 0, player.getYSpeed(),
                "Fast grab byte $81 triggers loc_22690; loc_2215C writes y_vel from sin(angle)<<3");
    }

    @Test
    void fastReleasedVineTransitionsIntoRomReturnStateNearCenter() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance vine = registry.create(new ObjectSpawn(
                0x2600, 0x0660, MHZ_SWING_VINE, 0, 0, false, 0));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x2600, (short) 0x0680);
        player.setXSpeed((short) 0x0400);

        vine.update(0, player);
        player.setJumpInputPressed(true);
        vine.update(1, player);
        vine.update(2, player);

        assertTrue(vine.traceDebugDetails().contains("state=RETURNING"),
                "after the rider releases near angle 0, loc_226B0 branches to the damped loc_22748 return state");
    }

    @Test
    void grabbedPlayerOffscreenRenderFlagClearsControlAndDelaysRecapture() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance vine = registry.create(new ObjectSpawn(
                0x2600, 0x0660, MHZ_SWING_VINE, 0, 0, false, 0));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x2600, (short) 0x0680);

        vine.update(0, player);
        assertTrue(player.isObjectControlled(),
                "Setup sanity: the player must be hanging before the render_flags release path is exercised");
        player.setRenderFlagOnScreen(false);

        vine.update(1, player);

        assertFalse(player.isObjectControlled(),
                "loc_229E4 clears object_control when render_flags(a1) is non-negative while hanging");
        assertFalse(vine.isPersistent(),
                "loc_229E4 clears the per-player grab byte so the vine no longer stays persistent for that rider");

        player.setRenderFlagOnScreen(true);
        vine.update(2, player);

        assertFalse(player.isObjectControlled(),
                "loc_229E4 writes the $3C recapture delay, so the same player is not immediately grabbed again");
    }

    @Test
    void swingVineRendersRomRootSegmentAndHandleFrames() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        when(renderManager.getRenderer(Sonic3kObjectArtKeys.MHZ_SWING_VINE)).thenReturn(renderer);
        LevelManager levelManager = mock(LevelManager.class);
        when(levelManager.getObjectRenderManager()).thenReturn(renderManager);

        MhzSwingVineObjectInstance vine = new MhzSwingVineObjectInstance(new ObjectSpawn(
                0x2600, 0x0660, MHZ_SWING_VINE, 0, 0, false, 0));
        vine.setServices(new TestObjectServices().withLevelManager(levelManager));

        vine.appendRenderCommands(new ArrayList<>());

        verify(renderManager).getRenderer(Sonic3kObjectArtKeys.MHZ_SWING_VINE);
        verify(renderer).drawFrameIndex(0x23, 0x2600, 0x0660, false, false);
        verify(renderer).drawFrameIndex(0, 0x2600, 0x0660, false, false);
        verify(renderer).drawFrameIndex(0x22, 0x2600, 0x0670, false, false);
    }

    private static final class ZoneForTestRegistry extends Sonic3kObjectRegistry {
        private final int zoneId;

        private ZoneForTestRegistry(int zoneId) {
            this.zoneId = zoneId;
        }

        @Override
        protected int currentRomZoneId() {
            return zoneId;
        }
    }
}
