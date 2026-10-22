package com.openggf.sprites.managers;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Registered restore/forward control for the slide get-up turn byte. The player,
 * controller and registry are production-booted; the slide inlet is declared
 * synthetic component state inside explicit 0..2000 camera bounds. Release
 * replays through the canonical controller's public dispatcher. This does not
 * qualify an ordinary gameplay route, terrain collision, rendering or
 * native-ROM equivalence.
 */
@RequiresRom(SonicGame.SONIC_3K)
@Isolated
class TestKnucklesSlideRegisteredRewind {
    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void releaseRestoresAndReplaysAllRegisteredStateTwice(boolean reverseGravity) throws Exception {
        var settings = new GameplayCaptureSession.Settings(
                320, "knuckles", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),
                    Sonic3kZoneIds.ZONE_AIZ, 0, settings);
            var player = assertInstanceOf(Knuckles.class, session.player());
            var movement = assertInstanceOf(PlayableSpriteMovement.class, player.getMovementManager());
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            assertSame(player, GameServices.sprites().getSprite(player.getCode()));

            Field heldJump = PlayableSpriteMovement.class.getDeclaredField("inputJump");
            heldJump.setAccessible(true);

            // Declared component inlet, not a trace row or an input-authored route.
            var camera = GameServices.camera();
            camera.setMinX((short) 0);
            camera.setMaxX((short) 2000);
            camera.setMinY((short) 0);
            camera.setMaxY((short) 2000);
            GameServices.gameState().setReverseGravityActive(reverseGravity);
            player.setAir(true);
            player.setJumping(true);
            player.setDoubleJumpFlag(3);
            player.setDoubleJumpProperty((byte) 0x80);
            player.setAbilityMappingFrameControl(true);
            player.setMappingFrame(0xCC);
            player.setForcedAnimationId(0x20);
            player.applyCustomRadii(10, 10);
            player.setCentreX((short) 0x400);
            player.setCentreY((short) 0x400);
            player.setSubpixelRaw(0xA500, 0xB600);
            player.setGSpeed((short) 0x400);
            player.setXSpeed((short) 0x100);
            player.setYSpeed((short) 0);
            heldJump.setBoolean(movement, false);

            var inlet = registry.capture();
            assertTrue(inlet.entries().containsKey("sprites"), "the registered player must be captured");
            assertWorld(inlet, registry.capture(), "repeated inlet capture");
            release(movement);
            assertGetUp(player);
            var forward = registry.capture();
            assertFalse(RewindSnapshotDiff.keyEquals("sprites", inlet.get("sprites"), forward.get("sprites")),
                    "the captured player state must actually cross the get-up boundary");

            for (int cycle = 0; cycle < 2; cycle++) {
                // Prove that registered restore reaches this canonical controller,
                // rather than accidentally replaying a detached movement manager.
                heldJump.setBoolean(movement, true);
                player.setDoubleJumpProperty((byte) 0);
                registry.restore(inlet);
                assertSame(player, session.player());
                assertSame(player, GameServices.sprites().getSprite(player.getCode()));
                assertSame(movement, player.getMovementManager());
                assertFalse(heldJump.getBoolean(movement), "registered restore owns held input");
                assertEquals((byte) 0x80, player.getDoubleJumpProperty());
                assertTrue(player.getAir());
                assertEquals(3, player.getDoubleJumpFlag());
                assertWorld(inlet, registry.capture(), "inlet restore " + cycle);

                release(movement);
                assertGetUp(player);
                assertWorld(forward, registry.capture(), "get-up replay " + cycle);

                registry.restore(forward);
                assertSame(movement, player.getMovementManager());
                assertGetUp(player);
                assertWorld(forward, registry.capture(), "get-up restore " + cycle);
            }
        }
    }

    private static void release(PlayableSpriteMovement movement) {
        movement.handleMovement(false, false, false, false,
                false, false, false, false);
    }

    private static void assertGetUp(Knuckles player) {
        assertEquals((byte) 0x80, player.getDoubleJumpProperty(), "slide get-up retains its turn byte");
        assertFalse(player.getAir());
        assertFalse(player.isJumping());
        assertEquals(0, player.getDoubleJumpFlag());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
        assertEquals(15, player.getMoveLockTimer());
        assertEquals(player.getStandXRadius(), player.getXRadius());
        assertEquals(player.getStandYRadius(), player.getYRadius());
    }

    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context + " all registered keys");
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> context + " " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }
}
