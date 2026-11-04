package com.openggf;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.level.FreshLevelBoundaryAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A bonus-stage entry or return re-enters S3K Level: like any fresh load, but
 * places its destination players itself after the load. Level clears object
 * RAM before the title loop (loc_60DE, sonic3k.asm:7649-7651), sets the start
 * position once that loop exits (loc_6310 Get_LevelSizeStart, 7789-7792) and
 * creates the players in loc_6468 (7880-7915). The after-setup boundary holds
 * the already-placed players and the source camera until those points.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestFreshLevelBoundaryAfterSetup {
    @AfterEach
    void reset() {
        GameServices.configuration().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test
    void placedPlayersAreHeldThenPublishedThenCreated() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        // Isolate title ownership from the intro's legitimate control takeover
        // during loc_6468's initial sprite pass.
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).withSkippedZoneIntro().build();
        var level = GameServices.level();
        var camera = GameServices.camera();
        AbstractPlayableSprite player =
                (AbstractPlayableSprite) GameServices.sprites().getMainPlayable();
        player.setCentreX((short) 0x2778);
        player.setCentreY((short) 0x025C);
        camera.setX((short) 0x26D8);
        camera.setY((short) 0x01FC);
        short sourceX = 0x0060;
        short sourceY = 0x0240;

        FreshLevelBoundaryAccess.beginAfterSetup(level, sourceX, sourceY, 69);

        assertTrue(level.hasPendingFreshLevelTransitionBoundary());
        assertEquals(0, player.getCentreX(), "loc_60DE cleared the player slot");
        assertEquals(0, player.getCentreY());
        assertEquals(sourceX, camera.getX(), "the source camera stays until the title loop exits");
        assertEquals(sourceY, camera.getY());

        // The title host locks after setup captured the destination player.
        player.setControlLocked(true);
        level.publishFreshLevelTransitionInitialBoundary();
        assertTrue(player.isControlLocked(), "publication must retain the live title lock");
        assertEquals(0x2778, player.getCentreX(), "Get_LevelSizeStart places the player");
        assertEquals(0x025C, player.getCentreY());
        assertEquals((short) 0x26D8, camera.getX(), "and hands the camera to the destination");
        assertEquals((short) 0x01FC, camera.getY());
        assertTrue(level.hasPendingFreshLevelTransitionBoundary(),
                "loc_6468 has not created the players yet");

        // A LEVEL destination releases its host lock before final assembly.
        player.setControlLocked(false);
        FreshLevelBoundaryAccess.completeAtRelease(level);
        assertFalse(player.isControlLocked(), "assembly must retain the host's control release");
        assertFalse(level.hasPendingFreshLevelTransitionBoundary());
        assertEquals(0x2778, player.getCentreX());
        assertEquals(0x025C, player.getCentreY());
    }

    @Test
    void anAlreadyPendingBoundaryIsNotReopened() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var level = GameServices.level();
        FreshLevelBoundaryAccess.beginAfterSetup(level, (short) 0, (short) 0, 0);
        assertThrows(IllegalStateException.class,
                () -> FreshLevelBoundaryAccess.beginAfterSetup(level, (short) 0, (short) 0, 0));
        FreshLevelBoundaryAccess.completeAtRelease(level);
    }
}
