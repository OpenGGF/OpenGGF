package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
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

    private static HeadlessTestFixture capturedFixture() {
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
        p2.setCentreX((short) (X + 8));
        // FullSolid top window: relY = y - objY + 4 + d2 + y_radius.
        // Choose overlap 8 (inside native [0,$10)), not a replay pose.
        p2.setCentreY((short) (Y - 4 - column.getSolidParams().airHalfHeight() - p2.getYRadius() + 8));
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
        tickObjects(fixture); // native held capture from prior standing contact
        assertHeld(Rider.read());
        return fixture;
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
