package com.openggf.game.sonic2;

import com.openggf.game.GameServices;
import com.openggf.sprites.playable.*;
import com.openggf.tests.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

/** 5735S CNZ2: 300 consecutive offscreen CPU passes, then the 64-frame return gate. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestTasS2SidekickSchedule {
    @Test void offscreenTimeoutThenPeriodicReturnHonoursVisibleAndAirborneControls() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        var sonic = new Sonic("sonic", (short)0, (short)0);
        var tails = new Tails("tails", (short)0, (short)0);
        sonic.setCentreX((short)0x1000); sonic.setCentreY((short)0x400);
        sonic.resetPositionAndStatTableHistory();
        tails.setCentreX((short)0x600); tails.setCentreY((short)0x400);
        tails.setCpuControlled(true);
        GameServices.sprites().addSprite(sonic); GameServices.sprites().addSprite(tails);
        var camera = GameServices.camera(); camera.setX((short)0xF60); camera.setY((short)0x3A0);
        var cpu = new SidekickCpuController(tails, sonic);
        cpu.setInitialState(SidekickCpuController.State.NORMAL);
        int frame = 0;
        for (; frame < 299; frame++) {
            GameServices.sprites().refreshPlayableRenderFlags(camera);
            assertFalse(tails.isRenderFlagOnScreen(), "the real render owner must mark the distant follower offscreen");
            cpu.update(frame);
            assertEquals(SidekickCpuController.State.NORMAL, cpu.getState());
        }
        assertEquals(299, cpu.getDiagnosticRespawnCounter());
        // Visibility on the would-be timeout pass resets the consecutive counter.
        tails.setCentreX(sonic.getCentreX());
        GameServices.sprites().refreshPlayableRenderFlags(camera); cpu.update(frame++);
        assertEquals(SidekickCpuController.State.NORMAL, cpu.getState());
        assertEquals(0, cpu.getDiagnosticRespawnCounter());
        tails.setCentreX((short)0x600);
        for (int i = 1; i <= 300; i++) {
            GameServices.sprites().refreshPlayableRenderFlags(camera); cpu.update(frame++);
            assertEquals(i < 300 ? SidekickCpuController.State.NORMAL : SidekickCpuController.State.SPAWNING, cpu.getState());
        }
        assertEquals(0x4000, tails.getCentreX()); assertEquals(0, tails.getCentreY());
        assertTrue(tails.isObjectControlled());
        sonic.setAir(true);
        while ((frame & 63) != 0) { cpu.update(frame++); assertEquals(SidekickCpuController.State.SPAWNING, cpu.getState()); }
        cpu.update(frame++); // even on-mask, an airborne leader must block the automatic return
        assertEquals(SidekickCpuController.State.SPAWNING, cpu.getState());
        sonic.setAir(false);
        while ((frame & 63) != 0) { cpu.update(frame++); assertEquals(SidekickCpuController.State.SPAWNING, cpu.getState()); }
        cpu.update(frame);
        assertEquals(SidekickCpuController.State.APPROACHING, cpu.getState());
        assertEquals(sonic.getCentreX(), tails.getCentreX());
        assertEquals(sonic.getCentreY() - 0xC0, tails.getCentreY());
        assertTrue(tails.getAir());
    }
}
