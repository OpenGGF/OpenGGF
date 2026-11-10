package com.openggf.game.rewind;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.CameraSnapshot;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;
import com.openggf.game.sonic3k.objects.HCZ2WallObjectInstance;
import com.openggf.game.sonic3k.scroll.SwScrlHcz;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HCZ2 completed-publication and object-slot rewind contract, original-E 2026-11-10.
 * HCZ2_BackgroundInit selects the wall arena (sonic3k.asm:106046-106071);
 * HCZ2_WallMove starts at P1 X=$680 (106180-106223), and ShakeScreen_Setup
 * selects the continuous table by LF & $3F (104234-104256). Obj_HCZ2Wall
 * reads the preceding accumulator in its own slot (106277-106290), before
 * the next ScreenEvents dispatch, and derives x=-offset+$5BE,y=$700.
 *
 * Only the public component start is declared. All producer state comes from
 * ordinary frames. Expected slot-consumption/restore/forward failures are final
 * oracles, not setup gates; unexpected frame/capture/reflection errors stop.
 * The first warm step completes the production fresh-assembly inlet. Every
 * following ordinary frame checks the preceding completed accumulator explicitly.
 * No movie/aux data, phase/timer/LF/shake/link setter is used. These moving and
 * naturally stationary windows do not certify held/refresh or whole-route states.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHczMovingWallShakeRewind {
    private static final int CONTINUOUS_PERIOD = 0x40; // native LF mask $3F + 1
    private static final int WALL_TRIGGER_X = 0x680;
    private static final int STOCK_START_X = 0x170;
    private static final int STOCK_START_Y = 0x82C;
    private static final int WALL_X_BASE = 0x5BE;
    private static final int WALL_Y = 0x700;

    @Test
    void movingWallRestoresSavedCameraAndReplaysBothCompleteWorldBranches() {
        var fixture = fixtureAt(WALL_TRIGGER_X);
        var owners = owners();
        ordinaryFrame(fixture); // Complete the production fresh inlet and first screen publication.
        var a = observe(fixture, owners);
        assertOriginalGeneration(fixture, a, true);
        List<Executable> oracles = new ArrayList<>();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var worldA = registry.capture();
        assertEquals(a.camera(), worldA.get("camera"), "same completed A publication");

        Observation b = null;
        int forwardFrames = 0;
        // Discover the first actual different shake AND registered wall integer
        // offset/X within the source's finite period. Never choose an LF phase,
        // a coincident offset, a stopped wall or a fabricated handler value.
        for (int frame = 1; frame <= CONTINUOUS_PERIOD; frame++) {
            var candidate = advanceAndObserve(fixture, owners, oracles, "original moving frame " + frame);
            assertOriginalGeneration(fixture, candidate, true);
            if (candidate.camera().shakeOffsetY() != a.camera().shakeOffsetY()
                    && candidate.walls().getFirst().pose().offset() != a.walls().getFirst().pose().offset()
                    && candidate.walls().getFirst().pose().x() != a.walls().getFirst().pose().x()) {
                b = candidate;
                forwardFrames = frame;
                break;
            }
        }
        assertNotNull(b, "prerequisite: natural shake and managed integer wall change within LF mask period");
        assertNotEquals(a.camera().shakeOffsetY(), b.camera().shakeOffsetY());
        assertNotEquals(a.walls().getFirst().pose().offset(), b.walls().getFirst().pose().offset());
        assertNotEquals(a.walls().getFirst().pose().x(), b.walls().getFirst().pose().x());
        var worldB = registry.capture();
        assertEquals(b.camera(), worldB.get("camera"), "same completed B publication");
        replayTwiceThenAssert(fixture, owners, a, worldA, b, worldB, forwardFrames, oracles);
    }

    @Test
    void naturallyStationaryWallHasTheSameCompleteRestoreAndForwardContract() {
        // Stock HCZ2 start is below $680 but inside the real camera-selected
        // wall arena. The event remains enabled; no phase or shake is disabled.
        var fixture = fixtureAt(STOCK_START_X);
        var owners = owners();
        ordinaryFrame(fixture);
        var a = observe(fixture, owners);
        assertOriginalGeneration(fixture, a, false);
        List<Executable> oracles = new ArrayList<>();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var worldA = registry.capture();
        assertEquals(a.camera(), worldA.get("camera"));
        var b = advanceAndObserve(fixture, owners, oracles, "original stationary frame");
        assertOriginalGeneration(fixture, b, false);
        assertEquals(0, a.camera().shakeOffsetY());
        assertEquals(0, b.camera().shakeOffsetY());
        assertEquals(a.walls(), b.walls(), "naturally inactive movement, not a disabled event");
        var worldB = registry.capture();
        assertEquals(b.camera(), worldB.get("camera"));
        replayTwiceThenAssert(fixture, owners, a, worldA, b, worldB, 1, oracles);
    }

    private static void replayTwiceThenAssert(HeadlessTestFixture fixture, Owners owners,
                                             Observation a, CompositeSnapshot worldA,
                                             Observation b, CompositeSnapshot worldB, int frames,
                                             List<Executable> oracles) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        for (int replay = 1; replay <= 2; replay++) {
            String label = "restore/forward branch " + replay;
            registry.restore(worldA); // includes the production derived-state reconciliation
            var immediate = observe(fixture, owners);
            var restoredWorld = registry.capture();
            var immediateDiff = differences(worldA, restoredWorld);
            oracles.add(() -> assertEquals(a.camera(), immediate.camera(), label + " immediate Camera"));
            oracles.add(() -> assertTrue(immediateDiff.isEmpty(),
                    () -> label + " immediate full registry: " + immediateDiff));
            addWallOracles(oracles, label + " immediate", immediate, a);

            // No expected restore/slot equality assertion preempts either branch.
            // The same number of the same ordinary zero-input frames is replayed
            // from the actual restored A, even if its publication or pose is wrong.
            for (int frame = 0; frame < frames; frame++) {
                advanceAndObserve(fixture, owners, oracles, label + " ordinary frame " + frame);
            }
            var forward = observe(fixture, owners);
            var forwardWorld = registry.capture();
            var forwardDiff = differences(worldB, forwardWorld);
            oracles.add(() -> assertEquals(b.camera(), forward.camera(), label + " forward B Camera"));
            oracles.add(() -> assertTrue(forwardDiff.isEmpty(),
                    () -> label + " forward B full registry: " + forwardDiff));
            addWallOracles(oracles, label + " forward B", forward, b);
        }
        // Both honest branches finished before ANY expected contract assertion.
        // No key is removed; each key uses the existing complete equality rule.
        assertAll("saved Camera / preceding wall publication / identity / full-world replay", oracles);
    }

    private static Observation advanceAndObserve(HeadlessTestFixture fixture, Owners owners,
                                                 List<Executable> oracles, String label) {
        int precedingAccumulator = owners.events().getWallOffsetFixed();
        ordinaryFrame(fixture);
        var completed = observe(fixture, owners);
        int precedingHighWord = precedingAccumulator >> 16;
        var expected = new WallPose(precedingHighWord,
                (short) (WALL_X_BASE - precedingHighWord), WALL_Y);
        var actual = completed.walls().stream().map(ManagedWall::pose).toList();
        oracles.add(() -> assertEquals(List.of(expected), actual,
                label + " wall slot consumes preceding completed Events_bg, not this frame's next publication"));
        return completed;
    }

    private static void addWallOracles(List<Executable> oracles, String label,
                                      Observation actual, Observation expected) {
        oracles.add(() -> assertEquals(expected.walls(), actual.walls(),
                label + " registered wall identity and captured latched pose"));
        oracles.add(() -> assertEquals(1, actual.walls().size(), label + " one managed wall"));
        oracles.add(() -> assertTrue(actual.walls().stream().allMatch(wall -> wall.identity() != null),
                label + " every managed wall has a real registered identity"));
        oracles.add(() -> assertEquals(expected.publication(), actual.publication(),
                label + " canonical completed screen publication"));
        oracles.add(() -> assertEquals(expected.shakeFlag(), actual.shakeFlag(), label + " shared shake flag"));
        oracles.add(() -> assertEquals(actual.publication().shakeOffset(), actual.handlerShake(),
                label + " scroll consumes the captured applied shake"));
        oracles.add(() -> assertEquals(actual.publication().backgroundX(), actual.handlerBgX(),
                label + " scroll consumes the captured BG X"));
        oracles.add(() -> assertEquals((short) actual.publication().backgroundY(), actual.handlerBgY(),
                label + " scroll consumes the captured BG Y"));
    }

    private static List<String> differences(CompositeSnapshot expected, CompositeSnapshot actual) {
        List<String> differences = new ArrayList<>();
        if (!expected.entries().keySet().equals(actual.entries().keySet())) {
            differences.add("registry keys: expected=" + expected.entries().keySet()
                    + " actual=" + actual.entries().keySet());
        }
        var keys = new LinkedHashSet<>(expected.entries().keySet());
        keys.addAll(actual.entries().keySet());
        for (String key : keys) {
            // keyEquals compares the complete key. diffKey's 20-line preview
            // bounds failure text only; it never replaces the equality oracle.
            if (!RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key))) {
                differences.add(key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
            }
        }
        return List.copyOf(differences);
    }

    private static HeadlessTestFixture fixtureAt(int centreX) {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 1).withSkippedZoneIntro()
                .startPosition((short) centreX, (short) STOCK_START_Y).startPositionIsCentre().build();
        assertEquals(320, fixture.camera().getWidth(), "declared native viewport");
        assertTrue(fixture.camera().getX() < 0xC00 && fixture.camera().getY() >= 0x500,
                "prerequisite: public positioned entry actually selects HCZ2 wall background");
        return fixture;
    }

    private static Owners owners() {
        var manager = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider());
        var events = manager.getHczEvents();
        assertNotNull(events, "prerequisite: real HCZ event owner");
        // Both lookups return already loaded references; no handler factory/init
        // or event update is called for observation.
        var handler = assertInstanceOf(SwScrlHcz.class,
                GameServices.parallax().getHandler(Sonic3kZoneIds.ZONE_HCZ));
        return new Owners(events, handler);
    }

    private static void ordinaryFrame(HeadlessTestFixture fixture) {
        int lf = GameServices.level().getFrameCounter() & 0xFFFF;
        fixture.stepFrame(false, false, false, false, false);
        assertEquals((lf + 1) & 0xFFFF, GameServices.level().getFrameCounter() & 0xFFFF,
                "prerequisite: exactly one ordinary level frame");
        assertFalse(fixture.sprite().getDead(), "prerequisite: unrelated death");
        assertFalse(fixture.sprite().isObjectControlled(), "prerequisite: unrelated control takeover");
    }

    private static Observation observe(HeadlessTestFixture fixture, Owners owners) {
        // ObjectInstanceQueries.activeObjectsOfType only collects references and
        // sorts on stored slots. No isDestroyed/spawn/provider callback is read.
        var objects = GameServices.level().getObjectManager();
        var managed = objects.activeObjectsOfType(HCZ2WallObjectInstance.class);
        var identities = objects.captureIdentityContext().requireIdentityTable();
        return new Observation(fixture.camera().capture(),
                owners.events().getWallOffsetFixed(), owners.events().getAct2BgRoutine(),
                owners.events().getScreenShakeFlag(), owners.events().screenPublication(),
                owners.events().isEventsFg5(), owners.events().isCutsceneActive(),
                owners.handler().getHcz2BgPhase(), owners.handler().getShakeOffsetY(),
                owners.handler().getBgCameraX(), owners.handler().getVscrollFactorBG(),
                managed.stream().map(wall -> new ManagedWall(identities.idFor(wall), wallPose(wall))).toList());
    }

    private static void assertOriginalGeneration(HeadlessTestFixture fixture, Observation point, boolean moving) {
        assertEquals(4, point.bgRoutine(), "prerequisite: genuine wall-move event dispatch");
        assertTrue(point.publication().valid(), "prerequisite: completed native screen publication");
        assertTrue(point.publication().wallPlane());
        assertEquals(SwScrlHcz.Hcz2BgPhase.WALL_CHASE, point.phase());
        assertTrue((point.eventOffsetFixed() >> 16) > -0x600, "prerequisite: wall has not reached its stop");
        assertFalse(point.eventsFg5());
        assertFalse(point.cutscene());
        assertOriginalManagedWall(point);
        assertEquals(point.publication().shakeOffset(), point.handlerShake());
        assertEquals(point.handlerShake(), point.camera().shakeOffsetY(), "actual completed shake publication");
        assertEquals(point.publication().backgroundX(), point.handlerBgX());
        assertEquals((short) point.publication().backgroundY(), point.handlerBgY());
        if (moving) {
            assertTrue(point.eventOffsetFixed() < 0);
            assertTrue(point.shakeFlag() < 0, "native constant shake remains enabled");
        } else {
            assertTrue(fixture.sprite().getCentreX() < WALL_TRIGGER_X);
            assertEquals(0, point.eventOffsetFixed());
            assertEquals(0, point.shakeFlag());
        }
    }

    private static void assertOriginalManagedWall(Observation point) {
        assertEquals(1, point.walls().size(), "prerequisite: original unique managed wall");
        var wall = point.walls().getFirst();
        assertNotNull(wall.identity(), "prerequisite: managed wall is registered under its own identity");
        assertEquals((short) (WALL_X_BASE - wall.pose().offset()), wall.pose().x());
        assertEquals(WALL_Y, wall.pose().y());
    }

    private static WallPose wallPose(HCZ2WallObjectInstance wall) {
        try {
            var field = HCZ2WallObjectInstance.class.getDeclaredField("wallOffsetSupplierValue");
            field.setAccessible(true);
            // Concrete getters are pure arithmetic/constant reads, not generic actor callbacks.
            return new WallPose(field.getInt(wall), wall.getX(), wall.getY());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("prerequisite: existing wall-offset projection", failure);
        }
    }

    private record Owners(Sonic3kHCZEvents events, SwScrlHcz handler) { }
    private record WallPose(int offset, int x, int y) { }
    private record ManagedWall(ObjectRefId identity, WallPose pose) { }
    private record Observation(CameraSnapshot camera, int eventOffsetFixed,
                               int bgRoutine, int shakeFlag, Sonic3kHCZEvents.ScreenPublication publication,
                               boolean eventsFg5, boolean cutscene, SwScrlHcz.Hcz2BgPhase phase,
                               int handlerShake, int handlerBgX, short handlerBgY, List<ManagedWall> walls) { }
}
