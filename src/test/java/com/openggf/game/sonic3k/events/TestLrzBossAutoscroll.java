package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.LrzBossActState;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLrzBossAutoscroll {
    @ParameterizedTest
    @CsvSource({"0,1039,1104,0,131072,0", "0,1040,1104,4,92672,-92672",
            "4,1040,817,4,92672,-92672", "4,1040,816,8,131072,0",
            "8,1615,816,8,131072,0", "8,1616,816,12,92672,-92672",
            "12,1616,753,12,92672,-92672", "12,1616,752,16,131072,0",
            "16,2319,752,16,131072,0", "16,2320,752,20,121088,50176",
            "20,2400,799,20,121088,50176", "20,2400,800,24,131072,0",
            "24,3006,800,24,131072,0", "24,3007,800,24,0,0"})
    void stageThresholdsUseSameDispatchVelocity(int stage, int x, int y, int next, int dx, int dy) {
        var camera = new Camera(); camera.setX((short)x); camera.setY((short)y);
        var main = mock(AbstractPlayableSprite.class); when(main.getCentreX()).thenReturn((short)(x+0x80));
        var state = new LrzBossActState(); state.setAutoscrollRoutine(stage);
        LrzBossAutoscroll.advance(state, camera, main, List.of());
        assertEquals(next, state.autoscrollRoutine());
        assertEquals((x << 16) + dx, (camera.getX() << 16) | state.cameraFractionX());
        assertEquals((y << 16) + dy, (camera.getY() << 16) | state.cameraFractionY());
        assertEquals(camera.getX(), camera.getMinX()); assertEquals(camera.getX(), camera.getMaxX());
        assertEquals(camera.getY(), camera.getMinY()); assertEquals(camera.getY(), camera.getMaxY());
        assertEquals(camera.getY(), camera.getMaxYTarget()); assertTrue(camera.getFrozen());
    }

    @Test void restoredFractionalMotionAndDelayReproduceNextDispatch() {
        var runtime = new LrzZoneRuntimeState(22, 0, PlayerCharacter.SONIC_AND_TAILS);
        var state = runtime.bossAct(); state.setAutoscrollRoutine(20); state.setAutoscrollDelay(45);
        var camera = new Camera(); camera.setX((short)0x920); camera.setY((short)0x2F0);
        var main = mock(AbstractPlayableSprite.class); when(main.getCentreX()).thenReturn((short)0x9C0);
        for (int i=0;i<45;i++) LrzBossAutoscroll.advance(state, camera, main, List.of());
        assertEquals(0x920, camera.getX()); assertFalse(camera.getFrozen());
        LrzBossAutoscroll.advance(state, camera, main, List.of());
        assertEquals(0x921, camera.getX()); assertEquals(0xD900, state.cameraFractionX());
        assertEquals(0xC400, state.cameraFractionY());
        byte[] before = runtime.captureBytes(); short x=camera.getX(), y=camera.getY();
        LrzBossAutoscroll.advance(state, camera, main, List.of());
        byte[] after = runtime.captureBytes(); short afterX=camera.getX(), afterY=camera.getY();
        runtime.restoreBytes(before); camera.setX(x); camera.setY(y);
        LrzBossAutoscroll.advance(state, camera, main, List.of());
        assertArrayEquals(after, runtime.captureBytes()); assertEquals(afterX,camera.getX()); assertEquals(afterY,camera.getY());
    }

    @Test void onlyPrimaryReleasesArenaAndReleaseDoesNotRunClamp() {
        var state = new LrzBossActState(); state.setAutoscrollRoutine(24);
        var camera = new Camera(); camera.setX((short)0xBC0); camera.setY((short)0x320);
        var main=mock(AbstractPlayableSprite.class); var side=mock(AbstractPlayableSprite.class);
        when(main.getCentreX()).thenReturn((short)0xC4F); when(side.getCentreX()).thenReturn((short)0xC50);
        LrzBossAutoscroll.advance(state,camera,main,List.of(side)); assertEquals(24,state.autoscrollRoutine());
        when(main.getCentreX()).thenReturn((short)0xC50);
        LrzBossAutoscroll.advance(state,camera,main,List.of(side));
        assertEquals(-1,state.autoscrollRoutine()); assertFalse(camera.getFrozen());
        assertEquals(0xA00,camera.getMinX()); assertEquals(0xBC0,camera.getMaxX());
        assertEquals(0x560,camera.getMaxY()); assertEquals(0x560,camera.getMaxYTarget());
    }

    @Test void nativeMovemWordRestoreTruncatesSecondaryVelocityAndPushingCrushes() {
        var state = new LrzBossActState(); state.setAutoscrollRoutine(4);
        var camera=new Camera(); camera.setX((short)0x410); camera.setY((short)0x400);
        var main=mock(AbstractPlayableSprite.class); var side=mock(AbstractPlayableSprite.class);
        LrzBossAutoscroll.advance(state,camera,main,List.of(side));
        verify(main).setGSpeed((short)0x16A); verify(side).setGSpeed((short)0x6A);
        when(main.getPushing()).thenReturn(true);
        LrzBossAutoscroll.advance(state,camera,main,List.of(side)); verify(main).applyCrushDeath();
    }
}
