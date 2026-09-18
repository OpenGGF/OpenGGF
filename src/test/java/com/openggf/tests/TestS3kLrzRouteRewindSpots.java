package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.LrzBigDoorObjectInstance;
import com.openggf.game.sonic3k.objects.LrzFallingSpikeObjectInstance;
import com.openggf.game.sonic3k.objects.LrzRockCrusherObjectInstance;
import com.openggf.game.sonic3k.objects.badniks.IwamodokiBadnikInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rewind spots for the Lava Reef objects whose state is only reachable on real terrain, with a
 * real object graph and a real level load: the {@code $1A} big door opening, the {@code $1D}
 * shooting trigger latching through {@code sub_42EC0}, the {@code $18} falling spike crossing its
 * landing boundary, the {@code $9C} rock crusher rumbling, and the {@code $9A} Iwamodoki's lit
 * fuse.
 *
 * <p>Each spot captures, diverges by one frame, restores, compares the restored snapshot against
 * the captured one, then replays the same frame and compares against the diverged one. That is the
 * before / active / after shape the campaign uses everywhere else, but here through
 * {@code RewindRegistry}'s whole composite rather than an object harness, because these states
 * span the object, the level trigger array and the runtime state.
 *
 * <p><b>Coverage limit, stated plainly.</b> Each case starts the act at a position taken from the
 * Sonic + Tails fixture's own recorded route rows, so the terrain, the object placements and the
 * camera are the real ones -- but the act is entered there, not walked to from the level start.
 * These are route-position spots, not cold-route spots. The cold route itself is still the
 * campaign's open item.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzRouteRewindSpots {

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    /**
     * {@code Obj_LRZFallingSpike}: {@code loc_42898} lets go once the nearer player is within
     * {@code $2F(a0)} pixels horizontally, then {@code loc_428D6} is {@code MoveSprite} plus
     * {@code ObjCheckFloorDist} -- so the landing boundary needs real floor data underneath, which
     * is why this spot cannot live in the object harness.
     */
    @Test
    void theFallingSpikeCrossesItsLandingBoundaryAndRewinds() {
        HeadlessTestFixture fixture = load(0x0216, 0x0533);
        LrzFallingSpikeObjectInstance spike = advanceUntil(fixture, 600,
                LrzFallingSpikeObjectInstance.class,
                s -> s.isFalling(), false, false, false, true, false);
        assertTrue(spike.isFalling(), "the spike must be in mid-fall for this spot to mean anything");

        rewindSpot(fixture, "mid-fall", false, false, false, true, false);

        LrzFallingSpikeObjectInstance landed = advanceUntil(fixture, 600,
                LrzFallingSpikeObjectInstance.class,
                LrzFallingSpikeObjectInstance::isLanded, false, false, false, false, false);
        assertTrue(landed.isLanded(), "ObjCheckFloorDist must find the floor under the placement");
        rewindSpot(fixture, "landed", false, false, false, false, false);
    }

    /** {@code Obj_LRZBigDoor}'s opening ramp, captured while it is moving. */
    @Test
    void theBigDoorOpensOnARouteAndRewinds() {
        HeadlessTestFixture fixture = load(0x0AEC, 0x0268);
        LrzBigDoorObjectInstance door = advanceUntil(fixture, 600,
                LrzBigDoorObjectInstance.class,
                d -> d.isOpening() || d.isFullyOpen(), false, false, true, false, false);
        assertTrue(door.isOpening() || door.isFullyOpen(),
                "the big door must have started its ramp; openTimer=" + door.openTimer());
        rewindSpot(fixture, "big door opening", false, false, true, false, false);
    }

    /**
     * {@code Obj_LRZRockCrusher} once {@code Check_CameraInRange} has released its rumble.
     *
     * <p>Whole-composite, including the four {@code S3kCameraGradualObjectInstance} children
     * {@code Child7_ChangeLevSize} creates. Those children used to vanish on restore, which is
     * why this spot was once object-level; the cause was a missing probe constructor on the
     * child class rather than an engine defect, and
     * {@link #dynamicallyCreatedChildrenSurviveACompositeRestore} now states it directly.
     */
    @Test
    void theRockCrusherRumblesOnARouteAndRewinds() {
        HeadlessTestFixture fixture = load(0x0F33, 0x0721);
        LrzRockCrusherObjectInstance crusher = advanceUntil(fixture, 900,
                LrzRockCrusherObjectInstance.class,
                c -> c.routine() >= 4, false, false, false, true, false);
        assertTrue(crusher.routine() >= 4,
                "the crusher must have left its waiting routine; routine=" + crusher.routine());
        rewindSpot(fixture, "crusher rumbling", false, false, false, false, false);
    }

    /**
     * {@code Obj_Iwamodoki} with its fuse lit, before it detonates. Whole-composite; the
     * Fireworm segments alive at this position used to be dropped by the restore.
     */
    @Test
    void theIwamodokiFuseLightsOnARouteAndRewinds() {
        HeadlessTestFixture fixture = load(0x0A65, 0x06D2);
        IwamodokiBadnikInstance bomb = advanceUntil(fixture, 600,
                IwamodokiBadnikInstance.class,
                b -> b.routine() >= 4 && !b.detonated(), false, false, false, true, false);
        assertTrue(bomb.routine() >= 4 && !bomb.detonated(),
                "the fuse must be lit and not yet blown; routine=" + bomb.routine());
        rewindSpot(fixture, "iwamodoki fuse", false, false, false, false, false);
    }

    /**
     * The engine-level restore gap recorded in {@code s3k-known-bugs}: dynamically created
     * children vanish from the object manager after a composite restore. This states it as a
     * count, so it fails for the reason it names rather than through a whole-composite diff.
     *
     * <p>Both parents implement {@code RewindRecreatable}, so the restore path reaches
     * {@code ObjectRewindDynamicCodecs.genericRecreate}, which must first build a probe
     * instance from one of its known constructor signatures before it can call
     * {@code recreateForRewind}.
     */
    @Test
    void dynamicallyCreatedChildrenSurviveACompositeRestore() {
        HeadlessTestFixture fixture = load(0x0F33, 0x0721);
        advanceUntil(fixture, 900, LrzRockCrusherObjectInstance.class,
                c -> c.routine() >= 4, false, false, false, true, false);
        assertChildrenSurvive(fixture, "crusher children",
                com.openggf.game.sonic3k.objects.S3kCameraGradualObjectInstance.class);
    }

    /** The Fireworm's four segments, the other half of the same gap. */
    @Test
    void firewormSegmentsSurviveACompositeRestore() {
        HeadlessTestFixture fixture = load(0x0A65, 0x06D2);
        advanceUntil(fixture, 900,
                com.openggf.game.sonic3k.objects.badniks.FirewormSegmentInstance.class,
                s -> true, false, false, false, true, false);
        assertChildrenSurvive(fixture, "fireworm segments",
                com.openggf.game.sonic3k.objects.badniks.FirewormSegmentInstance.class);
    }

    private static void assertChildrenSurvive(HeadlessTestFixture fixture, String label,
            Class<?> childType) {
        int before = live(childType).size();
        assertTrue(before > 0, label + ": no children to begin with, so this spot proves nothing");
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot snapshot = registry.capture();
        fixture.stepFrame(false, false, false, false, false);
        registry.restore(snapshot);
        assertEquals(before, live(childType).size(), label + " present after restore");
    }

    // ===== harness =====

    private static <T extends ObjectInstance> T advanceUntil(HeadlessTestFixture fixture,
            int maxFrames, Class<T> type, Predicate<T> done,
            boolean up, boolean down, boolean left, boolean right, boolean jump) {
        T last = null;
        for (int frame = 0; frame < maxFrames; frame++) {
            List<T> live = live(type);
            for (T candidate : live) {
                if (done.test(candidate)) {
                    return candidate;
                }
            }
            if (!live.isEmpty()) {
                last = live.getFirst();
            }
            fixture.stepFrame(up, down, left, right, jump);
        }
        if (last == null) {
            throw new AssertionError("no live " + type.getSimpleName()
                    + " within " + maxFrames + " frames; player at ("
                    + Integer.toHexString(fixture.sprite().getCentreX() & 0xFFFF) + ","
                    + Integer.toHexString(fixture.sprite().getCentreY() & 0xFFFF) + ")");
        }
        return last;
    }

    private static <T> List<T> live(Class<T> type) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    /** Capture, diverge one frame, restore, compare; then replay that frame and compare. */
    private static void rewindSpot(HeadlessTestFixture fixture, String label,
            boolean up, boolean down, boolean left, boolean right, boolean jump) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepFrame(up, down, left, right, jump);
        CompositeSnapshot after = registry.capture();
        assertDiffers(before, after, label);

        registry.restore(before);
        assertSame(before, registry.capture(), label + " restore");
        fixture.stepFrame(up, down, left, right, jump);
        assertSame(after, registry.capture(), label + " forward replay");
    }

    /** A spot that cannot tell a restore from a no-op proves nothing, so the frame must move. */
    private static void assertDiffers(CompositeSnapshot a, CompositeSnapshot b, String label) {
        for (String key : a.entries().keySet()) {
            if (!RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty()) {
                return;
            }
        }
        throw new AssertionError(label + ": the diverging frame changed nothing at all");
    }

    private static void assertSame(CompositeSnapshot a, CompositeSnapshot b, String label) {
        assertEquals(a.entries().keySet(), b.entries().keySet(), label + " snapshot keys");
        for (String key : a.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)));
        }
    }

    private static HeadlessTestFixture load(int x, int y) {
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0)
                .startPosition((short) x, (short) y)
                .startPositionIsCentre()
                .build();
    }
}
