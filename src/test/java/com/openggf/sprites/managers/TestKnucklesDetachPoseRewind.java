package com.openggf.sprites.managers;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Declared component inlet above AIZ terrain, independent of any movie/trace
 * coordinates. Production movement detaches a grounded player and production
 * animation consumes the selected pose. Exercises the existing registered
 * restore path; does not certify route, pixels or load-boundary rewind.
 */
@RequiresRom(SonicGame.SONIC_3K)
@Isolated
class TestKnucklesDetachPoseRewind {
    @AfterEach void tearDown() { TestEnvironment.resetAll(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void groundedPoseSurvivesDetachAndRegisteredReplay(boolean down) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            var player = session.player();
            ObjectControlState.none().applyTo(player);
            player.setControlLocked(false);
            player.setCentreX((short) 0x400);
            player.setCentreY((short) 0x100);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
            player.setAir(false);
            player.setRolling(false);
            player.setAnimationId(Sonic3kAnimationIds.WAIT.id());
            var camera = GameServices.camera();
            camera.setMinX((short) 0);
            camera.setMaxX((short) 0x3000);
            camera.setMinY((short) 0);
            camera.setMaxY((short) 0x1000);
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var inlet = registry.capture();

            dispatch(player, down);
            assertPose(player, down);
            var detached = registry.capture();
            dispatch(player, false);
            assertContinuation(player, down);
            var airborne = registry.capture();
            registry.restore(detached);
            player = GameServices.sprites().getMainPlayable();
            assertWorld(detached, registry.capture());
            dispatch(player, false);
            assertContinuation(player, down);
            assertWorld(airborne, registry.capture());
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(inlet);
                player = GameServices.sprites().getMainPlayable();
                assertWorld(inlet, registry.capture());
                dispatch(player, down);
                assertPose(player, down);
                assertWorld(detached, registry.capture());
                dispatch(player, false);
                assertContinuation(player, down);
                assertWorld(airborne, registry.capture());
            }
        }
    }

    private static void dispatch(AbstractPlayableSprite player, boolean down) {
        player.captureOnObjectAtFrameStart();
        player.getMovementManager().handleMovement(false, down, false, false, false, false, false, false);
        player.getAnimationManager().update(1);
    }

    private static void assertPose(AbstractPlayableSprite player, boolean down) {
        assertTrue(player.getAir(), "real terrain collision must detach this unsupported inlet");
        assertFalse(player.getAirAtFrameStart(), "the player dispatched the ground routine");
        assertEquals(down, player.getCrouching());
        assertEquals((down ? Sonic3kAnimationIds.DUCK : Sonic3kAnimationIds.WAIT).id(), player.getAnimationId());
        if (down) assertEquals(0x9B, player.getMappingFrame(), "ROM-backed Knuckles Duck mappings");
    }

    private static void assertContinuation(AbstractPlayableSprite player, boolean down) {
        assertTrue(player.getAir());
        assertTrue(player.getAirAtFrameStart(), "continuation dispatches the air routine");
        assertFalse(player.getCrouching(), "transient ground pose expires on the neutral air tick");
        assertFalse(player.getAnimationManager().hasGroundMovementAnimSpeed(),
                "the ground movement snapshot is scoped to one dispatch");
        assertEquals((down ? Sonic3kAnimationIds.DUCK : Sonic3kAnimationIds.WAIT).id(), player.getAnimationId(),
                "air movement retains the previously published animation byte");
        if (down) assertEquals(0x9B, player.getMappingFrame());
    }

    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + " " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }
}
