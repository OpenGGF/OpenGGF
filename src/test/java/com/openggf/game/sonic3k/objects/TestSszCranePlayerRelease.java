package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.sprites.playable.ObjectControlState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszCranePlayerRelease {
    @Test void landingReleasesNativeControlAndStopsAllPlayerVelocities() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var player = fixture.sprite();
        // Native crane reference: player lands at ($220,$4AC), control $83 -> 0.
        player.setCentreX((short) 0x21C);
        player.setCentreY((short) 0x480);
        player.setXSpeed((short) 0x123);
        player.setYSpeed((short) 0x234);
        player.setGSpeed((short) 0x345);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        ((SszZoneRuntimeState) GameServices.zoneRuntimeState()).setBossRightX(0x220);
        var release = new SszCranePlayerRelease(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        release.setServices(TestEnvironment.objectServices());
        GameServices.level().getObjectManager().addDynamicObject(release);
        for (int update = 0; update < 100 && !release.isDestroyed(); update++) {
            release.update(update, null);
        }
        assertTrue(release.isDestroyed(), "ROM-backed arena floor ends the scripted release");
        assertEquals(0x220, player.getCentreX());
        assertEquals(0x4AC, player.getCentreY());
        assertFalse(player.isObjectMappingFrameControl());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
    }

    @Test void releaseMovesOnItsInitPassAndGravityDoesNotWaitForThePoseTimer() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var player = fixture.sprite();
        player.setCentreX((short) 0x200);
        player.setCentreY((short) 0x300);
        ((SszZoneRuntimeState) GameServices.zoneRuntimeState()).setBossRightX(0x208);
        var release = new SszCranePlayerRelease(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        release.setServices(TestEnvironment.objectServices());
        GameServices.level().getObjectManager().addDynamicObject(release);
        release.update(0, null);
        assertEquals(0x204, player.getCentreX());
        assertEquals(0x300, player.getCentreY());
        assertEquals(0xC0, player.getMappingFrame());
        release.update(1, null);
        assertEquals(0x208, player.getCentreX());
        assertEquals(0x301, player.getCentreY());
        assertEquals(0xCA, player.getMappingFrame());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int update = 0; update < 5; update++) release.update(update, null);
        assertEquals(0xCA, player.getMappingFrame());
        assertTrue(player.getCentreY() > 0x301, "gravity runs during the six-update pose timer");
        release.update(6, null);
        assertEquals(0xCB, player.getMappingFrame());
        assertEquals(0x304, player.getCentreY());
        registry.restore(saved);
        var restored = GameServices.level().getObjectManager()
                .activeObjectsOfType(SszCranePlayerRelease.class).getFirst();
        for (int update = 0; update < 6; update++) restored.update(update, null);
        var replayedPlayer = TestEnvironment.objectServices().spriteManager().getMainPlayable();
        assertEquals(0x208, replayedPlayer.getCentreX());
        assertEquals(0x304, replayedPlayer.getCentreY());
        assertEquals(0xCB, replayedPlayer.getMappingFrame());
    }
}
