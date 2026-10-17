package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R19 independent Obj_HCZSpinningColumn / sub_32784 contract. Declared component
 * geometry, real ROM factory/allocation, normal SST and SolidObjectFull contact;
 * no trace input, assigned slot, private rider seeding or cached-flag writes.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestHczSpinningColumnCachedRelease {
    private static final int X = 0x400;
    private static final int Y = 0x400;

    @Test
    void cachedOffscreenReleasePreservesStandingAndRecapturesAcrossWorldRecreation() {
        var fixture = capturedFixture();
        var objects = GameServices.level().getObjectManager();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var beforeVisibilityFlip = registry.capture();
        var beforePose = Pose.read();
        var original = column();

        publishVisibility(fixture, false);
        tickObjects(fixture);
        var firstDispatch = Rider.read();
        // On the corrected owner this is the native release boundary. Keeping
        // its actual snapshot also tests recreation on the deliberately old-red
        // implementation, without claiming that its held state is a release.
        var postVisibilityDispatch = registry.capture();
        var tail = twoOffscreenDispatches(fixture);
        var after = registry.capture();

        for (int replay = 0; replay < 2; replay++) {
            registry.restore(beforeVisibilityFlip);
            assertNotSame(original, column(), "force real object recreation");
            assertWorld(beforeVisibilityFlip, registry.capture());
            assertSame(column(), objects.getRidingObject(sidekick()), "relink actual solid binding");
            publishVisibility(fixture, false);
            tickObjects(fixture);
            assertEquals(firstDispatch, Rider.read());
            assertWorld(postVisibilityDispatch, registry.capture());
            assertEquals(tail, twoOffscreenDispatches(fixture));
            assertWorld(after, registry.capture());
        }
        for (int replay = 0; replay < 2; replay++) {
            var previous = column();
            registry.restore(postVisibilityDispatch);
            assertNotSame(previous, column());
            assertWorld(postVisibilityDispatch, registry.capture());
            assertEquals(tail, twoOffscreenDispatches(fixture));
            assertWorld(after, registry.capture());
        }

        // loc_327FC tests cached render_flags BEFORE routine/standing/hold.
        // loc_328AC sets Air, priority $100, control=0; loc_328BE clears only
        // the private held flag. Neither writes OnObj, pose or velocities.
        assertAll("native cached release and retained object-standing lifecycle",
                () -> assertTrue(firstDispatch.air(), "offscreen held player becomes airborne"),
                () -> assertFalse(firstDispatch.controlled(), "offscreen release clears control"),
                () -> assertFalse(firstDispatch.cpuAllowed()),
                () -> assertFalse(firstDispatch.movementSuppressed()),
                () -> assertTrue(firstDispatch.onObject(), "loc_328AC preserves OnObj"),
                () -> assertTrue(firstDispatch.ridingColumn(), "FullSolid offscreen skip preserves riding"),
                () -> assertTrue(firstDispatch.objectStanding(), "native column standing bit survives"),
                () -> assertEquals(beforePose, firstDispatch.pose(), "release precedes the shrinking-radius hold"),
                () -> assertEquals(2, firstDispatch.priority(), "$100 / $80"),
                // Inactive sub_32784 uses the retained COLUMN standing bit;
                // it may recapture while the player's cached flag is still false.
                () -> assertHeld(tail.get(0)),
                () -> assertTrue(tail.get(1).air()),
                () -> assertFalse(tail.get(1).controlled()),
                () -> assertTrue(tail.get(1).onObject()),
                () -> assertTrue(tail.get(1).ridingColumn()),
                () -> assertTrue(tail.get(1).objectStanding()));
    }

    @Test
    void visibleHeldControlAndJumpUseTheRealColumnSstAndSolidBinding() {
        var fixture = capturedFixture();
        var p2 = sidekick();
        var before = Pose.read();
        publishVisibility(fixture, true);
        tickObjects(fixture);
        assertHeld(Rider.read());
        assertEquals(before.y(), p2.getCentreY());
        assertEquals(before.xSub(), p2.getXSubpixelRaw());
        assertEquals(before.ySub(), p2.getYSubpixelRaw());

        // Existing logical controller entrypoint; no private rider or CPU state.
        p2.setJumpInputPressed(true, true);
        int releaseY = p2.getCentreY();
        tickObjects(fixture);
        assertAll("native newly pressed jump control",
                () -> assertFalse(p2.isObjectControlled()),
                () -> assertTrue(p2.getAir()),
                () -> assertTrue(p2.isJumping()),
                () -> assertTrue(p2.getRolling()),
                () -> assertEquals(releaseY, p2.getCentreY()),
                () -> assertEquals(before.xSub(), p2.getXSubpixelRaw()),
                () -> assertEquals(before.ySub(), p2.getYSubpixelRaw()),
                () -> assertEquals(-0x680, p2.getYSpeed()),
                () -> assertEquals(0, p2.getXSpeed()),
                () -> assertEquals(0, p2.getGSpeed()));
        // The following visible FullSolid checkpoint can unseat an airborne
        // rider. Its end-of-SST OnObj is not the loc_328AC release write.
    }

    @Test
    void releasedAirborneRideRetainsBubbleLockThroughNextNormalFrameAndWorldRecreation() {
        var fixture = capturedFixtureAfterVisibleCpuSlot(true);
        int producedLock = sidekick().getMoveLockTimer();
        assertTrue(producedLock > 0, "prerequisite: actual bubble collection supplied a positive lock");
        var result = releaseAndReplayNextNormalFrame(fixture);

        // loc_328AC retains Air + OnObj. loc_13872 dispatches from status&6;
        // Tails_Stand_Freespace never calls Player_SlopeRepel. Later column
        // recapture may clear Air again, but cannot consume that air-phase lock.
        // Keep these native timer oracles AFTER both forced recreation cycles.
        assertAll("released air phase retains the production lock",
                () -> assertEquals(producedLock, result.releasedLock()),
                () -> assertEquals(producedLock, result.nextFrame().moveLock()));
    }

    @Test
    void genuinelyGroundedBubbleLockDecrementsThroughNextNormalFrameAndWorldRecreation() {
        var fixture = landedFixture(true);
        assertFalse(sidekick().getAir(), "prerequisite: actual FullSolid ground landing");
        assertFalse(sidekick().isObjectControlled(), "prerequisite: capture has not run yet");
        int producedLock = sidekick().getMoveLockTimer();
        assertTrue(producedLock > 0, "prerequisite: actual bubble collection supplied a positive lock");
        var objects = GameServices.level().getObjectManager();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var grounded = registry.capture();
        var next = stepOneNormalFrame(fixture);
        var after = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            var previous = column();
            registry.restore(grounded);
            assertNotSame(previous, column(), "force actual grounded-world recreation");
            assertWorld(grounded, registry.capture());
            assertSame(column(), objects.getRidingObject(sidekick()));
            assertEquals(next, stepOneNormalFrame(fixture));
            assertWorld(after, registry.capture());
        }
        // Tails_Stand_Path calls Player_SlopeRepel; loc_11E86 decrements once.
        // This control rules out a blanket freeze of a legitimate ground lock.
        assertEquals(producedLock - 1, next.moveLock());
    }

    @Test
    void releasedAirborneRideWithNaturalZeroLockStaysZeroThroughNextNormalFrameAndWorldRecreation() {
        var fixture = capturedFixtureAfterVisibleCpuSlot(false);
        assertEquals(0, sidekick().getMoveLockTimer(), "prerequisite: naturally zero, never reset by this test");
        var result = releaseAndReplayNextNormalFrame(fixture);
        assertAll("a real zero-lock release does not arm a timer",
                () -> assertEquals(0, result.releasedLock()),
                () -> assertEquals(0, result.nextFrame().moveLock()));
    }

    private static LockLifecycle releaseAndReplayNextNormalFrame(HeadlessTestFixture fixture) {
        var objects = GameServices.level().getObjectManager();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var held = registry.capture();
        var beforePose = Pose.read();
        publishVisibility(fixture, false);
        tickObjects(fixture); // real owning release and offscreen FullSolid checkpoint
        var releasedRider = Rider.read();
        assertAll("prerequisite: native release, not a manufactured airborne pose",
                () -> assertTrue(releasedRider.air()),
                () -> assertTrue(releasedRider.onObject()),
                () -> assertTrue(releasedRider.ridingColumn()),
                () -> assertTrue(releasedRider.objectStanding()),
                () -> assertFalse(releasedRider.controlled()),
                () -> assertFalse(releasedRider.cpuAllowed()),
                () -> assertFalse(releasedRider.movementSuppressed()),
                () -> assertEquals(beforePose, releasedRider.pose()));
        int releasedLock = sidekick().getMoveLockTimer();
        var released = registry.capture();
        var next = stepOneNormalFrame(fixture);
        var after = registry.capture();

        for (int replay = 0; replay < 2; replay++) {
            var previous = column();
            registry.restore(held);
            assertNotSame(previous, column(), "force actual pre-release world recreation");
            assertWorld(held, registry.capture());
            assertSame(column(), objects.getRidingObject(sidekick()), "relink actual riding owner");
            publishVisibility(fixture, false);
            tickObjects(fixture);
            assertEquals(releasedRider, Rider.read());
            assertEquals(releasedLock, sidekick().getMoveLockTimer(), "replay equality, not native timer oracle");
            assertWorld(released, registry.capture());
            assertEquals(next, stepOneNormalFrame(fixture));
            assertWorld(after, registry.capture());
        }
        for (int replay = 0; replay < 2; replay++) {
            var previous = column();
            registry.restore(released);
            assertNotSame(previous, column(), "force actual released-world recreation");
            assertWorld(released, registry.capture());
            assertSame(column(), objects.getRidingObject(sidekick()));
            assertEquals(next, stepOneNormalFrame(fixture));
            assertWorld(after, registry.capture());
        }
        return new LockLifecycle(releasedLock, next);
    }

    private static NormalFrame stepOneNormalFrame(HeadlessTestFixture fixture) {
        var p2 = sidekick();
        assertNormalPlayerPrerequisites();
        // Query independent copies of the loaded floor-sensor geometry. These
        // test-owned scans use the real ROM/path/dual-plane lookup and write no
        // player sensor or gameplay state. Reject an unrelated terrain landing.
        var floors = p2.getGroundSensors();
        assertNotNull(floors, "prerequisite: native floor sensor pair exists");
        assertEquals(2, floors.length, "prerequisite: both loaded floor sensors");
        for (var floor : floors) {
            assertEquals(Direction.DOWN, floor.getDirection());
            var probe = new GroundSensor(p2, Direction.DOWN, floor.getX(), floor.getY(), true);
            for (short dx = -1; dx <= 1; dx++) {
                var result = probe.scan(dx, (short) 1);
                assertNotNull(result, "prerequisite: loaded terrain lookup available");
                assertTrue(result.distance() > 0, "prerequisite: clear terrain below declared object contact");
            }
        }
        int beforeLevelFrame = GameServices.level().getFrameCounter() & 0xFFFF;
        fixture.stepFrame(false, false, false, false, false); // unchanged normal CPU -> player -> objects
        assertEquals((beforeLevelFrame + 1) & 0xFFFF, GameServices.level().getFrameCounter() & 0xFFFF,
                "prerequisite: exactly one ordinary gameplay frame");
        assertSame(p2, sidekick(), "prerequisite: no player replacement/respawn");
        assertNormalPlayerPrerequisites();
        assertHeld(Rider.read()); // actual subsequent native standing-bit recapture
        return new NormalFrame(Rider.read(), p2.getMoveLockTimer(), p2.getCpuController().getState());
    }

    private static void assertNormalPlayerPrerequisites() {
        var p2 = sidekick();
        assertTrue(p2.isCpuControlled());
        assertFalse(p2.getDead(), "prerequisite: no death/respawn path");
        assertFalse(p2.isHurt(), "prerequisite: no hurt landing");
        assertEquals(0, p2.getDoubleJumpFlag(), "prerequisite: no flight/ability takeover");
        assertNotNull(p2.getCpuController());
        var state = p2.getCpuController().getState();
        assertTrue(state == SidekickCpuController.State.NORMAL || state == SidekickCpuController.State.PANIC,
                () -> "prerequisite: ordinary CPU state, not " + state);
        assertSame(column(), GameServices.level().getObjectManager().getRidingObject(p2));
        assertTrue(GameServices.level().getObjectManager().hasObjectStandingBit(p2, column()));
        assertTrue(p2.isOnObject());
    }

    private record NormalFrame(Rider rider, int moveLock, SidekickCpuController.State cpuState) { }
    private record LockLifecycle(int releasedLock, NormalFrame nextFrame) { }

    private static HeadlessTestFixture capturedFixture() {
        return capturedFixture(false);
    }

    private static HeadlessTestFixture capturedFixture(boolean collectBubble) {
        var fixture = landedFixture(collectBubble);
        tickObjects(fixture); // native held capture from prior standing contact
        assertHeld(Rider.read());
        return fixture;
    }

    private static HeadlessTestFixture capturedFixtureAfterVisibleCpuSlot(boolean collectBubble) {
        var fixture = capturedFixture(collectBubble);
        var p2 = sidekick();
        var cpu = p2.getCpuController();
        assertNotNull(cpu, "prerequisite: real captured P2 controller");
        publishVisibility(fixture, true);
        int beforeLevelFrame = GameServices.level().getFrameCounter() & 0xFFFF;
        fixture.stepFrame(false, false, false, false, false);
        assertEquals((beforeLevelFrame + 1) & 0xFFFF, GameServices.level().getFrameCounter() & 0xFFFF,
                "prerequisite: one ordinary visible held CPU slot");
        assertSame(p2, sidekick(), "prerequisite: no warm-slot player replacement");
        assertSame(cpu, p2.getCpuController(), "prerequisite: same warm-slot CPU controller");
        assertNormalPlayerPrerequisites();
        assertHeld(Rider.read());
        assertTrue(p2.hasRenderFlagOnScreenState(), "prerequisite: warm-slot cached flag valid");
        assertTrue(p2.isRenderFlagOnScreen(), "prerequisite: warm-slot actual visible publication");
        // Native loc_326B6 installs code pointer $000326B6. The ordinary
        // visible CPU slot owns Tails_CPU_interact's high-word refresh via
        // loc_13F2E; this only reads its stored projection, never refreshes it.
        assertEquals(0x0003, cpu.getDiagnosticInteractId(),
                "prerequisite: actual CPU slot latched the column code high word");
        return fixture;
    }

    private static HeadlessTestFixture landedFixture(boolean collectBubble) {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 1)
                .withSkippedZoneIntro().build();
        fixture.sprite().setCentreX((short) 0x600);
        fixture.sprite().setCentreY((short) 0x580);
        var objects = GameServices.level().getObjectManager();
        var factory = GameServices.module().createObjectRegistry();
        objects.createDynamicObject(() -> factory.create(new ObjectSpawn(
                X, Y, Sonic3kObjectIds.HCZ_SPINNING_COLUMN, 0, 0, false, 0)));
        fixture.camera().setFrozen(true);
        fixture.camera().setX((short) (X - 0x80));
        fixture.camera().setY((short) (Y - 0x80));
        fixture.stepIdleFrames(1); // ordinary dynamic admission/first dispatch
        var column = column();
        assertTrue(column.getSlotIndex() >= 0, "ordinary allocator supplies the SST slot");
        assertFalse(column.isSkipSolidContactThisFrame());

        var p2 = sidekick();
        assertTrue(p2.isCpuControlled());
        ObjectControlState.none().applyTo(p2);
        p2.setControlLocked(false);
        p2.setOnObject(false);
        p2.setRolling(false);
        p2.setAir(true);
        p2.setJumpInputPressed(false, false);
        if (collectBubble) {
            collectOrdinaryLargeBubble(fixture);
        }
        p2.setCentreX((short) (X + 8));
        // FullSolid top window: relY = y - objY + 4 + d2 + y_radius.
        // Choose overlap 8 (inside native [0,$10)), not a replay pose.
        p2.setCentreY((short) (Y - 4 - column.getSolidParams().airHalfHeight() - p2.getYRadius() + 8));
        if (collectBubble) {
            GameServices.level().updatePlayableWaterStateForCurrentLevel(p2);
        }
        p2.setSubpixelRaw(0x3A00, 0x7B00);
        p2.setXSpeed((short) 0);
        p2.setYSpeed((short) 0x100);
        p2.setGSpeed((short) 0);
        publishVisibility(fixture, true);
        tickObjects(fixture); // actual FullSolid landing -> actual listener
        assertTrue(p2.isOnObject(), "setup must land through production FullSolid");
        assertSame(column(), objects.getRidingObject(p2));
        assertTrue(objects.hasObjectStandingBit(p2, column()));
        assertFalse(p2.isObjectControlled(), "landing precedes the next owning capture dispatch");
        return fixture;
    }

    private static void collectOrdinaryLargeBubble(HeadlessTestFixture fixture) {
        var p2 = sidekick();
        assertEquals(0, p2.getMoveLockTimer(), "prerequisite: no earlier positive-lock producer");
        var level = GameServices.level();
        var water = GameServices.water();
        int zone = level.getFeatureZoneId();
        int act = level.getFeatureActId();
        assertTrue(water.hasWater(zone, act), "prerequisite: real ROM-loaded water");
        int bubbleY = water.getWaterLevelY(zone, act) + 0x80;
        assertTrue(bubbleY > Y + 0x80 && bubbleY < 0x7F00,
                "prerequisite: submerged bubble inlet separated from the column");
        short cameraX = fixture.camera().getX();
        short cameraY = fixture.camera().getY();
        fixture.camera().setX((short) (X - 0x80));
        fixture.camera().setY((short) (bubbleY - 0x80));
        var factory = GameServices.module().createObjectRegistry();
        var allocated = level.getObjectManager().createDynamicObject(() -> factory.create(
                new ObjectSpawn(X, bubbleY, Sonic3kObjectIds.BUBBLER, 2, 0, false, 0)));
        var bubble = assertInstanceOf(BubblerObjectInstance.class, allocated, "real ROM object registry");
        assertTrue(bubble.getSlotIndex() >= 0, "ordinary bubble SST allocation");
        try {
            // Ani_Bubbler subtype2: delay $0E, frames2..6, then routine advance.
            // This is only a bounded setup deadline; no timer/frame is assigned.
            for (int dispatch = 0; dispatch < 6 * (0x0E + 1) && p2.getMoveLockTimer() == 0; dispatch++) {
                // Declared pickup overlap, relative to the real rising/wobbling
                // bubble. No private animation/inhalable/consumed state is read.
                p2.setCentreX((short) bubble.getX());
                p2.setCentreY((short) (bubble.getY() + 1));
                level.updatePlayableWaterStateForCurrentLevel(p2);
                assertTrue(p2.isInWater(), "prerequisite: ordinary water owner admitted pickup");
                assertFalse(p2.isObjectControlled(), "prerequisite: collection precedes column capture");
                tickObjects(fixture);
            }
            // loc_2FBB2 collection really calls replenishAir and writes35. This
            // validates the producer, never seeds native29 or any move-lock field.
            assertEquals(35, p2.getMoveLockTimer(), "prerequisite: actual large-bubble collection");
        } finally {
            fixture.camera().setX(cameraX);
            fixture.camera().setY(cameraY);
        }
    }

    private static List<Rider> twoOffscreenDispatches(HeadlessTestFixture fixture) {
        var results = new ArrayList<Rider>();
        for (int dispatch = 0; dispatch < 2; dispatch++) {
            publishVisibility(fixture, false);
            tickObjects(fixture);
            results.add(Rider.read());
        }
        return List.copyOf(results);
    }

    private static void publishVisibility(HeadlessTestFixture fixture, boolean visible) {
        var p2 = sidekick();
        // Only the copied camera inlet changes. The physical camera/column
        // remain admitted. Use the body's actual loaded width and publisher.
        fixture.camera().setXCopy((short) (visible
                ? p2.getRenderCentreX() - 0x80
                : p2.getRenderCentreX() + p2.getRenderFlagWidthPixels() + 1));
        fixture.camera().setYCopy((short) (p2.getRenderCentreY() - 0x80));
        GameServices.sprites().refreshPlayableRenderFlags(fixture.camera());
        assertTrue(p2.hasRenderFlagOnScreenState());
        assertEquals(visible, p2.isRenderFlagOnScreen(), "real copied-camera/body publication");
    }

    private static void tickObjects(HeadlessTestFixture fixture) {
        var objects = GameServices.level().getObjectManager();
        objects.snapshotTouchResponseState();
        // Real Process_Sprites/SST ordering, including the column's own normal
        // inline FullSolid call. No direct onSolidContact/updateRider calls.
        objects.update(fixture.camera().getX(), fixture.sprite(), List.of(sidekick()),
                GameServices.level().getFrameCounter(), false, true, false);
    }

    private static AbstractPlayableSprite sidekick() {
        return GameServices.sprites().getSidekicks().getFirst();
    }

    private static HCZSpinningColumnObjectInstance column() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o instanceof HCZSpinningColumnObjectInstance
                        && o.getX() == X && o.getY() == Y && o.getSpawn().subtype() == 0)
                .map(HCZSpinningColumnObjectInstance.class::cast).findFirst().orElseThrow();
    }

    private static void assertHeld(Rider rider) {
        assertAll("normal captured held rider", () -> assertTrue(rider.controlled()),
                () -> assertTrue(rider.cpuAllowed()), () -> assertTrue(rider.movementSuppressed()),
                () -> assertFalse(rider.air()), () -> assertTrue(rider.onObject()),
                () -> assertTrue(rider.ridingColumn()), () -> assertTrue(rider.objectStanding()));
    }

    private record Pose(short x, short y, int xSub, int ySub, short vx, short vy, short ground) {
        static Pose read() {
            var p2 = sidekick();
            return new Pose(p2.getCentreX(), p2.getCentreY(), p2.getXSubpixelRaw(), p2.getYSubpixelRaw(),
                    p2.getXSpeed(), p2.getYSpeed(), p2.getGSpeed());
        }
    }

    private record Rider(boolean air, boolean onObject, boolean controlled, boolean cpuAllowed,
                         boolean movementSuppressed, int priority, boolean ridingColumn, boolean objectStanding, Pose pose) {
        static Rider read() {
            var p2 = sidekick();
            return new Rider(p2.getAir(), p2.isOnObject(), p2.isObjectControlled(),
                    p2.isObjectControlAllowsCpu(), p2.isObjectControlSuppressesMovement(),
                    p2.getPriorityBucket(), GameServices.level().getObjectManager().getRidingObject(p2) == column(),
                    GameServices.level().getObjectManager().hasObjectStandingBit(p2, column()), Pose.read());
        }
    }

    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }
}
