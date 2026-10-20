package com.openggf.game.rewind;

import com.openggf.game.GameServices;
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
 * R55 independent joint HCZ2 producer/publication/relink rewind contract.
 * Native044 sonic3k.asm: HCZ2_BackgroundInit106041 selects the wall arena;
 * HCZ2_WallMove106175/loc_5102E starts at P1 X=$680; ShakeScreen_Setup104229
 * selects the continuous table by LF & $3F; Obj_HCZ2Wall106272 reads Events_bg
 * on every update and derives x=-offset+$5BE, y=$700.
 *
 * Only the public component start is declared. All producer state comes from
 * ordinary frames. Expected restore/link/forward equality failures are final
 * oracles, not setup gates; unexpected frame/capture/reflection errors stop.
 * This joint component cannot identify an actual R52 caller or isolate two
 * causal faults. No movie/aux data, phase/timer/LF/shake/link setter is used.
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
        ordinaryFrame(fixture); // BG init falls through to the first genuine wall movement
        var a = observe(fixture, owners);
        assertOriginalGeneration(fixture, a, true);
        assertOriginalBinding(a);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var worldA = registry.capture();
        assertEquals(a.camera(), worldA.get("camera"), "same completed A publication");

        Observation b = null;
        int forwardFrames = 0;
        // Discover the first actual different shake AND registered wall integer
        // offset/X within the source's finite period. Never choose an LF phase,
        // a coincident offset, a stopped wall or a fabricated handler value.
        for (int frame = 1; frame <= CONTINUOUS_PERIOD; frame++) {
            ordinaryFrame(fixture);
            var candidate = observe(fixture, owners);
            assertOriginalGeneration(fixture, candidate, true);
            if (candidate.camera().shakeOffsetY() != a.camera().shakeOffsetY()
                    && candidate.walls().getFirst().offset() != a.walls().getFirst().offset()
                    && candidate.walls().getFirst().x() != a.walls().getFirst().x()) {
                b = candidate;
                forwardFrames = frame;
                break;
            }
        }
        assertNotNull(b, "prerequisite: natural shake and managed integer wall change within LF mask period");
        assertNotEquals(a.camera().shakeOffsetY(), b.camera().shakeOffsetY());
        assertNotEquals(a.walls().getFirst().offset(), b.walls().getFirst().offset());
        assertNotEquals(a.walls().getFirst().x(), b.walls().getFirst().x());
        var worldB = registry.capture();
        assertEquals(b.camera(), worldB.get("camera"), "same completed B publication");
        replayTwiceThenAssert(fixture, owners, a, worldA, b, worldB, forwardFrames);
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
        assertOriginalBinding(a);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var worldA = registry.capture();
        assertEquals(a.camera(), worldA.get("camera"));
        ordinaryFrame(fixture);
        var b = observe(fixture, owners);
        assertOriginalGeneration(fixture, b, false);
        assertEquals(0, a.camera().shakeOffsetY());
        assertEquals(0, b.camera().shakeOffsetY());
        assertEquals(a.walls(), b.walls(), "naturally inactive movement, not a disabled event");
        var worldB = registry.capture();
        assertEquals(b.camera(), worldB.get("camera"));
        replayTwiceThenAssert(fixture, owners, a, worldA, b, worldB, 1);
    }

    private static void replayTwiceThenAssert(HeadlessTestFixture fixture, Owners owners,
                                             Observation a, CompositeSnapshot worldA,
                                             Observation b, CompositeSnapshot worldB, int frames) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        List<Executable> oracles = new ArrayList<>();
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

            // No equality/expected linkage assertion preempts either branch.
            // The same number of the same ordinary zero-input frames is replayed
            // from the actual restored A, even if its publication/link is wrong.
            for (int frame = 0; frame < frames; frame++) ordinaryFrame(fixture);
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
        assertAll("joint saved Camera / live wall linkage / full-world replay", oracles);
    }

    private static void addWallOracles(List<Executable> oracles, String label,
                                      Observation actual, Observation expected) {
        oracles.add(() -> assertTrue(actual.linked(), label + " event reference is the registered managed wall"));
        oracles.add(() -> assertEquals(expected.walls(), actual.walls(), label + " managed wall pose/offset"));
        oracles.add(() -> assertEquals(expected.cachedWallPose(), actual.cachedWallPose(),
                label + " event-held wall pose/offset"));
        var nativePose = new WallPose(actual.eventOffsetPixels(),
                WALL_X_BASE - actual.eventOffsetPixels(), WALL_Y);
        oracles.add(() -> assertEquals(List.of(nativePose), actual.walls(),
                label + " managed wall reads the restored/current Events_bg offset"));
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
        var managed = GameServices.level().getObjectManager().activeObjectsOfType(HCZ2WallObjectInstance.class);
        var cached = cachedWall(owners.events());
        boolean linked = managed.size() == 1 && cached == managed.getFirst();
        return new Observation(fixture.camera().capture(),
                owners.events().getWallOffsetPixels(), owners.events().getWallOffsetFixed(),
                owners.events().getAct2BgRoutine(), owners.events().isWallMoving(),
                owners.events().isWallStopped(), owners.events().getShakeTimer(),
                owners.events().isEventsFg5(), owners.events().isCutsceneActive(),
                owners.handler().getHcz2BgPhase(), owners.handler().getShakeOffsetY(),
                managed.stream().map(TestS3kHczMovingWallShakeRewind::wallPose).toList(),
                cached == null ? null : wallPose(cached), linked);
    }

    private static void assertOriginalGeneration(HeadlessTestFixture fixture, Observation point, boolean moving) {
        assertEquals(4, point.bgRoutine(), "prerequisite: genuine wall-move event dispatch");
        assertEquals(SwScrlHcz.Hcz2BgPhase.WALL_CHASE, point.phase());
        assertEquals(moving, point.moving());
        assertFalse(point.stopped());
        assertEquals(0, point.shakeTimer());
        assertFalse(point.eventsFg5());
        assertFalse(point.cutscene());
        assertEquals(List.of(new WallPose(point.eventOffsetPixels(),
                WALL_X_BASE - point.eventOffsetPixels(), WALL_Y)), point.walls());
        assertEquals(point.handlerShake(), point.camera().shakeOffsetY(), "actual completed shake publication");
        if (moving) {
            assertTrue(point.eventOffsetFixed() < 0 && point.eventOffsetPixels() < 0);
        } else {
            assertTrue(fixture.sprite().getCentreX() < WALL_TRIGGER_X);
            assertEquals(0, point.eventOffsetFixed());
            assertEquals(0, point.eventOffsetPixels());
        }
    }

    private static void assertOriginalBinding(Observation a) {
        // This prerequisite is checked only before original A. Every restored
        // linkage and pose observation reaches the final contract assertAll.
        assertTrue(a.linked(), "prerequisite: original event holds the actual managed wall");
        assertEquals(a.walls().getFirst(), a.cachedWallPose());
    }

    private static HCZ2WallObjectInstance cachedWall(Sonic3kHCZEvents events) {
        try {
            var field = Sonic3kHCZEvents.class.getDeclaredField("wallObject");
            field.setAccessible(true); // reflection accessibility only, no gameplay-field write
            return (HCZ2WallObjectInstance) field.get(events);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("prerequisite: existing event wall-reference projection", failure);
        }
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
    private record Observation(CameraSnapshot camera, int eventOffsetPixels, int eventOffsetFixed,
                               int bgRoutine, boolean moving, boolean stopped, int shakeTimer,
                               boolean eventsFg5, boolean cutscene, SwScrlHcz.Hcz2BgPhase phase,
                               int handlerShake, List<WallPose> walls, WallPose cachedWallPose, boolean linked) { }
}
