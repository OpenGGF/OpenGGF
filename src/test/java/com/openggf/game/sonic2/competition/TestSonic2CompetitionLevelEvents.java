package com.openggf.game.sonic2.competition;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SingletonResetExtension.class)
class TestSonic2CompetitionLevelEvents {
    @ParameterizedTest
    @CsvSource({"0, 0x2920", "5, 0x2100", "3, 0x26A0"})
    void nativeActTwoReplacesBothRightBoundariesWithoutEnteringBossArena(int zone, int maxX) {
        var fixture = new Fixture();
        fixture.events.initLevel(zone, 1);

        fixture.events.update();

        for (var camera : new Camera[]{fixture.p1, fixture.p2}) {
            assertAll(
                    () -> assertEquals(maxX, camera.getMaxX()),
                    () -> assertEquals(maxX, camera.getMaxXTarget()),
                    () -> assertEquals(0x40, camera.getMinX()),
                    () -> assertEquals(0x60, camera.getMinY()),
                    () -> assertEquals(0x720, camera.getMaxY()),
                    () -> assertEquals(0x620, camera.getMaxYTarget()));
        }
        // Routine zero remains native routine zero, rather than advancing into
        // an ordinary boss arena when a camera crosses its ordinary trigger.
        fixture.p1.setX((short) 0x2A00);
        fixture.p1.setMaxX((short) 0x3FFF);
        fixture.p2.setMaxX((short) 0x3FFF);
        fixture.events.update();
        assertEquals(maxX, fixture.p1.getMaxX());
        assertEquals(maxX, fixture.p2.getMaxX());
        assertEquals(0x40, fixture.p1.getMinX());
        assertEquals(0x620, fixture.p1.getMaxYTarget());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5, 3})
    void nativeActOneLeavesAllBoundaryWordsUntouched(int zone) {
        var fixture = new Fixture();
        fixture.events.initLevel(zone, 0);

        fixture.events.update();

        for (var camera : new Camera[]{fixture.p1, fixture.p2}) {
            assertAll(
                    () -> assertEquals(0x40, camera.getMinX()),
                    () -> assertEquals(0x3000, camera.getMaxX()),
                    () -> assertEquals(0x60, camera.getMinY()),
                    () -> assertEquals(0x720, camera.getMaxY()),
                    () -> assertEquals(0x620, camera.getMaxYTarget()));
        }
    }

    @Test
    void secondCameraScrollsAndPublishesBeforeNativeEventBoundaryWrite() {
        var fixture = new Fixture();
        fixture.events.initLevel(0, 1);
        fixture.p1.setX((short) 0x180);
        fixture.p1.setFrozen(false);
        fixture.sonic.setCentreX((short) 0x400);
        fixture.p2.setFrozen(false);
        fixture.p2.setMaxX((short) 0x2940);
        fixture.p2.setX((short) 0x2920);
        fixture.tails.setCentreX((short) 0x3100);

        fixture.events.update();

        assertEquals(0x180, fixture.p1.getX(), "the canonical world pass owns P1 camera scroll");
        assertEquals(0x2930, fixture.p2.getX(), "P2 scroll sees the prior boundary, not this tick's $2920");
        assertEquals(0x2930, fixture.p2.getXCopy());
        assertEquals(fixture.p2.getY(), fixture.p2.getYCopy());
        assertEquals(0x2920, fixture.p2.getMaxX());
        assertEquals(0x720, fixture.p2.getMaxY(), "the ROM has no second boundary-easing tail");
    }

    @Test
    void primaryMotionSampleBracketsTheActualCameraCallAfterEarlierObjectWrites() {
        var fixture = new Fixture();
        var motions = new ArrayList<Motion>();
        var events = fixture.withMotion((slot, oldX, oldY, frozen) -> {
            assertEquals(0x180, fixture.p1.getXCopy(), "the callback precedes render-copy publication");
            motions.add(new Motion(slot, oldX, oldY, frozen, fixture.p1.getX(), fixture.p1.getY()));
        });
        events.initLevel(0, 0);
        fixture.p1.setFrozen(false);
        fixture.p1.setX((short) 0x180);
        fixture.p1.setY((short) 0x100);
        fixture.sonic.setCentreX((short) 0x500);
        fixture.sonic.setCentreY((short) 0x160);
        // An object can write live camera RAM before DeformBgLayer. That write
        // is not part of ScrollHoriz's native displacement WORD.
        fixture.p1.setXAfterRenderCopy((short) 0x200);

        events.updateAfterObjectsBeforeCamera();
        assertTrue(motions.isEmpty());
        fixture.p1.updatePosition();
        assertEquals(0x210, fixture.p1.getX());
        events.updateFixedInLevelObjects();

        assertEquals(List.of(new Motion(0, (short) 0x200, (short) 0x100, false,
                (short) 0x210, (short) 0x100)), motions);
        events.updateFixedInLevelObjects();
        assertEquals(1, motions.size(), "the primary sample belongs to one camera call only");
    }

    @Test
    void secondaryMotionCallbackPrecedesItsRenderCopyAndBothDynamicBoundaryWrites() {
        var fixture = new Fixture();
        var motions = new ArrayList<Motion>();
        var events = fixture.withMotion((slot, oldX, oldY, frozen) -> {
            assertEquals(1, slot);
            assertEquals(0x2920, fixture.p2.getXCopy());
            assertEquals(0x2940, fixture.p2.getMaxX());
            assertEquals(0x3000, fixture.p1.getMaxX());
            motions.add(new Motion(slot, oldX, oldY, frozen, fixture.p2.getX(), fixture.p2.getY()));
        });
        events.initLevel(0, 1);
        fixture.p2.setFrozen(false);
        fixture.p2.setMaxX((short) 0x2940);
        fixture.p2.setX((short) 0x2920);
        fixture.p2.setY((short) 0x100);
        fixture.tails.setCentreX((short) 0x3100);
        fixture.tails.setCentreY((short) 0x160);

        events.update();

        assertEquals(List.of(new Motion(1, (short) 0x2920, (short) 0x100, false,
                (short) 0x2930, (short) 0x100)), motions);
        assertEquals(0x2930, fixture.p2.getXCopy());
        assertEquals(0x2920, fixture.p1.getMaxX());
        assertEquals(0x2920, fixture.p2.getMaxX());
    }

    @ParameterizedTest
    @CsvSource({"0, false", "0, true", "1, false", "1, true"})
    void completedStationaryAndFrozenCallsRemainDistinctAtTheMotionPort(int slot, boolean frozen) {
        var fixture = new Fixture();
        var camera = slot == 0 ? fixture.p1 : fixture.p2;
        var actor = slot == 0 ? fixture.sonic : fixture.tails;
        camera.setX((short) 0x200);
        camera.setY((short) 0x100);
        camera.setFrozen(frozen);
        actor.setCentreX((short) 0x2A0);
        actor.setCentreY((short) 0x160);
        var motions = new ArrayList<Motion>();
        var events = fixture.withMotion((completedSlot, oldX, oldY, wasFrozen) -> {
            motions.add(new Motion(completedSlot, oldX, oldY, wasFrozen, camera.getX(), camera.getY()));
        });
        events.initLevel(0, 0);

        if (slot == 0) {
            events.updateAfterObjectsBeforeCamera();
            camera.updatePosition();
            events.updateFixedInLevelObjects();
        } else {
            events.update();
        }

        assertEquals(List.of(new Motion(slot, (short) 0x200, (short) 0x100, frozen,
                (short) 0x200, (short) 0x100)), motions);
    }

    @Test
    void explicitClearAndLevelInitializationDiscardUncompletedPrimarySamples() {
        var fixture = new Fixture();
        var motions = new ArrayList<Motion>();
        var events = fixture.withMotion((slot, oldX, oldY, frozen) ->
                motions.add(new Motion(slot, oldX, oldY, frozen, fixture.p1.getX(), fixture.p1.getY())));
        events.initLevel(0, 0);
        fixture.p1.setX((short) 0x200);
        events.updateAfterObjectsBeforeCamera();

        events.clearCameraSample();
        events.updateFixedInLevelObjects();
        assertTrue(motions.isEmpty());
        events.updateAfterObjectsBeforeCamera();
        events.initLevel(5, 1);
        events.updateFixedInLevelObjects();
        assertTrue(motions.isEmpty(), "a new act cannot complete the outgoing camera call");

        fixture.p1.setX((short) 0x300);
        events.updateAfterObjectsBeforeCamera();
        fixture.p1.updatePosition();
        events.updateFixedInLevelObjects();
        assertEquals(List.of(new Motion(0, (short) 0x300, fixture.p1.getY(), true,
                (short) 0x300, fixture.p1.getY())), motions);
    }

    @Test
    void throwingPrimaryCallbackHasAlreadyConsumedTheSampleBeforeAnotherContinuation() {
        var fixture = new Fixture();
        var motions = new ArrayList<Motion>();
        var failure = new IllegalStateException("owned camera callback failure");
        var events = fixture.withMotion((slot, oldX, oldY, frozen) -> {
            motions.add(new Motion(slot, oldX, oldY, frozen, fixture.p1.getX(), fixture.p1.getY()));
            if (motions.size() == 1) throw failure;
        });
        events.initLevel(0, 0);
        fixture.p1.setX((short) 0x200);
        events.updateAfterObjectsBeforeCamera();
        fixture.p1.updatePosition();

        assertSame(failure, assertThrows(IllegalStateException.class, events::updateFixedInLevelObjects));
        events.updateFixedInLevelObjects();
        assertEquals(1, motions.size(), "a failed callback cannot replay the consumed sample");

        fixture.p1.setX((short) 0x300);
        events.updateAfterObjectsBeforeCamera();
        fixture.p1.updatePosition();
        events.updateFixedInLevelObjects();
        assertEquals(2, motions.size());
        assertEquals(0x300, motions.get(1).oldX(), "the new call must sample current camera RAM");
    }

    @Test
    void throwingSecondaryCallbackDoesNotPublishCopiesOrEventsAndRetryTakesAFreshSample() {
        var fixture = new Fixture();
        var motions = new ArrayList<Motion>();
        var failure = new IllegalStateException("owned secondary callback failure");
        var events = fixture.withMotion((slot, oldX, oldY, frozen) -> {
            motions.add(new Motion(slot, oldX, oldY, frozen, fixture.p2.getX(), fixture.p2.getY()));
            if (motions.size() == 1) throw failure;
        });
        events.initLevel(0, 1);
        fixture.p2.setFrozen(false);
        fixture.p2.setMaxX((short) 0x2940);
        fixture.p2.setX((short) 0x2920);
        fixture.p2.setY((short) 0x100);
        fixture.tails.setCentreX((short) 0x3100);
        fixture.tails.setCentreY((short) 0x160);

        assertSame(failure, assertThrows(IllegalStateException.class, events::update));
        assertEquals(0x2930, fixture.p2.getX(), "the real scroll preceded the failing consumer");
        assertEquals(0x2920, fixture.p2.getXCopy());
        assertEquals(0x2940, fixture.p2.getMaxX());
        assertEquals(0x3000, fixture.p1.getMaxX());

        events.update();
        assertEquals(2, motions.size());
        assertEquals(0x2930, motions.get(1).oldX());
        assertEquals(0x2940, fixture.p2.getXCopy());
        assertEquals(0x2920, fixture.p1.getMaxX());
        assertEquals(0x2920, fixture.p2.getMaxX());
    }

    @Test
    void activeTeleportRejectsBeforeEitherCameraPathAndClearsTheEarlierPrimarySample() {
        var fixture = new Fixture();
        var motions = new ArrayList<Motion>();
        var events = fixture.withMotion((slot, oldX, oldY, frozen) ->
                motions.add(new Motion(slot, oldX, oldY, frozen, (short) 0, (short) 0)));
        events.initLevel(0, 1);
        fixture.p1.setFrozen(false);
        fixture.p2.setFrozen(false);
        fixture.p1.setX((short) 0x200);
        fixture.p2.setX((short) 0x300);
        fixture.sonic.setCentreX((short) 0x700);
        fixture.tails.setCentreX((short) 0x800);
        events.updateAfterObjectsBeforeCamera();
        fixture.session.updateMenu(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()), true);
        // Exercise only the existing Session flag-admission boundary. No actor
        // swap is modeled or qualified by this camera-path rejection test.
        var host = mock(Sonic2CompetitionHost.class);
        when(host.swapNativeActorWorldState()).thenReturn(true);
        assertTrue(fixture.session.teleportInitializedMonitor(host));
        assertEquals(0x40, fixture.session.teleportTimer());

        assertThrows(UnsupportedOperationException.class, events::updateAfterObjectsBeforeCamera);
        assertThrows(UnsupportedOperationException.class, events::update);
        events.updateFixedInLevelObjects();

        assertTrue(motions.isEmpty());
        assertEquals(0x200, fixture.p1.getX());
        assertEquals(0x200, fixture.p1.getXCopy());
        assertEquals(0x300, fixture.p2.getX());
        assertEquals(0x300, fixture.p2.getXCopy());
        assertEquals(0x3000, fixture.p1.getMaxX());
        assertEquals(0x3000, fixture.p2.getMaxX());
    }

    @Test
    void signpostTailLocksP1ThenDefersEligibleP2UntilTheNextTail() {
        var fixture = new Fixture();
        fixture.events.initLevel(0, 0);
        fixture.p1.setX((short) 0x2F00);
        fixture.p2.setX((short) 0x2F00);

        fixture.events.updateAtLevelLoopTail();

        assertEquals(0x2F00, fixture.p1.getMinX());
        assertEquals(0x40, fixture.p2.getMinX(), "native P1 write returns without visiting P2");

        fixture.events.updateAtLevelLoopTail();

        assertEquals(0x2F00, fixture.p2.getMinX(), "P1's existing lock falls through to P2");
    }

    @ParameterizedTest
    @CsvSource({"0x2EFF, false", "0x2F00, true"})
    void p1BelowThresholdOrPausedFallsThroughToP2(int p1X, boolean paused) {
        var fixture = new Fixture();
        fixture.events.initLevel(5, 0);
        fixture.p1.setX((short) p1X);
        fixture.p2.setX((short) 0x2F00);
        if (paused) fixture.session.playerState(0).pauseTimer();

        fixture.events.updateAtLevelLoopTail();

        assertEquals(0x40, fixture.p1.getMinX());
        assertEquals(0x2F00, fixture.p2.getMinX());
    }

    @ParameterizedTest
    @CsvSource({"0x2EFF, false", "0x2F00, true"})
    void p2BelowThresholdOrPausedDoesNotLockItsLeftBoundary(int p2X, boolean paused) {
        var fixture = new Fixture();
        fixture.events.initLevel(3, 0);
        fixture.p1.setX((short) 0x100);
        fixture.p2.setX((short) p2X);
        if (paused) fixture.session.playerState(1).pauseTimer();

        fixture.events.updateAtLevelLoopTail();

        assertEquals(0x40, fixture.p1.getMinX());
        assertEquals(0x40, fixture.p2.getMinX());
    }

    @Test
    void debugPlacementSuppressesBothPlayersSignpostBoundaryUpdates() {
        var fixture = new Fixture();
        fixture.events.initLevel(0, 0);
        fixture.p1.setX((short) 0x2F00);
        fixture.p2.setX((short) 0x2F00);
        fixture.sonic.setDebugMode(true);

        fixture.events.updateAtLevelLoopTail();

        assertEquals(0x40, fixture.p1.getMinX());
        assertEquals(0x40, fixture.p2.getMinX());
    }

    @ParameterizedTest
    @CsvSource({"0x9000, 0x100, 0x8F00", "0x8080, 0, 0x40"})
    void signpostThresholdUsesWrappedWordSubtractionAndSignedComparison(int maxX, int x, int minX) {
        var fixture = new Fixture();
        fixture.events.initLevel(0, 0);
        fixture.p1.setMaxX((short) maxX);
        fixture.p1.setX((short) x);
        fixture.session.playerState(1).pauseTimer();

        fixture.events.updateAtLevelLoopTail();

        assertEquals((short) minX, fixture.p1.getMinX());
    }

    @ParameterizedTest
    @CsvSource({"1, 0", "11, 0", "12, 0", "0, -1", "5, 2", "3, 2"})
    void initializationRejectsUnsupportedLogicalZonesAndActs(int zone, int act) {
        var fixture = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> fixture.events.initLevel(zone, act));
    }

    private record Motion(int slot, short oldX, short oldY, boolean frozen, short newX, short newY) { }

    private static final class Fixture {
        final Sonic2CompetitionSession session = new Sonic2CompetitionSession();
        final Sonic sonic = new Sonic("sonic", (short) 0x100, (short) 0x100);
        final Tails tails = new Tails("tails", (short) 0x100, (short) 0x100);
        final Camera p1;
        final Camera p2;
        final Sonic2CompetitionLevelEvents events;

        Fixture() {
            var config = SonicConfigurationService.getInstance();
            config.setConfigValue(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
            config.setConfigValue(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
            p1 = new Camera(config);
            p2 = new Camera(config);
            session.bindPlayers(sonic, tails);
            session.bindCameras(p1, p2);
            p1.setFocusedSprite(sonic);
            p2.setFocusedSprite(tails);
            for (var camera : new Camera[]{p1, p2}) {
                camera.setFrozen(true);
                camera.setMinX((short) 0x40);
                camera.setMaxX((short) 0x3000);
                camera.setMinY((short) 0x60);
                camera.setMaxY((short) 0x720);
                camera.setMaxYTarget((short) 0x620);
            }
            events = new Sonic2CompetitionLevelEvents(session);
        }

        Sonic2CompetitionLevelEvents withMotion(Sonic2CompetitionLevelEvents.CameraMotion motion) {
            return new Sonic2CompetitionLevelEvents(session, null, null, motion);
        }
    }
}
