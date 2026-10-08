package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Production results publication, hardware resource admission, reload and retained Plane B. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezSeamlessActChange {
    private S3kDezZoneRuntimeState state() {
        return S3kRuntimeStates.currentDez(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }
    @Test void resultsSignalRebasesTheArenaAndRedrawsTheRetainedBackgroundOverEightPasses() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(11, 0)
                .startPosition((short) 0x3740, (short) 0x3AC).startPositionIsCentre().build();
        fixture.sprite().setDebugMode(true);
        fixture.stepIdleFrames(2);
        // This short transition check enters after the boss and its children have retired.
        for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
            if (object instanceof com.openggf.level.objects.AbstractObjectInstance instance
                    && object.getClass().getSimpleName().startsWith("DezMiniboss")) instance.setDestroyed(true);
        }
        fixture.stepIdleFrames(1);
        var camera = fixture.camera();
        camera.setMinX((short) 0x3680); camera.setMaxX((short) 0x36C0);
        camera.setMinY((short) 0x28C); camera.setMaxY((short) 0x28C);
        camera.setX((short) 0x3680); camera.setY((short) 0x28C);
        state().setCameraStoredMaxY(0x1234); state().setBossFlag(true);
        GameServices.gameState().setReverseGravityActive(true);
        GameServices.gameState().setEndOfLevelActive(true);
        Sonic3kLevelTriggerManager.setBit(0, 0);
        int[][] retainedPalette = new int[2][16];
        for (int line = 0; line < 2; line++) for (int color = 0; color < 16; color++)
            retainedPalette[line][color] = com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                    GameServices.level().getCurrentLevel().getPalette(line).getColor(color));
        int oldTop = GameServices.level().getBackgroundTileDescriptorAtWorld(0, 0);
        int oldBottom = GameServices.level().getBackgroundTileDescriptorAtWorld(0, 240);
        var events = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        events.signalActTransition();
        assertNotEquals(0, state().eventsFg5());
        fixture.stepIdleFrames(1);
        assertEquals(4, state().backgroundRoutine()); assertEquals(0, state().eventsFg5());
        assertTrue(state().blockJobOrdinal() >= 0); assertTrue(state().artJobOrdinal() >= 0);
        int waited = 0;
        while (state().actIndex() == 0 && waited++ < 240) fixture.stepIdleFrames(1);
        assertEquals(1, state().actIndex(), "the production Kos queues must release the reload");
        assertEquals(0, state().foregroundRoutine()); assertEquals(0, state().backgroundRoutine());
        for (int line = 0; line < 2; line++) for (int color = 0; color < 16; color++)
            assertEquals(retainedPalette[line][color], com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                    GameServices.level().getCurrentLevel().getPalette(line).getColor(color)));
        assertEquals(0x140, fixture.sprite().getCentreX() & 65535);
        assertEquals(0x7AC, fixture.sprite().getCentreY() & 65535);
        assertEquals(0x80, camera.getMinX()); assertEquals(0xC0, camera.getMaxX());
        assertEquals(0x68C, camera.getMinY()); assertEquals(0x68C, camera.getMaxY());
        assertEquals(0x68C, camera.getMaxYTarget());
        assertEquals(0x1234, state().cameraStoredMaxY(), "stored bounds are not rebased");
        assertFalse(state().bossFlag()); assertFalse(Sonic3kLevelTriggerManager.testBit(0, 0));
        assertTrue(GameServices.gameState().isReverseGravityActive());
        assertTrue(GameServices.gameState().isEndOfLevelActive());
        assertEquals(oldTop, state().transitionPlane().descriptor(0, 0));
        var rewind = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = rewind.capture();
        for (int pass = 1; pass <= 8; pass++) {
            fixture.stepIdleFrames(1);
            assertEquals(pass < 8 ? 4 : 8, state().backgroundRoutine());
            if (pass < 8) assertEquals(oldTop, state().transitionPlane().descriptor(0, 0));
        }
        assertEquals(GameServices.level().getBackgroundTileDescriptorAtWorld(0, 0), state().transitionPlane().descriptor(0, 0));
        assertEquals(oldBottom, state().transitionPlane().descriptor(0, 240), "unsigned source clipping skips -$10");
        byte[] expected = state().captureBytes();
        rewind.restore(saved); fixture.stepIdleFrames(8);
        assertArrayEquals(expected, state().captureBytes(), "retained rows and delayed row count replay");
    }
}
