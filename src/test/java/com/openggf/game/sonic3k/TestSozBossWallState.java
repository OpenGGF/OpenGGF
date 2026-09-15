package com.openggf.game.sonic3k;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.SozBossWallState;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSozBossWallState {
    @Test void openingKeepsVulnerabilityUntilAllRowsRecoverAndFinalDelayExpires() {
        var runtime = new SozZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);
        var event = runtime.events();
        event.bossY(0x6D8);
        event.bossWallHitY(0x650);
        int[] delays = new int[17];
        var wall = event.bossWall();
        assertEquals(SozBossWallState.SOUND_HIT, wall.tick(event, false, delays));
        assertEquals(4, event.bossWallRoutine());
        assertEquals(2, event.bossWallTimer()); // same-dispatch stage fallthrough
        assertEquals(-9, wall.rowOffset(1));
        assertEquals(-1, wall.rowOffset(9));
        wall.tick(event, false, delays);
        wall.tick(event, false, delays);
        wall.tick(event, false, delays);
        assertEquals(8, event.bossWallRoutine());
        assertEquals(7, event.bossWallTimer());
        assertEquals(-17, wall.rowOffset(1));
        for (int i = 0; i < 7; i++) wall.tick(event, false, delays);
        assertEquals(SozBossWallState.SOUND_COLLAPSE, wall.tick(event, false, delays));
        assertEquals(0x0C, event.bossWallRoutine());
        assertEquals(-25, wall.rowOffset(1)); // first eight-pixel displacement occurs immediately
        boolean sawRecoveredDelay = false;
        for (int tick = 0; tick < 400 && event.bossWallHitY() != 0; tick++) {
            wall.tick(event, false, delays);
            if (event.bossWallTimer() == 15 && event.bossWallRoutine() == 0x10) {
                sawRecoveredDelay = true;
                for (int row = 1; row <= 9; row++) assertEquals(0, wall.rowOffset(row));
                assertEquals(0x650, event.bossWallHitY());
            }
        }
        assertTrue(sawRecoveredDelay);
        assertEquals(0, event.bossWallHitY());
        assertEquals(0, event.bossWallRoutine());
    }

    @Test void defeatRetractsTwelveRowsWithStaggeredDelaysAndRestoresExactly() {
        var runtime = new SozZoneRuntimeState(1, PlayerCharacter.KNUCKLES);
        var event = runtime.events();
        var wall = event.bossWall();
        int[] delays = new int[17];
        wall.tick(event, true, delays);
        assertEquals(-5, wall.rowOffset(1));
        assertEquals(0, wall.rowOffset(2));
        assertEquals(0x14, event.bossWallRoutine());
        for (int i = 0; i < 4; i++) wall.tick(event, true, delays);
        assertEquals(0, wall.rowOffset(2));
        wall.tick(event, true, delays);
        assertEquals(-5, wall.rowOffset(2));
        byte[] before = runtime.captureBytes();
        for (int i = 0; i < 120; i++) wall.tick(event, true, delays);
        assertTrue(wall.defeatRetractionComplete());
        byte[] after = runtime.captureBytes();
        runtime.restoreBytes(before);
        for (int i = 0; i < 120; i++) wall.tick(event, true, delays);
        assertArrayEquals(after, runtime.captureBytes());
        assertEquals(0, wall.rowOffset(0));
        for (int row = 1; row <= 12; row++) assertEquals(-256, wall.rowOffset(row));
    }
    @org.junit.jupiter.api.Test
    void wallBounceUsesSideContactRatherThanStandingBits() {
        var wall = new com.openggf.game.sonic3k.objects.SozBossWallObjectInstance(
                new com.openggf.level.objects.ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        var player = org.mockito.Mockito.mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        wall.onSolidContact(player, new com.openggf.level.objects.SolidContact(
                true, false, false, true, false), 0);
        org.mockito.Mockito.verify(player, org.mockito.Mockito.never()).setXSpeed(org.mockito.ArgumentMatchers.anyShort());
        wall.onSolidContact(player, new com.openggf.level.objects.SolidContact(
                false, true, false, false, false), 0);
        org.mockito.Mockito.verify(player).setXSpeed((short) -0x300);
        org.mockito.Mockito.verify(player).setYSpeed((short) -0x300);
    }

}
