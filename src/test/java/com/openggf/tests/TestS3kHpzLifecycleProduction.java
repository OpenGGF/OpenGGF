package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputHandler;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.TestS3kHpzCompatibilityMatrix.Scenario;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Hidden Palace ($1601) checkpoint/death reload and Knuckles' teleporter exit through the production loop. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHpzLifecycleProduction {
    /** Object Pos/1.bin: StarPost $34 subtype 2 at ($CF0,$3E8). */
    private static final int POST_INDEX = 2;
    private static final int POST_X = 0xCF0;
    private static final int POST_Y = 0x3E8;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    static Stream<Scenario> scenarios() {
        return TestS3kHpzCompatibilityMatrix.scenarios();
    }

    static Stream<Scenario> legacyGeometryScenarios() {
        return TestS3kHpzCompatibilityMatrix.legacyGeometryScenarios();
    }

    @ParameterizedTest(name = "HPZ physical checkpoint and two deaths {0}")
    @MethodSource("scenarios")
    void touchingThePlacedStarPostThenDyingReloadsAtThePost(Scenario row) {
        checkCheckpointLifecycle(row);
    }

    @ParameterizedTest(name = "legacy custom-width checkpoint {0}")
    @MethodSource("legacyGeometryScenarios")
    void legacyCustomWidthRetainsPostAndKnucklesRefusalGeometry(Scenario row) {
        checkCheckpointLifecycle(row);
    }

    private void checkCheckpointLifecycle(Scenario row) {
        var fixture = TestS3kHpzCompatibilityMatrix.boot(row, POST_X - 24, POST_Y + 4);
        var player = fixture.sprite();
        assertFalse(GameServices.level().getCheckpointState().isActive());
        for (int i = 0; i < 60 && !GameServices.level().getCheckpointState().isActive(); i++) {
            fixture.stepFrame(false, false, false, true, false);
        }
        if (row.main().equals("knuckles")) {
            // HPZ_ScreenInit sets Knuckles' Camera_max_X_pos to $AA0, so the level's right
            // boundary ($AA0 + 320 - 24) keeps him far left of this Sonic/Tails-route post.
            assertFalse(GameServices.level().getCheckpointState().isActive());
            assertTrue((player.getCentreX() & 0xFFFF) <= 0xAA0 + 320 - 24,
                    "Knuckles is held at the $AA0 camera boundary, was $"
                            + Integer.toHexString(player.getCentreX() & 0xFFFF));
            TestS3kHpzCompatibilityMatrix.assertScenario(row);
            return;
        }
        assertEquals(POST_INDEX, GameServices.level().getCheckpointState().getLastCheckpointIndex(),
                "walking into the placed post must activate it");
        var input = new InputHandler();
        var neutral = new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, "");
        input.setLogicalOverride(com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(neutral, neutral));
        var loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        try {
            for (int cycle = 0; cycle < 2; cycle++) {
                advance(fixture, loop, 30);
                var manager = GameServices.level().getObjectManager();
                var oldRuntime = GameServices.zoneRuntimeRegistry().current();
                var rewind = fixture.gameplayMode().getRewindController();
                assertNotNull(rewind, "production live history is enabled");
                int largestOutgoingFrame = rewind.currentFrame();
                assertTrue(GameServices.sprites().getMainPlayable().applyPitDeath());
                boolean reloaded = false;
                for (int i = 0; i < 1200; i++) {
                    advance(fixture, loop, 1);
                    if (GameServices.level().getObjectManager() != manager) {
                        reloaded = true;
                        assertEquals(Sonic3kZoneIds.ZONE_HPZ, GameServices.level().getCurrentZone());
                        assertEquals(1, GameServices.level().getCurrentAct());
                        var focused = GameServices.camera().getFocusedSprite();
                        assertSame(GameServices.sprites().getMainPlayable(), focused);
                        assertEquals(POST_X, focused.getCentreX() & 0xFFFF);
                        assertEquals(POST_Y, focused.getCentreY() & 0xFFFF);
                        assertFalse(focused.getDead());
                        assertNotSame(oldRuntime, GameServices.zoneRuntimeRegistry().current());
                        assertEquals(POST_INDEX, GameServices.level().getCheckpointState().getLastCheckpointIndex());
                        assertTrue(largestOutgoingFrame > 10, "death has a nonempty outgoing timeline");
                        var destinationRewind = fixture.gameplayMode().getRewindController();
                        assertTrue(destinationRewind == null
                                        || destinationRewind.currentFrame() < largestOutgoingFrame,
                                "full checkpoint load retires the outgoing history: outgoing="
                                        + largestOutgoingFrame + " destination="
                                        + (destinationRewind == null ? "retired" : destinationRewind.currentFrame()));
                        TestS3kHpzCompatibilityMatrix.assertScenario(row);
                        break;
                    }
                    var currentRewind = fixture.gameplayMode().getRewindController();
                    if (currentRewind != null) {
                        largestOutgoingFrame = Math.max(largestOutgoingFrame, currentRewind.currentFrame());
                    }
                }
                assertTrue(reloaded, "the death countdown must reach the GameLoop reload, cycle=" + cycle);
                boolean ready = false;
                for (int i = 0; i < 600 && !ready; i++) {
                    advance(fixture, loop, 1);
                    var title = GameServices.module().getTitleCardProvider();
                    ready = loop.getCurrentGameMode() == GameMode.LEVEL
                            && (title == null || title.isComplete())
                            && !fixture.gameplayMode().getFadeManager().isActive()
                            && !GameServices.level().hasPendingFreshLevelTransitionBoundary()
                            && !GameServices.sprites().getMainPlayable().isControlLocked();
                }
                assertTrue(ready, "native title/fade must release checkpoint control, cycle=" + cycle);
                TestS3kHpzCompatibilityMatrix.assertScenario(row);
                replayReleasedWorldTwice(fixture, loop, row, "checkpoint release " + cycle);
            }
        } finally {
            loop.closePresence();
        }
    }

    static Stream<Scenario> knucklesPresets() {
        return Stream.of(WidescreenAspect.values()).map(aspect -> new Scenario("knuckles", aspect, "off", ""));
    }

    static Stream<Scenario> knucklesCustomWidths() {
        return legacyGeometryScenarios().filter(row -> row.main().equals("knuckles"));
    }

    @ParameterizedTest(name = "Knuckles actual HPZ to SSZ2 load {0}")
    @MethodSource("knucklesPresets")
    void knucklesUpperTeleporterStartsSkySanctuaryActTwo(Scenario row) {
        checkKnucklesExit(row);
    }

    @ParameterizedTest(name = "legacy custom-width Knuckles exit {0}")
    @MethodSource("knucklesCustomWidths")
    void legacyCustomWidthRetainsKnucklesExitGeometry(Scenario row) {
        checkKnucklesExit(row);
    }

    private void checkKnucklesExit(Scenario row) {
        // Knuckles is supported only without donation (LaunchProfile), so width is his breadth axis.
        var fixture = TestS3kHpzCompatibilityMatrix.boot(row, 0xAF0, 0x400);
        var manager = GameServices.level().getObjectManager();
        var loop = new GameLoop(new InputHandler());
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        try {
            for (int i = 0; i < 60; i++) {
                fixture.stepFrame(false, false, false, false, false);
            }
            for (int i = 0; i < 18; i++) {
                fixture.stepFrame(false, false, false, true, true);
            }
            for (int i = 0; i < 6; i++) {
                fixture.stepFrame(false, false, false, true, false);
            }
            boolean loaded = false;
            for (int i = 0; i < 900 && !loaded; i++) {
                if (GameServices.level().getObjectManager() == manager) {
                    fixture.stepFrame(false, false, false, false, false);
                } else {
                    loaded = true;
                }
                if (!loaded && GameServices.level().getCurrentZone() != Sonic3kZoneIds.ZONE_HPZ) {
                    loaded = true;
                }
                if (!loaded && i > 400) {
                    fixture.gameplayMode().getFadeManager().update();
                    loop.step();
                }
            }
            assertTrue(loaded, "loc_45B94 must start a new level");
            assertEquals(Sonic3kZoneIds.ZONE_SSZ, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct(), "StartNewLevel #$A01");
            assertNotSame(manager, GameServices.level().getObjectManager());
            TestS3kHpzCompatibilityMatrix.assertScenario(row);
            assertEquals("knuckles", GameServices.sprites().getMainPlayable().getCode());
            assertEquals(-1, GameServices.level().getCheckpointState().getLastCheckpointIndex(),
                    "HPZ checkpoint history must not suppress SSZ's fresh arrival");
            boolean midRise = false;
            for (int frame = 0; frame < 600 && !midRise; frame++) {
                fixture.gameplayMode().getFadeManager().update(); loop.step();
                var arrivals = GameServices.level().getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.SszArrivalControllerObjectInstance.class);
                midRise = !arrivals.isEmpty() && arrivals.getFirst().phaseForTest() == 1
                        && arrivals.getFirst().riseRemainingForTest() < 50;
            }
            assertTrue(midRise, "the real HPZ load must continue into SSZ's teleporter rise");
            var registry = fixture.gameplayMode().getRewindRegistry();
            var saved = registry.capture();
            CompositeSnapshot expected = null;
            int expectedElapsed = -1;
            for (int replay = 0; replay < 3; replay++) {
                if (replay != 0) {
                    registry.restore(saved);
                    same(saved, registry.capture(), "SSZ2 rise restore " + replay);
                    TestS3kHpzCompatibilityMatrix.assertScenario(row);
                }
                int elapsed = 0;
                for (; elapsed < 1600; elapsed++) {
                    fixture.gameplayMode().getFadeManager().update(); loop.step();
                    var liveState = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState();
                    if (liveState.cutsceneFlag(5) && !GameServices.sprites().getMainPlayable().isObjectControlled()) break;
                }
                assertTrue(elapsed < 1600, "incoming crane must hand control back");
                var player = GameServices.sprites().getMainPlayable();
                assertFalse(player.getDead());
                assertFalse(player.isObjectControlled(), "incoming route must finish the crane release");
                assertEquals(1, GameServices.level().getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance.class).size());
                TestS3kHpzCompatibilityMatrix.assertScenario(row);
                var actual = registry.capture();
                if (replay == 0) { expected = actual; expectedElapsed = elapsed; }
                else {
                    assertEquals(expectedElapsed, elapsed, "same native crane release dispatch");
                    same(expected, actual, "HPZ to SSZ2 complete incoming graph replay " + replay);
                }
            }
        } finally {
            loop.closePresence();
        }
    }

    private static void advance(HeadlessTestFixture fixture, GameLoop loop, int frames) {
        for (int i = 0; i < frames; i++) {
            fixture.gameplayMode().getFadeManager().update();
            loop.step();
        }
    }

    private static void replayReleasedWorldTwice(HeadlessTestFixture fixture, GameLoop loop,
                                               Scenario row, String label) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        var saved = registry.capture();
        advance(fixture, loop, 30);
        var expected = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(saved);
            same(saved, registry.capture(), label + " restore " + cycle);
            TestS3kHpzCompatibilityMatrix.assertScenario(row);
            advance(fixture, loop, 30);
            same(expected, registry.capture(), label + " forward " + cycle);
            TestS3kHpzCompatibilityMatrix.assertScenario(row);
        }
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }
}
